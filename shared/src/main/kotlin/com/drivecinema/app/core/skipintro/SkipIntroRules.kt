package com.drivecinema.app.core.skipintro

import kotlin.math.abs

/** What the lookup concluded for one episode copy. */
sealed interface SkipIntroOutcome {
    data class Found(val times: IntroTimes, val source: IntroSource) : SkipIntroOutcome
    data object ConfirmedNone : SkipIntroOutcome
    data object Unknown : SkipIntroOutcome
}

/**
 * The merge and validation rules of `docs/design/o0-skip-intro.md` §5, as pure functions. SkipDB is asked first;
 * IntroDB only when SkipDB has no usable answer (D5, user 2026-10-26). Each service returns at most one intro per
 * episode, so two answers are never combined: the first usable one wins.
 */
object SkipIntroRules {
    const val MIN_INTRO_MS = 5_000L
    const val MAX_INTRO_MS = 300_000L
    const val EXACT_MIN_CONFIDENCE = 0.5
    const val SHIFTED_MIN_CONFIDENCE = 0.7
    const val SHIFTED_MAX_OFFSET_MS = 15_000L

    /** IntroDB has no length matching: its times are used only when its end credits end where our file ends. */
    const val SAME_CUT_TOLERANCE_MS = 2_000L

    /** 0 ≤ start < end ≤ file length, 5–300 s long, ending in the first half of the file. */
    fun isPlausible(times: IntroTimes, fileLengthMs: Long): Boolean {
        val length = times.endMs - times.startMs
        return fileLengthMs > 0 &&
            times.startMs >= 0 &&
            times.endMs <= fileLengthMs &&
            length in MIN_INTRO_MS..MAX_INTRO_MS &&
            times.endMs <= fileLengthMs / 2
    }

    /** SkipDB's times when they are for this file (`exact`, or a small `shifted`) and confident enough. */
    fun usableSkipDb(answer: SkipDbAnswer?, fileLengthMs: Long): IntroTimes? {
        val found = answer as? SkipDbAnswer.Found ?: return null
        val confidence = found.confidence ?: return null
        val matches = when (found.match) {
            SkipDbMatch.EXACT -> confidence >= EXACT_MIN_CONFIDENCE
            SkipDbMatch.SHIFTED ->
                confidence >= SHIFTED_MIN_CONFIDENCE && abs(found.offsetMs) <= SHIFTED_MAX_OFFSET_MS
            // A different cut (out-of-range) or no comparison at all: the button could be in the wrong place.
            SkipDbMatch.AGNOSTIC, SkipDbMatch.OUT_OF_RANGE, SkipDbMatch.UNKNOWN -> false
        }
        return found.times.takeIf { matches && isPlausible(it, fileLengthMs) }
    }

    /** IntroDB's times when at least one person timed them on a file of our length (its outro ends at our end). */
    fun usableIntroDb(answer: IntroDbAnswer?, fileLengthMs: Long): IntroTimes? {
        val found = answer as? IntroDbAnswer.Found ?: return null
        val outroEnd = found.outroEndMs ?: return null
        val sameCut = abs(outroEnd - fileLengthMs) <= SAME_CUT_TOLERANCE_MS
        return found.times.takeIf { found.submissionCount >= 1 && sameCut && isPlausible(it, fileLengthMs) }
    }

    /** Whether IntroDB should be asked after SkipDB's answer ([skipDb] null = SkipDB failed). */
    fun needsIntroDb(skipDb: SkipDbAnswer?, fileLengthMs: Long): Boolean =
        skipDb !is SkipDbAnswer.ConfirmedNone && usableSkipDb(skipDb, fileLengthMs) == null

    /**
     * The outcome from both answers; null for a service means it failed or was not asked. Unknown also covers
     * "data exists, but not usable for this file".
     */
    fun decide(skipDb: SkipDbAnswer?, introDb: IntroDbAnswer?, fileLengthMs: Long): SkipIntroOutcome {
        usableSkipDb(skipDb, fileLengthMs)?.let { return SkipIntroOutcome.Found(it, IntroSource.SKIPDB) }
        if (skipDb is SkipDbAnswer.ConfirmedNone) return SkipIntroOutcome.ConfirmedNone
        usableIntroDb(introDb, fileLengthMs)?.let { return SkipIntroOutcome.Found(it, IntroSource.INTRODB) }
        if (introDb is IntroDbAnswer.ConfirmedNone) return SkipIntroOutcome.ConfirmedNone
        return SkipIntroOutcome.Unknown
    }
}

/** When the "Skip intro" button shows and where it seeks (O.2 and O.3 draw it). */
object SkipIntroWindow {
    /** The button goes away this long before the intro ends: a skip of under a second isn't worth a press. */
    const val END_MARGIN_MS = 1_000L

    fun isVisible(intro: IntroTimes, positionMs: Long, dismissed: Boolean = false): Boolean =
        !dismissed && positionMs >= intro.startMs && positionMs < intro.endMs - END_MARGIN_MS

    fun seekTarget(intro: IntroTimes): Long = intro.endMs
}
