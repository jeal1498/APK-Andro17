package com.minimal.launcher.categories

import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.data.db.AppAssignmentEntity
import com.minimal.launcher.data.db.AppPairEntity
import com.minimal.launcher.data.db.CategoryEntity

/** A category ready to display, with its apps resolved. [id] == [UNCLASSIFIED_ID] for the virtual one. */
data class CategoryWithApps(
    val id: Long,
    val name: String,
    val iconKey: String,
    val builtinKey: String?,
    val keywords: String,
    val apps: List<AppInfo>,
    /** Split-screen pairs stored in this category (shown next to its apps). */
    val pairs: List<AppPair> = emptyList(),
) {
    val isUnclassified: Boolean get() = id == UNCLASSIFIED_ID
}

/** A split-screen pair/trio ready to display. [categoryId] is where it is shown. */
data class AppPair(val id: Long, val apps: List<AppInfo>, val categoryId: Long, val manualCategory: Boolean) {
    val label: String get() = apps.joinToString(" + ") { it.label }
}

/** How an app ended up in its categories. */
enum class AssignmentSource { MANUAL, AUTOMATIC, UNCLASSIFIED }

data class Resolution(val categoryIds: Set<Long>, val source: AssignmentSource)

/**
 * Pure resolution logic. Hierarchy:
 *  1. Explicit manual assignment (even an empty one) — never overwritten by rules.
 *  2. Special rules (Conducción > Inteligencia artificial > Google), then automatic local rules.
 *     An app can get several automatic categories; the first is the primary.
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
        val auto = Classifier.classifyAll(
            Classifier.AppSignals(app.label, app.packageName, app.systemCategory),
            targets,
        )
        return if (auto.isNotEmpty()) Resolution(LinkedHashSet(auto), AssignmentSource.AUTOMATIC)
        else Resolution(emptySet(), AssignmentSource.UNCLASSIFIED)
    }

    /**
     * Where a pair lives: its manual category if it still exists, otherwise the first category of its
     * first app (e.g. Uber + DiDi → Conducción), otherwise "Sin clasificar".
     */
    fun pairCategory(manualCategoryId: Long?, firstAppCategories: Set<Long>, validIds: Set<Long>): Long = when {
        manualCategoryId != null && manualCategoryId in validIds -> manualCategoryId
        else -> firstAppCategories.firstOrNull { it in validIds } ?: UNCLASSIFIED_ID
    }

    fun group(
        apps: List<AppInfo>,
        categories: List<CategoryEntity>,
        assignments: List<AppAssignmentEntity>,
        manualApps: Set<String>,
        pairs: List<AppPairEntity> = emptyList(),
    ): List<CategoryWithApps> {
        val targets = targets(categories)
        val manualMap = assignments.groupBy({ it.appKey }, { it.categoryId }).mapValues { it.value.toSet() }
        val buckets = LinkedHashMap<Long, MutableList<AppInfo>>()
        categories.forEach { buckets[it.id] = ArrayList() }
        val unclassified = ArrayList<AppInfo>()
        val resolutions = HashMap<String, Resolution>()
        for (app in apps) {
            val res = resolve(app, targets, manualApps, manualMap)
            resolutions[app.key] = res
            if (res.categoryIds.isEmpty()) unclassified += app
            else res.categoryIds.forEach { buckets[it]?.add(app) }
        }
        // Pairs only show while at least two of their apps are installed and visible.
        val byKey = apps.associateBy { it.key }
        val validIds = categories.mapTo(HashSet()) { it.id }
        val pairBuckets = pairs.mapNotNull { p ->
            val members = p.appKeys.mapNotNull { byKey[it] }
            if (members.size < 2) return@mapNotNull null
            val cat = pairCategory(p.categoryId, resolutions[members.first().key]?.categoryIds.orEmpty(), validIds)
            AppPair(p.id, members, cat, p.categoryId != null && p.categoryId in validIds)
        }.groupBy { it.categoryId }
        return categories.map { c ->
            CategoryWithApps(c.id, c.name, c.iconKey, c.builtinKey, c.keywords, buckets[c.id].orEmpty(), pairBuckets[c.id].orEmpty())
        } + CategoryWithApps(UNCLASSIFIED_ID, UNCLASSIFIED_NAME, "unclassified", null, "", unclassified, pairBuckets[UNCLASSIFIED_ID].orEmpty())
    }
}
