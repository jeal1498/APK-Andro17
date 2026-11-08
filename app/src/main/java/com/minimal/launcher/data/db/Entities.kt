package com.minimal.launcher.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A drawer category. [builtinKey] identifies default categories so local rules can target them. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val position: Int,
    val builtinKey: String? = null,
    /** User-defined keywords (comma separated) matched against app label and package. */
    val keywords: String = "",
)

/** Explicit (manual) assignment of an app to a category. Never written by the automatic classifier. */
@Entity(
    tableName = "app_assignments",
    primaryKeys = ["appKey", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class AppAssignmentEntity(val appKey: String, val categoryId: Long)

/** Apps whose classification is under manual control (auto rules are ignored for them). */
@Entity(tableName = "manual_apps")
data class ManualAppEntity(@PrimaryKey val appKey: String)

@Entity(tableName = "hidden_apps")
data class HiddenAppEntity(@PrimaryKey val appKey: String)

/**
 * Every item placed on the home screen, dock or inside a home folder.
 * - DESKTOP: [containerId] = page index, [x]/[y] = cell, spans = size.
 * - DOCK: [x] = slot.
 * - FOLDER: [containerId] = id of the folder item, [x] = order inside the folder.
 */
@Entity(tableName = "home_items", indices = [Index("container", "containerId")])
data class HomeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val container: Int,
    val containerId: Long = 0,
    val x: Int = 0,
    val y: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val type: Int,
    val appKey: String? = null,
    val widgetId: Int? = null,
    val title: String? = null,
    val shortcutId: String? = null,
    val packageName: String? = null,
    val userSerial: Long = 0,
) {
    companion object {
        const val CONTAINER_DESKTOP = 0
        const val CONTAINER_DOCK = 1
        const val CONTAINER_FOLDER = 2

        const val TYPE_APP = 0
        const val TYPE_WIDGET = 1
        const val TYPE_FOLDER = 2
        const val TYPE_SHORTCUT = 3
        const val TYPE_CLOCK = 4
    }
}

/**
 * How often an app is opened per time slot (see [com.minimal.launcher.usage.TimeSlots]) and day of week.
 * Local only; feeds "Frecuentes" and "Sugeridas".
 */
@Entity(tableName = "app_usage", primaryKeys = ["appKey", "timeSlot", "dayOfWeek"], indices = [Index("appKey")])
data class AppUsageEntity(
    val appKey: String,
    val timeSlot: Int,
    /** 1 = Sunday … 7 = Saturday ([java.util.Calendar.DAY_OF_WEEK]). */
    val dayOfWeek: Int,
    val count: Int,
    val lastOpened: Long,
)

/**
 * A split-screen pair (2 apps) or trio (3rd app as a floating window). [apps] holds the app keys joined
 * with [SEPARATOR]. [categoryId] null = automatic (category of the first app).
 */
@Entity(tableName = "app_pairs")
data class AppPairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apps: String,
    val categoryId: Long? = null,
    val position: Int = 0,
) {
    val appKeys: List<String> get() = apps.split(SEPARATOR).filter { it.isNotEmpty() }

    companion object {
        const val SEPARATOR = "|"
        fun encode(keys: List<String>): String = keys.joinToString(SEPARATOR)
    }
}
