package com.drivecinema.app.core.player

import com.drivecinema.app.core.metadata.MediaInfoFormat
import java.util.Locale
import java.util.MissingResourceException

/**
 * Task P.8: the words for audio and subtitle tracks in the player's menus, the same on the phone, the TV and the
 * desktop. The language comes first ("Hungarian · Magyar"), then the format ("AC-3 (Dolby Digital) · 5.1").
 * Codes come from Media3 (BCP 47 "hu", MIME "audio/ac3") or VLC (ISO 639-2 "hun", FourCC "a52 ").
 */
object TrackLabels {

    const val UNKNOWN_LANGUAGE = "Unknown language"
    const val OFF = "Off"

    /** ISO 639-2 bibliographic codes that differ from the terminology codes Java knows ("ger" for "deu"). */
    private val bibliographic = mapOf(
        "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs", "dut" to "nl",
        "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is", "mac" to "mk", "mao" to "mi",
        "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy"
    )

    private val iso3To2: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { code ->
            try {
                Locale(code).isO3Language.takeIf { it.isNotEmpty() }?.let { it to code }
            } catch (_: MissingResourceException) {
                null
            }
        }.toMap()
    }

    private val englishNames: Map<String, String> by lazy {
        Locale.getISOLanguages().associateBy { Locale(it).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT) }
    }

    /** "hu", "hun", "hu-HU" or "Hungarian" → the two-letter code; null when unknown or blank. */
    private fun languageCode(raw: String?): String? {
        val text = raw?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
        val base = text.substringBefore('-').lowercase(Locale.ROOT)
        return when {
            base == "und" -> null
            base.length == 2 -> base
            base.length == 3 -> bibliographic[base] ?: iso3To2[base] ?: base
            else -> englishNames[text.lowercase(Locale.ROOT)]
        }
    }

    /**
     * "Hungarian · Magyar"; "English" when both names are the same; [UNKNOWN_LANGUAGE] for a blank or "und" code;
     * an unknown code as given (upper-case), a name Java doesn't know as given.
     */
    fun language(raw: String?): String {
        if (raw.isNullOrBlank()) return UNKNOWN_LANGUAGE
        val code = languageCode(raw) ?: return if (raw.trim().length > 3) raw.trim() else UNKNOWN_LANGUAGE
        val locale = Locale(code)
        val english = locale.getDisplayLanguage(Locale.ENGLISH)
        if (english.isBlank() || english.equals(code, ignoreCase = true)) return raw.trim().uppercase(Locale.ROOT)
        val native = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
        return if (native.isBlank() || native.equals(english, ignoreCase = true) || native.equals(code, ignoreCase = true)) {
            english
        } else {
            "$english · $native"
        }
    }

    /** "AC-3 (Dolby Digital)", "E-AC-3 (Dolby Digital Plus)", "AAC"; an unknown code as given, without "audio/". */
    fun audioCodec(code: String?): String? = when (val name = MediaInfoFormat.audioCodecName(code)) {
        null -> null
        "AC-3" -> "AC-3 (Dolby Digital)"
        "E-AC-3" -> "E-AC-3 (Dolby Digital Plus)"
        "E-AC-3 Atmos" -> "Dolby Atmos"
        "TrueHD" -> "Dolby TrueHD"
        else -> name.removePrefix("AUDIO/")
    }

    /** "AC-3 (Dolby Digital) · 5.1"; the parts that are known, or null. */
    fun audioFormat(codec: String?, channels: Int?): String? =
        listOfNotNull(audioCodec(codec), MediaInfoFormat.channelsLabel(channels?.takeIf { it > 0 }))
            .joinToString(" · ").ifEmpty { null }

    private val subtitleFormats = mapOf(
        "SRT" to setOf("application/x-subrip", "subt", "srt"),
        "ASS" to setOf("text/x-ssa", "ssa", "ass"),
        "WebVTT" to setOf("text/vtt", "application/x-mp4-vtt", "wvtt", "webvtt"),
        "PGS" to setOf("application/pgs", "bdpg", "pgs"),
        "VobSub" to setOf("application/vobsub", "spu", "vobsub"),
        "TTML" to setOf("application/ttml+xml", "ttml"),
        "DVB" to setOf("application/dvbsubs", "dvbs"),
        "TX3G" to setOf("application/x-quicktime-tx3g", "tx3g"),
        "CC" to setOf("application/cea-608", "application/cea-708", "cc1", "c608", "c708")
    )

    /** "SRT", "ASS", "PGS", …; null for a code it doesn't know (a subtitle format name isn't worth guessing). */
    fun subtitleFormat(code: String?): String? {
        val key = code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return subtitleFormats.entries.firstOrNull { key in it.value }?.key
    }

    /** A subtitle row's title: its language, or the track's own title when the language isn't known. */
    fun subtitleTitle(language: String?, title: String?): String {
        val name = language(language)
        val cleanTitle = title?.trim().orEmpty()
        return if (name == UNKNOWN_LANGUAGE && cleanTitle.isNotEmpty()) cleanTitle else name
    }

    /**
     * The line under a subtitle's language: the track's own title when it adds something ("Commentary"), then
     * "Forced", "SDH" and the format. Null when there is nothing to add.
     */
    fun subtitleDetails(language: String?, title: String?, isForced: Boolean, isSdh: Boolean, format: String?): String? {
        val parts = mutableListOf<String>()
        val cleanTitle = title?.trim().orEmpty()
        val languageNames = language(language).split(" · ") + subtitleTitle(language, title)
        if (cleanTitle.isNotEmpty() && languageNames.none { it.equals(cleanTitle, ignoreCase = true) }) parts += cleanTitle
        if (isForced && parts.none { it.contains("forced", ignoreCase = true) }) parts += "Forced"
        if (isSdh && parts.none { it.contains("SDH", ignoreCase = true) }) parts += "SDH"
        format?.let { parts += it }
        return parts.joinToString(" · ").ifEmpty { null }
    }
}
