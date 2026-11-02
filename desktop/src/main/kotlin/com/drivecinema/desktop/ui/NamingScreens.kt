package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.metadata.FolderCheck
import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.core.metadata.TmdbSeriesResult
import com.drivecinema.app.core.naming.NamingCheckFilter
import com.drivecinema.app.core.naming.NamingCheckRow
import com.drivecinema.app.core.naming.NamingCheckSeason
import com.drivecinema.app.core.naming.NamingCheckState
import com.drivecinema.app.core.naming.NamingGuideState
import com.drivecinema.app.core.naming.NamingPick
import com.drivecinema.app.core.naming.NamingSearchKind
import com.drivecinema.app.core.naming.NamingSeasonSection
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.desktop.ui.design.DesktopBanner
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.DesktopChoiceRow
import com.drivecinema.desktop.ui.design.DesktopGroup
import com.drivecinema.desktop.ui.design.DesktopGroupDivider
import com.drivecinema.desktop.ui.design.DesktopSectionHeader
import com.drivecinema.desktop.ui.design.DesktopStatusChip

/** What the naming helper page's buttons do (`NamingGuideModel`'s calls). [copy] puts text on the clipboard. */
class DesktopNamingHelperActions(
    val setQuery: (String) -> Unit,
    val setKind: (NamingSearchKind) -> Unit,
    val search: () -> Unit,
    val pickSeries: (TmdbSeriesResult) -> Unit,
    val pickFilm: (TmdbMetadata) -> Unit,
    val closePick: () -> Unit,
    val toggleSeason: (Int) -> Unit,
    val setLayout: (season: Int, NamingGuide.Layout) -> Unit,
    val retrySeason: (Int) -> Unit,
    val copy: (String) -> Unit
)

/**
 * Task R.3: the desktop's naming helper, a page of the settings shell (design R.0 §3): search TMDB for a series or a
 * film, pick one, and see the folder and file names the app recognises. Copy one name, or a season's names with Copy
 * all. Read-only (R.4 adds the Drive buttons to the Naming check). Lives in the shell's scrolling column.
 */
@Composable
fun ColumnScope.NamingHelperPage(state: NamingGuideState, actions: DesktopNamingHelperActions) {
    PageHeader(NamingTexts.HELPER_TITLE, NamingTexts.HELPER_INTRO)
    Spacer(Modifier.height(20.dp))
    if (!state.tmdbAvailable) {
        DesktopBanner(NamingTexts.NEEDS_TMDB, kind = DcBannerKind.Warning)
        Spacer(Modifier.height(16.dp))
    }
    when (val picked = state.picked) {
        null -> SearchSection(state, actions)
        is NamingPick.Series -> SeriesSection(picked, actions)
        is NamingPick.Film -> FilmSection(picked, actions)
    }
}

@Composable
private fun SearchSection(state: NamingGuideState, actions: DesktopNamingHelperActions) {
    Chips(
        NamingSearchKind.entries,
        state.kind,
        label = { if (it == NamingSearchKind.SERIES) NamingTexts.SERIES else NamingTexts.FILMS },
        onSelect = actions.setKind
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.setQuery,
            label = { Text(NamingTexts.searchLabel(state.kind)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.search() }),
            shape = RoundedCornerShape(DcTokens.Radius.CONTROL.dp),
            colors = fieldColors(),
            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                    actions.search()
                    true
                } else false
            }
        )
        DesktopButton(
            NamingTexts.SEARCH,
            onClick = actions.search,
            icon = DesktopIcons.Search,
            enabled = state.tmdbAvailable,
            loading = state.isSearching
        )
    }
    if (state.searchFailed) {
        Spacer(Modifier.height(12.dp))
        DesktopBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
    }
    val rows: List<Pair<TmdbMetadata, Pair<Boolean, () -> Unit>>>? = when (state.kind) {
        NamingSearchKind.SERIES -> state.seriesResults?.map { r -> r.metadata to (r.animated to { actions.pickSeries(r) }) }
        NamingSearchKind.FILMS -> state.filmResults?.map { f -> f to (false to { actions.pickFilm(f) }) }
    }
    if (rows != null) {
        Spacer(Modifier.height(16.dp))
        if (rows.isEmpty()) {
            Text(NamingTexts.noResults(state.query.trim()), style = DesktopType.body, color = DesktopColors.TextSecondary)
        } else {
            DesktopGroup {
                rows.forEachIndexed { index, (metadata, extra) ->
                    if (index > 0) DesktopGroupDivider(startInset = 72.dp)
                    ResultRow(metadata, animated = extra.first, onClick = extra.second)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(metadata: TmdbMetadata, animated: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (hovered) DesktopColors.SurfaceRaised else Color.Transparent, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RemoteImage(
            TmdbImages.poster(metadata.posterPath),
            contentDescription = null,
            modifier = Modifier.size(width = 40.dp, height = 60.dp).clip(RoundedCornerShape(DcTokens.Radius.TAG.dp)).background(DesktopColors.SurfaceRaised)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                metadata.title + (metadata.year?.let { " ($it)" } ?: ""),
                style = DesktopType.rowTitle,
                color = DesktopColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (animated) DesktopStatusChip(NamingTexts.ANIMATION, kind = DcStatusKind.Brand)
            metadata.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = DesktopType.caption, color = DesktopColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(DesktopIcons.ChevronRight, contentDescription = null, tint = DesktopColors.TextTertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SeriesSection(picked: NamingPick.Series, actions: DesktopNamingHelperActions) {
    val show = picked.result.metadata
    PickedTitle(show.title + (show.year?.let { " ($it)" } ?: ""), onBack = actions.closePick)
    val plan = picked.plan
    when {
        picked.isLoading -> Loading()
        plan == null -> DesktopBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
        else -> {
            NameLine(NamingTexts.seriesFolderPath(plan.folderName), note = NamingTexts.folderCheck(plan.folderFindsShow)) {
                CopyButton("folder name") { actions.copy(plan.folderName) }
            }
            picked.orderedSections.forEach { section ->
                val tmdbSeason = plan.seasons.firstOrNull { it.seasonNumber == section.season }
                SeasonHeader(
                    NamingTexts.seasonTitle(section.season, tmdbSeason?.name),
                    NamingTexts.episodeCount(tmdbSeason?.episodeCount ?: 0),
                    section.isOpen,
                    onToggle = { actions.toggleSeason(section.season) },
                    actions = {
                        val table = section.table
                        if (section.isOpen && table != null) {
                            DesktopButton(
                                NamingTexts.COPY_ALL,
                                onClick = { actions.copy(NamingTexts.guideCopy(table)) },
                                kind = DcButtonKind.Tertiary,
                                icon = DesktopIcons.ContentCopy,
                                small = true
                            )
                        }
                    }
                )
                if (section.isOpen) GuideSeason(plan, section, actions)
            }
        }
    }
}

@Composable
private fun GuideSeason(plan: NamingGuide.SeriesPlan, section: NamingSeasonSection, actions: DesktopNamingHelperActions) {
    val layouts = NamingTexts.layouts(section)
    if (layouts.size > 1) {
        Chips(layouts, section.table?.layout ?: section.layout, label = NamingTexts::layoutLabel, onSelect = { actions.setLayout(section.season, it) })
        Spacer(Modifier.height(8.dp))
    }
    val table = section.table
    when {
        section.isLoading && table == null -> Loading()
        section.failed && table == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DesktopBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
            DesktopButton(NamingTexts.RETRY, onClick = { actions.retrySeason(section.season) }, kind = DcButtonKind.Secondary, small = true)
        }
        table != null -> {
            if (table.seriesFolder != plan.folderName) {
                NameLine(NamingTexts.seriesFolderPath(table.seriesFolder), note = NamingTexts.folderCheck(table.folderFindsShow)) {
                    CopyButton("folder name") { actions.copy(table.seriesFolder) }
                }
            }
            DesktopGroup {
                table.rows.forEachIndexed { index, row ->
                    if (index > 0) DesktopGroupDivider()
                    EntryRow(
                        line = NamingTexts.episodeLine(row.episode),
                        name = if (row.readsBack) "${row.seasonFolder}/${row.fileName}" else null,
                        note = if (row.readsBack) null else NamingTexts.CANT_READ,
                        warning = !row.readsBack
                    ) {
                        if (row.readsBack) CopyButton(row.fileName) { actions.copy(row.fileName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmSection(picked: NamingPick.Film, actions: DesktopNamingHelperActions) {
    val film = picked.film
    PickedTitle(film.title + (film.year?.let { " ($it)" } ?: ""), onBack = actions.closePick)
    val plan = picked.plan
    if (picked.isLoading || plan == null) {
        Loading()
    } else {
        NameLine(NamingTexts.filmPath(plan), note = NamingTexts.filmCheck(plan)) {
            CopyButton(plan.fileName) { actions.copy(plan.fileName) }
        }
    }
}

/** What the Naming check page's buttons do (`NamingCheckModel`'s calls). */
class DesktopNamingCheckActions(
    val retry: () -> Unit,
    val setFilter: (NamingCheckFilter) -> Unit,
    val toggleSeason: (Int) -> Unit,
    val pick: (fileId: String, FolderCheck.Missing?) -> Unit,
    val copy: (String) -> Unit,
    val openHelper: () -> Unit,
    val back: () -> Unit
)

/**
 * Task R.3: a series' Naming check on the desktop (design R.0 §3): the files the app can't place, TMDB's seasons
 * with each file and the missing episodes (greyed, with their names), the All / Problems / Missing filter, Copy for
 * one name and Copy all for every rename (`old → new`). Read-only. [writeActions] is the place for Task R.4's
 * "Create folders" and "Apply renames" (built on its own branch), next to Copy all; null shows none.
 */
@Composable
fun NamingCheckScreen(
    seriesTitle: String,
    state: NamingCheckState,
    actions: DesktopNamingCheckActions,
    writeActions: (@Composable RowScope.(NamingCheckState.Ready) -> Unit)? = null
) {
    var pickingFor by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp)) {
                BackButton(actions.back)
                Spacer(Modifier.height(18.dp))
                PageHeader(NamingTexts.CHECK_TITLE, NamingTexts.CHECK_INTRO)
                Text(seriesTitle, style = DesktopType.title, color = DesktopColors.TextPrimary, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(16.dp))
                when (state) {
                    NamingCheckState.Loading -> Loading()
                    NamingCheckState.NoTmdb -> DesktopBanner(NamingTexts.NEEDS_TMDB, kind = DcBannerKind.Warning)
                    NamingCheckState.LocalSeries -> {
                        DesktopBanner(NamingTexts.LOCAL_SERIES)
                        Spacer(Modifier.height(12.dp))
                        DesktopButton(NamingTexts.HELPER_TITLE, onClick = actions.openHelper, kind = DcButtonKind.Secondary)
                    }
                    is NamingCheckState.Failed -> {
                        DesktopBanner(if (state.drive) NamingTexts.DRIVE_FAILED else NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
                        Spacer(Modifier.height(12.dp))
                        DesktopButton(NamingTexts.RETRY, onClick = actions.retry, kind = DcButtonKind.Secondary, icon = DesktopIcons.Refresh)
                    }
                    is NamingCheckState.Ready -> CheckReady(state, actions, writeActions, onPick = { pickingFor = it })
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
    val ready = state as? NamingCheckState.Ready
    val file = pickingFor?.let { id -> ready?.report?.files?.firstOrNull { it.file.fileId == id } }
    if (ready != null && file != null) {
        AlertDialog(
            onDismissRequest = { pickingFor = null },
            containerColor = DesktopColors.Surface,
            title = { Text(NamingTexts.PICK_EPISODE, color = DesktopColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(file.file.name, style = DesktopType.caption, color = DesktopColors.TextSecondary)
                    Text(NamingTexts.PICK_INTRO, style = DesktopType.caption, color = DesktopColors.TextSecondary)
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        ready.report.missing.forEach { m ->
                            DesktopChoiceRow(
                                NamingTexts.episodeCode(m.episode.seasonNumber, m.episode.episodeNumber) + " · " + m.episode.name,
                                selected = ready.picks[file.file.fileId] == m,
                                onClick = { actions.pick(file.file.fileId, m); pickingFor = null }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (ready.picks[file.file.fileId] != null) {
                    DesktopButton(NamingTexts.CLEAR_PICK, onClick = { actions.pick(file.file.fileId, null); pickingFor = null }, kind = DcButtonKind.Tertiary)
                }
            },
            dismissButton = { DesktopButton("Cancel", onClick = { pickingFor = null }, kind = DcButtonKind.Secondary) }
        )
    }
}

@Composable
private fun CheckReady(
    ready: NamingCheckState.Ready,
    actions: DesktopNamingCheckActions,
    writeActions: (@Composable RowScope.(NamingCheckState.Ready) -> Unit)?,
    onPick: (String) -> Unit
) {
    val report = ready.report
    NameLine(NamingTexts.seriesFolderPath(report.listing.seriesFolderName))
    if (report.show == null) {
        DesktopBanner(NamingTexts.noShow(report.listing.seriesFolderName), kind = DcBannerKind.Warning)
        Spacer(Modifier.height(12.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chips(
            NamingCheckFilter.entries,
            ready.filter,
            label = {
                when (it) {
                    NamingCheckFilter.ALL -> NamingTexts.ALL
                    NamingCheckFilter.PROBLEMS -> NamingTexts.problems(ready.problemCount)
                    NamingCheckFilter.MISSING -> NamingTexts.missing(ready.missingCount)
                }
            },
            onSelect = actions.setFilter,
            modifier = Modifier.weight(1f)
        )
        val renames = NamingTexts.renameList(ready)
        DesktopButton(
            NamingTexts.COPY_ALL,
            onClick = { actions.copy(renames) },
            kind = DcButtonKind.Secondary,
            icon = DesktopIcons.ContentCopy,
            enabled = renames.isNotEmpty(),
            small = true
        )
        writeActions?.invoke(this, ready)
    }
    when {
        ready.filter == NamingCheckFilter.PROBLEMS && ready.problemCount == 0 -> Note(NamingTexts.NO_PROBLEMS)
        ready.filter == NamingCheckFilter.MISSING && ready.missingCount == 0 -> Note(NamingTexts.NOTHING_MISSING)
    }
    if (ready.filter != NamingCheckFilter.MISSING && ready.unplaced.isNotEmpty()) {
        DesktopSectionHeader(NamingTexts.UNPLACED)
        DesktopGroup {
            ready.unplaced.forEachIndexed { index, result ->
                if (index > 0) DesktopGroupDivider()
                FileEntry(ready, result, actions, onPick)
            }
        }
    }
    for (season in ready.seasons) {
        val rows = season.rows(ready.filter)
        if (ready.filter != NamingCheckFilter.ALL && rows.isEmpty()) continue
        SeasonHeader(season.title, seasonSummary(season), season.isOpen, onToggle = { actions.toggleSeason(season.season) })
        if (!season.isOpen) continue
        if (season.isLoading) Loading()
        if (season.failed) DesktopBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
        if (rows.isEmpty()) continue
        DesktopGroup {
            rows.forEachIndexed { index, row ->
                if (index > 0) DesktopGroupDivider()
                when (row) {
                    is NamingCheckRow.File -> FileEntry(ready, row.result, actions, onPick)
                    is NamingCheckRow.Missing -> EntryRow(
                        line = NamingTexts.episodeLine(row.missing.episode),
                        status = NamingTexts.MISSING_STATUS,
                        name = NamingTexts.suggestionPath(row.missing.suggestion),
                        dimmed = true
                    ) { CopyButton(row.missing.suggestion.fileName) { actions.copy(row.missing.suggestion.fileName) } }
                    is NamingCheckRow.Suggested -> EntryRow(
                        line = NamingTexts.episodeLine(row.row.episode),
                        name = if (row.row.readsBack) "${row.row.seasonFolder}/${row.row.fileName}" else null,
                        note = if (row.row.readsBack) null else NamingTexts.CANT_READ,
                        warning = !row.row.readsBack,
                        dimmed = true
                    ) { if (row.row.readsBack) CopyButton(row.row.fileName) { actions.copy(row.row.fileName) } }
                }
            }
        }
    }
}

/** "3 files · 4 missing", or "24 episodes, none on Drive". */
private fun seasonSummary(season: NamingCheckSeason): String {
    if (!season.hasFiles) return NamingTexts.noneOnDrive(season.episodeCount)
    val files = season.rows.count { it is NamingCheckRow.File }
    val missing = season.rows.count { it is NamingCheckRow.Missing }
    return listOfNotNull(if (files == 1) "1 file" else "$files files", missing.takeIf { it > 0 }?.let { "$it missing" }).joinToString(" · ")
}

@Composable
private fun FileEntry(ready: NamingCheckState.Ready, result: FileResult, actions: DesktopNamingCheckActions, onPick: (String) -> Unit) {
    val suggestion = ready.suggestionFor(result)
    val matched = result is FileResult.Matched
    EntryRow(
        line = if (result is FileResult.Matched) NamingTexts.episodeLine(result.episode) else NamingTexts.status(result),
        name = NamingTexts.filePath(result.file),
        note = NamingTexts.reason(result),
        suggestion = suggestion?.let { NamingTexts.suggestionLine(it, result.file) },
        icon = if (matched) DesktopIcons.CheckCircle else DesktopIcons.Warning,
        iconTint = if (matched) DesktopColors.Success else DesktopColors.Warning
    ) {
        if (ready.canPick(result)) {
            DesktopButton(
                if (suggestion == null) NamingTexts.PICK_EPISODE else NamingTexts.CHANGE_PICK,
                onClick = { onPick(result.file.fileId) },
                kind = DcButtonKind.Tertiary,
                small = true
            )
        }
        if (suggestion != null) CopyButton(suggestion.fileName) { actions.copy(suggestion.fileName) }
    }
}

// ---- Parts ----

@Composable
private fun PickedTitle(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        DesktopButton("Results", onClick = onBack, kind = DcButtonKind.Tertiary, icon = DesktopIcons.ArrowBack, small = true)
        Text(title, style = DesktopType.headline, color = DesktopColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            DesktopButton(
                label(option),
                onClick = { onSelect(option) },
                kind = if (option == selected) DcButtonKind.Primary else DcButtonKind.Secondary,
                icon = if (option == selected) DesktopIcons.Check else null,
                small = true
            )
        }
    }
}

@Composable
private fun SeasonHeader(
    title: String,
    supporting: String,
    isOpen: Boolean,
    onToggle: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp)
            .background(if (hovered) DesktopColors.Surface else Color.Transparent, RoundedCornerShape(DcTokens.Radius.CONTROL.dp))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (isOpen) DesktopIcons.ExpandMore else DesktopIcons.ChevronRight,
            contentDescription = if (isOpen) "Close" else "Open",
            tint = DesktopColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = DesktopType.title, color = DesktopColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(supporting, style = DesktopType.caption, color = DesktopColors.TextSecondary)
        }
        actions()
    }
}

/** A folder or file name with its copy button, outside the season tables. */
@Composable
private fun NameLine(text: String, note: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = DesktopType.body.copy(fontFamily = FontFamily.Monospace), color = DesktopColors.TextPrimary)
            note?.let { Text(it, style = DesktopType.caption, color = DesktopColors.Warning) }
        }
        trailing()
    }
}

/** One row of a season table: its first line, the name, a note, a suggestion and its buttons on the right. */
@Composable
private fun EntryRow(
    line: String,
    name: String?,
    status: String? = null,
    note: String? = null,
    warning: Boolean = false,
    suggestion: String? = null,
    dimmed: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color = DesktopColors.TextSecondary,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = DcTokens.DESKTOP.rowMinHeight.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                line,
                style = DesktopType.rowTitle,
                color = if (dimmed) DesktopColors.TextTertiary else DesktopColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            status?.let { Text(it, style = DesktopType.caption, color = DesktopColors.TextSecondary) }
            name?.let {
                Text(
                    it,
                    style = DesktopType.caption.copy(fontFamily = FontFamily.Monospace),
                    color = if (dimmed) DesktopColors.TextTertiary else DesktopColors.TextSecondary
                )
            }
            note?.let { Text(it, style = DesktopType.caption, color = if (warning) DesktopColors.Warning else DesktopColors.TextSecondary) }
            suggestion?.let { Text(it, style = DesktopType.caption, color = DesktopColors.TextPrimary) }
        }
        trailing()
    }
}

@Composable
private fun CopyButton(what: String, onCopy: () -> Unit) {
    IconActionButton(
        DesktopIcons.ContentCopy,
        tooltip = "Copy $what",
        onClick = onCopy,
        size = 36.dp,
        iconSize = 18.dp,
        tint = DesktopColors.TextSecondary
    )
}

@Composable
private fun Loading() {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(color = DesktopColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Text(NamingTexts.LOADING, style = DesktopType.body, color = DesktopColors.TextSecondary)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = DesktopType.body, color = DesktopColors.TextSecondary, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DesktopColors.TextPrimary,
    unfocusedTextColor = DesktopColors.TextPrimary,
    focusedBorderColor = DesktopColors.FocusRing,
    unfocusedBorderColor = DesktopColors.Outline,
    focusedLabelColor = DesktopColors.TextPrimary,
    unfocusedLabelColor = DesktopColors.TextSecondary,
    cursorColor = DesktopColors.TextPrimary
)
