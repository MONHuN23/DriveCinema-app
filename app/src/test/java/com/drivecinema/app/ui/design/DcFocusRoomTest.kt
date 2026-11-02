package com.drivecinema.app.ui.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P.7: the room a focused control needs at a scroll's edge, and the bring-into-view rule that keeps it. */
class DcFocusRoomTest {

    private val spec = FocusRoomBringIntoViewSpec(room = 10f)

    @Test
    fun theFocusRoomCoversTabsButtonsAndRows() {
        assertTrue(dcFocusOverflow(150.dp) <= DcFocusRoom)
        assertTrue(dcFocusOverflow(64.dp, DcTokens.Focus.ROW_SCALE) <= DcFocusRoom)
        assertTrue(dcFocusOverflow(500.dp, DcTokens.Focus.ROW_SCALE) <= DcFocusRoom)
    }

    @Test
    fun anItemWithRoomInViewDoesNotScroll() {
        assertEquals(0f, spec.calculateScrollDistance(offset = 10f, size = 60f, containerSize = 100f))
    }

    @Test
    fun anItemAtTheBottomEdgeScrollsToLeaveRoomBelowIt() {
        // Flush with the bottom: the default spec stops here and the scroll clips the ring.
        assertEquals(10f, spec.calculateScrollDistance(offset = 340f, size = 60f, containerSize = 400f))
        assertEquals(30f, spec.calculateScrollDistance(offset = 360f, size = 60f, containerSize = 400f))
    }

    @Test
    fun anItemAtTheTopEdgeScrollsToLeaveRoomAboveIt() {
        assertEquals(-10f, spec.calculateScrollDistance(offset = 0f, size = 60f, containerSize = 400f))
        assertEquals(-25f, spec.calculateScrollDistance(offset = -15f, size = 60f, containerSize = 400f))
    }

    @Test
    fun anItemThatFitsOnlyWithoutRoomFollowsTheDefaultRule() {
        assertEquals(-3f, spec.calculateScrollDistance(offset = -3f, size = 95f, containerSize = 100f))
        assertEquals(0f, spec.calculateScrollDistance(offset = 0f, size = 300f, containerSize = 100f))
    }
}
