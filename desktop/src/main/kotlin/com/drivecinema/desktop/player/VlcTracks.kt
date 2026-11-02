package com.drivecinema.desktop.player

import com.drivecinema.app.core.player.TrackLabels

/** P.8: what VLC knows about one track of the open file (`media().info()`); matched to its menu entry by id. */
data class VlcTrackDetails(
    val id: Int,
    val language: String?,
    val codec: String?,
    val channels: Int? = null,
    val description: String? = null
)

/** Task K.1: VLC's track descriptions as menu entries. */
object VlcTracks {
    /** VLC's id for "no track" (subtitles off). */
    const val OFF = -1

    private val PREFIX = Regex("""^(Track|Subtitles?)\s*\d+\s*-\s*""", RegexOption.IGNORE_CASE)

    /** "Track 1 - [Hungarian]" → "Hungarian", "Track 2 - English [SDH]" → "English [SDH]", blank → "Track N". */
    fun label(id: Int, description: String?): String {
        val text = description?.trim().orEmpty()
        val rest = text.replace(PREFIX, "").trim()
        return when {
            rest.startsWith("[") && rest.endsWith("]") && rest.length > 2 -> rest.substring(1, rest.length - 1).trim()
            rest.isNotEmpty() -> rest
            else -> "Track $id"
        }
    }

    /**
     * P.8: the language from VLC's track info ("Hungarian · Magyar"), else from the description ("Track 1 -
     * [Hungarian]"), else the description itself.
     */
    private fun title(id: Int, description: String?, details: VlcTrackDetails?): String {
        val language = details?.language?.let { TrackLabels.language(it) }?.takeIf { it != TrackLabels.UNKNOWN_LANGUAGE }
        return language ?: TrackLabels.language(label(id, description))
    }

    /** Audio menu entries from VLC's (id, description) list, without VLC's "Disable": language, then "AC-3 (Dolby Digital) · 5.1". */
    fun audioOptions(descriptions: List<Pair<Int, String?>>, details: List<VlcTrackDetails> = emptyList()): List<AudioTrackOption> =
        descriptions.filter { it.first >= 0 }.map { (id, description) ->
            val info = details.firstOrNull { it.id == id }
            AudioTrackOption(id, title(id, description, info), TrackLabels.audioFormat(info?.codec, info?.channels))
        }

    /** Subtitle menu entries, without VLC's "Disable" (the menu has its own Off): language, then the title and format. */
    fun subtitleOptions(descriptions: List<Pair<Int, String?>>, details: List<VlcTrackDetails> = emptyList()): List<AudioTrackOption> =
        descriptions.filter { it.first >= 0 }.map { (id, description) ->
            val info = details.firstOrNull { it.id == id }
            AudioTrackOption(
                id,
                title(id, description, info),
                TrackLabels.subtitleDetails(info?.language, info?.description, false, false, TrackLabels.subtitleFormat(info?.codec))
            )
        }
}
