package com.minimal.launcher.usage

import com.minimal.launcher.data.db.AppUsageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SuggestionsTest {
    private val now = 1_000L * 86_400_000L
    private val monday = Calendar.MONDAY
    private val saturday = Calendar.SATURDAY
    private fun u(app: String, slot: Int, day: Int, count: Int) = AppUsageEntity(app, slot, day, count, now)
    private val all = setOf("mail", "news", "game", "spotify", "maps", "uber")

    private fun pick(
        usage: List<AppUsageEntity>, slot: Int = 2, day: Int = monday, ctx: DeviceContext = DeviceContext(),
        dismissed: Set<String> = emptySet(), exclude: Set<String> = emptySet(),
    ) = Suggestions.select(usage, now, slot, day, all, dismissed, exclude, ctx, musicApps = listOf("spotify"), carApps = listOf("maps", "uber"))

    @Test fun dependsOnTimeSlotAndDayType() {
        val usage = listOf(u("mail", 2, Calendar.TUESDAY, 9), u("news", 5, monday, 9), u("game", 2, saturday, 9))
        assertEquals(listOf("mail"), pick(usage)) // weekday morning: other weekdays count too
        assertEquals(listOf("game"), pick(usage, day = Calendar.SUNDAY))
        assertEquals(listOf("news"), pick(usage, slot = 5))
    }

    @Test fun eventRulesComeFirst() {
        val usage = listOf(u("mail", 2, monday, 9), u("uber", 0, monday, 5))
        assertEquals(listOf("spotify", "mail"), pick(usage, ctx = DeviceContext(headphones = true)))
        assertEquals(listOf("uber", "maps", "mail"), pick(usage, ctx = DeviceContext(inCar = true)))
    }

    @Test fun dismissedAndFrequentAreSkipped() {
        val usage = listOf(u("mail", 2, monday, 9), u("news", 2, monday, 8))
        assertEquals(listOf("news"), pick(usage, dismissed = setOf(Suggestions.dismissKey("mail", 2, weekend = false))))
        assertEquals(listOf("mail", "news"), pick(usage, dismissed = setOf(Suggestions.dismissKey("mail", 2, weekend = true))))
        assertEquals(listOf("news"), pick(usage, exclude = setOf("mail")))
        assertTrue(pick(listOf(u("mail", 2, monday, 1))).isEmpty()) // a single launch is not a habit
    }

    @Test fun carHeuristics() {
        assertTrue(Suggestions.looksLikeCar("Toyota Corolla"))
        assertTrue(Suggestions.looksLikeCar("MY-CAR"))
        assertTrue(Suggestions.looksLikeCar("Ford SYNC"))
        assertFalse(Suggestions.looksLikeCar("Galaxy Buds2"))
        assertFalse(Suggestions.looksLikeCar(null))
    }
}
