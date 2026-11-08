package com.minimal.launcher.home

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.ui.components.AppTile
import com.minimal.launcher.ui.components.detectLauncherGestures
import com.minimal.launcher.widgets.WidgetView
import kotlin.math.floor
import kotlin.math.roundToInt

/** Geometry of one grid page in pixels. */
data class CellMetrics(val cellW: Float, val cellH: Float, val cols: Int, val rows: Int) {
    fun cellAt(p: Offset): Pair<Int, Int> =
        floor(p.x / cellW).toInt().coerceIn(0, cols - 1) to floor(p.y / cellH).toInt().coerceIn(0, rows - 1)
}

/** Callbacks from a home page to the screen. */
interface CellCallbacks {
    fun onItemTap(item: HomeItemEntity, localInItem: Offset, itemSize: IntSize)
    fun onItemLongPress(item: HomeItemEntity, pointerRoot: Offset, grab: Offset, sizePx: IntSize)
    fun onBackgroundLongPress(page: Int, local: Offset)
    fun onBackgroundTap()
    fun onDrag(delta: Offset)
    fun onDragEnd(cancelled: Boolean)
    fun onResize(item: HomeItemEntity, x: Int, y: Int, spanX: Int, spanY: Int)
}

@Composable
fun CellLayout(
    page: Int,
    isCurrentPage: Boolean,
    items: List<HomeItemEntity>,
    folderChildren: Map<Long, List<HomeItemEntity>>,
    apps: Map<String, AppInfo>,
    cols: Int,
    rows: Int,
    maxIconSize: Dp,
    showLabels: Boolean,
    labelStyle: TextStyle,
    wallpaperTextColor: Color,
    darkText: Boolean,
    drag: DragController,
    hiddenItemId: Long?,
    resizingItemId: Long?,
    resizeLimits: (HomeItemEntity, CellMetrics) -> ResizeLimits,
    callbacks: CellCallbacks,
    overlay: @Composable (HomeItemEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val pageItems = remember(items, page) {
        items.filter { it.container == HomeItemEntity.CONTAINER_DESKTOP && it.containerId.toInt() == page }
    }
    val currentItems by rememberUpdatedState(pageItems)
    val currentResizing by rememberUpdatedState(resizingItemId)
    val cb by rememberUpdatedState(callbacks)
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                origin = coords.positionInRoot()
                if (isCurrentPage) {
                    drag.gridOrigin = origin
                    drag.gridSize = coords.size
                }
            },
    ) {
        val metrics = with(density) { CellMetrics(maxWidth.toPx() / cols, maxHeight.toPx() / rows, cols, rows) }
        val currentMetrics by rememberUpdatedState(metrics)
        val cellWdp = maxWidth / cols
        val cellHdp = maxHeight / rows
        val labelSpace = 22.dp
        val iconSize = minOf(maxIconSize, cellWdp * 0.78f, if (showLabels) cellHdp - labelSpace - 4.dp else cellHdp * 0.8f)
            .coerceAtLeast(28.dp)
        val labelsFit = showLabels && cellHdp >= iconSize + labelSpace

        LaunchedEffect(isCurrentPage, origin, metrics) {
            if (isCurrentPage) drag.gridOrigin = origin
        }

        // One detector for the whole page: icons, folders, clock and empty space.
        // Widgets handle their own touches (AndroidView) and are excluded via shouldHandle.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(page) {
                    fun itemAt(p: Offset): HomeItemEntity? {
                        val (cx, cy) = currentMetrics.cellAt(p)
                        return GridPlacement.itemAt(currentItems, page, cx, cy)
                    }
                    detectLauncherGestures(
                        shouldHandle = { p -> itemAt(p)?.type != HomeItemEntity.TYPE_WIDGET || currentResizing != null },
                        onTap = { p ->
                            val item = itemAt(p)
                            if (item == null || currentResizing != null) cb.onBackgroundTap()
                            else {
                                val m = currentMetrics
                                cb.onItemTap(
                                    item,
                                    Offset(p.x - item.x * m.cellW, p.y - item.y * m.cellH),
                                    IntSize((item.spanX * m.cellW).toInt(), (item.spanY * m.cellH).toInt()),
                                )
                            }
                        },
                        onLongPress = { p ->
                            val item = itemAt(p)
                            if (item == null) cb.onBackgroundLongPress(page, p)
                            else {
                                val m = currentMetrics
                                val topLeft = Offset(item.x * m.cellW, item.y * m.cellH)
                                cb.onItemLongPress(
                                    item, origin + p, p - topLeft,
                                    IntSize((item.spanX * m.cellW).toInt(), (item.spanY * m.cellH).toInt()),
                                )
                            }
                        },
                        onDrag = { _, delta -> cb.onDrag(delta) },
                        onDragEnd = { cb.onDragEnd(it) },
                    )
                },
        ) {
            // Drop target preview while dragging over this page.
            val payload = drag.payload
            if (payload != null && isCurrentPage && drag.moved) {
                val local = drag.topLeft - origin
                val inside = local.x > -metrics.cellW / 2 && local.y > -metrics.cellH / 2 &&
                    local.x < metrics.cellW * cols && local.y < metrics.cellH * rows
                if (inside) {
                    val (tx, ty) = GridPlacement.cellForPosition(local.x, local.y, metrics.cellW, metrics.cellH, payload.spanX, payload.spanY, cols, rows)
                    val free = GridPlacement.isFree(pageItems, page, tx, ty, payload.spanX, payload.spanY, cols, rows, payload.itemId)
                    if (free) {
                        Box(
                            Modifier
                                .offset { IntOffset((tx * metrics.cellW).roundToInt(), (ty * metrics.cellH).roundToInt()) }
                                .size(cellWdp * payload.spanX, cellHdp * payload.spanY)
                                .graphicsLayer { alpha = 0.9f }
                                .border(2.dp, wallpaperTextColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
                        )
                    }
                }
            }

            pageItems.forEach { item ->
                key(item.id) {
                    val w = cellWdp * item.spanX
                    val h = cellHdp * item.spanY
                    Box(
                        Modifier
                            .offset { IntOffset((item.x * metrics.cellW).roundToInt(), (item.y * metrics.cellH).roundToInt()) }
                            .size(w, h)
                            .graphicsLayer { alpha = if (item.id == hiddenItemId) 0f else 1f },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (item.type) {
                            HomeItemEntity.TYPE_WIDGET -> WidgetView(
                                appWidgetId = item.widgetId ?: -1,
                                widthDp = w.value.toInt(),
                                heightDp = h.value.toInt(),
                                onLongPress = {
                                    val m = currentMetrics
                                    val size = IntSize((item.spanX * m.cellW).toInt(), (item.spanY * m.cellH).toInt())
                                    val topLeft = origin + Offset(item.x * m.cellW, item.y * m.cellH)
                                    val grab = Offset(size.width / 2f, size.height / 2f)
                                    callbacks.onItemLongPress(item, topLeft + grab, grab, size)
                                },
                                onDrag = { dx, dy -> callbacks.onDrag(Offset(dx, dy)) },
                                onDragEnd = { callbacks.onDragEnd(it) },
                            )
                            HomeItemEntity.TYPE_CLOCK -> ClockWidget(
                                textColor = wallpaperTextColor,
                                shadow = !darkText,
                                compact = h < 96.dp,
                            )
                            else -> {
                                val app = item.appKey?.let { apps[it] }
                                if (item.type == HomeItemEntity.TYPE_APP && app == null) return@Box
                                AppTile(
                                    icon = { size -> ItemIcon(item, app, folderChildren[item.id].orEmpty(), size) },
                                    label = itemLabel(item, app),
                                    iconSize = iconSize,
                                    showLabel = labelsFit,
                                    labelStyle = labelStyle,
                                )
                            }
                        }
                        overlay(item)
                    }
                }
            }

            pageItems.firstOrNull { it.id == resizingItemId }?.let { item ->
                ResizeFrame(item, metrics, cellWdp, cellHdp, pageItems, resizeLimits(item, metrics), wallpaperTextColor) { x, y, sx, sy ->
                    callbacks.onResize(item, x, y, sx, sy)
                }
            }
        }
    }
}

data class ResizeLimits(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val horizontal: Boolean, val vertical: Boolean)

/** Frame with handles to resize a widget in whole cells, respecting provider limits and collisions. */
@Composable
private fun ResizeFrame(
    item: HomeItemEntity,
    m: CellMetrics,
    cellWdp: Dp,
    cellHdp: Dp,
    pageItems: List<HomeItemEntity>,
    limits: ResizeLimits,
    color: Color,
    onCommit: (x: Int, y: Int, spanX: Int, spanY: Int) -> Unit,
) {
    var rect by remember(item) { mutableStateOf(listOf(item.x, item.y, item.spanX, item.spanY)) }
    val (x, y, sx, sy) = rect
    val page = item.containerId.toInt()

    fun tryRect(nx: Int, ny: Int, nsx: Int, nsy: Int) {
        if (nsx !in limits.minX..limits.maxX || nsy !in limits.minY..limits.maxY) return
        if (GridPlacement.isFree(pageItems, page, nx, ny, nsx, nsy, m.cols, m.rows, excludeId = item.id)) rect = listOf(nx, ny, nsx, nsy)
    }

    Box(
        Modifier
            .offset { IntOffset((x * m.cellW).roundToInt(), (y * m.cellH).roundToInt()) }
            .size(cellWdp * sx, cellHdp * sy)
            .border(2.dp, color, RoundedCornerShape(16.dp)),
    ) {
        @Composable
        fun Handle(align: Alignment, onDelta: (Offset) -> Unit) {
            var acc by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .align(align)
                    .size(36.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { acc = Offset.Zero },
                            onDragEnd = { onCommit(rect[0], rect[1], rect[2], rect[3]) },
                            onDragCancel = { onCommit(rect[0], rect[1], rect[2], rect[3]) },
                        ) { change, amount ->
                            change.consume()
                            acc += amount
                            val stepX = (acc.x / m.cellW).roundToInt()
                            val stepY = (acc.y / m.cellH).roundToInt()
                            if (stepX != 0 || stepY != 0) {
                                val before = rect
                                onDelta(Offset(stepX.toFloat(), stepY.toFloat()))
                                if (rect != before) acc = Offset(acc.x - stepX * m.cellW, acc.y - stepY * m.cellH)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).background(color, CircleShape))
            }
        }
        if (limits.horizontal) {
            Handle(Alignment.CenterEnd) { d -> tryRect(rect[0], rect[1], rect[2] + d.x.toInt(), rect[3]) }
            Handle(Alignment.CenterStart) { d -> tryRect(rect[0] + d.x.toInt(), rect[1], rect[2] - d.x.toInt(), rect[3]) }
        }
        if (limits.vertical) {
            Handle(Alignment.BottomCenter) { d -> tryRect(rect[0], rect[1], rect[2], rect[3] + d.y.toInt()) }
            Handle(Alignment.TopCenter) { d -> tryRect(rect[0], rect[1] + d.y.toInt(), rect[2], rect[3] - d.y.toInt()) }
        }
    }
}
