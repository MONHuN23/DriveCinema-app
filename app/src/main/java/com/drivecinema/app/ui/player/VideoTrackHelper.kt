package com.drivecinema.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer

/** Which renderer's decoder failed to start. */
enum class DecoderFailure { AUDIO, VIDEO, NONE }

/**
 * Explains video the device cannot decode (4K or HEVC 10-bit on a 1080p TV, AV1 on older hardware) instead of
 * leaving a silent black screen, and tells audio decoder failures apart from video ones.
 */
object VideoTrackHelper {

    fun formatCodecName(mimeType: String?): String = when (mimeType) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        MimeTypes.VIDEO_VC1 -> "VC-1"
        else -> mimeType?.removePrefix("video/")?.uppercase() ?: "Unknown"
    }

    /** e.g. "HEVC 10-bit HDR 2160p", "H.264 1080p". */
    fun describeFormat(format: Format): String {
        val parts = mutableListOf(formatCodecName(format.sampleMimeType))
        val colorInfo = format.colorInfo
        if (colorInfo != null && colorInfo.lumaBitdepth != Format.NO_VALUE && colorInfo.lumaBitdepth > 8) {
            parts += "${colorInfo.lumaBitdepth}-bit"
        }
        if (ColorInfo.isTransferHdr(colorInfo)) {
            parts += "HDR"
        }
        if (format.height != Format.NO_VALUE && format.height > 0) {
            parts += "${format.height}p"
        }
        return parts.joinToString(" ")
    }

    fun unsupportedMessage(description: String): String =
        "Video format ($description) is not supported by this device's decoder."

    /**
     * Returns a banner message when the media has video but none of its video tracks can be played on this
     * device, or null when video is fine (or the media has no video at all).
     */
    fun unsupportedVideoMessage(tracks: Tracks): String? {
        val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        if (videoGroups.isEmpty()) return null
        val anySupported = videoGroups.any { group -> (0 until group.length).any { group.isTrackSupported(it) } }
        if (anySupported) return null
        val firstFormat = videoGroups.first().getTrackFormat(0)
        return unsupportedMessage(describeFormat(firstFormat))
    }

    /** Classifies a playback error by the format of the renderer whose decoder failed to initialise. */
    fun classifyDecoderFailure(error: PlaybackException): DecoderFailure {
        if (error.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) return DecoderFailure.NONE
        val mimeType = (error as? ExoPlaybackException)?.rendererFormat?.sampleMimeType
            ?: (error.cause as? MediaCodecRenderer.DecoderInitializationException)?.mimeType
        return when {
            MimeTypes.isVideo(mimeType) -> DecoderFailure.VIDEO
            MimeTypes.isAudio(mimeType) -> DecoderFailure.AUDIO
            error.message?.contains("MediaCodecVideoRenderer", ignoreCase = true) == true -> DecoderFailure.VIDEO
            // Unknown renderer: keep the earlier behaviour (fall back to video-only playback).
            else -> DecoderFailure.AUDIO
        }
    }

    /** Error card text for a video decoder failure, naming the format when it is known. */
    fun videoDecoderErrorMessage(error: PlaybackException): String {
        val format = (error as? ExoPlaybackException)?.rendererFormat
        return unsupportedMessage(if (format != null) describeFormat(format) else "unknown")
    }
}
