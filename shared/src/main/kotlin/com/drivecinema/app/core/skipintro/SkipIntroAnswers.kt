package com.drivecinema.app.core.skipintro

import org.json.JSONObject

/** An episode's intro, in milliseconds from the start of the file. */
data class IntroTimes(val startMs: Long, val endMs: Long)

enum class IntroSource { SKIPDB, INTRODB }

/** How SkipDB's stored file length compares with ours (`match` in its answer). */
enum class SkipDbMatch { EXACT, SHIFTED, AGNOSTIC, OUT_OF_RANGE, UNKNOWN }

/** SkipDB's answer for one episode's intro (`segments.intro`). */
sealed interface SkipDbAnswer {
    data class Found(
        val times: IntroTimes,
        val match: SkipDbMatch,
        val offsetMs: Long,
        val confidence: Double?
    ) : SkipDbAnswer

    /** `0/0`: the community confirmed the episode has no intro. */
    data object ConfirmedNone : SkipDbAnswer

    /** `null`: no approved data yet. */
    data object Unknown : SkipDbAnswer
}

/** IntroDB's answer for one episode. [outroEndMs] is used to tell whether the times are for the same cut. */
sealed interface IntroDbAnswer {
    data class Found(val times: IntroTimes, val submissionCount: Int, val outroEndMs: Long?) : IntroDbAnswer

    /** `no_intro: true`. */
    data object ConfirmedNone : IntroDbAnswer

    data object Unknown : IntroDbAnswer
}

/**
 * Parses the two services' JSON (docs: skipdb.tv/docs, introdb.app/docs/api, checked 2026-10-26). Null means the
 * body is not a valid answer, which the lookup treats like a network error: no button, nothing cached. Range checks
 * are left to [SkipIntroRules], so an inverted range parses and is then rejected there.
 */
object SkipIntroParsers {

    fun parseSkipDb(body: String): SkipDbAnswer? {
        val root = parseObject(body) ?: return null
        val segments = root.optJSONObject("segments") ?: return null
        if (!segments.has("intro")) return null
        val intro = segments.optJSONObject("intro") ?: return SkipDbAnswer.Unknown
        val start = intro.millis("start_ms") ?: return null
        val end = intro.millis("end_ms") ?: return null
        if (start == 0L && end == 0L) return SkipDbAnswer.ConfirmedNone
        val match = when (intro.optString("match", "")) {
            "exact" -> SkipDbMatch.EXACT
            "shifted" -> SkipDbMatch.SHIFTED
            "agnostic" -> SkipDbMatch.AGNOSTIC
            "out-of-range" -> SkipDbMatch.OUT_OF_RANGE
            else -> SkipDbMatch.UNKNOWN
        }
        return SkipDbAnswer.Found(
            times = IntroTimes(start, end),
            match = match,
            offsetMs = intro.millis("offset_ms") ?: 0L,
            confidence = intro.number("confidence")
        )
    }

    fun parseIntroDb(body: String): IntroDbAnswer? {
        val root = parseObject(body) ?: return null
        if (root.optBoolean("no_intro", false)) return IntroDbAnswer.ConfirmedNone
        if (!root.has("intro")) return null
        val intro = root.optJSONObject("intro") ?: return IntroDbAnswer.Unknown
        val start = intro.millisOrSeconds("start") ?: return null
        val end = intro.millisOrSeconds("end") ?: return null
        val outroEnd = root.optJSONObject("outro")?.millisOrSeconds("end")
        val count = intro.number("submission_count")?.toInt() ?: 0
        return IntroDbAnswer.Found(IntroTimes(start, end), count, outroEnd)
    }

    private fun parseObject(body: String): JSONObject? =
        try {
            JSONObject(body)
        } catch (e: Exception) {
            null
        }

    /** A finite number, also when sent as a string ("61000"); anything else is null. */
    private fun JSONObject.number(key: String): Double? {
        val value = opt(key)
        val number = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        }
        return number?.takeIf { it.isFinite() }
    }

    private fun JSONObject.millis(key: String): Long? = number(key)?.let { Math.round(it) }

    /** IntroDB gives both `<name>_ms` and `<name>_sec`; milliseconds win. */
    private fun JSONObject.millisOrSeconds(name: String): Long? =
        millis("${name}_ms") ?: number("${name}_sec")?.let { Math.round(it * 1000) }
}
