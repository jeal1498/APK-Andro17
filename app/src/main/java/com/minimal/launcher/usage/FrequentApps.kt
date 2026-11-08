package com.minimal.launcher.usage

import com.minimal.launcher.data.db.AppUsageEntity
import com.minimal.launcher.data.prefs.FrequentPosition
import kotlin.math.pow

/** Pure ranking for the "Frecuentes" row: pinned apps first, then by launches with a per-day decay. */
object FrequentApps {
    /** A launch loses half its weight every [HALF_LIFE_DAYS] days. */
    const val HALF_LIFE_DAYS = 7.0
    const val MIN = 5
    const val MAX = 8
    private const val DAY_MS = 86_400_000.0

    fun decay(lastOpened: Long, now: Long): Double {
        val days = ((now - lastOpened).coerceAtLeast(0L)) / DAY_MS
        return 0.5.pow(days / HALF_LIFE_DAYS)
    }

    fun scores(usage: List<AppUsageEntity>, now: Long): Map<String, Double> =
        usage.groupBy { it.appKey }.mapValues { (_, rows) -> rows.sumOf { it.count * decay(it.lastOpened, now) } }

    fun select(usage: List<AppUsageEntity>, pinned: List<String>, available: Set<String>, limit: Int, now: Long): List<String> {
        val n = limit.coerceIn(MIN, MAX)
        val pins = pinned.filter { it in available }.distinct()
        val ranked = scores(usage, now).entries
            .filter { it.key in available && it.key !in pins && it.value > 0.0 }
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key }
        return (pins + ranked).take(n)
    }
}

/** Where the row is actually drawn: the side bar only exists on tablets. */
object FrequentPlacement {
    fun effective(position: FrequentPosition, isTablet: Boolean): FrequentPosition =
        if (position == FrequentPosition.SIDEBAR && !isTablet) FrequentPosition.TOP else position

    /** Target when the row is dropped at ([x], [y]) inside a [width]×[height] screen in edit mode. */
    fun dropTarget(x: Float, y: Float, width: Float, height: Float, isTablet: Boolean): FrequentPosition = when {
        isTablet && (x < width * 0.18f || x > width * 0.82f) -> FrequentPosition.SIDEBAR
        y < height / 2f -> FrequentPosition.TOP
        else -> FrequentPosition.BOTTOM
    }
}
