package com.drivecinema.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

data class AudioTrackInfo(
    val groupIndex: Int = 0,
    val trackIndex: Int = 0,
    val mimeType: String? = null,
    val codecs: String? = null,
    val language: String? = null,
    val channelCount: Int = 2,
    val sampleRate: Int = 48000,
    val isSupported: Boolean = true,
    val isSelected: Boolean = false,
    val languageDisplay: String = "English",
    val codecDisplay: String = "AAC",
    val channelsDisplay: String = "Stereo",
    val label: String = "English",
    val index: Int = trackIndex,
    val mediaTrackGroup: androidx.media3.common.TrackGroup? = null
)

sealed interface AudioSelectionResult {
    data class SupportedTrackSelected(val track: AudioTrackInfo) : AudioSelectionResult
    data class NoSupportedTrack(val allTracks: List<AudioTrackInfo>) : AudioSelectionResult
    data object NoAudioTracks : AudioSelectionResult
}

object AudioTrackHelper {

    fun formatCodecName(mimeType: String?): String {
        return when (mimeType) {
            "audio/ac3" -> "AC-3 (Dolby Digital)"
            "audio/eac3" -> "E-AC-3 (Dolby Digital Plus)"
            "audio/eac3-joc" -> "Dolby Atmos"
            "audio/true-hd" -> "Dolby TrueHD"
            "audio/mp4a-latm" -> "AAC"
            "audio/opus" -> "Opus"
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/vnd.dts" -> "DTS"
            "audio/vnd.dts.hd" -> "DTS-HD"
            "audio/vorbis" -> "Vorbis"
            else -> mimeType?.removePrefix("audio/")?.uppercase() ?: "Unknown"
        }
    }

    fun formatChannelCount(channels: Int): String {
        return when (channels) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> if (channels > 0) "$channels ch" else ""
        }
    }

    fun formatLanguage(lang: String?): String {
        if (lang.isNullOrBlank()) return "Unknown Language"
        return try {
            val locale = java.util.Locale.forLanguageTag(lang)
            val display = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
            if (display.isNotBlank()) display.replaceFirstChar { it.uppercase() } else lang.uppercase()
        } catch (_: Exception) {
            lang.uppercase()
        }
    }

    fun shouldShowSelector(tracks: List<AudioTrackInfo>): Boolean {
        return tracks.size > 1
    }

    fun extractAudioTracks(tracks: Tracks): List<AudioTrackInfo> {
        val list = mutableListOf<AudioTrackInfo>()
        for ((groupIndex, groupInfo) in tracks.groups.withIndex()) {
            if (groupInfo.type == C.TRACK_TYPE_AUDIO) {
                val group = groupInfo.mediaTrackGroup
                for (i in 0 until group.length) {
                    val format = group.getFormat(i)
                    val isSupported = groupInfo.isTrackSupported(i)
                    val isSelected = groupInfo.isTrackSelected(i)

                    val lang = formatLanguage(format.language)
                    val codec = formatCodecName(format.sampleMimeType)
                    val channels = formatChannelCount(format.channelCount)

                    val details = listOf(codec, channels).filter { it.isNotBlank() }.joinToString(" • ")
                    val fullLabel = if (details.isNotBlank()) "$lang ($details)" else lang
                    val displayLabel = if (isSupported) fullLabel else "$fullLabel (Unsupported)"

                    list.add(
                        AudioTrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = i,
                            mimeType = format.sampleMimeType,
                            codecs = format.codecs,
                            language = format.language,
                            channelCount = format.channelCount,
                            sampleRate = format.sampleRate,
                            isSupported = isSupported,
                            isSelected = isSelected,
                            languageDisplay = lang,
                            codecDisplay = codec,
                            channelsDisplay = channels,
                            label = displayLabel,
                            index = i,
                            mediaTrackGroup = group
                        )
                    )
                }
            }
        }
        return list
    }

    /**
     * Resolves audio track selection by:
     * 1. Keeping current track if it is already supported.
     * 2. Automatically switching to the first supported track if an unsupported track is selected.
     * 3. Disabling audio track type if NO supported track exists (preventing decoder crashes).
     */
    fun resolveAudioTracks(player: Player, tracks: Tracks): AudioSelectionResult {
        val audioTracks = extractAudioTracks(tracks)
        if (audioTracks.isEmpty()) {
            return AudioSelectionResult.NoAudioTracks
        }

        // Check if currently selected track is already supported
        val currentSelected = audioTracks.find { it.isSelected }
        if (currentSelected != null && currentSelected.isSupported) {
            return AudioSelectionResult.SupportedTrackSelected(currentSelected)
        }

        // Find first supported track
        val firstSupported = audioTracks.find { it.isSupported }
        if (firstSupported != null) {
            selectAudioTrack(player, firstSupported)
            return AudioSelectionResult.SupportedTrackSelected(firstSupported)
        } else {
            // No supported tracks exist: disable audio type to avoid MediaCodecAudioRenderer crash
            val isAudioDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)
            if (!isAudioDisabled) {
                disableAudio(player)
            }
            return AudioSelectionResult.NoSupportedTrack(audioTracks)
        }
    }

    fun selectAudioTrack(player: Player, track: AudioTrackInfo) {
        val mediaTrackGroup = track.mediaTrackGroup
        if (mediaTrackGroup != null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(
                    TrackSelectionOverride(mediaTrackGroup, listOf(track.trackIndex))
                )
                .build()
            return
        }

        val currentTracks = player.currentTracks
        if (track.groupIndex in currentTracks.groups.indices) {
            val groupInfo = currentTracks.groups[track.groupIndex]
            if (groupInfo.type == C.TRACK_TYPE_AUDIO) {
                val group = groupInfo.mediaTrackGroup
                if (track.trackIndex in 0 until group.length) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(
                            TrackSelectionOverride(group, listOf(track.trackIndex))
                        )
                        .build()
                }
            }
        }
    }

    fun selectAudioTrack(player: Player, trackIndex: Int) {
        val audioTracks = extractAudioTracks(player.currentTracks)
        val target = audioTracks.find { it.trackIndex == trackIndex }
        if (target != null) {
            selectAudioTrack(player, target)
        }
    }

    fun disableAudio(player: Player) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
    }
}
