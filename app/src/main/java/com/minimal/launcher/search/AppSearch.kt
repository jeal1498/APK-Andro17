package com.minimal.launcher.search

import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.categories.Classifier

/** Local search over app labels (accent and case insensitive). Ranking: prefix > word prefix > contains > package. */
object AppSearch {
    fun search(apps: List<AppInfo>, query: String): List<AppInfo> {
        val q = Classifier.normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        return apps.mapNotNull { app ->
            val label = Classifier.normalize(app.label)
            val rank = when {
                label.startsWith(q) -> 0
                label.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
                label.contains(q) -> 2
                q.length >= 3 && app.packageName.lowercase().contains(q) -> 3
                else -> return@mapNotNull null
            }
            rank to app
        }.sortedWith(compareBy({ it.first }, { Classifier.normalize(it.second.label) })).map { it.second }
    }
}
