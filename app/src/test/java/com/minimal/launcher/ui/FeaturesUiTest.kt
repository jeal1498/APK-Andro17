package com.minimal.launcher.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.onFirst
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Base for feature UI tests: one test per class (each class gets a fresh JVM, see build.gradle.kts). */
abstract class FeatureUiBase : LauncherUiBase() {
    protected fun keyOf(pkg: String) = app.container.apps.apps.value.first { it.packageName == pkg }.key
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class FrequentRowUiTest : FeatureUiBase() {
    @Test fun frequentRowAppearsAfterLaunchesAndEnablesEditMode() {
        launch()
        repeat(3) { app.container.usage.record(keyOf("com.whatsapp")) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("frequent_home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Frecuentes").performTouchInput { longClick(center) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("edit_mode_bar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit_done").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("edit_mode_bar").fetchSemanticsNodes().isEmpty() }
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class PairUiTest : FeatureUiBase() {
    @Test fun pairShowsInCategoryOfFirstAppAndAsksForAccessibility() {
        launch()
        kotlinx.coroutines.runBlocking { app.container.pairs.create(listOf(keyOf("com.whatsapp"), keyOf("com.instagram.android"))) }
        compose.onNodeWithTag("home").performTouchInput { swipeUp() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("drawer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_categories").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("category_Comunicación").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("category_Comunicación").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pair_icon", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("pair_icon", useUnmergedTree = true).onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("accessibility_dialog").fetchSemanticsNodes().isNotEmpty() }
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class GestureUiTest : FeatureUiBase() {
    @Test fun customGesturesRunTheirActions() {
        launch()
        kotlinx.coroutines.runBlocking {
            app.container.settings.setGesture(com.minimal.launcher.gestures.HomeGesture.SWIPE_UP, com.minimal.launcher.gestures.GestureAction(com.minimal.launcher.gestures.GestureAction.Type.EDIT_MODE))
            app.container.settings.setGesture(com.minimal.launcher.gestures.HomeGesture.SWIPE_DOWN, com.minimal.launcher.gestures.GestureAction(com.minimal.launcher.gestures.GestureAction.Type.DRAWER))
        }
        compose.waitUntil(5_000) { kotlinx.coroutines.runBlocking { app.container.settings.current().gesture(com.minimal.launcher.gestures.HomeGesture.SWIPE_UP).type } == com.minimal.launcher.gestures.GestureAction.Type.EDIT_MODE }
        compose.waitForIdle()
        compose.onNodeWithTag("home").performTouchInput { swipeUp(startY = centerY + 200f, endY = centerY - 400f) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("edit_mode_bar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("edit_done").performClick()
        compose.onNodeWithTag("home").performTouchInput { swipeDown(startY = centerY - 400f, endY = centerY + 200f) }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("drawer").fetchSemanticsNodes().isNotEmpty() }
    }
}
