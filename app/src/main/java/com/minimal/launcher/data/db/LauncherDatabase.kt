package com.minimal.launcher.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        AppAssignmentEntity::class,
        ManualAppEntity::class,
        HiddenAppEntity::class,
        HomeItemEntity::class,
        AppUsageEntity::class,
        AppPairEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2: app_usage + app_pairs (new tables only, so Room generates the migration).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun hiddenAppDao(): HiddenAppDao
    abstract fun homeDao(): HomeDao
    abstract fun usageDao(): UsageDao
    abstract fun pairDao(): PairDao

    companion object {
        const val NAME = "launcher.db"

        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, NAME).build()
    }
}
