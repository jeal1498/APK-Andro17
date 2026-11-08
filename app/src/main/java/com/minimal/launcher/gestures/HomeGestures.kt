package com.minimal.launcher.gestures

import kotlin.math.abs
import kotlin.math.hypot

/** Gestures recognised on empty areas of the home screen. */
enum class HomeGesture(val label: String) {
    SWIPE_UP("Deslizar hacia arriba"),
    SWIPE_DOWN("Deslizar hacia abajo"),
    SWIPE_LEFT("Deslizar a la izquierda (en la última página)"),
    SWIPE_RIGHT("Deslizar a la derecha (en la primera página)"),
    DOUBLE_TAP("Doble toque"),
    PINCH("Pellizcar"),
    TWO_FINGER_UP("Deslizar con dos dedos hacia arriba"),
    TWO_FINGER_DOWN("Deslizar con dos dedos hacia abajo"),
    LONG_PRESS("Mantener pulsado un espacio vacío"),
}

/** What a gesture does. [target] is the app key (APP) or the pair id (PAIR). */
data class GestureAction(val type: Type, val target: String? = null) {
    enum class Type(val label: String) {
        NONE("Nada"),
        DRAWER("Abrir el cajón de apps"),
        SEARCH("Buscar"),
        NOTIFICATIONS("Abrir notificaciones"),
        QUICK_SETTINGS("Ajustes rápidos"),
        EDIT_MODE("Vista de páginas / modo edición"),
        HOME_MENU("Menú de inicio (widgets, fondo…)"),
        FLASHLIGHT("Encender o apagar la linterna"),
        SCREEN_OFF("Apagar la pantalla"),
        APP("Abrir una app"),
        PAIR("Abrir un par de apps"),
    }

    /** Needs the accessibility service (notifications fall back to the status bar API). */
    val needsAccessibility: Boolean get() = type == Type.SCREEN_OFF

    fun encode(): String = if (target != null) "${type.name}:$target" else type.name

    companion object {
        val NONE = GestureAction(Type.NONE)

        fun decode(raw: String?): GestureAction? {
            if (raw.isNullOrEmpty()) return null
            val type = runCatching { Type.valueOf(raw.substringBefore(':')) }.getOrNull() ?: return null
            val target = raw.substringAfter(':', "").ifEmpty { null }
            if ((type == Type.APP || type == Type.PAIR) && target == null) return null
            return GestureAction(type, target)
        }

        fun defaultFor(g: HomeGesture): GestureAction = when (g) {
            HomeGesture.SWIPE_UP -> GestureAction(Type.DRAWER)
            HomeGesture.SWIPE_DOWN -> GestureAction(Type.NOTIFICATIONS)
            HomeGesture.PINCH -> GestureAction(Type.EDIT_MODE)
            HomeGesture.LONG_PRESS -> GestureAction(Type.HOME_MENU)
            else -> NONE
        }
    }
}

/** Pure classification so it can be unit-tested without touch input. */
object GestureClassifier {
    /** Single-finger swipe; [minDistance] in px. Horizontal swipes only count when the pager cannot scroll that way. */
    fun swipe(dx: Float, dy: Float, minDistance: Float, canScrollBackward: Boolean, canScrollForward: Boolean): HomeGesture? {
        if (abs(dy) >= abs(dx)) {
            if (abs(dy) < minDistance) return null
            return if (dy < 0) HomeGesture.SWIPE_UP else HomeGesture.SWIPE_DOWN
        }
        if (abs(dx) < minDistance * 1.5f) return null
        return when {
            dx > 0 && !canScrollBackward -> HomeGesture.SWIPE_RIGHT
            dx < 0 && !canScrollForward -> HomeGesture.SWIPE_LEFT
            else -> null // the pager changes page
        }
    }

    /** Two fingers: pinch when they get much closer, otherwise a vertical swipe of their centre. */
    fun twoFinger(startDistance: Float, endDistance: Float, centroidDy: Float, minDistance: Float): HomeGesture? = when {
        startDistance > 0f && endDistance / startDistance < 0.7f -> HomeGesture.PINCH
        abs(centroidDy) >= minDistance -> if (centroidDy < 0) HomeGesture.TWO_FINGER_UP else HomeGesture.TWO_FINGER_DOWN
        else -> null
    }

    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x2 - x1, y2 - y1)

    /** True when [x],[y] falls inside the system gesture areas (back edges, home bar). */
    fun inSystemZone(x: Float, y: Float, width: Float, height: Float, left: Float, right: Float, bottom: Float): Boolean =
        x < left || x > width - right || y > height - bottom
}
