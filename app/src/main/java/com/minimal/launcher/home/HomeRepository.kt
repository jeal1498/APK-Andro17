package com.minimal.launcher.home

import androidx.room.withTransaction
import com.minimal.launcher.data.db.HomeDao
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_DESKTOP
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_DOCK
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_FOLDER
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_APP
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_FOLDER
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_SHORTCUT
import com.minimal.launcher.data.db.HomeItemEntity.Companion.TYPE_WIDGET
import com.minimal.launcher.data.db.LauncherDatabase
import com.minimal.launcher.data.prefs.SettingsRepository

/** Persistence of everything placed on the home screen. All mutations are transactional. */
class HomeRepository(
    private val db: LauncherDatabase,
    private val settings: SettingsRepository,
) {
    private val dao: HomeDao = db.homeDao()
    val items = dao.observeAll()

    sealed interface Result {
        data class Placed(val id: Long, val page: Int) : Result
        data object NoSpace : Result
        data object AlreadyThere : Result
    }

    private suspend fun grid(): Triple<Int, Int, Int> {
        val s = settings.current()
        return Triple(s.homeColumns.coerceAtLeast(1), s.homeRows.coerceAtLeast(1), s.homePages)
    }

    /** Places an item on the first free spot from [startPage]; adds a page if every page is full. */
    suspend fun addToDesktop(template: HomeItemEntity, startPage: Int): Result = db.withTransaction {
        val (cols, rows, pages) = grid()
        val all = dao.getAll()
        val spot = GridPlacement.findFreeSpot(
            all, startPage.coerceIn(0, pages - 1), pages, cols, rows,
            template.spanX.coerceAtMost(cols), template.spanY.coerceAtMost(rows),
        )
        if (spot.page >= pages) settings.setHomePages(spot.page + 1)
        val id = dao.insert(
            template.copy(
                id = 0, container = CONTAINER_DESKTOP, containerId = spot.page.toLong(), x = spot.x, y = spot.y,
                spanX = template.spanX.coerceAtMost(cols), spanY = template.spanY.coerceAtMost(rows),
            ),
        )
        Result.Placed(id, spot.page)
    }

    /** Places an item at an exact cell if it is free. */
    suspend fun addAt(template: HomeItemEntity, page: Int, x: Int, y: Int): Result = db.withTransaction {
        val (cols, rows, _) = grid()
        val all = dao.getAll()
        if (!GridPlacement.isFree(all, page, x, y, template.spanX, template.spanY, cols, rows)) return@withTransaction Result.NoSpace
        val id = dao.insert(template.copy(id = 0, container = CONTAINER_DESKTOP, containerId = page.toLong(), x = x, y = y))
        Result.Placed(id, page)
    }

    suspend fun addAppToDesktop(appKey: String, startPage: Int): Result {
        if (dao.getAll().any { it.container == CONTAINER_DESKTOP && it.appKey == appKey && it.type == TYPE_APP }) return Result.AlreadyThere
        return addToDesktop(HomeItemEntity(container = CONTAINER_DESKTOP, type = TYPE_APP, appKey = appKey), startPage)
    }

    suspend fun addToDock(appKey: String, slots: Int, slot: Int? = null): Result = db.withTransaction {
        val dock = dao.itemsIn(CONTAINER_DOCK, 0)
        if (dock.any { it.appKey == appKey }) return@withTransaction Result.AlreadyThere
        val used = dock.map { it.x }.toSet()
        val target = slot?.takeIf { it in 0 until slots && it !in used } ?: (0 until slots).firstOrNull { it !in used }
            ?: return@withTransaction Result.NoSpace
        Result.Placed(dao.insert(HomeItemEntity(container = CONTAINER_DOCK, x = target, type = TYPE_APP, appKey = appKey)), 0)
    }

    /** Moves an existing item to a desktop cell (from desktop or dock). */
    suspend fun moveToDesktop(itemId: Long, page: Int, x: Int, y: Int): Boolean = db.withTransaction {
        val (cols, rows, _) = grid()
        val item = dao.get(itemId) ?: return@withTransaction false
        val all = dao.getAll()
        if (!GridPlacement.isFree(all, page, x, y, item.spanX, item.spanY, cols, rows, excludeId = itemId)) return@withTransaction false
        dao.update(item.copy(container = CONTAINER_DESKTOP, containerId = page.toLong(), x = x, y = y))
        true
    }

    /** Moves an app/shortcut/folder into a dock slot; swaps when the slot is taken by another item. */
    suspend fun moveToDock(itemId: Long, slot: Int, slots: Int): Boolean = db.withTransaction {
        val item = dao.get(itemId) ?: return@withTransaction false
        if (item.type == TYPE_WIDGET || item.spanX != 1 || item.spanY != 1 || slot !in 0 until slots) return@withTransaction false
        val occupant = dao.itemsIn(CONTAINER_DOCK, 0).firstOrNull { it.x == slot && it.id != itemId }
        if (occupant != null) {
            if (item.container == CONTAINER_DOCK) {
                dao.update(occupant.copy(x = item.x))
            } else {
                return@withTransaction false
            }
        }
        dao.update(item.copy(container = CONTAINER_DOCK, containerId = 0, x = slot, y = 0))
        true
    }

    suspend fun resize(itemId: Long, x: Int, y: Int, spanX: Int, spanY: Int): Boolean = db.withTransaction {
        val (cols, rows, _) = grid()
        val item = dao.get(itemId) ?: return@withTransaction false
        val all = dao.getAll()
        if (!GridPlacement.isFree(all, item.containerId.toInt(), x, y, spanX, spanY, cols, rows, excludeId = itemId)) return@withTransaction false
        dao.update(item.copy(x = x, y = y, spanX = spanX, spanY = spanY))
        true
    }

    /** Drops [draggedId] onto the app [targetId]: both end up in a new folder at the target position. */
    suspend fun createFolder(targetId: Long, draggedId: Long, title: String = "Carpeta"): Long? = db.withTransaction {
        val target = dao.get(targetId) ?: return@withTransaction null
        val dragged = dao.get(draggedId) ?: return@withTransaction null
        if (target.type !in FOLDERABLE || dragged.type !in FOLDERABLE) return@withTransaction null
        val folderId = dao.insert(
            HomeItemEntity(container = target.container, containerId = target.containerId, x = target.x, y = target.y, type = TYPE_FOLDER, title = title),
        )
        dao.update(target.copy(container = CONTAINER_FOLDER, containerId = folderId, x = 0, y = 0))
        dao.update(dragged.copy(container = CONTAINER_FOLDER, containerId = folderId, x = 1, y = 0))
        folderId
    }

    suspend fun createFolderWithApp(appKey: String, targetId: Long, title: String = "Carpeta"): Long? = db.withTransaction {
        val newId = dao.insert(HomeItemEntity(container = CONTAINER_FOLDER, containerId = -1, type = TYPE_APP, appKey = appKey))
        createFolder(targetId, newId, title)
    }

    suspend fun addToFolder(folderId: Long, itemId: Long): Boolean = db.withTransaction {
        val folder = dao.get(folderId)?.takeIf { it.type == TYPE_FOLDER } ?: return@withTransaction false
        val item = dao.get(itemId)?.takeIf { it.type in FOLDERABLE } ?: return@withTransaction false
        val children = dao.itemsIn(CONTAINER_FOLDER, folder.id)
        if (children.any { it.appKey != null && it.appKey == item.appKey && item.type == TYPE_APP }) {
            dao.delete(item.id)
        } else {
            dao.update(item.copy(container = CONTAINER_FOLDER, containerId = folder.id, x = children.size, y = 0))
        }
        true
    }

    suspend fun addAppToFolder(folderId: Long, appKey: String): Boolean = db.withTransaction {
        val children = dao.itemsIn(CONTAINER_FOLDER, folderId)
        if (children.any { it.appKey == appKey }) return@withTransaction false
        dao.insert(HomeItemEntity(container = CONTAINER_FOLDER, containerId = folderId, x = children.size, type = TYPE_APP, appKey = appKey))
        true
    }

    suspend fun renameFolder(folderId: Long, title: String) {
        val folder = dao.get(folderId) ?: return
        dao.update(folder.copy(title = title.trim().ifEmpty { "Carpeta" }))
    }

    /** Takes an item out of a folder and puts it on the desktop. */
    suspend fun removeFromFolder(itemId: Long, startPage: Int): Result = db.withTransaction {
        val item = dao.get(itemId) ?: return@withTransaction Result.NoSpace
        val folderId = item.containerId
        dao.delete(itemId)
        val res = addToDesktop(item.copy(id = 0), startPage)
        normalizeFolder(folderId)
        res
    }

    /** Deletes an item. Returns the AppWidget id to release if it was a widget. */
    suspend fun delete(itemId: Long): Int? = db.withTransaction {
        val item = dao.get(itemId) ?: return@withTransaction null
        if (item.type == TYPE_FOLDER) dao.deleteChildren(CONTAINER_FOLDER, item.id)
        dao.delete(itemId)
        if (item.container == CONTAINER_FOLDER) normalizeFolder(item.containerId)
        item.widgetId
    }

    /** Ungroups a folder: its apps go back to the desktop. */
    suspend fun ungroupFolder(folderId: Long, startPage: Int) = db.withTransaction {
        val folder = dao.get(folderId) ?: return@withTransaction
        val children = dao.itemsIn(CONTAINER_FOLDER, folderId)
        dao.delete(folderId)
        children.forEach { child ->
            dao.delete(child.id)
            addToDesktop(child.copy(id = 0), folder.containerId.toInt().takeIf { folder.container == CONTAINER_DESKTOP } ?: startPage)
        }
    }

    /** Folders with a single item turn back into that item; empty folders are deleted; order is compacted. */
    private suspend fun normalizeFolder(folderId: Long) {
        val folder = dao.get(folderId) ?: return
        val children = dao.itemsIn(CONTAINER_FOLDER, folderId)
        when (children.size) {
            0 -> dao.delete(folderId)
            1 -> {
                dao.delete(folderId)
                dao.update(children[0].copy(container = folder.container, containerId = folder.containerId, x = folder.x, y = folder.y))
            }
            else -> dao.updateAll(children.mapIndexed { i, c -> c.copy(x = i) })
        }
    }

    suspend fun reorderFolder(folderId: Long, orderedIds: List<Long>) {
        val children = dao.itemsIn(CONTAINER_FOLDER, folderId).associateBy { it.id }
        dao.updateAll(orderedIds.mapIndexedNotNull { i, id -> children[id]?.copy(x = i) })
    }

    /** Removes everything that belongs to uninstalled apps. Returns widget ids to release. */
    suspend fun removeApps(appKeys: List<String>, packageName: String?, userSerial: Long?) = db.withTransaction {
        val toRemove = dao.itemsForApps(appKeys).toMutableList()
        if (packageName != null) {
            toRemove += dao.shortcutsForPackages(listOf(packageName)).filter { userSerial == null || it.userSerial == userSerial }
        }
        val folders = toRemove.filter { it.container == CONTAINER_FOLDER }.map { it.containerId }.toSet()
        toRemove.forEach { dao.delete(it.id) }
        folders.forEach { normalizeFolder(it) }
    }

    /** Re-fits the desktop after a grid size change. */
    suspend fun applyGrid(cols: Int, rows: Int) = db.withTransaction {
        val pages = settings.current().homePages
        val (changed, newPages) = GridPlacement.repack(dao.getAll(), cols, rows, pages)
        settings.setHomeGrid(cols, rows)
        if (newPages != pages) settings.setHomePages(newPages)
        dao.updateAll(changed)
        // The dock has one slot per column: overflowing dock items move to the desktop.
        dao.itemsIn(CONTAINER_DOCK, 0).filter { it.x >= cols }.forEach { item ->
            dao.delete(item.id)
            addToDesktop(item.copy(id = 0, container = CONTAINER_DESKTOP), 0)
        }
    }

    suspend fun removePage(page: Int): Boolean = db.withTransaction {
        val s = settings.current()
        if (s.homePages <= 1) return@withTransaction false
        val all = dao.getAll()
        if (all.any { it.container == CONTAINER_DESKTOP && it.containerId.toInt() == page }) return@withTransaction false
        dao.updateAll(all.filter { it.container == CONTAINER_DESKTOP && it.containerId > page }.map { it.copy(containerId = it.containerId - 1) })
        settings.setHomePages(s.homePages - 1)
        true
    }

    suspend fun count() = dao.count()
    suspend fun insertRaw(item: HomeItemEntity) = dao.insert(item)
    suspend fun widgetIds() = dao.widgetIds()
    suspend fun allItems() = dao.getAll()
    suspend fun get(id: Long) = dao.get(id)

    companion object {
        val FOLDERABLE = setOf(TYPE_APP, TYPE_SHORTCUT)
    }
}
