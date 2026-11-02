package com.drivecinema.app.ui.mobile.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.mobile.home.PosterCard
import com.drivecinema.app.ui.player.WatchState

/**
 * The phone's Library tab: a title with the sort menu, a Films / Series segmented control, watch-state chips with
 * counts, then the poster grid. The choices survive a rotation.
 */
@Composable
fun FullLibraryGridScreen(
    movies: List<MediaItemEntity>,
    series: List<SeriesEntity>,
    onMovieClick: (MediaItemEntity) -> Unit,
    onSeriesClick: (SeriesEntity) -> Unit,
    modifier: Modifier = Modifier,
    filmStates: Map<String, WatchState> = emptyMap(),
    seriesStates: Map<String, WatchState> = emptyMap()
) {
    var showSeries by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.TITLE) }
    var filter by rememberSaveable { mutableStateOf(LibraryWatchFilter.ALL) }

    val films = remember(movies, filmStates) { MobileLibrary.films(movies, filmStates) }
    val shows = remember(series, seriesStates) { MobileLibrary.series(series, seriesStates) }
    val counts = remember(films, shows, showSeries) { MobileLibrary.counts(if (showSeries) shows else films) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DcColors.Canvas)
    ) {
        LibraryHeader(sort = sort, onSortChange = { sort = it })
        KindSegments(
            showSeries = showSeries,
            filmCount = movies.size,
            seriesCount = series.size,
            onChange = { showSeries = it }
        )
        WatchFilterChips(filter = filter, counts = counts, onChange = { filter = it })

        if (showSeries) {
            val visible = remember(shows, sort, filter) { MobileLibrary.apply(shows, sort, filter) }
            PosterGrid(
                entries = visible,
                key = { it.id },
                posterPath = { it.posterPath },
                onClick = onSeriesClick,
                emptyText = emptyText(filter, "series")
            )
        } else {
            val visible = remember(films, sort, filter) { MobileLibrary.apply(films, sort, filter) }
            PosterGrid(
                entries = visible,
                key = { it.id },
                posterPath = { it.posterPath },
                onClick = onMovieClick,
                emptyText = emptyText(filter, "films")
            )
        }
    }
}

private fun emptyText(filter: LibraryWatchFilter, kind: String): String = when (filter) {
    LibraryWatchFilter.ALL -> "No $kind yet"
    LibraryWatchFilter.UNWATCHED -> "No unwatched $kind"
    LibraryWatchFilter.IN_PROGRESS -> "No $kind in progress"
    LibraryWatchFilter.WATCHED -> "No watched $kind yet"
}

@Composable
private fun LibraryHeader(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Library", style = DcTheme.type.headline, color = DcColors.Text1, modifier = Modifier.weight(1f))
        Box {
            Row(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.DropdownList) { menuOpen = true }
                    .padding(horizontal = 12.dp)
                    .testTag("library_sort"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(AppIcons.Sort, contentDescription = null, tint = DcColors.Text1, modifier = Modifier.size(20.dp))
                Text("Sort: ${sort.label}", style = DcTheme.type.label, color = DcColors.Text1)
                Icon(AppIcons.ExpandMore, contentDescription = null, tint = DcColors.Text3, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = DcColors.Surface2
            ) {
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, style = DcTheme.type.body, color = DcColors.Text1) },
                        trailingIcon = if (option == sort) {
                            { Icon(AppIcons.Check, contentDescription = "Selected", tint = DcColors.Text1) }
                        } else null,
                        onClick = {
                            onSortChange(option)
                            menuOpen = false
                        },
                        modifier = Modifier.testTag("library_sort_${option.name.lowercase()}")
                    )
                }
            }
        }
    }
}

/** Films / Series as a two-part segmented control (a pill track with the selected half raised). */
@Composable
private fun KindSegments(showSeries: Boolean, filmCount: Int, seriesCount: Int, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DcColors.Surface1)
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf(Triple(false, "Films", filmCount), Triple(true, "Series", seriesCount)).forEach { (isSeries, label, count) ->
            val selected = showSeries == isSeries
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (selected) DcColors.Surface3 else DcColors.Surface1)
                    .selectable(selected = selected, role = Role.Tab) { onChange(isSeries) }
                    .testTag("library_kind_${label.lowercase()}"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Icon(AppIcons.Check, contentDescription = null, tint = DcColors.Text1, modifier = Modifier.size(18.dp))
                    Box(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = DcTheme.type.label.copy(fontWeight = FontWeight.Medium),
                    color = if (selected) DcColors.Text1 else DcColors.Text3
                )
                Box(Modifier.width(6.dp))
                Text(count.toString(), style = DcTheme.type.caption, color = DcColors.Text3)
            }
        }
    }
}

@Composable
private fun WatchFilterChips(
    filter: LibraryWatchFilter,
    counts: Map<LibraryWatchFilter, Int>,
    onChange: (LibraryWatchFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LibraryWatchFilter.entries.forEach { option ->
            val selected = option == filter
            val shape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .clip(shape)
                    .background(if (selected) DcColors.Text1 else DcColors.Canvas)
                    .border(1.dp, if (selected) DcColors.Text1 else DcColors.Outline, shape)
                    .selectable(selected = selected, role = Role.RadioButton) { onChange(option) }
                    .padding(horizontal = 14.dp)
                    .testTag("library_filter_${option.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val content = if (selected) DcColors.Ink else DcColors.Text2
                Text(option.label, style = DcTheme.type.caption.copy(fontWeight = FontWeight.Medium), color = content)
                if (option != LibraryWatchFilter.ALL) {
                    Text(
                        (counts[option] ?: 0).toString(),
                        style = DcTheme.type.caption,
                        color = content.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> PosterGrid(
    entries: List<LibraryEntry<T>>,
    key: (T) -> String,
    posterPath: (T) -> String?,
    onClick: (T) -> Unit,
    emptyText: String
) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                emptyText,
                style = DcTheme.type.body,
                color = DcColors.Text3,
                modifier = Modifier.padding(top = 48.dp).testTag("library_empty")
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(entries, key = { key(it.item) }) { entry ->
            PosterCard(
                title = entry.title,
                posterPath = posterPath(entry.item),
                onClick = { onClick(entry.item) }
            )
        }
    }
}
