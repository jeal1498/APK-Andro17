package com.minimal.launcher.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** UI checks for the features added on top of the base launcher (Frecuentes, pares, gestos…). */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class FeaturesUiTest : LauncherUiBase() {

    private fun keyOf(pkg: String) = app.container.apps.apps.value.first { it.packageName == pkg }.key

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
