package com.minimal.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY position, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories")
    suspend fun nextPosition(): Int
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM app_assignments")
    fun observeAssignments(): Flow<List<AppAssignmentEntity>>

    @Query("SELECT appKey FROM manual_apps")
    fun observeManualApps(): Flow<List<String>>

    @Query("SELECT * FROM app_assignments")
    suspend fun allAssignments(): List<AppAssignmentEntity>

    @Query("SELECT * FROM app_assignments WHERE appKey = :appKey")
    suspend fun assignmentsFor(appKey: String): List<AppAssignmentEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM manual_apps WHERE appKey = :appKey)")
    suspend fun isManual(appKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertManualApp(app: ManualAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAssignments(assignments: List<AppAssignmentEntity>)

    @Query("DELETE FROM app_assignments WHERE appKey = :appKey")
    suspend fun deleteAssignmentsFor(appKey: String)

    @Query("DELETE FROM app_assignments WHERE appKey = :appKey AND categoryId = :categoryId")
    suspend fun deleteAssignment(appKey: String, categoryId: Long)

    @Query("DELETE FROM manual_apps WHERE appKey = :appKey")
    suspend fun deleteManualApp(appKey: String)

    @Query("DELETE FROM app_assignments")
    suspend fun clearAssignments()

    @Query("DELETE FROM manual_apps")
    suspend fun clearManualApps()

    /** Replaces the manual categories of an app. An empty set means "explicitly unclassified". */
    @Transaction
    suspend fun setManualCategories(appKey: String, categoryIds: Set<Long>) {
        insertManualApp(ManualAppEntity(appKey))
        deleteAssignmentsFor(appKey)
        insertAssignments(categoryIds.map { AppAssignmentEntity(appKey, it) })
    }

    @Transaction
    suspend fun restoreAutomatic(appKey: String) {
        deleteAssignmentsFor(appKey)
        deleteManualApp(appKey)
    }

    @Transaction
    suspend fun restoreAllAutomatic() {
        clearAssignments()
        clearManualApps()
    }

    /** Called when apps are uninstalled so no stale rows remain. */
    @Query("DELETE FROM app_assignments WHERE appKey IN (:keys)")
    suspend fun deleteAssignmentsForApps(keys: List<String>)

    @Query("DELETE FROM manual_apps WHERE appKey IN (:keys)")
    suspend fun deleteManualForApps(keys: List<String>)
}

@Dao
interface HiddenAppDao {
    @Query("SELECT appKey FROM hidden_apps")
    fun observe(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(app: HiddenAppEntity)

    @Query("DELETE FROM hidden_apps WHERE appKey = :appKey")
    suspend fun unhide(appKey: String)
}

@Dao
interface HomeDao {
    @Query("SELECT * FROM home_items ORDER BY container, containerId, y, x")
    fun observeAll(): Flow<List<HomeItemEntity>>

    @Query("SELECT * FROM home_items")
    suspend fun getAll(): List<HomeItemEntity>

    @Query("SELECT * FROM home_items WHERE id = :id")
    suspend fun get(id: Long): HomeItemEntity?

    @Query("SELECT * FROM home_items WHERE container = :container AND containerId = :containerId ORDER BY x")
    suspend fun itemsIn(container: Int, containerId: Long): List<HomeItemEntity>

    @Query("SELECT COUNT(*) FROM home_items")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: HomeItemEntity): Long

    @Update
    suspend fun update(item: HomeItemEntity)

    @Update
    suspend fun updateAll(items: List<HomeItemEntity>)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM home_items WHERE container = :container AND containerId = :containerId")
    suspend fun deleteChildren(container: Int, containerId: Long)

    @Query("SELECT * FROM home_items WHERE appKey IN (:keys)")
    suspend fun itemsForApps(keys: List<String>): List<HomeItemEntity>

    @Query("SELECT widgetId FROM home_items WHERE widgetId IS NOT NULL")
    suspend fun widgetIds(): List<Int>

    @Query("SELECT * FROM home_items WHERE type = 3 AND packageName IN (:packages)")
    suspend fun shortcutsForPackages(packages: List<String>): List<HomeItemEntity>
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM app_usage")
    fun observeAll(): Flow<List<AppUsageEntity>>

    @Query("SELECT * FROM app_usage")
    suspend fun getAll(): List<AppUsageEntity>

    @Query("UPDATE app_usage SET count = count + 1, lastOpened = :now WHERE appKey = :appKey AND timeSlot = :slot AND dayOfWeek = :day")
    suspend fun increment(appKey: String, slot: Int, day: Int, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(usage: AppUsageEntity)

    @Transaction
    suspend fun record(appKey: String, slot: Int, day: Int, now: Long) {
        if (increment(appKey, slot, day, now) == 0) insert(AppUsageEntity(appKey, slot, day, 1, now))
    }

    @Query("DELETE FROM app_usage WHERE appKey IN (:keys)")
    suspend fun deleteForApps(keys: List<String>)

    @Query("DELETE FROM app_usage")
    suspend fun clear()
}

@Dao
interface PairDao {
    @Query("SELECT * FROM app_pairs ORDER BY position, id")
    fun observeAll(): Flow<List<AppPairEntity>>

    @Query("SELECT * FROM app_pairs ORDER BY position, id")
    suspend fun getAll(): List<AppPairEntity>

    @Query("SELECT * FROM app_pairs WHERE id = :id")
    suspend fun get(id: Long): AppPairEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM app_pairs")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(pair: AppPairEntity): Long

    @Update
    suspend fun update(pair: AppPairEntity)

    @Query("DELETE FROM app_pairs WHERE id = :id")
    suspend fun delete(id: Long)
}
