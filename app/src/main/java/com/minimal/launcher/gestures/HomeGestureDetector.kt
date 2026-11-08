package com.minimal.launcher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Width of the system gesture areas, in px (back edges and home bar). */
data class SystemZones(val left: Float, val right: Float, val bottom: Float)

/**
 * Swipes (1 finger), pinch and two-finger swipes on the home screen. Runs on the parent of the pages,
 * after children (Main pass), so:
 *  - item long-press/drag ([com.minimal.launcher.home.DragController]) and widget scrolling win: a
 *    gesture is ignored when [enabled] is false or a child already consumed the movement;
 *  - horizontal movement is left to the pager; it only counts at the first/last page;
 *  - touches that start in the system gesture areas are ignored.
 * Taps, double taps and long-presses are reported by [com.minimal.launcher.home.CellLayout].
 */
suspend fun PointerInputScope.detectHomeGestures(
    enabled: () -> Boolean,
    zones: () -> SystemZones,
    /** True when swipe-up opens the drawer: then it follows the finger. */
    liveDrawer: () -> Boolean,
    canScroll: () -> Pair<Boolean, Boolean>,
    onDrawerDrag: (dy: Float) -> Unit,
    onDrawerRelease: (velocityY: Float) -> Unit,
    onGesture: (HomeGesture) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) return@awaitEachGesture
        val z = zones()
        if (GestureClassifier.inSystemZone(down.position.x, down.position.y, size.width.toFloat(), size.height.toFloat(), z.left, z.right, z.bottom)) {
            return@awaitEachGesture
        }
        val (canBack, canForward) = canScroll()
        val slop = viewConfiguration.touchSlop
        val minDistance = 56.dp.toPx()
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }

        var mode = Mode.UNDECIDED
        var total = Offset.Zero
        var drivingDrawer = false
        var startDistance = 0f
        var startCentroid = Offset.Zero
        var lastDistance = 0f
        var lastCentroid = Offset.Zero

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2 && mode != Mode.VERTICAL) {
                val (a, b) = pressed
                val distance = GestureClassifier.distance(a.position.x, a.position.y, b.position.x, b.position.y)
                val centroid = (a.position + b.position) / 2f
                if (mode != Mode.MULTI) {
                    mode = Mode.MULTI
                    startDistance = distance
                    startCentroid = centroid
                }
                lastDistance = distance
                lastCentroid = centroid
                event.changes.forEach(PointerInputChange::consume)
                continue
            }
            if (mode == Mode.MULTI) continue // waiting for the remaining finger to lift
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!enabled()) return@awaitEachGesture
            val step = change.position - change.previousPosition
            total = change.position - down.position
            velocity.addPosition(change.uptimeMillis, change.position)
            if (mode == Mode.UNDECIDED) {
                when {
                    abs(total.y) > slop && abs(total.y) > abs(total.x) -> {
                        if (change.isConsumed) return@awaitEachGesture // a scrollable widget took it
                        mode = Mode.VERTICAL
                    }
                    abs(total.x) > slop -> mode = Mode.HORIZONTAL
                }
            }
            if (mode == Mode.VERTICAL) {
                if (liveDrawer() && (drivingDrawer || total.y < 0f)) {
                    drivingDrawer = true
                    onDrawerDrag(step.y)
                }
                change.consume()
            }
        }

        when (mode) {
            Mode.MULTI -> GestureClassifier.twoFinger(startDistance, lastDistance, lastCentroid.y - startCentroid.y, minDistance)?.let(onGesture)
            Mode.VERTICAL -> if (drivingDrawer) {
                onDrawerRelease(velocity.calculateVelocity().y)
            } else {
                GestureClassifier.swipe(total.x, total.y, minDistance, canBack, canForward)?.let(onGesture)
            }
            Mode.HORIZONTAL -> GestureClassifier.swipe(total.x, total.y, minDistance, canBack, canForward)?.let(onGesture)
            Mode.UNDECIDED -> Unit
        }
    }
}

private enum class Mode { UNDECIDED, VERTICAL, HORIZONTAL, MULTI }
