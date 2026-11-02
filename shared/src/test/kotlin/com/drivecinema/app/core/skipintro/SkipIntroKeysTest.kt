package com.drivecinema.app.core.skipintro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** TV remote keys and the "Skip intro" button (O.2). */
class SkipIntroKeysTest {

    @Test
    fun withTheControlsHiddenOkSkipsAndBackHides() {
        assertEquals(SkipIntroKeyAction.SKIP, SkipIntroKeys.action(isOk = true, isBack = false, controlsVisible = false, buttonVisible = true))
        assertEquals(SkipIntroKeyAction.HIDE, SkipIntroKeys.action(isOk = false, isBack = true, controlsVisible = false, buttonVisible = true))
    }

    @Test
    fun otherKeysKeepTheirUsualMeaning() {
        assertNull("D-pad shows the controls as before", SkipIntroKeys.action(isOk = false, isBack = false, controlsVisible = false, buttonVisible = true))
    }

    @Test
    fun withTheControlsShowingTheButtonIsAnOrdinaryControl() {
        assertNull(SkipIntroKeys.action(isOk = true, isBack = false, controlsVisible = true, buttonVisible = true))
        assertNull("Back hides the controls first", SkipIntroKeys.action(isOk = false, isBack = true, controlsVisible = true, buttonVisible = true))
    }

    @Test
    fun withoutTheButtonNothingChanges() {
        assertNull(SkipIntroKeys.action(isOk = true, isBack = false, controlsVisible = false, buttonVisible = false))
        assertNull(SkipIntroKeys.action(isOk = false, isBack = true, controlsVisible = false, buttonVisible = false))
    }
}
