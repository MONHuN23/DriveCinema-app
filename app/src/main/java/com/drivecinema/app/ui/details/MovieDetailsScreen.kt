package com.drivecinema.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.TmdbImages
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcIconButton
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons

/** A film's Play button: "Resume from 1:23:45" after 10 s watched (and not finished), else "Play Movie". */
fun moviePlay(progress: PlaybackProgressEntity?): Pair<String, Long> {
    val position = progress?.takeIf { !it.isCompleted && it.positionMs > 10_000L }?.positionMs
    return if (position != null) "Resume from ${formatMinutes(position)}" to position else "Play Movie" to 0L
}

/**
 * A film's details on the phone (Task P.5, design §5): the backdrop, the poster over it with the title, one facts line
 * ("2024 · 2h 46m · [star] 8.4") and the genres as text; the copy lines (Task L.3); Play; the overview.
 */
@Composable
fun MovieDetailsScreen(
    movie: MediaItemEntity,
    progress: PlaybackProgressEntity?,
    onPlayClick: (startPositionMs: Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Task L.3: one technical line per available copy ([MediaInfoFormat.infoLine]). */
    infoLines: List<String> = emptyList(),
    /** Milestone D: the phone's "Play on TV"; null hides it (TV, or no TV linked). */
    onPlayOnTvClick: (() -> Unit)? = null
) {
    val (playLabel, startPos) = moviePlay(progress)
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.yield()
        runCatching { playFocusRequester.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            DetailsBackdrop(
                TmdbImages.detailsHeader(movie.backdropPath, movie.posterPath),
                contentDescription = movie.title,
                modifier = Modifier.fillMaxWidth().height(PHONE_BACKDROP_HEIGHT)
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhoneTitleBlock(
                    title = movie.title,
                    posterPath = movie.posterPath,
                    facts = facts(movie.year?.toString(), runtimeText(movie.runtimeSeconds), voteAverage = movie.voteAverage),
                    genres = genresText(movie.genres)
                )
                if (infoLines.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        infoLines.forEach { line ->
                            Text(line, style = DcTheme.type.caption, color = DcColors.Text2, modifier = Modifier.testTag("movie_info_line"))
                        }
                    }
                }
                DcButton(
                    playLabel,
                    onClick = { onPlayClick(startPos) },
                    icon = AppIcons.PlayArrow,
                    modifier = Modifier.fillMaxWidth().focusRequester(playFocusRequester)
                )
                movie.overview?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = DcTheme.type.body, color = DcColors.Text2, modifier = Modifier.padding(bottom = 32.dp))
                }
                if (onPlayOnTvClick != null) {
                    PlayOnTvButton(label = "Play on TV", onClick = onPlayOnTvClick)
                }
            }
        }
        DcIconButton(
            AppIcons.ArrowBack,
            contentDescription = "Back",
            onClick = onBackClick,
            onImage = true,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
        )
    }
}

/** The phone's backdrop height (design §5). */
internal val PHONE_BACKDROP_HEIGHT = 210.dp

/** The poster over the backdrop's lower edge, the title, the facts line and the genres beside it. */
@Composable
internal fun PhoneTitleBlock(title: String, posterPath: String?, facts: Facts, genres: String?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().overlapUp(56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (!posterPath.isNullOrEmpty()) {
            AsyncImage(
                model = TmdbImages.poster(posterPath),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 104.dp, height = 156.dp)
                    .clip(RoundedCornerShape(DcTokens.Radius.CONTROL.dp))
                    .background(DcColors.Surface1)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = DcTheme.type.display, color = DcColors.Text1)
            FactsText(facts, Modifier.testTag("details_facts"))
            GenresText(genres)
        }
    }
}

private fun formatMinutes(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
