package com.minimal.launcher.home

import com.minimal.launcher.data.db.HomeItemEntity

/** Pure cell math for the home grid. Coordinates are (column, row) with spans. */
object GridPlacement {

    data class Spot(val page: Int, val x: Int, val y: Int)

    fun desktopItems(items: List<HomeItemEntity>) = items.filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }

    fun occupancy(items: List<HomeItemEntity>, page: Int, cols: Int, rows: Int, excludeId: Long? = null): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        desktopItems(items).filter { it.containerId.toInt() == page && it.id != excludeId }.forEach { item ->
            for (dy in 0 until item.spanY) for (dx in 0 until item.spanX) {
                val cx = item.x + dx
                val cy = item.y + dy
                if (cx in 0 until cols && cy in 0 until rows) grid[cy][cx] = true
            }
        }
        return grid
    }

    fun fits(cols: Int, rows: Int, x: Int, y: Int, spanX: Int, spanY: Int) =
        x >= 0 && y >= 0 && spanX >= 1 && spanY >= 1 && x + spanX <= cols && y + spanY <= rows

    fun isFree(
        items: List<HomeItemEntity>, page: Int, x: Int, y: Int, spanX: Int, spanY: Int,
        cols: Int, rows: Int, excludeId: Long? = null,
    ): Boolean {
        if (!fits(cols, rows, x, y, spanX, spanY)) return false
        val grid = occupancy(items, page, cols, rows, excludeId)
        for (dy in 0 until spanY) for (dx in 0 until spanX) if (grid[y + dy][x + dx]) return false
        return true
    }

    /** Item occupying a given cell of a page, if any. */
    fun itemAt(items: List<HomeItemEntity>, page: Int, x: Int, y: Int, excludeId: Long? = null): HomeItemEntity? =
        desktopItems(items).firstOrNull {
            it.id != excludeId && it.containerId.toInt() == page &&
                x in it.x until it.x + it.spanX && y in it.y until it.y + it.spanY
        }

    fun findFreeSpotOnPage(
        items: List<HomeItemEntity>, page: Int, cols: Int, rows: Int, spanX: Int, spanY: Int, excludeId: Long? = null,
    ): Spot? {
        if (spanX > cols || spanY > rows) return null
        val grid = occupancy(items, page, cols, rows, excludeId)
        for (y in 0..rows - spanY) for (x in 0..cols - spanX) {
            var free = true
            loop@ for (dy in 0 until spanY) for (dx in 0 until spanX) if (grid[y + dy][x + dx]) { free = false; break@loop }
            if (free) return Spot(page, x, y)
        }
        return null
    }

    /**
     * First free spot starting at [startPage], then the following pages, then earlier ones.
     * If every page is full, returns a spot on a new page (index == pageCount).
     */
    fun findFreeSpot(
        items: List<HomeItemEntity>, startPage: Int, pageCount: Int, cols: Int, rows: Int, spanX: Int, spanY: Int,
    ): Spot {
        val order = (startPage until pageCount) + (0 until startPage.coerceAtMost(pageCount))
        for (page in order) findFreeSpotOnPage(items, page, cols, rows, spanX, spanY)?.let { return it }
        return Spot(pageCount, 0, 0)
    }

    /**
     * Re-fits desktop items after the grid size changes. Items that still fit keep their place;
     * others are clamped (widgets) and moved to the first free spot, adding pages when needed.
     * Returns the updated items (only those that changed) and the resulting page count.
     */
    fun repack(items: List<HomeItemEntity>, cols: Int, rows: Int, pageCount: Int): Pair<List<HomeItemEntity>, Int> {
        val desktop = desktopItems(items).sortedWith(compareBy({ it.containerId }, { it.y }, { it.x }))
        val placed = ArrayList<HomeItemEntity>()
        val pending = ArrayList<HomeItemEntity>()
        for (item in desktop) {
            val spanX = item.spanX.coerceAtMost(cols)
            val spanY = item.spanY.coerceAtMost(rows)
            val candidate = item.copy(spanX = spanX, spanY = spanY)
            if (isFree(placed, candidate.containerId.toInt(), candidate.x, candidate.y, spanX, spanY, cols, rows)) {
                placed += candidate
            } else {
                pending += candidate
            }
        }
        var pages = pageCount
        for (item in pending) {
            val spot = findFreeSpot(placed, item.containerId.toInt().coerceAtMost(pages - 1).coerceAtLeast(0), pages, cols, rows, item.spanX, item.spanY)
            if (spot.page >= pages) pages = spot.page + 1
            placed += item.copy(containerId = spot.page.toLong(), x = spot.x, y = spot.y)
        }
        val original = desktop.associateBy { it.id }
        return placed.filter { original[it.id] != it } to pages
    }

    /** Converts a pixel position (relative to the grid origin) to the nearest cell for an item of the given span. */
    fun cellForPosition(px: Float, py: Float, cellWidth: Float, cellHeight: Float, spanX: Int, spanY: Int, cols: Int, rows: Int): Pair<Int, Int> {
        val x = kotlin.math.round(px / cellWidth).toInt().coerceIn(0, (cols - spanX).coerceAtLeast(0))
        val y = kotlin.math.round(py / cellHeight).toInt().coerceIn(0, (rows - spanY).coerceAtLeast(0))
        return x to y
    }
}
