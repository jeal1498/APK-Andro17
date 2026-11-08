package com.minimal.launcher.usage

import com.minimal.launcher.data.db.AppUsageEntity
import com.minimal.launcher.data.db.UsageDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar

/** Time of day split in 6 slots of 4 hours (0 = 00-04 h … 5 = 20-24 h). */
object TimeSlots {
    const val COUNT = 6
    const val HOURS_PER_SLOT = 24 / COUNT

    fun slotOfHour(hour: Int): Int = (hour.coerceIn(0, 23) / HOURS_PER_SLOT)

    /** (slot, day of week 1..7) for an instant in the device time zone. */
    fun of(millis: Long): Pair<Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return slotOfHour(c.get(Calendar.HOUR_OF_DAY)) to c.get(Calendar.DAY_OF_WEEK)
    }

    fun isWeekend(dayOfWeek: Int): Boolean = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    fun label(slot: Int): String {
        val from = slot * HOURS_PER_SLOT
        return "%02d:00–%02d:00".format(from, from + HOURS_PER_SLOT)
    }
}

/** Records app launches (local only). [enabled] lets the user switch learning off. */
class UsageRepository(
    private val dao: UsageDao,
    private val scope: CoroutineScope,
    private val enabled: suspend () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val usage: Flow<List<AppUsageEntity>> = dao.observeAll()

    fun record(appKey: String) {
        scope.launch {
            if (!enabled()) return@launch
            val now = clock()
            val (slot, day) = TimeSlots.of(now)
            dao.record(appKey, slot, day, now)
        }
    }

    suspend fun forget(keys: List<String>) {
        if (keys.isNotEmpty()) dao.deleteForApps(keys)
    }

    suspend fun clear() = dao.clear()
}
