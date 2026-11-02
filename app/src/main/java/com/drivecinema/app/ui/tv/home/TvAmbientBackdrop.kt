package com.drivecinema.app.ui.tv.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.drivecinema.app.ui.TmdbImages
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.getValue

object TvAmbientBackdropDefaults {
    /** How long focus has to rest on a card before the backdrop follows it (Task M.1). */
    const val RestMillis: Long = 300L
    const val CrossfadeMillis: Int = 300
    val BottomColor = Color(0xFF0C0D10)
}

/**
 * Calls [action] with a value only once no newer value has arrived for [restMillis]. A D-pad key held down moves
 * focus about 12 times a second, so it gives no call at all until the key is released, then one.
 */
suspend fun <T> Flow<T>.collectAfterRest(restMillis: Long, action: suspend (T) -> Unit) {
    collectLatest { value ->
        delay(restMillis)
        action(value)
    }
}

/**
 * The TV's ambient backdrop behind Home, the Films/Series grid and search. Cards report focus on every move
 * ([onCardFocused]); the shown backdrop ([shownPath]) changes only after focus has rested
 * [TvAmbientBackdropDefaults.RestMillis] on one card with no D-pad key held down, so a held key requests no backdrop
 * images (M.0: about 7 w1280 requests per 5 s of Right). Only [TvAmbientBackdrop] reads these, so a focus move
 * recomposes nothing on the screen.
 */
@Stable
class TvAmbientBackdropState(private val restMillis: Long = TvAmbientBackdropDefaults.RestMillis) {
    var focusedPath: String? by mutableStateOf(null)
        private set

    var shownPath: String? by mutableStateOf(null)
        private set

    /**
     * True while a D-pad arrow is down. A held key moves focus once, then the TV waits about half a second before
     * repeating it: longer than the rest, so without this the second card's backdrop showed during every hold
     * (user's TV check, 2026-10-27).
     */
    var isDpadHeld: Boolean by mutableStateOf(false)
        private set

    private val heldKeys = mutableSetOf<Key>()

    /** A card got or lost focus. A card without a backdrop keeps the current one, as before. */
    fun onCardFocused(isFocused: Boolean, backdropPath: String?) {
        if (isFocused && backdropPath != null) focusedPath = backdropPath
    }

    /** Sees every key event on the screen ([trackDpadHold]); never consumes it. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.key !in DpadArrows) return false
        when (event.type) {
            KeyEventType.KeyDown -> heldKeys += event.key
            KeyEventType.KeyUp -> heldKeys -= event.key
        }
        isDpadHeld = heldKeys.isNotEmpty()
        return false
    }

    /** Runs while the backdrop is on screen: moves [shownPath] to [focusedPath] once focus has rested. */
    suspend fun followFocus() {
        snapshotFlow { focusedPath to isDpadHeld }.collectAfterRest(restMillis) { (path, held) ->
            if (!held) shownPath = path
        }
    }

    private companion object {
        val DpadArrows = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
    }
}

/** On a screen's root: lets [state] see the D-pad arrows going down and up, so the backdrop waits for a release. */
fun Modifier.trackDpadHold(state: TvAmbientBackdropState): Modifier =
    onPreviewKeyEvent { state.onKeyEvent(it) }

/**
 * The backdrop [height] tall, darkened from [topScrimColor] at the top to the screen's colour at the bottom. With
 * [sideFade] it also fades into the screen's colour from the left, so text can sit beside it (TV Home's spotlight, UI
 * task ui/tv-browse-naming); give it a start padding to anchor it to the right.
 */
@Composable
fun TvAmbientBackdrop(
    state: TvAmbientBackdropState,
    height: Dp,
    topScrimColor: Color,
    label: String,
    modifier: Modifier = Modifier,
    sideFade: Boolean = false
) {
    LaunchedEffect(state) { state.followFocus() }

    Crossfade(
        targetState = state.shownPath,
        animationSpec = tween(durationMillis = TvAmbientBackdropDefaults.CrossfadeMillis),
        label = label,
        modifier = modifier
    ) { backdrop ->
        val url = TmdbImages.backdrop(backdrop)
        if (url != null) {
            Box(modifier = Modifier.fillMaxSize().testTag("tv_ambient_backdrop")) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(topScrimColor, TvAmbientBackdropDefaults.BottomColor)
                            )
                        )
                )
                if (sideFade) {
                    val bottom = TvAmbientBackdropDefaults.BottomColor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .background(
                                Brush.horizontalGradient(
                                    0f to bottom,
                                    0.35f to bottom.copy(alpha = 0.55f),
                                    0.7f to bottom.copy(alpha = 0.15f),
                                    1f to bottom.copy(alpha = 0.35f)
                                )
                            )
                    )
                }
            }
        }
    }
}
