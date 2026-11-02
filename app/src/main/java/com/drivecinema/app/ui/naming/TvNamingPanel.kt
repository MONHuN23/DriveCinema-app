package com.drivecinema.app.ui.naming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.core.naming.NamingGuideState
import com.drivecinema.app.core.naming.NamingPick
import com.drivecinema.app.core.naming.NamingSearchKind
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcStatusChip
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.tv.home.TvAmbientBackdropDefaults
import com.drivecinema.app.ui.tv.home.collectAfterRest

/** A series result's key, for the return focus and the names panel. */
internal fun seriesResultKey(tmdbId: Int) = "result_s$tmdbId"

/** A film result's key. */
internal fun filmResultKey(tmdbId: Int) = "result_f$tmdbId"

/**
 * Which result the TV's names panel shows (UI task ui/tv-browse-naming): the focused one, once the focus has rested
 * like the TV backdrop (Task M.1). Rows only write [onFocused]; only the panel reads [shownKey], so a focus move
 * recomposes nothing else.
 */
@Stable
internal class TvNamingPreviewState(private val restMillis: Long = TvAmbientBackdropDefaults.RestMillis) {
    private var focusedKey: String? by mutableStateOf(null)

    var shownKey: String? by mutableStateOf(null)
        private set

    fun onFocused(key: String) {
        focusedKey = key
    }

    suspend fun follow() {
        snapshotFlow { focusedKey }.collectAfterRest(restMillis) { shownKey = it }
    }
}

/** What the panel shows for one result. */
private class PanelItem(
    val metadata: TmdbMetadata,
    val kind: NamingSearchKind,
    val animated: Boolean,
    val names: String,
    val hint: String
)

/** The result [key] names, or the first result: the panel is never empty while there are results. */
private fun panelItem(state: NamingGuideState, key: String?): PanelItem? = when (state.kind) {
    NamingSearchKind.SERIES -> state.seriesResults?.let { results ->
        (results.firstOrNull { seriesResultKey(it.metadata.tmdbId) == key } ?: results.firstOrNull())?.let { result ->
            PanelItem(
                result.metadata, state.kind, result.animated,
                NamingTexts.previewTree(NamingGuide.seriesPreview(result, results)),
                NamingTexts.PREVIEW_HINT_SERIES
            )
        }
    }
    NamingSearchKind.FILMS -> state.filmResults?.let { results ->
        (results.firstOrNull { filmResultKey(it.tmdbId) == key } ?: results.firstOrNull())?.let { film ->
            PanelItem(film, state.kind, false, NamingTexts.filmPath(NamingGuide.filmPreview(film)), NamingTexts.PREVIEW_HINT_FILM)
        }
    }
}

/**
 * The TV naming helper's right side: the focused result's poster, overview and the names the app recognises,
 * before OK. Without results, the helper's introduction. Not focusable: Right from a result goes nowhere.
 */
@Composable
internal fun TvNamingPanel(state: NamingGuideState, preview: TvNamingPreviewState, modifier: Modifier = Modifier) {
    LaunchedEffect(preview) { preview.follow() }
    Column(
        modifier = modifier
            .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(20.dp)
            .testTag("naming_panel"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val item = panelItem(state, preview.shownKey)
        if (item == null) {
            Text(NamingTexts.HELPER_INTRO, style = DcTheme.type.body, color = DcColors.Text2)
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NamingPoster(item.metadata.posterPath, 92.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.metadata.title,
                    style = DcTheme.type.title,
                    color = DcColors.Text1,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("naming_panel_title")
                )
                Text(NamingTexts.kindLine(item.metadata.year, item.kind), style = DcTheme.type.caption, color = DcColors.Text2)
                if (item.animated) DcStatusChip(NamingTexts.ANIMATION, kind = DcStatusKind.Brand)
            }
        }
        item.metadata.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = DcTheme.type.caption, color = DcColors.Text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        PanelLabel(NamingTexts.PREVIEW_LABEL)
        NamesBox(item.names, Modifier.testTag("naming_panel_names"))
        Text(item.hint, style = DcTheme.type.caption, color = DcColors.Text3)
    }
}

/**
 * The TV's left side of a picked title: its poster, name and, for a series, its folder with the folder check. The
 * seasons (or the film's name) are the list beside it.
 */
@Composable
internal fun TvPickedSide(picked: NamingPick, modifier: Modifier = Modifier) {
    val metadata = when (picked) {
        is NamingPick.Series -> picked.result.metadata
        is NamingPick.Film -> picked.film
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NamingPoster(metadata.posterPath, 150.dp, Modifier.testTag("naming_picked_poster"))
        Text(
            metadata.title + (metadata.year?.let { " ($it)" } ?: ""),
            style = DcTheme.type.title,
            color = DcColors.Text1,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        val plan = (picked as? NamingPick.Series)?.plan
        if (plan != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelLabel(NamingTexts.SERIES_FOLDER)
                NamesBox(NamingTexts.seriesFolderPath(plan.folderName), Modifier.fillMaxWidth())
                NamingTexts.folderCheck(plan.folderFindsShow)?.let { NamingNote(it, warning = true) }
            }
        }
        Text(NamingTexts.BACK_TO_RESULTS, style = DcTheme.type.caption, color = DcColors.Text3)
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text.uppercase(), style = DcTheme.type.caption, color = DcColors.Text3)
}

/** Names in a monospaced face on a darker tile, so the exact spelling reads. */
@Composable
private fun NamesBox(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = DcTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
        color = DcColors.Text1,
        modifier = modifier
            .background(DcColors.Surface2, RoundedCornerShape(DcTokens.Radius.CONTROL.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}
