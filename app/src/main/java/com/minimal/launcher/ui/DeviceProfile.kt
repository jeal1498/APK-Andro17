package com.minimal.launcher.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.minimal.launcher.data.prefs.LauncherSettings
import kotlin.math.roundToInt

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Layout decisions derived from the window size class (official breakpoints: 600dp / 840dp width,
 * 480dp height) plus the device's smallest width. Recomputed on rotation / window resize.
 */
@Immutable
data class DeviceProfile(
    val widthDp: Int,
    val heightDp: Int,
    val widthClass: WidthClass,
    val compactHeight: Boolean,
    val isTablet: Boolean,
    val isLandscape: Boolean,
    val drawerColumns: Int,
    val categoryColumns: Int,
    val homePadding: Dp,
    val maxIconSize: Dp,
    val drawerIconSize: Dp,
    val drawerMaxWidth: Dp,
) {
    /** Phone in landscape: dock moves to the side and the search pill hides to save height. */
    val dockOnSide: Boolean get() = compactHeight && !isTablet && isLandscape

    companion object {
        fun defaultHomeGrid(smallestWidthDp: Int, heightDp: Int): Pair<Int, Int> = when {
            smallestWidthDp >= 840 -> 7 to 5
            smallestWidthDp >= 600 -> 6 to 5
            // Phones: 4×5 like stock Android; configurable in settings.
            else -> 4 to 5
        }

        fun compute(
            widthDp: Int, heightDp: Int, smallestWidthDp: Int, widthClass: WidthClass, compactHeight: Boolean,
            settings: LauncherSettings,
        ): DeviceProfile {
            val isTablet = smallestWidthDp >= 600
            val landscape = widthDp > heightDp
            val contentWidth = when (widthClass) {
                WidthClass.EXPANDED -> (widthDp * 0.86f).coerceAtMost(1200f)
                WidthClass.MEDIUM -> widthDp * 0.92f
                WidthClass.COMPACT -> widthDp.toFloat()
            }
            val autoDrawer = (contentWidth / 104f).toInt().coerceIn(4, 9)
            val drawerCols = if (settings.drawerColumns > 0) {
                if (landscape && !isTablet) (settings.drawerColumns * 1.7f).roundToInt() else settings.drawerColumns
            } else autoDrawer
            val autoCategories = (contentWidth / 125f).toInt().coerceIn(2, 6)
            val categoryCols = if (settings.categoryColumns > 0) {
                if (landscape && !isTablet) (settings.categoryColumns * 1.7f).roundToInt() else settings.categoryColumns
            } else autoCategories
            return DeviceProfile(
                widthDp = widthDp,
                heightDp = heightDp,
                widthClass = widthClass,
                compactHeight = compactHeight,
                isTablet = isTablet,
                isLandscape = landscape,
                drawerColumns = drawerCols.coerceIn(3, 12),
                categoryColumns = categoryCols.coerceIn(2, 8),
                homePadding = when (widthClass) {
                    WidthClass.EXPANDED -> (widthDp * 0.05f).dp
                    WidthClass.MEDIUM -> (widthDp * 0.04f).dp
                    WidthClass.COMPACT -> 8.dp
                },
                maxIconSize = if (isTablet) 64.dp else 58.dp,
                drawerIconSize = if (isTablet) 60.dp else 54.dp,
                drawerMaxWidth = contentWidth.dp,
            )
        }
    }
}

@Composable
fun rememberDeviceProfile(settings: LauncherSettings): DeviceProfile {
    val configuration = LocalConfiguration.current
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val widthClass = when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WidthClass.EXPANDED
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WidthClass.MEDIUM
        else -> WidthClass.COMPACT
    }
    val compactHeight = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val w = configuration.screenWidthDp
    val h = configuration.screenHeightDp
    val sw = configuration.smallestScreenWidthDp
    return remember(w, h, sw, widthClass, compactHeight, settings.drawerColumns, settings.categoryColumns) {
        DeviceProfile.compute(w, h, sw, widthClass, compactHeight, settings)
    }
}
