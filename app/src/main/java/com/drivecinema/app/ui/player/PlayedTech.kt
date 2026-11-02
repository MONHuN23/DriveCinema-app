package com.drivecinema.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import com.drivecinema.app.core.metadata.MediaInfoFormat

/** Task L.3: what Media3 reports about the playing copy; saved on its `media_sources` row. Null = not reported. */
data class PlayedTech(
    val widthPx: Int?,
    val heightPx: Int?,
    val durationMs: Long?,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val frameRate: Float?,
    val isHdr: Boolean?
) {
    companion object {
        /** From the selected video and audio tracks (the first of each when none is selected, e.g. audio switched off). */
        fun from(tracks: Tracks, durationMs: Long?): PlayedTech? =
            from(trackFormat(tracks, C.TRACK_TYPE_VIDEO), trackFormat(tracks, C.TRACK_TYPE_AUDIO), durationMs)

        fun from(video: Format?, audio: Format?, durationMs: Long?): PlayedTech? {
            if (video == null && audio == null) return null
            return PlayedTech(
                widthPx = video?.width?.takeIf { it > 0 },
                heightPx = video?.height?.takeIf { it > 0 },
                durationMs = durationMs?.takeIf { it > 0 && it != C.TIME_UNSET },
                videoCodec = MediaInfoFormat.videoCodecName(video?.sampleMimeType),
                audioCodec = MediaInfoFormat.audioCodecName(audio?.sampleMimeType),
                audioChannels = audio?.channelCount?.takeIf { it > 0 },
                frameRate = video?.frameRate?.takeIf { it > 0f },
                isHdr = video?.let(::isHdr)
            )
        }

        /** Dolby Vision, or a PQ / HLG transfer; null when the file doesn't say (never guessed). */
        private fun isHdr(video: Format): Boolean? = when {
            video.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> true
            video.colorInfo == null -> null
            else -> video.colorInfo?.colorTransfer.let { it == C.COLOR_TRANSFER_ST2084 || it == C.COLOR_TRANSFER_HLG }
        }

        private fun trackFormat(tracks: Tracks, type: Int): Format? {
            val groups = tracks.groups.filter { it.type == type && it.length > 0 }
            for (group in groups) {
                for (i in 0 until group.length) if (group.isTrackSelected(i)) return group.getTrackFormat(i)
            }
            return groups.firstOrNull()?.getTrackFormat(0)
        }
    }
}
