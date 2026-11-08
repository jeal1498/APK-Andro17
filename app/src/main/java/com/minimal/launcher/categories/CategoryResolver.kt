package com.minimal.launcher.categories

import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.data.db.AppAssignmentEntity
import com.minimal.launcher.data.db.CategoryEntity

/** A category ready to display, with its apps resolved. [id] == [UNCLASSIFIED_ID] for the virtual one. */
data class CategoryWithApps(
    val id: Long,
    val name: String,
    val iconKey: String,
    val builtinKey: String?,
    val keywords: String,
    val apps: List<AppInfo>,
) {
    val isUnclassified: Boolean get() = id == UNCLASSIFIED_ID
}

/** How an app ended up in its categories. */
enum class AssignmentSource { MANUAL, AUTOMATIC, UNCLASSIFIED }

data class Resolution(val categoryIds: Set<Long>, val source: AssignmentSource)

/**
 * Pure resolution logic. Hierarchy:
 *  1. Explicit manual assignment (even an empty one) — never overwritten by rules.
 *  2. Automatic local rule.
 *  3. Unclassified.
 */
object CategoryResolver {

    fun targets(categories: List<CategoryEntity>): List<Classifier.Target> = categories.map { c ->
        Classifier.Target(
            id = c.id,
            builtinKey = c.builtinKey,
            userKeywords = c.keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    fun resolve(
        app: AppInfo,
        targets: List<Classifier.Target>,
        manualApps: Set<String>,
        manualAssignments: Map<String, Set<Long>>,
    ): Resolution {
        val validIds = targets.mapTo(HashSet()) { it.id }
        if (app.key in manualApps) {
            val ids = manualAssignments[app.key].orEmpty().filterTo(LinkedHashSet()) { it in validIds }
            return if (ids.isEmpty()) Resolution(emptySet(), AssignmentSource.UNCLASSIFIED)
            else Resolution(ids, AssignmentSource.MANUAL)
        }
        val auto = Classifier.classify(
            Classifier.AppSignals(app.label, app.packageName, app.systemCategory),
            targets,
        )
        return if (auto != null) Resolution(setOf(auto), AssignmentSource.AUTOMATIC)
        else Resolution(emptySet(), AssignmentSource.UNCLASSIFIED)
    }

    fun group(
        apps: List<AppInfo>,
        categories: List<CategoryEntity>,
        assignments: List<AppAssignmentEntity>,
        manualApps: Set<String>,
    ): List<CategoryWithApps> {
        val targets = targets(categories)
        val manualMap = assignments.groupBy({ it.appKey }, { it.categoryId }).mapValues { it.value.toSet() }
        val buckets = LinkedHashMap<Long, MutableList<AppInfo>>()
        categories.forEach { buckets[it.id] = ArrayList() }
        val unclassified = ArrayList<AppInfo>()
        for (app in apps) {
            val res = resolve(app, targets, manualApps, manualMap)
            if (res.categoryIds.isEmpty()) unclassified += app
            else res.categoryIds.forEach { buckets[it]?.add(app) }
        }
        return categories.map { c ->
            CategoryWithApps(c.id, c.name, c.iconKey, c.builtinKey, c.keywords, buckets[c.id].orEmpty())
        } + CategoryWithApps(UNCLASSIFIED_ID, UNCLASSIFIED_NAME, "unclassified", null, "", unclassified)
    }
}
