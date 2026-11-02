package com.drivecinema.app.ui.naming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.drivecinema.app.core.metadata.FolderCheck
import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import com.drivecinema.app.core.naming.NamingCheckFilter
import com.drivecinema.app.core.naming.NamingCheckRow
import com.drivecinema.app.core.naming.NamingCheckSeason
import com.drivecinema.app.core.naming.NamingCheckState
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcChoiceRow
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.design.DcTopBar
import com.drivecinema.app.ui.icons.AppIcons

/** What the Naming check's buttons do ([com.drivecinema.app.core.naming.NamingCheckModel]'s calls). */
class NamingCheckActions(
    val retry: () -> Unit,
    val setFilter: (NamingCheckFilter) -> Unit,
    val toggleSeason: (Int) -> Unit,
    /** The phone's "Which episode is this?": the file is that missing episode (null: not this one). */
    val pick: (fileId: String, FolderCheck.Missing?) -> Unit,
    val copy: (String) -> Unit,
    val openHelper: () -> Unit,
    val back: () -> Unit
)

/**
 * Task R.2: a series page's Naming check, on the phone and the TV (design R.0 §3): the files the app can't place,
 * then TMDB's seasons with each file (recognised, or the problem and the name to use) and the missing episodes,
 * greyed, with their names. All / Problems / Missing filter the rows. Read-only, no badge (Task R.6). The TV is
 * view-only: no copy and no episode picking; every row takes the focus, the filter starts focused.
 */
@Composable
fun NamingCheckScreen(
    seriesTitle: String,
    state: NamingCheckState,
    actions: NamingCheckActions,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val pad = if (isTv) 48.dp else 16.dp
    val firstFocus = remember { FocusRequester() }
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }

    if (isTv) {
        val key = when (state) {
            is NamingCheckState.Ready -> "ready"
            is NamingCheckState.Failed -> "failed"
            else -> null
        }
        LaunchedEffect(key) {
            if (key == null) return@LaunchedEffect
            repeat(5) {
                if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                withFrameNanos { }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        if (isTv) {
            Column(Modifier.padding(start = pad, end = pad, top = 34.dp, bottom = 6.dp)) {
                Text(NamingTexts.CHECK_TITLE, style = DcTheme.type.headline, color = DcColors.Text1)
                Text(seriesTitle, style = DcTheme.type.body, color = DcColors.Text2)
            }
        } else {
            DcTopBar(NamingTexts.CHECK_TITLE, onBack = actions.back)
        }
        DcFocusRoomScrolling {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("naming_check_list"),
                contentPadding = PaddingValues(start = pad, end = pad, top = if (isTv) DcFocusRoom else 4.dp, bottom = 32.dp)
            ) {
                if (!isTv) {
                    item(key = "series") { Text(seriesTitle, style = DcTheme.type.title, color = DcColors.Text1, modifier = Modifier.padding(bottom = 6.dp)) }
                }
                when (state) {
                    NamingCheckState.Loading -> item(key = "loading") { NamingLoading(NamingTexts.LOADING) }
                    NamingCheckState.NoTmdb -> item(key = "noTmdb") { DcBanner(NamingTexts.NEEDS_TMDB, kind = DcBannerKind.Warning) }
                    NamingCheckState.LocalSeries -> item(key = "local") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DcBanner(NamingTexts.LOCAL_SERIES, modifier = Modifier.testTag("naming_check_local"))
                            DcButton(
                                NamingTexts.HELPER_TITLE,
                                onClick = actions.openHelper,
                                kind = DcButtonKind.Secondary,
                                modifier = Modifier.focusRequester(firstFocus)
                            )
                        }
                    }
                    is NamingCheckState.Failed -> item(key = "failed") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DcBanner(if (state.drive) NamingTexts.DRIVE_FAILED else NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error)
                            DcButton(
                                NamingTexts.RETRY,
                                onClick = actions.retry,
                                kind = DcButtonKind.Secondary,
                                icon = AppIcons.Refresh,
                                modifier = Modifier.focusRequester(firstFocus).testTag("naming_check_retry")
                            )
                        }
                    }
                    is NamingCheckState.Ready -> readyItems(state, actions, isTv, firstFocus, onPick = { pickingFor = it })
                }
            }
        }
    }

    val ready = state as? NamingCheckState.Ready
    val file = pickingFor?.let { id -> ready?.report?.files?.firstOrNull { it.file.fileId == id } }
    if (!isTv && ready != null && file != null) {
        PickEpisodeDialog(
            file = file,
            missing = ready.report.missing,
            picked = ready.picks[file.file.fileId],
            onPick = { missing ->
                actions.pick(file.file.fileId, missing)
                pickingFor = null
            },
            onDismiss = { pickingFor = null }
        )
    }
}

private fun LazyListScope.readyItems(
    ready: NamingCheckState.Ready,
    actions: NamingCheckActions,
    isTv: Boolean,
    firstFocus: FocusRequester,
    onPick: (String) -> Unit
) {
    val report = ready.report
    item(key = "intro") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 760.dp).padding(bottom = 6.dp)) {
            Text(NamingTexts.CHECK_INTRO, style = DcTheme.type.body, color = DcColors.Text2)
            NamingName(NamingTexts.seriesFolderPath(report.listing.seriesFolderName))
            if (report.show == null) DcBanner(NamingTexts.noShow(report.listing.seriesFolderName), kind = DcBannerKind.Warning)
            NamingChips(
                options = NamingCheckFilter.entries,
                selected = ready.filter,
                label = {
                    when (it) {
                        NamingCheckFilter.ALL -> NamingTexts.ALL
                        NamingCheckFilter.PROBLEMS -> NamingTexts.problems(ready.problemCount)
                        NamingCheckFilter.MISSING -> NamingTexts.missing(ready.missingCount)
                    }
                },
                onSelect = actions.setFilter,
                modifier = Modifier.focusRequester(firstFocus),
                tagPrefix = "naming_filter"
            )
        }
    }
    when {
        ready.filter == NamingCheckFilter.PROBLEMS && ready.problemCount == 0 -> item(key = "noProblems") { NamingNote(NamingTexts.NO_PROBLEMS) }
        ready.filter == NamingCheckFilter.MISSING && ready.missingCount == 0 -> item(key = "noMissing") { NamingNote(NamingTexts.NOTHING_MISSING) }
    }
    if (ready.filter != NamingCheckFilter.MISSING && ready.unplaced.isNotEmpty()) {
        item(key = "unplacedTitle") {
            Text(NamingTexts.UNPLACED, style = DcTheme.type.title, color = DcColors.Text1, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        }
        items(ready.unplaced, key = { "file_${it.file.fileId}" }) { result -> FileRow(ready, result, actions, isTv, onPick) }
    }
    for (season in ready.seasons) {
        val rows = season.rows(ready.filter)
        // Filtered: only the seasons with something to show.
        if (ready.filter != NamingCheckFilter.ALL && rows.isEmpty()) continue
        item(key = "season_${season.season}") {
            SeasonHeader(
                title = season.title,
                supporting = seasonSummary(season),
                isOpen = season.isOpen,
                onToggle = { actions.toggleSeason(season.season) },
                modifier = Modifier.testTag("naming_check_season_${season.season}")
            )
        }
        if (!season.isOpen) continue
        when {
            season.isLoading -> item(key = "loading_${season.season}") { NamingLoading(NamingTexts.LOADING) }
            season.failed -> item(key = "failed_${season.season}") { DcBanner(NamingTexts.TMDB_FAILED, kind = DcBannerKind.Error) }
        }
        items(rows, key = { rowKey(season.season, it) }) { row ->
            when (row) {
                is NamingCheckRow.File -> FileRow(ready, row.result, actions, isTv, onPick)
                is NamingCheckRow.Missing -> NamingRow(
                    modifier = Modifier.testTag("naming_missing_${season.season}_${row.missing.episode.episodeNumber}"),
                    trailing = if (isTv) null else ({ CopyButton(row.missing.suggestion.fileName) { actions.copy(row.missing.suggestion.fileName) } })
                ) {
                    NamingLine(NamingTexts.episodeLine(row.missing.episode), dimmed = true)
                    NamingNote(NamingTexts.MISSING_STATUS)
                    NamingName(NamingTexts.missingLine(row.missing), dimmed = true)
                }
                is NamingCheckRow.Suggested -> NamingRow(
                    trailing = if (isTv || !row.row.readsBack) null else ({ CopyButton(row.row.fileName) { actions.copy(row.row.fileName) } })
                ) {
                    NamingLine(NamingTexts.episodeLine(row.row.episode), dimmed = true)
                    if (row.row.readsBack) NamingName("${row.row.seasonFolder}/${row.row.fileName}", dimmed = true)
                    else NamingNote(NamingTexts.CANT_READ, warning = true)
                }
            }
        }
    }
}

private fun rowKey(season: Int, row: NamingCheckRow): String = when (row) {
    is NamingCheckRow.File -> "file_${row.result.file.fileId}"
    is NamingCheckRow.Missing -> "missing_${season}_${row.missing.episode.episodeNumber}"
    is NamingCheckRow.Suggested -> "suggested_${season}_${row.row.episode.episodeNumber}"
}

/** "3 files · 4 missing", or "24 episodes, none on Drive". */
private fun seasonSummary(season: NamingCheckSeason): String {
    if (!season.hasFiles) return NamingTexts.noneOnDrive(season.episodeCount)
    val files = season.rows.count { it is NamingCheckRow.File }
    val missing = season.rows.count { it is NamingCheckRow.Missing }
    return listOfNotNull(
        if (files == 1) "1 file" else "$files files",
        missing.takeIf { it > 0 }?.let { "$it missing" }
    ).joinToString(" · ")
}

/** One file: recognised (its episode), or the problem, why, and the name to use. */
@Composable
private fun FileRow(
    ready: NamingCheckState.Ready,
    result: FileResult,
    actions: NamingCheckActions,
    isTv: Boolean,
    onPick: (String) -> Unit
) {
    val suggestion = ready.suggestionFor(result)
    val matched = result is FileResult.Matched
    NamingRow(
        modifier = Modifier.testTag("naming_file_${result.file.fileId}"),
        leading = {
            if (matched) StatusIcon(AppIcons.CheckCircle, DcColors.Success, NamingTexts.status(result))
            else StatusIcon(AppIcons.Warning, DcColors.Warning, NamingTexts.status(result))
        },
        trailing = if (isTv || suggestion == null) null else ({ CopyButton(suggestion.fileName) { actions.copy(suggestion.fileName) } })
    ) {
        when (result) {
            is FileResult.Matched -> NamingLine(NamingTexts.episodeLine(result.episode))
            else -> NamingLine(NamingTexts.status(result))
        }
        NamingName(NamingTexts.filePath(result.file))
        NamingTexts.reason(result)?.let { NamingNote(it) }
        suggestion?.let { Text(NamingTexts.suggestionLine(it, result.file), style = DcTheme.type.caption, color = DcColors.Text1) }
        if (!isTv && ready.canPick(result)) {
            DcButton(
                if (suggestion == null) NamingTexts.PICK_EPISODE else NamingTexts.CHANGE_PICK,
                onClick = { onPick(result.file.fileId) },
                kind = DcButtonKind.Tertiary,
                small = true,
                modifier = Modifier.testTag("naming_pick_${result.file.fileId}")
            )
        }
    }
}

/** The phone's "Which episode is this?": the season's missing episodes; the pick gives the file the episode's name. */
@Composable
private fun PickEpisodeDialog(
    file: FileResult,
    missing: List<FolderCheck.Missing>,
    picked: FolderCheck.Missing?,
    onPick: (FolderCheck.Missing?) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.SHEET.dp))
                .padding(20.dp)
                .testTag("naming_pick_dialog"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(NamingTexts.PICK_EPISODE, style = DcTheme.type.title, color = DcColors.Text1)
            Text(file.file.name, style = DcTheme.type.caption, color = DcColors.Text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(NamingTexts.PICK_INTRO, style = DcTheme.type.caption, color = DcColors.Text2)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                missing.forEach { m ->
                    DcChoiceRow(
                        NamingTexts.episodeCode(m.episode.seasonNumber, m.episode.episodeNumber) + " · " + m.episode.name,
                        selected = m == picked,
                        onClick = { onPick(m) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                if (picked != null) DcButton(NamingTexts.CLEAR_PICK, onClick = { onPick(null) }, kind = DcButtonKind.Tertiary)
                DcButton("Cancel", onClick = onDismiss, kind = DcButtonKind.Secondary)
            }
        }
    }
}
