package com.minimal.launcher.categories

import com.minimal.launcher.data.db.AssignmentDao
import com.minimal.launcher.data.db.CategoryDao
import com.minimal.launcher.data.db.CategoryEntity

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val assignmentDao: AssignmentDao,
) {
    val categories = categoryDao.observeAll()
    val assignments = assignmentDao.observeAssignments()
    val manualApps = assignmentDao.observeManualApps()

    /** Creates the built-in categories once (first launch). */
    suspend fun ensureDefaults() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(
            BuiltinCategory.entries.mapIndexed { i, c ->
                CategoryEntity(name = c.displayName, iconKey = c.iconKey, position = i, builtinKey = c.key)
            },
        )
    }

    suspend fun create(name: String, iconKey: String, keywords: String = ""): Long =
        categoryDao.insert(
            CategoryEntity(name = name.trim(), iconKey = iconKey, position = categoryDao.nextPosition(), keywords = keywords),
        )

    suspend fun update(category: CategoryEntity) = categoryDao.update(category)

    suspend fun delete(id: Long) = categoryDao.delete(id)

    /** Persists a new order; [orderedIds] lists every category id in the desired order. */
    suspend fun reorder(orderedIds: List<Long>) {
        val byId = categoryDao.getAll().associateBy { it.id }
        categoryDao.updateAll(orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(position = i) })
    }

    suspend fun setManualCategories(appKey: String, categoryIds: Set<Long>) =
        assignmentDao.setManualCategories(appKey, categoryIds)

    suspend fun restoreAutomatic(appKey: String) = assignmentDao.restoreAutomatic(appKey)

    suspend fun restoreAllAutomatic() = assignmentDao.restoreAllAutomatic()

    suspend fun isManual(appKey: String) = assignmentDao.isManual(appKey)

    suspend fun forgetApps(keys: List<String>) {
        if (keys.isEmpty()) return
        assignmentDao.deleteAssignmentsForApps(keys)
        assignmentDao.deleteManualForApps(keys)
    }

    /** Restores built-ins that were deleted, keeping user categories. */
    suspend fun restoreMissingBuiltins() {
        val existing = categoryDao.getAll().mapNotNull { it.builtinKey }.toSet()
        var pos = categoryDao.nextPosition()
        BuiltinCategory.entries.filter { it.key !in existing }.forEach {
            categoryDao.insert(CategoryEntity(name = it.displayName, iconKey = it.iconKey, position = pos++, builtinKey = it.key))
        }
    }
}
