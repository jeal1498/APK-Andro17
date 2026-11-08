package com.minimal.launcher.categories

import com.minimal.launcher.data.db.AssignmentDao
import com.minimal.launcher.data.db.CategoryDao
import com.minimal.launcher.data.db.CategoryEntity

/** Remembers which built-in taxonomy version the stored categories follow. */
interface TaxonomyVersionStore {
    suspend fun get(): Int
    suspend fun set(version: Int)
}

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val assignmentDao: AssignmentDao,
    private val taxonomy: TaxonomyVersionStore? = null,
) {
    val categories = categoryDao.observeAll()
    val assignments = assignmentDao.observeAssignments()
    val manualApps = assignmentDao.observeManualApps()

    /** Creates the built-in categories once (first launch) and upgrades older taxonomies. */
    suspend fun ensureDefaults() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(
                BuiltinCategory.entries.mapIndexed { i, c ->
                    CategoryEntity(name = c.displayName, iconKey = c.iconKey, position = i, builtinKey = c.key)
                },
            )
            taxonomy?.set(CATEGORY_TAXONOMY_VERSION)
            return
        }
        val version = taxonomy?.get() ?: CATEGORY_TAXONOMY_VERSION
        if (version < CATEGORY_TAXONOMY_VERSION) {
            migrateTaxonomy()
            taxonomy?.set(CATEGORY_TAXONOMY_VERSION)
        }
    }

    /**
     * v1 -> v2: adds the new built-ins (Google, IA, Conducción, Salud…), renames built-ins whose
     * name/icon were never customised, and retires Juegos/Fotografía. A retired category that holds
     * manual assignments is kept as a user category so no manual data is lost. Manual assignments
     * and user categories are never deleted. Built-ins go first, in the new order.
     */
    suspend fun migrateTaxonomy() {
        val existing = categoryDao.getAll()
        val updated = ArrayList<CategoryEntity>()
        val manualCategoryIds = assignmentDao.allAssignments().mapTo(HashSet()) { it.categoryId }
        for (cat in existing) {
            val key = cat.builtinKey
            when {
                key in OBSOLETE_BUILTIN_KEYS && cat.id in manualCategoryIds -> updated += cat.copy(builtinKey = null)
                key in OBSOLETE_BUILTIN_KEYS -> categoryDao.delete(cat.id)
                else -> {
                    val builtin = BuiltinCategory.fromKey(key)
                    val legacy = LEGACY_BUILTIN_DEFAULTS[key]
                    if (builtin != null && legacy != null) {
                        updated += cat.copy(
                            name = if (cat.name == legacy.first) builtin.displayName else cat.name,
                            iconKey = if (cat.iconKey == legacy.second) builtin.iconKey else cat.iconKey,
                        )
                    } else {
                        updated += cat
                    }
                }
            }
        }
        val presentKeys = updated.mapNotNull { it.builtinKey }.toSet()
        val missing = BuiltinCategory.entries.filter { it.key !in presentKeys }
        val newIds = missing.associate { b ->
            b.key to categoryDao.insert(CategoryEntity(name = b.displayName, iconKey = b.iconKey, position = Int.MAX_VALUE, builtinKey = b.key))
        }
        // Order: the built-ins in their canonical order, then everything else as it was.
        val builtinRank = BuiltinCategory.entries.mapIndexed { i, b -> b.key to i }.toMap()
        val all = updated + missing.map { b ->
            CategoryEntity(id = newIds.getValue(b.key), name = b.displayName, iconKey = b.iconKey, position = 0, builtinKey = b.key)
        }
        val ordered = all.sortedWith(
            compareBy<CategoryEntity> { builtinRank[it.builtinKey] ?: Int.MAX_VALUE }.thenBy { it.position }.thenBy { it.id },
        )
        categoryDao.updateAll(ordered.mapIndexed { i, c -> c.copy(position = i) })
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
