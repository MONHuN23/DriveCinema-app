package com.drivecinema.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.player.WatchState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcControlColors
import com.drivecinema.app.ui.design.DcFocusIndication
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcTokens
import java.util.Locale

// Task P.5: what the phone's and the TV's film and series pages share.

/** "7.2", or null without a rating; the screens draw a star icon before it ([FactsText]). */
fun ratingText(voteAverage: Double?): String? =
    voteAverage?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }

/** What the star icon reads as, for screen readers (and in [Facts.text]). */
const val RATED = "rated"

/**
 * A facts line: the known [parts] in order, then the [rating] after a star icon. Shown as "2012 · 3 seasons · [star]
 * 7.2" (design §5); [text] is how it reads: "2012 · 3 seasons · rated 7.2".
 */
data class Facts(val parts: List<String>, val rating: String? = null) {
    val text: String get() = (parts + listOfNotNull(rating?.let { "$RATED $it" })).joinToString(" · ")
    val isEmpty: Boolean get() = parts.isEmpty() && rating == null
}

/** The facts that are known, in this order, and the rating last. */
fun facts(vararg parts: String?, voteAverage: Double? = null): Facts =
    Facts(parts.filterNot { it.isNullOrBlank() }.map { it!! }, ratingText(voteAverage))

/** "2h 46m", "48m", or null when unknown. */
fun runtimeText(runtimeSeconds: Int?): String? {
    val minutes = (runtimeSeconds ?: return null) / 60
    if (minutes <= 0) return null
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

/** "1 season", "3 seasons", or null without seasons. */
fun seasonCountText(count: Int): String? = when (count) {
    0 -> null
    1 -> "1 season"
    else -> "$count seasons"
}

/** The genres as text ("Drama · Thriller"): they were chips that looked tappable (UI audit, §58). */
fun genresText(genres: String?): String? =
    genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(" · ")?.ifEmpty { null }

/** The series' Play button, with today's wording: "Resume S1E3 • Title", "Play S2E1", "Replay S1E1". */
fun playEpisodeLabel(episode: EpisodeItem): String {
    val action = when {
        episode.isCompleted -> "Replay"
        episode.positionMs > 0 -> "Resume"
        else -> "Play"
    }
    val title = if (episode.title.isNotEmpty()) " • ${episode.title}" else ""
    return "$action S${episode.season}E${episode.episode}$title"
}

/**
 * The TV's Play button (Task M.4): the episode code and the time left, sized to its text ("Resume S1E4 · 12 min
 * left", "Play S1E1", "Replay S1E1"); the title is in the focused episode's panel.
 */
fun tvPlayEpisodeLabel(episode: EpisodeItem): String {
    val code = "S${episode.season}E${episode.episode}"
    return when (WatchState.of(episode.positionMs, episode.isCompleted)) {
        WatchState.WATCHED -> "Replay $code"
        WatchState.NOT_STARTED -> "Play $code"
        WatchState.IN_PROGRESS -> factsOf("Resume $code", WatchState.label(episode.positionMs, episode.durationMs, false)
            ?.takeIf { it.endsWith("left") })
    }
}

private fun factsOf(vararg parts: String?): String = parts.filterNotNull().joinToString(" · ")

/** An episode's facts: its copy's length and resolution (Task L.3), then its rating; null when none known. */
fun episodeFacts(episode: EpisodeItem): Facts? = facts(episode.info, voteAverage = episode.voteAverage).takeUnless { it.isEmpty }

/** "E3. Title" ("E3. Episode 3" without a title). */
fun episodeTitle(episode: EpisodeItem): String = "E${episode.episode}. ${episode.title.ifEmpty { "Episode ${episode.episode}" }}"

/** The details header's picture (the `TmdbImages.detailsHeader` URL) fading into the canvas at the bottom. */
@Composable
internal fun DetailsBackdrop(url: String?, contentDescription: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(DcColors.Surface1))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to DcColors.Canvas))
        )
    }
}

/** Places the content [by] higher than it is laid out and takes that much less room: the poster over the backdrop. */
internal fun Modifier.overlapUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/** A season tab's look: no fill; focused (TV) white with dark text; the selected one has a red line under it. */
private val tabColors = DcControlColors(
    container = Color.Transparent,
    content = DcColors.Text2,
    focusedContainer = DcColors.Focus,
    focusedContent = DcColors.Ink
)

/**
 * The seasons as tabs with a red underline on the one shown (they were filter chips). [tabModifier] goes on each tab
 * (the TV's focus rules); each tab has the `season_tab_<n>` test tag and the selected semantics. A plain scrolling row,
 * not a lazy one: a series has few seasons, and every tab exists, so the TV can always send the focus to the selected one.
 * [DcFocusRoom] inside the scroll keeps a focused tab's scale and ring from being clipped at the row's ends (P.7).
 */
@Composable
internal fun SeasonTabs(
    seasons: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tabModifier: (season: Int) -> Modifier = { Modifier }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("season_tabs")
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = DcFocusRoom),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        seasons.forEach { season ->
            SeasonTab(season, isSelected = season == selected, onClick = { onSelect(season) }, modifier = tabModifier(season))
        }
    }
}

@Composable
private fun SeasonTab(season: Int, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val radius = DcTokens.Radius.CONTROL.dp
    Column(
        modifier = modifier
            .indication(interactionSource, DcFocusIndication(tabColors, radius))
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .testTag("season_tab_$season")
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .width(IntrinsicSize.Max)
    ) {
        Text(
            "Season $season",
            style = DcTheme.type.label.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) DcColors.Text1 else DcColors.Text2
        )
        // As wide as the label, so it sits centred under "Season N" (it was a 28 dp stub at the left, P.7).
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .then(if (isSelected) Modifier.background(DcColors.Brand, RoundedCornerShape(2.dp)) else Modifier)
                .testTag("season_tab_line_$season")
        )
    }
}

/**
 * A facts line ([Facts]) with a star icon before the rating (an icon, not a character: `AppIconsTest` keeps symbols out
 * of the UI sources); nothing when no fact is known.
 */
@Composable
internal fun FactsText(
    facts: Facts,
    modifier: Modifier = Modifier,
    style: TextStyle = DcTheme.type.body,
    color: Color = DcColors.Text2
) {
    if (facts.isEmpty) return
    val text = buildAnnotatedString {
        append(facts.parts.joinToString(" · "))
        facts.rating?.let { rating ->
            if (facts.parts.isNotEmpty()) append(" · ")
            appendInlineContent(STAR, RATED)
            append(" $rating")
        }
    }
    val star = mapOf(
        STAR to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            Icon(AppIcons.Star, contentDescription = null, tint = DcColors.RatingStar, modifier = Modifier.fillMaxSize())
        }
    )
    Text(text, style = style, color = color, inlineContent = star, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

private const val STAR = "star"

/** The genres line under the facts ([genresText]). */
@Composable
internal fun GenresText(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Text(text, style = DcTheme.type.caption, color = DcColors.Text3, modifier = modifier)
}
