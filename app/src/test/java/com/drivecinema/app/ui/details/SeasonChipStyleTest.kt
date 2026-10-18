package com.drivecinema.app.ui.details

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** TV check 2026-10-18: with the remote you couldn't see which season chip had focus; only the selected one was red. */
class SeasonChipStyleTest {

    @Test
    fun focusedChipGetsWhiteBorderWhetherSelectedOrNot() {
        assertNotNull(seasonChipStyle(selected = false, focused = true).border)
        assertNotNull(seasonChipStyle(selected = true, focused = true).border)
        assertEquals(Color.White, seasonChipStyle(selected = false, focused = true).border!!.brush.let { (it as androidx.compose.ui.graphics.SolidColor).value })
    }

    @Test
    fun unfocusedChipsHaveNoFocusBorder() {
        assertNull(seasonChipStyle(selected = false, focused = false).border)
        assertNull(seasonChipStyle(selected = true, focused = false).border)
    }

    @Test
    fun selectedStaysRedAndFocusedUnselectedIsLighterThanIdle() {
        val red = Color(0xFFE50914)
        assertEquals(red, seasonChipStyle(selected = true, focused = false).container)
        assertEquals(red, seasonChipStyle(selected = true, focused = true).container)
        assertNotEquals(seasonChipStyle(false, false).container, seasonChipStyle(false, true).container)
        assertEquals(Color.White, seasonChipStyle(false, true).label)
    }
}
