package com.drivecinema.app.ui.tv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.player.ContinueWatching
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState

/** What the spotlight says about one card (UI task ui/tv-browse-naming). */
@Immutable
data class TvSpotlightItem(
    /** "Continue watching", "Film", "Series". */
    val eyebrow: String,
    val title: String,
    /** "S1E1 · 22 min left", "2012 · Comedy · Fantasy". */
    val meta: String?,
    /** How far it is watched, for Continue Watching; null: no bar. */
    val progress: Float?,
    val overview: String?
)

/** TV Home's card keys: the return focus and the spotlight use the same ones. */
object TvHomeKeys {
    fun continueWatching(id: String) = "cw:$id"
    fun film(id: String) = "film:$id"
    fun series(id: String) = "series:$id"
}

/**
 * The spotlight for the card [key] names, or for Home's first card (Continue Watching, else the first film, else the
 * first series) when [key] is null or no longer on Home. Null on an empty library.
 */
fun tvSpotlightItem(state: MobileHomeUiState, key: String?): TvSpotlightItem? {
    fun genres(text: String?) = text?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2).orEmpty()
    fun facts(year: Int?, genres: String?) = (listOfNotNull(year?.toString()) + genres(genres)).joinToString(" · ").ifEmpty { null }

    val cw = state.continueWatching.firstOrNull { TvHomeKeys.continueWatching(it.id) == key }
    val film = state.movies.firstOrNull { TvHomeKeys.film(it.id) == key }
    val series = state.series.firstOrNull { TvHomeKeys.series(it.id) == key }
    return when {
        cw != null || (film == null && series == null && state.continueWatching.isNotEmpty()) -> {
            val item = cw ?: state.continueWatching.first()
            val overview = if (item.type == MediaType.EPISODE) {
                state.series.firstOrNull { it.id == item.seriesId }?.overview
            } else {
                state.movies.firstOrNull { it.id == item.id }?.overview
            }
            TvSpotlightItem(
                eyebrow = "Continue watching",
                title = item.title,
                meta = ContinueWatching.subtitle(item),
                progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else null,
                overview = overview
            )
        }
        film != null || (series == null && state.movies.isNotEmpty()) -> {
            val movie = film ?: state.movies.first()
            TvSpotlightItem("Film", movie.title, facts(movie.year, movie.genres), null, movie.overview)
        }
        else -> (series ?: state.series.firstOrNull())?.let { show ->
            TvSpotlightItem("Series", show.title, facts(show.year, show.genres), null, show.overview)
        }
    }
}

/**
 * Which card the spotlight shows: the focused one once focus has rested with no D-pad key held, the same rule as
 * [backdrop]'s image (Task M.1). Cards only call [onFocused]; only [TvSpotlight] reads [shownKey], so a focus move
 * recomposes nothing else on Home.
 */
@Stable
class TvSpotlightState(
    private val backdrop: TvAmbientBackdropState,
    private val restMillis: Long = TvAmbientBackdropDefaults.RestMillis
) {
    private var focusedKey: String? by mutableStateOf(null)

    var shownKey: String? by mutableStateOf(null)
        private set

    fun onFocused(key: String) {
        focusedKey = key
    }

    suspend fun follow() {
        snapshotFlow { focusedKey to backdrop.isDpadHeld }.collectAfterRest(restMillis) { (key, held) ->
            if (!held) shownKey = key
        }
    }
}

/** TV Home's spotlight: the focused card's title, episode or facts, progress and two lines of overview. */
@Composable
fun TvSpotlight(state: TvSpotlightState, home: MobileHomeUiState, modifier: Modifier = Modifier) {
    LaunchedEffect(state) { state.follow() }
    val item = tvSpotlightItem(home, state.shownKey) ?: return
    Column(modifier = modifier.testTag("tv_spotlight"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            item.eyebrow.uppercase(),
            color = DcColors.Text3,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.3.sp
        )
        Text(
            item.title,
            color = DcColors.Text1,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("tv_spotlight_title")
        )
        if (item.meta != null || item.progress != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.progress != null) {
                    Box(
                        Modifier
                            .width(80.dp)
                            .height(4.dp)
                            .background(DcColors.Surface3, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(80.dp * item.progress)
                                .background(DcColors.Brand, RoundedCornerShape(2.dp))
                        )
                    }
                }
                item.meta?.let { Text(it, color = DcColors.Text2, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            }
        }
        item.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = DcColors.Text2, fontSize = 16.sp, lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
