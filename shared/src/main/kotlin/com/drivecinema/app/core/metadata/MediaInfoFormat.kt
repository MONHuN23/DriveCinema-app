package com.drivecinema.app.core.metadata

import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.SourceKind
import java.util.Locale

/**
 * Task L.3: names and the info line for a copy's technical details, the same words on every platform.
 * Codec codes come from VLC (FourCC: "hevc", "a52 ") or Media3 (MIME: "video/hevc", "audio/ac3"); unknown codes are
 * shown as reported (upper-case), never guessed.
 */
object MediaInfoFormat {

    private val videoCodecs = mapOf(
        "H.264" to setOf("h264", "avc1", "avc", "x264", "video/avc"),
        "HEVC" to setOf("hevc", "h265", "hev1", "hvc1", "x265", "video/hevc"),
        "AV1" to setOf("av01", "av1", "video/av01"),
        "VP9" to setOf("vp90", "vp09", "vp9", "video/x-vnd.on2.vp9"),
        "VP8" to setOf("vp80", "vp8", "video/x-vnd.on2.vp8"),
        "MPEG-4" to setOf("mp4v", "xvid", "divx", "dx50", "fmp4", "video/mp4v-es"),
        "MPEG-2" to setOf("mpgv", "mp2v", "mpeg2", "video/mpeg2"),
        "VC-1" to setOf("wvc1", "vc-1", "vc1", "wmv3", "video/wvc1"),
        "Dolby Vision" to setOf("dvhe", "dvh1", "dvav", "dva1", "video/dolby-vision")
    )

    private val audioCodecs = mapOf(
        "AAC" to setOf("mp4a", "aac", "audio/mp4a-latm", "audio/aac"),
        "AC-3" to setOf("a52", "ac3", "ac-3", "audio/ac3"),
        "E-AC-3" to setOf("eac3", "ec-3", "e-ac3", "audio/eac3"),
        "E-AC-3 Atmos" to setOf("audio/eac3-joc"),
        "TrueHD" to setOf("mlp", "trhd", "truehd", "audio/true-hd"),
        "DTS" to setOf("dts", "dtsc", "audio/vnd.dts"),
        "DTS-HD" to setOf("dtsh", "dtsl", "audio/vnd.dts.hd"),
        "Opus" to setOf("opus", "audio/opus"),
        "FLAC" to setOf("flac", "audio/flac"),
        "MP3" to setOf("mpga", "mp3", "audio/mpeg"),
        "Vorbis" to setOf("vorb", "vorbis", "audio/vorbis"),
        "PCM" to setOf("araw", "s16l", "s24l", "lpcm", "pcm", "audio/raw")
    )

    private val containers = mapOf(
        "mkv" to "MKV", "mp4" to "MP4", "m4v" to "M4V", "avi" to "AVI", "mov" to "MOV", "webm" to "WEBM",
        "ts" to "TS", "m2ts" to "M2TS", "mpg" to "MPEG", "mpeg" to "MPEG", "wmv" to "WMV", "flv" to "FLV", "3gp" to "3GP"
    )

    fun videoCodecName(code: String?): String? = codecName(code, videoCodecs)

    fun audioCodecName(code: String?): String? = codecName(code, audioCodecs)

    private fun codecName(code: String?, names: Map<String, Set<String>>): String? {
        val key = code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        return names.entries.firstOrNull { key in it.value }?.key ?: key.uppercase(Locale.ROOT)
    }

    /** "Mono", "Stereo", "5.1", "7.1", else "N ch". */
    fun channelsLabel(channels: Int?): String? = when (channels) {
        null, 0 -> null
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$channels ch"
    }

    /** By the usual names, allowing for cropped films (1920×800 is 1080p, 3840×1600 is 2160p). */
    fun resolutionLabel(width: Int?, height: Int?): String? {
        val w = width ?: 0
        val h = height ?: 0
        if (w <= 0 || h <= 0) return null
        return when {
            w >= 3200 || h >= 2000 -> "2160p"
            w >= 2200 || h >= 1300 -> "1440p"
            w >= 1800 || h >= 1000 -> "1080p"
            w >= 1200 || h >= 700 -> "720p"
            else -> "${h}p"
        }
    }

    /** "MKV" from "Dune.2021.2160p.mkv", a path or a `file:` / `dchost:` key; null when it isn't a known video type. */
    fun containerOf(fileName: String?): String? {
        val name = fileName?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
        if ('.' !in name) return null
        return containers[name.substringAfterLast('.').lowercase(Locale.ROOT)]
    }

    /** "8.4 GB", "700 MB" (binary units, as file managers show them). */
    fun sizeLabel(bytes: Long?): String? {
        val b = bytes?.takeIf { it > 0 } ?: return null
        val gb = b / (1024.0 * 1024 * 1024)
        return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else "${(b / (1024.0 * 1024)).toLong().coerceAtLeast(1)} MB"
    }

    /** "2 h 12 min", "48 min". */
    fun durationLabel(ms: Long?): String? {
        val minutes = (ms ?: return null) / 60_000
        if (minutes <= 0) return null
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    fun sourceLabel(kind: SourceKind): String = when (kind) {
        SourceKind.DRIVE -> "Drive"
        SourceKind.LOCAL -> "This device"
        SourceKind.HOST -> "Another PC"
    }

    /**
     * One line per copy: "2 h 12 min · 2160p HDR · HEVC · E-AC-3 5.1 · MKV · 8.4 GB · Drive". Unknown parts are left
     * out; null when nothing but the source is known. [withSource] adds where the copy is.
     */
    fun infoLine(source: MediaSourceEntity, withSource: Boolean = true): String? {
        val resolution = resolutionLabel(source.widthPx, source.heightPx)?.let { if (source.isHdr == true) "$it HDR" else it }
        val audio = listOfNotNull(source.audioCodec, channelsLabel(source.audioChannels)).joinToString(" ").ifEmpty { null }
        val parts = listOfNotNull(
            durationLabel(source.durationMs),
            resolution,
            source.videoCodec,
            audio,
            source.container ?: containerOf(source.sourceKey),
            sizeLabel(source.sizeBytes)
        )
        if (parts.isEmpty()) return null
        return (if (withSource) parts + sourceLabel(source.kind) else parts).joinToString(" · ")
    }

    /** Copies in the order they would play: this device, another PC, then Drive. */
    fun sortCopies(copies: List<MediaSourceEntity>): List<MediaSourceEntity> = copies.sortedBy {
        when (it.kind) {
            SourceKind.LOCAL -> 0
            SourceKind.HOST -> 1
            SourceKind.DRIVE -> 2
        }
    }

    /** The copy with the most known details (length, resolution, codec), in play order on a tie. */
    fun bestKnown(copies: List<MediaSourceEntity>): MediaSourceEntity? =
        sortCopies(copies).maxByOrNull { listOfNotNull(it.durationMs, it.heightPx, it.videoCodec).size }

    /** Short line for an episode row: length + resolution. */
    fun shortLine(source: MediaSourceEntity): String? =
        listOfNotNull(durationLabel(source.durationMs), resolutionLabel(source.widthPx, source.heightPx))
            .joinToString(" · ").ifEmpty { null }
}
