package com.drivecinema.app.core.skipintro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures follow the answers seen in the O.0 spike (2026-10-26, `docs/design/o0-skip-intro.md` §10). */
class SkipIntroParsersTest {

    @Test
    fun skipDbFullAnswer() {
        val body = """{"imdb_id":"tt0475784","season":1,"episode":2,"segments":{"intro":{"start_ms":0,"end_ms":101400,
            "adjusted":false,"offset_ms":0,"match":"exact","confidence":0.9},"recap":null,"outro":null,"preview":null},
            "intro_length_estimate_ms":104100}"""
        assertEquals(
            SkipDbAnswer.Found(IntroTimes(0, 101_400), SkipDbMatch.EXACT, 0, 0.9),
            SkipIntroParsers.parseSkipDb(body)
        )
    }

    @Test
    fun skipDbOutOfRangeKeepsTheOffset() {
        val body = """{"segments":{"intro":{"start_ms":0,"end_ms":31000,"offset_ms":-48100,"match":"out-of-range",
            "confidence":0.6}}}"""
        assertEquals(
            SkipDbAnswer.Found(IntroTimes(0, 31_000), SkipDbMatch.OUT_OF_RANGE, -48_100, 0.6),
            SkipIntroParsers.parseSkipDb(body)
        )
    }

    @Test
    fun skipDbNullIntroIsUnknown() {
        assertEquals(SkipDbAnswer.Unknown, SkipIntroParsers.parseSkipDb("""{"segments":{"intro":null,"recap":null}}"""))
    }

    @Test
    fun skipDbZeroZeroIsConfirmedNone() {
        val body = """{"segments":{"intro":{"start_ms":0,"end_ms":0,"match":"exact","confidence":0.9}}}"""
        assertEquals(SkipDbAnswer.ConfirmedNone, SkipIntroParsers.parseSkipDb(body))
    }

    @Test
    fun skipDbNumbersAsStringsAreAccepted() {
        val body = """{"segments":{"intro":{"start_ms":"61000","end_ms":"91000","match":"shifted","offset_ms":"-1200",
            "confidence":"0.8"}}}"""
        assertEquals(
            SkipDbAnswer.Found(IntroTimes(61_000, 91_000), SkipDbMatch.SHIFTED, -1_200, 0.8),
            SkipIntroParsers.parseSkipDb(body)
        )
    }

    @Test
    fun skipDbInvertedRangeParsesAndIsLeftToTheRules() {
        val body = """{"segments":{"intro":{"start_ms":90000,"end_ms":60000,"match":"exact","confidence":0.9}}}"""
        assertEquals(IntroTimes(90_000, 60_000), (SkipIntroParsers.parseSkipDb(body) as SkipDbAnswer.Found).times)
    }

    @Test
    fun skipDbUnknownMatchWordIsKeptAsUnknownMatch() {
        val body = """{"segments":{"intro":{"start_ms":1000,"end_ms":31000,"match":"fuzzy","confidence":0.9}}}"""
        assertEquals(SkipDbMatch.UNKNOWN, (SkipIntroParsers.parseSkipDb(body) as SkipDbAnswer.Found).match)
    }

    @Test
    fun skipDbMalformedAnswersAreNull() {
        assertNull(SkipIntroParsers.parseSkipDb("not json"))
        assertNull(SkipIntroParsers.parseSkipDb("""{"error":"rate limited"}"""))
        assertNull(SkipIntroParsers.parseSkipDb("""{"segments":{}}"""))
        assertNull(SkipIntroParsers.parseSkipDb("""{"segments":{"intro":{"start_ms":"soon","end_ms":1000}}}"""))
        assertNull(SkipIntroParsers.parseSkipDb("""{"segments":{"intro":{"end_ms":1000}}}"""))
    }

    @Test
    fun introDbFullAnswerWithOutro() {
        val body = """{"imdb_id":"tt14824792","media_type":"tv","season":1,"episode":2,"intro":{"start_sec":0,
            "end_sec":27,"start_ms":0,"end_ms":27000,"confidence":1,"submission_count":1},"recap":null,
            "outro":{"start_sec":2291,"end_sec":2362,"start_ms":2291000,"end_ms":2362000},"no_intro":false}"""
        assertEquals(
            IntroDbAnswer.Found(IntroTimes(0, 27_000), submissionCount = 1, outroEndMs = 2_362_000),
            SkipIntroParsers.parseIntroDb(body)
        )
    }

    @Test
    fun introDbSecondsAreUsedWhenMillisecondsAreMissing() {
        val body = """{"intro":{"start_sec":1.5,"end_sec":"92","submission_count":2},"outro":null,"no_intro":false}"""
        assertEquals(
            IntroDbAnswer.Found(IntroTimes(1_500, 92_000), submissionCount = 2, outroEndMs = null),
            SkipIntroParsers.parseIntroDb(body)
        )
    }

    @Test
    fun introDbNoIntroIsConfirmedNone() {
        val body = """{"intro":null,"outro":{"end_ms":2850000},"no_intro":true}"""
        assertEquals(IntroDbAnswer.ConfirmedNone, SkipIntroParsers.parseIntroDb(body))
    }

    @Test
    fun introDbNullIntroIsUnknown() {
        assertEquals(IntroDbAnswer.Unknown, SkipIntroParsers.parseIntroDb("""{"intro":null,"no_intro":false}"""))
    }

    @Test
    fun introDbMalformedAnswersAreNull() {
        assertNull(SkipIntroParsers.parseIntroDb("<html>Bad gateway</html>"))
        assertNull(SkipIntroParsers.parseIntroDb("""{"error":"not found"}"""))
        assertNull(SkipIntroParsers.parseIntroDb("""{"intro":{"start_ms":0}}"""))
    }
}
