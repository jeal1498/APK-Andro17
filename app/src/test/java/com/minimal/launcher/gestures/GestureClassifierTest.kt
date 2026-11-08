package com.minimal.launcher.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierTest {
    private val min = 100f

    @Test fun verticalSwipes() {
        assertEquals(HomeGesture.SWIPE_UP, GestureClassifier.swipe(10f, -300f, min, true, true))
        assertEquals(HomeGesture.SWIPE_DOWN, GestureClassifier.swipe(-20f, 250f, min, true, true))
        assertNull(GestureClassifier.swipe(0f, 40f, min, true, true)) // too short
    }

    @Test fun horizontalSwipesNeverStealPageChanges() {
        // Middle page: the pager scrolls both ways, so no gesture.
        assertNull(GestureClassifier.swipe(400f, 10f, min, canScrollBackward = true, canScrollForward = true))
        assertNull(GestureClassifier.swipe(-400f, 10f, min, canScrollBackward = true, canScrollForward = true))
        // First page, finger to the right → gesture; last page, finger to the left → gesture.
        assertEquals(HomeGesture.SWIPE_RIGHT, GestureClassifier.swipe(400f, 10f, min, canScrollBackward = false, canScrollForward = true))
        assertEquals(HomeGesture.SWIPE_LEFT, GestureClassifier.swipe(-400f, 10f, min, canScrollBackward = true, canScrollForward = false))
    }

    @Test fun twoFingers() {
        assertEquals(HomeGesture.PINCH, GestureClassifier.twoFinger(600f, 300f, 0f, min))
        assertEquals(HomeGesture.TWO_FINGER_DOWN, GestureClassifier.twoFinger(400f, 410f, 300f, min))
        assertEquals(HomeGesture.TWO_FINGER_UP, GestureClassifier.twoFinger(400f, 390f, -300f, min))
        assertNull(GestureClassifier.twoFinger(400f, 380f, 20f, min))
    }

    @Test fun systemGestureAreasAreIgnored() {
        assertTrue(GestureClassifier.inSystemZone(10f, 500f, 1000f, 2000f, 40f, 40f, 60f))
        assertTrue(GestureClassifier.inSystemZone(980f, 500f, 1000f, 2000f, 40f, 40f, 60f))
        assertTrue(GestureClassifier.inSystemZone(500f, 1970f, 1000f, 2000f, 40f, 40f, 60f))
        assertFalse(GestureClassifier.inSystemZone(500f, 1000f, 1000f, 2000f, 40f, 40f, 60f))
    }

    @Test fun actionsRoundTripAndDefaults() {
        val app = GestureAction(GestureAction.Type.APP, "com.x/.Main#0")
        assertEquals(app, GestureAction.decode(app.encode()))
        assertEquals(GestureAction(GestureAction.Type.PAIR, "12"), GestureAction.decode("PAIR:12"))
        assertNull(GestureAction.decode("APP"))
        assertNull(GestureAction.decode("BOGUS"))
        assertEquals(GestureAction.Type.DRAWER, GestureAction.defaultFor(HomeGesture.SWIPE_UP).type)
        assertEquals(GestureAction.Type.HOME_MENU, GestureAction.defaultFor(HomeGesture.LONG_PRESS).type)
    }
}
