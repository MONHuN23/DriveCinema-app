package com.drivecinema.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.drivecinema.app.core.player.TrackLabels
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcFormFactor
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.theme.LocalDcFormFactor
import com.drivecinema.app.ui.design.DcChoiceRow
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.DcTokens

/** Task P.8: which track panel is open in the player. */
enum class TrackPanelKind { AUDIO, SUBTITLES }

/** One row of a track panel: the language, then the format; [selected] is the track that plays. */
data class TrackChoice(val title: String, val supporting: String?, val selected: Boolean, val enabled: Boolean = true)

object TrackChoices {

    const val UNSUPPORTED = "Not supported on this device"

    /** One row per audio track; only the playing one is selected; a track the device can't play is disabled. */
    fun audio(tracks: List<AudioTrackInfo>): List<TrackChoice> {
        val playing = AudioTrackHelper.playingTrack(tracks)
        return tracks.map { track ->
            TrackChoice(
                title = track.languageDisplay,
                supporting = if (track.isSupported) track.formatDisplay else listOfNotNull(track.formatDisplay, UNSUPPORTED).joinToString(" · "),
                selected = track == playing,
                enabled = track.isSupported
            )
        }
    }

    /** "Off" first (selected while no subtitle shows), then each subtitle track inside the file (K.1). */
    fun subtitles(tracks: List<SubtitleTrackInfo>, isOff: Boolean): List<TrackChoice> =
        listOf(TrackChoice(TrackLabels.OFF, null, selected = isOff)) +
            tracks.map { TrackChoice(it.title, it.details, selected = !isOff && it.isSelected) }
}

@Composable
fun AudioTrackPanel(
    tracks: List<AudioTrackInfo>,
    isTv: Boolean,
    onTrackSelected: (AudioTrackInfo) -> Unit,
    onClose: () -> Unit
) {
    TrackPanel(
        title = "Audio",
        choices = TrackChoices.audio(tracks),
        isTv = isTv,
        onChoose = { index -> onTrackSelected(tracks[index]) },
        onClose = onClose
    )
}

@Composable
fun SubtitleTrackPanel(
    tracks: List<SubtitleTrackInfo>,
    isOff: Boolean,
    isTv: Boolean,
    onTrackSelected: (SubtitleTrackInfo) -> Unit,
    onOff: () -> Unit,
    onClose: () -> Unit
) {
    TrackPanel(
        title = "Subtitles",
        choices = TrackChoices.subtitles(tracks, isOff),
        isTv = isTv,
        onChoose = { index -> if (index == 0) onOff() else onTrackSelected(tracks[index - 1]) },
        onClose = onClose
    )
}

/**
 * Task P.8: a panel on the right edge, over a light scrim, so most of the picture stays in view. The remote starts on
 * the current choice (TV only: a focused row turns white, which a phone has no use for); OK or a tap picks a row and
 * closes the panel; a tap on the scrim closes it. Back is handled by the player ([MinimalPlayerView] and
 * `PlayerActivity`). Focus can't leave the panel while it is open.
 */
@Composable
fun TrackPanel(
    title: String,
    choices: List<TrackChoice>,
    isTv: Boolean,
    onChoose: (index: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // DcFocusRoomScrolling: a row scrolled into view keeps room for its focus scale and ring (P.7's rule).
    CompositionLocalProvider(LocalDcFormFactor provides if (isTv) DcFormFactor.Tv else DcFormFactor.Phone) { DcFocusRoomScrolling {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .testTag("track_panel_scrim")
                .background(Color(0x66000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        ) {
            val width = if (isTv) TV_WIDTH else min(PHONE_WIDTH, maxWidth * 0.85f)
            val initialFocus = remember { FocusRequester() }
            val focusIndex = choices.indexOfFirst { it.selected && it.enabled }.takeIf { it >= 0 } ?: choices.indexOfFirst { it.enabled }
            val slide = remember { Animatable(1f) }
            LaunchedEffect(Unit) { slide.animateTo(0f, tween(SLIDE_MS, easing = FastOutSlowInEasing)) }
            if (isTv && focusIndex >= 0) {
                LaunchedEffect(Unit) {
                    for (attempt in 1..5) {
                        withFrameNanos { }
                        if (runCatching { initialFocus.requestFocus() }.getOrDefault(false)) break
                    }
                }
            }
            val sheet = DcTokens.Radius.SHEET.dp
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(width)
                    .testTag("track_panel")
                    .graphicsLayer { translationX = slide.value * size.width }
                    .background(DcColors.Surface1, RoundedCornerShape(topStart = sheet, bottomStart = sheet))
                    // Taps on the panel itself don't reach the scrim (which closes it).
                    .pointerInput(Unit) { detectTapGestures { } }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (isTv) 24.dp else 16.dp, vertical = if (isTv) 27.dp else 16.dp)
            ) {
                Text(
                    title,
                    style = DcTheme.type.title,
                    color = DcColors.Text1,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                Column(
                    modifier = Modifier
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup()
                        .testTag("track_panel_list")
                        .verticalScroll(rememberScrollState())
                        // Room inside the scroll, so the first and last rows' rings aren't cut off (TV photos, 2026-10-29).
                        .padding(vertical = DcFocusRoom),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    choices.forEachIndexed { index, choice ->
                        DcChoiceRow(
                            title = choice.title,
                            supporting = choice.supporting,
                            selected = choice.selected,
                            enabled = choice.enabled,
                            onClick = { onChoose(index); onClose() },
                            modifier = Modifier
                                .then(if (index == focusIndex) Modifier.focusRequester(initialFocus) else Modifier)
                                .testTag("track_choice_$index")
                        )
                    }
                }
            }
        }
    } }
}

/** Under half of the TV's 960 dp, room for "E-AC-3 (Dolby Digital Plus) · 7.1" on one line. */
private val TV_WIDTH = 360.dp
private val PHONE_WIDTH = 340.dp
private const val SLIDE_MS = 200
