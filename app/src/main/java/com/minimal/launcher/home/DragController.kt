package com.minimal.launcher.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.minimal.launcher.data.db.HomeItemEntity

/** What is being dragged: an existing home item ([itemId]) or a new app coming from the drawer ([appKey]). */
data class DragPayload(
    val itemId: Long?,
    val appKey: String?,
    val type: Int,
    val spanX: Int,
    val spanY: Int,
    val sizePx: IntSize,
    val fromDock: Boolean = false,
) {
    val isSingleCell get() = spanX == 1 && spanY == 1
    val folderable get() = type == HomeItemEntity.TYPE_APP || type == HomeItemEntity.TYPE_SHORTCUT
    val dockable get() = isSingleCell && (folderable || type == HomeItemEntity.TYPE_FOLDER)
}

/** Shared drag state in root coordinates, plus the geometry of the drop targets (grid + dock). */
@Stable
class DragController {
    var payload by mutableStateOf<DragPayload?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var moved by mutableStateOf(false)
        private set
    private var grab = Offset.Zero
    private var start = Offset.Zero

    // Drop-target geometry (root coordinates), reported by the layouts.
    var gridOrigin by mutableStateOf(Offset.Zero)
    var gridSize by mutableStateOf(IntSize.Zero)
    var dockBounds by mutableStateOf(Rect.Zero)

    val topLeft: Offset get() = pointer - grab
    val isDragging get() = payload != null

    fun start(payload: DragPayload, pointerRoot: Offset, grabOffset: Offset) {
        this.payload = payload
        pointer = pointerRoot
        start = pointerRoot
        grab = grabOffset
        moved = false
    }

    fun moveBy(delta: Offset, thresholdPx: Float) {
        pointer += delta
        if (!moved && (pointer - start).getDistance() > thresholdPx) moved = true
    }

    fun moveTo(pointerRoot: Offset, thresholdPx: Float) = moveBy(pointerRoot - pointer, thresholdPx)

    fun clear() {
        payload = null
        moved = false
    }
}
