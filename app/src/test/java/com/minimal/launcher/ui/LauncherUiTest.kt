package com.minimal.launcher.ui

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.minimal.launcher.LauncherApplication
import com.minimal.launcher.MainActivity
import com.minimal.launcher.TestApps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalTestApi::class)
abstract class LauncherUiBase {

    @get:Rule val compose: ComposeTestRule = createEmptyComposeRule()

    protected val app = ApplicationProvider.getApplicationContext<LauncherApplication>()

    private var scenario: ActivityScenario<MainActivity>? = null

    @org.junit.After fun closeScenario() { scenario?.close() }

    // Robolectric may resolve labels to package names; match both.
    protected val whatsapp = androidx.compose.ui.test.hasText("whatsapp", substring = true, ignoreCase = true)

    protected fun launch(): ActivityScenario<MainActivity> {
        TestApps.installSample(app)
        runBlocking { app.container.apps.reloadAll() }
        val scenario = ActivityScenario.launch(MainActivity::class.java).also { this.scenario = it }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dock").fetchSemanticsNodes().isNotEmpty() }
        return scenario
    }

    private fun bounds(n: SemanticsNodeInteraction): Rect = n.fetchSemanticsNode().boundsInRoot

    private fun assertInside(child: Rect, parent: Rect, what: String) {
        val eps = 1.5f
        assertTrue("$what $child outside $parent", child.left >= parent.left - eps && child.top >= parent.top - eps &&
            child.right <= parent.right + eps && child.bottom <= parent.bottom + eps)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openDrawer() {
        if (compose.onAllNodesWithTag("search_pill").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("search_pill").performClick()
        } else {
            compose.onNodeWithTag("home").performTouchInput { swipeUp() }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("drawer").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Full check of one window configuration: home, drawer categories, a category and all apps. */
    protected fun checkScenario(name: String, expectedColumns: Int) {
        launch()
        compose.waitUntil(10_000) { runBlocking { app.container.settings.current().homeColumns > 0 } }
        val settings = runBlocking { app.container.settings.current() }
        assertEquals("$name home columns", expectedColumns, settings.homeColumns)

        val root = bounds(compose.onRoot())
        assertInside(bounds(compose.onNodeWithTag("dock")), root, "$name dock")
        assertInside(bounds(compose.onNodeWithTag("home")), root, "$name home")
        screenshot("${name}_home")

        openDrawer()
        compose.onNodeWithTag("tab_categories").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("category_grid").fetchSemanticsNodes().isNotEmpty() }
        val grid = bounds(compose.onNodeWithTag("category_grid"))
        assertInside(grid, root, "$name category grid")
        compose.onAllNodes(hasTestTag("category_grid")).fetchSemanticsNodes()
        compose.onNodeWithTag("category_Comunicación").assertExistsCompat()
        assertInside(bounds(compose.onNodeWithTag("category_Comunicación")), grid, "$name category card")
        screenshot("${name}_drawer_categories")

        compose.onNodeWithTag("category_Comunicación").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("category_detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(whatsapp).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
        screenshot("${name}_category_detail")
        compose.onNodeWithTag("category_back").performClick()

        compose.onNodeWithTag("tab_all").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("all_apps").fetchSemanticsNodes().isNotEmpty() }
        assertInside(bounds(compose.onNodeWithTag("all_apps")), root, "$name all apps")
        screenshot("${name}_drawer_all")
    }

    private fun SemanticsNodeInteraction.assertExistsCompat() = fetchSemanticsNode()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherInteractionTest : LauncherUiBase() {
    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun searchFiltersAndSwipeOpensDrawer() {
        launch()
        compose.onNodeWithTag("home").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("drawer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("drawer_search").performTextInput("whats")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("search_results").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodes(whatsapp and hasNoTag("drawer_search")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("spotify", substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("drawer_search").performTextClearance()
        compose.onNodeWithTag("drawer_search").performTextInput("qqqqq")
        compose.onNodeWithText("No se encontraron apps", substring = true).fetchSemanticsNode()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun longPressOnEmptyHomeOpensMenuAndWidgetPicker() {
        launch()
        compose.onNodeWithTag("home").performTouchInput { longClick(center) }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Widgets")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes de inicio").fetchSemanticsNode()
        compose.onNodeWithText("Widgets").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("widget_picker").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reloj y fecha").fetchSemanticsNode()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun defaultLayoutHasClockAndPersists() {
        launch()
        // Never block the main looper here: the ViewModel works on Dispatchers.Main.
        compose.waitUntil(10_000) { runBlocking { app.container.home.allItems().isNotEmpty() } }
        compose.waitUntil(10_000) { runBlocking { app.container.settings.current().defaultsCreated } }
        val items = runBlocking { app.container.home.allItems() }
        assertTrue(items.any { it.type == com.minimal.launcher.data.db.HomeItemEntity.TYPE_CLOCK })
        assertTrue(runBlocking { app.container.settings.current().defaultsCreated })
    }
}

private fun hasNoTag(tag: String) = androidx.compose.ui.test.SemanticsMatcher("no tag $tag") {
    androidx.compose.ui.semantics.SemanticsProperties.TestTag !in it.config || it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] != tag
}

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class PhonePortraitTest : LauncherUiBase() { @Test fun scenario() = checkScenario("phone_portrait", 4) }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class PhoneLandscapeTest : LauncherUiBase() { @Test fun scenario() = checkScenario("phone_landscape", 4) }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
class TabletPortraitTest : LauncherUiBase() { @Test fun scenario() = checkScenario("tablet_portrait", 6) }

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
class TabletLandscapeTest : LauncherUiBase() { @Test fun scenario() = checkScenario("tablet_landscape", 6) }
