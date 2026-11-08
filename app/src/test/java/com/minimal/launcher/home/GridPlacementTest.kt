package com.minimal.launcher.home

import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.data.db.HomeItemEntity.Companion.CONTAINER_DESKTOP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPlacementTest {

    private fun item(id: Long, page: Int, x: Int, y: Int, sx: Int = 1, sy: Int = 1) =
        HomeItemEntity(id = id, container = CONTAINER_DESKTOP, containerId = page.toLong(), x = x, y = y, spanX = sx, spanY = sy, type = HomeItemEntity.TYPE_APP)

    @Test fun freeSpotSkipsOccupiedCellsAndWidgets() {
        val items = listOf(item(1, 0, 0, 0, 4, 1), item(2, 0, 0, 1), item(3, 0, 1, 1))
        val spot = GridPlacement.findFreeSpot(items, 0, 1, 4, 5, 1, 1)
        assertEquals(GridPlacement.Spot(0, 2, 1), spot)
        val wide = GridPlacement.findFreeSpot(items, 0, 1, 4, 5, 3, 2)
        assertEquals(GridPlacement.Spot(0, 0, 2), wide)
    }

    @Test fun fullPagesProduceNewPage() {
        val items = (0 until 4).flatMap { x -> (0 until 2).map { y -> item((x * 10 + y).toLong(), 0, x, y) } }
        assertEquals(GridPlacement.Spot(1, 0, 0), GridPlacement.findFreeSpot(items, 0, 1, 4, 2, 1, 1))
    }

    @Test fun searchStartsAtRequestedPageThenWraps() {
        val items = listOf(item(1, 1, 0, 0))
        assertEquals(GridPlacement.Spot(1, 1, 0), GridPlacement.findFreeSpot(items, 1, 2, 4, 5, 1, 1))
        val fullPage1 = (0 until 2).flatMap { x -> (0 until 2).map { y -> item(100L + x * 10 + y, 1, x, y) } }
        assertEquals(GridPlacement.Spot(0, 0, 0), GridPlacement.findFreeSpot(fullPage1, 1, 2, 2, 2, 1, 1))
    }

    @Test fun isFreeRespectsBoundsAndExclusion() {
        val items = listOf(item(1, 0, 1, 1, 2, 2))
        assertFalse(GridPlacement.isFree(items, 0, 2, 2, 1, 1, 4, 5))
        assertTrue(GridPlacement.isFree(items, 0, 2, 2, 1, 1, 4, 5, excludeId = 1))
        assertFalse(GridPlacement.isFree(items, 0, 3, 0, 2, 1, 4, 5)) // out of bounds
        assertTrue(GridPlacement.isFree(items, 1, 1, 1, 1, 1, 4, 5)) // other page
    }

    @Test fun itemAtFindsSpannedItem() {
        val items = listOf(item(1, 0, 1, 1, 2, 2))
        assertEquals(1L, GridPlacement.itemAt(items, 0, 2, 2)?.id)
        assertNull(GridPlacement.itemAt(items, 0, 0, 0))
    }

    @Test fun repackAfterShrinkingGridKeepsEverything() {
        val items = listOf(item(1, 0, 0, 0, 5, 2), item(2, 0, 4, 4), item(3, 0, 1, 3))
        val (changed, pages) = GridPlacement.repack(items, 4, 4, 1)
        val byId = (items.associateBy { it.id } + changed.associateBy { it.id })
        // Widget clamped to grid width, overflowing icon relocated, fitting icon untouched.
        assertEquals(4, byId[1]!!.spanX)
        assertTrue(changed.none { it.id == 3L })
        val moved = byId[2]!!
        assertTrue(GridPlacement.fits(4, 4, moved.x, moved.y, 1, 1))
        // No overlaps on any page.
        val all = byId.values.toList()
        for (a in all) for (b in all) if (a.id < b.id && a.containerId == b.containerId) {
            val overlap = a.x < b.x + b.spanX && b.x < a.x + a.spanX && a.y < b.y + b.spanY && b.y < a.y + a.spanY
            assertFalse("${a.id} overlaps ${b.id}", overlap)
        }
        assertTrue(pages >= 1)
    }

    @Test fun cellForPositionRoundsAndClamps() {
        assertEquals(1 to 2, GridPlacement.cellForPosition(110f, 260f, 100f, 120f, 1, 1, 4, 5))
        assertEquals(2 to 0, GridPlacement.cellForPosition(390f, -50f, 100f, 120f, 2, 1, 4, 5))
        assertNotNull(GridPlacement.findFreeSpotOnPage(emptyList(), 0, 4, 5, 4, 5))
        assertNull(GridPlacement.findFreeSpotOnPage(emptyList(), 0, 4, 5, 5, 1))
    }
}
