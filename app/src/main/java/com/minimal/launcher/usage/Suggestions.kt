package com.minimal.launcher.usage

import android.content.pm.ApplicationInfo
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.categories.Classifier
import com.minimal.launcher.data.db.AppUsageEntity

/** What the device is doing right now (all detected locally). */
data class DeviceContext(val headphones: Boolean = false, val inCar: Boolean = false)

/**
 * "Sugeridas": apps usually opened in this time slot on this kind of day (weekday / weekend), plus
 * event rules: headphones → music, car (car mode or charging with a car's Bluetooth) → maps / Uber / DiDi.
 * Everything is computed on the device from [AppUsageEntity] rows.
 */
object Suggestions {
    const val LIMIT = 4
    /** Minimum (decayed) launches in this slot before an app is suggested. */
    const val MIN_SCORE = 1.5

    fun dismissKey(appKey: String, slot: Int, weekend: Boolean) = "$appKey@$slot@${if (weekend) "we" else "wd"}"

    fun select(
        usage: List<AppUsageEntity>,
        now: Long,
        slot: Int,
        dayOfWeek: Int,
        available: Set<String>,
        dismissed: Set<String>,
        exclude: Set<String>,
        context: DeviceContext,
        musicApps: List<String>,
        carApps: List<String>,
        limit: Int = LIMIT,
    ): List<String> {
        val weekend = TimeSlots.isWeekend(dayOfWeek)
        fun allowed(key: String) = key in available && dismissKey(key, slot, weekend) !in dismissed
        val overall = FrequentApps.scores(usage, now)
        val byUse = compareByDescending<String> { overall[it] ?: 0.0 }.thenBy { it }

        val fromEvents = buildList {
            if (context.inCar) addAll(carApps.filter(::allowed).sortedWith(byUse).take(3))
            if (context.headphones) addAll(musicApps.filter(::allowed).sortedWith(byUse).take(2))
        }.distinct()

        val slotScores = HashMap<String, Double>()
        for (row in usage) {
            if (TimeSlots.isWeekend(row.dayOfWeek) != weekend) continue
            val weight = when (row.timeSlot) {
                slot -> 1.0
                (slot + 1) % TimeSlots.COUNT, (slot + TimeSlots.COUNT - 1) % TimeSlots.COUNT -> 0.3
                else -> continue
            }
            slotScores.merge(row.appKey, row.count * weight * FrequentApps.decay(row.lastOpened, now), Double::plus)
        }
        val fromTime = slotScores.entries
            .filter { it.value >= MIN_SCORE && allowed(it.key) && it.key !in exclude && it.key !in fromEvents }
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key }
        return (fromEvents + fromTime).take(limit)
    }

    private val musicPackages = setOf(
        "com.spotify.music", "com.google.android.apps.youtube.music", "deezer.android.app", "com.amazon.mp3",
        "com.apple.android.music", "com.soundcloud.android", "com.aspiro.tidal", "com.google.android.apps.podcasts",
        "au.com.shiftyjelly.pocketcasts", "com.audible.application", "com.pandora.android", "com.shazam.android",
    )
    private val musicWords = listOf("music", "musica", "radio", "podcast", "audio", "fm")
    private val carPackages = setOf(
        "com.google.android.apps.maps", "com.waze", "com.ubercab", "com.ubercab.driver", "com.didiglobal.passenger",
        "com.didiglobal.driver", "com.here.app.maps", "com.sygic.aura", "com.google.android.projection.gearhead",
    )

    fun isMusicApp(app: AppInfo): Boolean {
        if (app.systemCategory == ApplicationInfo.CATEGORY_AUDIO || app.packageName in musicPackages) return true
        val label = Classifier.normalize(app.label)
        return musicWords.any { w -> label.split(' ').any { it == w } }
    }

    /** Maps and ride-hailing apps, plus everything in the user's "Conducción" category. */
    fun isCarApp(app: AppInfo, drivingCategoryApps: Set<String>): Boolean =
        app.key in drivingCategoryApps || app.packageName in carPackages || app.systemCategory == ApplicationInfo.CATEGORY_MAPS

    private val carWords = listOf("car", "auto", "coche", "carro", "vehiculo", "sync", "uconnect", "mylink", "carplay", "android auto",
        "toyota", "nissan", "honda", "mazda", "ford", "chevrolet", "chevy", "kia", "hyundai", "volkswagen", "vw", "seat", "renault",
        "peugeot", "bmw", "audi", "mercedes", "jeep", "subaru", "suzuki", "mitsubishi", "byd", "tesla")

    /** Heuristic: a Bluetooth audio device whose name looks like a car head unit. */
    fun looksLikeCar(deviceName: String?): Boolean {
        val n = Classifier.normalize(deviceName ?: return false)
        val tokens = n.split(' ', '-', '_', '.')
        return carWords.any { w -> if (' ' in w) n.contains(w) else w in tokens }
    }
}
