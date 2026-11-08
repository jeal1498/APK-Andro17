package com.minimal.launcher.data.db

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun hiddenAppDao(): HiddenAppDao
    abstract fun homeDao(): HomeDao

    companion object {
        const val NAME = "launcher.db"

        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, NAME).build()
    }
}
