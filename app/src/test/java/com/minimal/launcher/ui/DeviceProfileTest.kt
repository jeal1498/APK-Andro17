package com.minimal.launcher.ui

import com.minimal.launcher.data.prefs.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Breakpoints and adaptive grids for the four reference scenarios. */
class DeviceProfileTest {
    private val s = LauncherSettings()

    @Test fun phonePortrait() {
        val p = DeviceProfile.compute(411, 891, 411, WidthClass.COMPACT, false, s)
        assertFalse(p.isTablet); assertFalse(p.dockOnSide)
        assertEquals(4, p.drawerColumns); assertEquals(3, p.categoryColumns)
        assertEquals(4 to 5, DeviceProfile.defaultHomeGrid(411, 891))
        assertEquals(4 to 5, DeviceProfile.defaultHomeGrid(360, 740))
    }

    @Test fun phoneLandscapeUsesSideDockAndMoreColumns() {
        val p = DeviceProfile.compute(891, 411, 411, WidthClass.EXPANDED, true, s)
        assertTrue(p.dockOnSide)
        assertTrue(p.drawerColumns >= 7)
        assertTrue(p.categoryColumns >= 5)
    }

    @Test fun tabletsGetMoreColumnsAndDynamicMargins() {
        val port = DeviceProfile.compute(800, 1280, 800, WidthClass.MEDIUM, false, s)
        val land = DeviceProfile.compute(1280, 800, 800, WidthClass.EXPANDED, false, s)
        assertTrue(port.isTablet && land.isTablet)
        assertFalse(land.dockOnSide)
        assertTrue(land.drawerColumns > port.drawerColumns || land.drawerColumns == 9)
        assertTrue(land.homePadding > port.homePadding)
        assertEquals(6 to 5, DeviceProfile.defaultHomeGrid(800, 1280))
        assertEquals(7 to 5, DeviceProfile.defaultHomeGrid(900, 1400))
        assertEquals(6 to 5, DeviceProfile.defaultHomeGrid(600, 960))
    }

    @Test fun userColumnsScaleInPhoneLandscape() {
        val custom = s.copy(drawerColumns = 4, categoryColumns = 3)
        assertEquals(4, DeviceProfile.compute(411, 891, 411, WidthClass.COMPACT, false, custom).drawerColumns)
        assertEquals(7, DeviceProfile.compute(891, 411, 411, WidthClass.EXPANDED, true, custom).drawerColumns)
    }
}
