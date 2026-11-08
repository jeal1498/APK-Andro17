package com.minimal.launcher.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange

/**
 * One detector for tap, long-press and drag-after-long-press, so a long-press never also
 * triggers a tap and a parent never sees a long-press meant for a child. Movement before the
 * long-press timeout is left unconsumed so pagers and lists keep scrolling.
 *
 * [shouldHandle] lets a container ignore downs that land on areas handled elsewhere (e.g. widgets).
 */
suspend fun PointerInputScope.detectLauncherGestures(
    shouldHandle: (Offset) -> Boolean = { true },
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onDrag: (change: PointerInputChange, delta: Offset) -> Unit = { _, _ -> },
    onDragEnd: (cancelled: Boolean) -> Unit = {},
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!shouldHandle(down.position)) return@awaitEachGesture
        var tapped = false
        var aborted = false
        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: run { aborted = true; return@withTimeoutOrNull false }
                if (change.changedToUp()) {
                    tapped = !change.isConsumed
                    return@withTimeoutOrNull false
                }
                if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    aborted = true
                    return@withTimeoutOrNull false
                }
            }
            @Suppress("UNREACHABLE_CODE") false
        } == null
        when {
            tapped -> onTap(down.position)
            aborted -> Unit
            timedOut -> {
                onLongPress(down.position)
                var ended = true
                val completed = drag(down.id) { change ->
                    val delta = change.positionChange()
                    change.consume()
                    onDrag(change, delta)
                }
                if (!completed) ended = false
                onDragEnd(!ended)
            }
        }
    }
}
