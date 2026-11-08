package com.minimal.launcher.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Widget view that detects long-press itself (the widget's own views consume touches, so Compose
 * gestures on a parent would never see them). After a long-press it intercepts the gesture and
 * reports drag deltas so the widget can be moved like any other home item.
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    interface Listener {
        fun onLongPress()
        fun onDrag(dx: Float, dy: Float, rawX: Float, rawY: Float)
        fun onDragEnd(cancelled: Boolean)
    }

    var listener: Listener? = null
    var lastReportedSize: Pair<Int, Int>? = null

    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        listener?.onLongPress()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX; downY = ev.rawY; lastX = ev.rawX; lastY = ev.rawY
                longPressed = false
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (!longPressed && (abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop)) {
                    handler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPressRunnable)
        }
        return longPressed
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!longPressed) {
            // Touch on an empty area of the widget: still detect long-press.
            onInterceptTouchEvent(ev)
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                listener?.onDrag(ev.rawX - lastX, ev.rawY - lastY, ev.rawX, ev.rawY)
                lastX = ev.rawX; lastY = ev.rawY
            }
            MotionEvent.ACTION_UP -> { longPressed = false; listener?.onDragEnd(false); performClick() }
            MotionEvent.ACTION_CANCEL -> { longPressed = false; listener?.onDragEnd(true) }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun cancelLongPress() {
        super.cancelLongPress()
        handler.removeCallbacks(longPressRunnable)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}
