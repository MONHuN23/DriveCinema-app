package com.drivecinema.app.core.skipintro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decision table of `docs/design/o0-skip-intro.md` §5; the cases named after shows use the O.0 spike's numbers. */
class SkipIntroRulesTest {

    private fun skip(start: Long, end: Long, match: SkipDbMatch, confidence: Double? = 0.9, offset: Long = 0) =
        SkipDbAnswer.Found(IntroTimes(start, end), match, offset, confidence)

    private fun intro(start: Long, end: Long, outroEnd: Long?, count: Int = 1) =
        IntroDbAnswer.Found(IntroTimes(start, end), count, outroEnd)

    @Test
    fun westworldExactSkipDbWins() {
        val outcome = SkipIntroRules.decide(skip(0, 101_400, SkipDbMatch.EXACT), null, 3_515_469)
        assertEquals(SkipIntroOutcome.Found(IntroTimes(0, 101_400), IntroSource.SKIPDB), outcome)
        assertFalse(SkipIntroRules.needsIntroDb(skip(0, 101_400, SkipDbMatch.EXACT), 3_515_469))
    }

    @Test
    fun familyGuyOutOfRangeFallsBackToIntroDbWithTheSameCut() {
        val skipDb = skip(0, 31_000, SkipDbMatch.OUT_OF_RANGE, 0.6, -48_100)
        assertTrue(SkipIntroRules.needsIntroDb(skipDb, 1_298_003))
        val outcome = SkipIntroRules.decide(skipDb, intro(0, 40_000, outroEnd = 1_298_000), 1_298_003)
        assertEquals(SkipIntroOutcome.Found(IntroTimes(0, 40_000), IntroSource.INTRODB), outcome)
    }

    @Test
    fun introDbWithoutAnOutroIsNotTrusted() {
        // Family Guy S21E1: IntroDB has 0:00–0:30 but nothing shows it was timed on this cut.
        val outcome = SkipIntroRules.decide(
            skip(0, 31_000, SkipDbMatch.OUT_OF_RANGE, 0.6, -58_100), intro(0, 30_000, outroEnd = null), 1_298_016
        )
        assertEquals(SkipIntroOutcome.Unknown, outcome)
    }

    @Test
    fun introDbFromAnotherCutIsNotTrusted() {
        val outcome = SkipIntroRules.decide(SkipDbAnswer.Unknown, intro(0, 27_000, outroEnd = 2_366_000), 2_361_942)
        assertEquals(SkipIntroOutcome.Unknown, outcome)
    }

    @Test
    fun tedUnknownInSkipDbUsesIntroDb() {
        val outcome = SkipIntroRules.decide(SkipDbAnswer.Unknown, intro(0, 27_000, outroEnd = 2_362_000), 2_361_942)
        assertEquals(SkipIntroOutcome.Found(IntroTimes(0, 27_000), IntroSource.INTRODB), outcome)
    }

    @Test
    fun invincibleConfirmedNoneInIntroDb() {
        val outcome = SkipIntroRules.decide(
            skip(0, 10_400, SkipDbMatch.OUT_OF_RANGE, 0.6, 192_200), IntroDbAnswer.ConfirmedNone, 3_042_791
        )
        assertEquals(SkipIntroOutcome.ConfirmedNone, outcome)
    }

    @Test
    fun skipDbConfirmedNoneStopsWithoutIntroDb() {
        assertFalse(SkipIntroRules.needsIntroDb(SkipDbAnswer.ConfirmedNone, 1_298_088))
        assertEquals(SkipIntroOutcome.ConfirmedNone, SkipIntroRules.decide(SkipDbAnswer.ConfirmedNone, null, 1_298_088))
    }

    @Test
    fun skipDbFailureAsksIntroDb() {
        assertTrue(SkipIntroRules.needsIntroDb(null, 2_361_942))
        assertEquals(SkipIntroOutcome.Unknown, SkipIntroRules.decide(null, null, 2_361_942))
    }

    @Test
    fun shiftedNeedsMoreConfidenceAndASmallOffset() {
        val length = 2_820_000L
        assertEquals(IntroTimes(61_000, 91_000),
            SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.SHIFTED, 0.7, -14_000), length))
        assertNull(SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.SHIFTED, 0.69, -1_000), length))
        assertNull(SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.SHIFTED, 0.9, 15_001), length))
    }

    @Test
    fun exactNeedsHalfConfidenceAndAgnosticIsNeverUsed() {
        val length = 2_820_000L
        assertEquals(IntroTimes(61_000, 91_000), SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.EXACT, 0.5), length))
        assertNull(SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.EXACT, 0.49), length))
        assertNull(SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.EXACT, null), length))
        assertNull(SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.AGNOSTIC, 1.0), length))
        assertNull(SkipIntroRules.usableSkipDb(skip(61_000, 91_000, SkipDbMatch.UNKNOWN, 1.0), length))
    }

    @Test
    fun validationRejectsImplausibleTimes() {
        val length = 1_300_000L
        assertTrue(SkipIntroRules.isPlausible(IntroTimes(0, 5_000), length))
        assertTrue(SkipIntroRules.isPlausible(IntroTimes(0, 300_000), length))
        assertFalse("inverted", SkipIntroRules.isPlausible(IntroTimes(90_000, 60_000), length))
        assertFalse("too short", SkipIntroRules.isPlausible(IntroTimes(0, 4_999), length))
        assertFalse("too long", SkipIntroRules.isPlausible(IntroTimes(0, 300_001), length))
        assertFalse("negative start", SkipIntroRules.isPlausible(IntroTimes(-1_000, 30_000), length))
        assertFalse("past the end", SkipIntroRules.isPlausible(IntroTimes(1_290_000, 1_310_000), length))
        assertFalse("second half", SkipIntroRules.isPlausible(IntroTimes(600_000, 660_000), length))
        assertFalse("no length", SkipIntroRules.isPlausible(IntroTimes(0, 30_000), 0))
    }

    @Test
    fun invalidSkipDbTimesFallBackToIntroDb() {
        val skipDb = skip(90_000, 60_000, SkipDbMatch.EXACT)
        assertTrue(SkipIntroRules.needsIntroDb(skipDb, 2_361_942))
    }

    @Test
    fun windowShowsFromTheStartUntilOneSecondBeforeTheEnd() {
        val intro = IntroTimes(129_400, 192_900)
        assertFalse(SkipIntroWindow.isVisible(intro, 129_399))
        assertTrue(SkipIntroWindow.isVisible(intro, 129_400))
        assertTrue(SkipIntroWindow.isVisible(intro, 191_899))
        assertFalse(SkipIntroWindow.isVisible(intro, 191_900))
        assertFalse("hidden with Back", SkipIntroWindow.isVisible(intro, 150_000, dismissed = true))
        assertEquals(192_900, SkipIntroWindow.seekTarget(intro))
    }
}
