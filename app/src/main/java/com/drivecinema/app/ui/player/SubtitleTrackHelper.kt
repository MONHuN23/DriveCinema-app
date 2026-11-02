package com.drivecinema.app.ui.player

import android.graphics.Color
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.drivecinema.app.core.player.TrackLabels

/** A subtitle track inside the video file (Task K.1). */
data class SubtitleTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    /** P.8: the language, "Hungarian · Magyar" ([TrackLabels.language]). */
    val title: String,
    /** P.8: what else the menu says about it, "Commentary · Forced · SRT" ([TrackLabels.subtitleDetails]). */
    val details: String?,
    val language: String?,
    val isForced: Boolean,
    val isSelected: Boolean,
    val mediaTrackGroup: TrackGroup? = null
)

/**
 * Task K.1: subtitle tracks inside the file (MKV/MP4: SRT, ASS/SSA, WebVTT, PGS, …). Media3 already demuxes them;
 * this lists them, labels them and switches them on or off. The file's own default/forced flags still decide what
 * shows at the start (as before K.1), so playback starts exactly as before.
 */
object SubtitleTrackHelper {

    fun extractSubtitleTracks(tracks: Tracks): List<SubtitleTrackInfo> {
        val list = mutableListOf<SubtitleTrackInfo>()
        for ((groupIndex, group) in tracks.groups.withIndex()) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            val trackGroup = group.mediaTrackGroup
            for (i in 0 until trackGroup.length) {
                if (!group.isTrackSupported(i)) continue
                val format = trackGroup.getFormat(i)
                val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                val sdh = format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0
                // Media3 parses subtitles while extracting; the original MIME type is then in `codecs`.
                val subtitleFormat = TrackLabels.subtitleFormat(format.codecs) ?: TrackLabels.subtitleFormat(format.sampleMimeType)
                list += SubtitleTrackInfo(
                    groupIndex = groupIndex,
                    trackIndex = i,
                    title = TrackLabels.subtitleTitle(format.language, format.label),
                    details = TrackLabels.subtitleDetails(format.language, format.label, forced, sdh, subtitleFormat),
                    language = format.language,
                    isForced = forced,
                    isSelected = group.isTrackSelected(i),
                    mediaTrackGroup = trackGroup
                )
            }
        }
        return list
    }

    /** True while no subtitle is shown (switched off, or none chosen). */
    fun isOff(player: Player, tracks: List<SubtitleTrackInfo>): Boolean =
        C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes || tracks.none { it.isSelected }

    fun select(player: Player, track: SubtitleTrackInfo) {
        val group = track.mediaTrackGroup ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group, listOf(track.trackIndex)))
            .build()
    }

    fun turnOff(player: Player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** TV: read from the sofa, larger than the phone default. */
    const val TV_TEXT_SIZE_MULTIPLIER = 1.3f

    /**
     * Phone: the user's Android caption settings. TV: larger white text with a black outline on a dark band, readable
     * on bright scenes and from a distance.
     */
    fun applyStyle(view: SubtitleView, isTv: Boolean) {
        if (isTv) {
            view.setStyle(
                CaptionStyleCompat(Color.WHITE, 0x99000000.toInt(), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null)
            )
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * TV_TEXT_SIZE_MULTIPLIER)
        } else {
            view.setUserDefaultStyle()
            view.setUserDefaultTextSize()
        }
    }
}
