package com.minimal.launcher.usage

import com.minimal.launcher.data.db.AppUsageEntity
import com.minimal.launcher.data.prefs.FrequentPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentAppsTest {
    private val day = 86_400_000L
    private val now = 100 * day
    private fun u(app: String, count: Int, daysAgo: Int, slot: Int = 0) = AppUsageEntity(app, slot, 2, count, now - daysAgo * day)

    @Test fun recentLaunchesBeatOldOnes() {
        val usage = listOf(u("old", 10, 30), u("new", 4, 0), u("mid", 3, 1, slot = 1), u("mid", 3, 1, slot = 2))
        val all = setOf("old", "new", "mid", "x", "y", "z")
        // old: 10 * 0.5^(30/7) ≈ 0.51; new: 4; mid: 6 * 0.5^(1/7) ≈ 5.4
        assertEquals(listOf("mid", "new", "old"), FrequentApps.select(usage, emptyList(), all, 5, now))
    }

    @Test fun pinnedFirstAndHiddenOrUninstalledExcluded() {
        val usage = listOf(u("a", 50, 0), u("b", 40, 0), u("gone", 99, 0))
        val r = FrequentApps.select(usage, pinned = listOf("z", "b", "gone"), available = setOf("a", "b", "z"), limit = 5, now = now)
        assertEquals(listOf("z", "b", "a"), r)
    }

    @Test fun limitIsBetweenFiveAndEight() {
        val usage = (1..20).map { u("app$it", it, 0) }
        val all = usage.map { it.appKey }.toSet()
        assertEquals(5, FrequentApps.select(usage, emptyList(), all, 2, now).size)
        assertEquals(8, FrequentApps.select(usage, emptyList(), all, 50, now).size)
        assertEquals("app20", FrequentApps.select(usage, emptyList(), all, 6, now).first())
    }

    @Test fun sidebarOnlyOnTablets() {
        assertEquals(FrequentPosition.TOP, FrequentPlacement.effective(FrequentPosition.SIDEBAR, isTablet = false))
        assertEquals(FrequentPosition.SIDEBAR, FrequentPlacement.effective(FrequentPosition.SIDEBAR, isTablet = true))
        assertEquals(FrequentPosition.HIDDEN, FrequentPlacement.effective(FrequentPosition.HIDDEN, isTablet = false))
    }

    @Test fun dropTargetFollowsPointer() {
        assertEquals(FrequentPosition.TOP, FrequentPlacement.dropTarget(500f, 100f, 1000f, 2000f, false))
        assertEquals(FrequentPosition.BOTTOM, FrequentPlacement.dropTarget(500f, 1500f, 1000f, 2000f, false))
        assertEquals(FrequentPosition.TOP, FrequentPlacement.dropTarget(20f, 100f, 1000f, 2000f, false))
        assertEquals(FrequentPosition.SIDEBAR, FrequentPlacement.dropTarget(20f, 1500f, 1000f, 2000f, true))
        assertTrue(TimeSlots.slotOfHour(23) == 5 && TimeSlots.slotOfHour(0) == 0 && TimeSlots.slotOfHour(9) == 2)
    }
}
