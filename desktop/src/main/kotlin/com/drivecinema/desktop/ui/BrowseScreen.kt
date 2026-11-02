package com.drivecinema.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.desktop.browse.BrowseCard
import com.drivecinema.desktop.browse.BrowseKind
import com.drivecinema.desktop.browse.BrowseOptions
import com.drivecinema.desktop.browse.BrowseSort
import com.drivecinema.desktop.browse.LibraryBrowse
import com.drivecinema.desktop.browse.WatchFilter
import com.drivecinema.desktop.library.LibraryState
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.desktopFocusRing
import kotlinx.coroutines.launch

/**
 * Task L.1: every film or every series as a poster grid, with sort, genre and watch-state filters. [options] and
 * [gridState] live in the caller, so they survive opening a title and coming back.
 */
@Composable
fun BrowseScreen(
    kind: BrowseKind,
    cards: List<BrowseCard>,
    options: BrowseOptions,
    onOptions: (BrowseOptions) -> Unit,
    top: LibraryTopBar,
    gridState: LazyGridState,
    onOpen: (BrowseCard) -> Unit
) {
    val scope = rememberCoroutineScope()
    val shown = remember(cards, options) { LibraryBrowse.apply(cards, options) }
    val genres = remember(cards) { LibraryBrowse.genres(cards) }
    // A new sort or filter starts at the top; coming back from a title keeps the position.
    val change = { new: BrowseOptions ->
        onOptions(new)
        scope.launch { gridState.scrollToItem(0) }
        Unit
    }

    Column(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        TopBar(top)
        (top.library as? LibraryState.Ready)?.message?.let { Banner(it) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 16.dp)
        ) {
            Text(kind.label, color = DesktopColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                if (options.isFiltered) "${shown.size} of ${cards.size}" else cards.size.toString(),
                color = DesktopColors.TextSecondary,
                fontSize = 16.sp
            )
            if (options.isFiltered) {
                DesktopButton("Clear filters", onClick = { change(BrowseOptions(sort = options.sort)) }, kind = DcButtonKind.Tertiary, small = true)
            }
            Spacer(Modifier.weight(1f))
            WatchFilterControl(options.watch) { change(options.copy(watch = it)) }
            Box(Modifier.width(1.dp).height(20.dp).background(DesktopColors.Divider))
            OptionMenu(
                label = options.genre ?: "All genres",
                active = options.genre != null,
                choices = listOf<String?>(null) + genres,
                choiceLabel = { it ?: "All genres" },
                selected = options.genre,
                onChoose = { change(options.copy(genre = it)) },
                onClear = { change(options.copy(genre = null)) }
            )
            OptionMenu(
                label = options.sort.label,
                active = false,
                choices = BrowseSort.entries,
                choiceLabel = { it.label },
                selected = options.sort,
                onChoose = { change(options.copy(sort = it)) },
                icon = DesktopIcons.Sort,
                tooltip = "Sort by"
            )
        }

        when {
            cards.isEmpty() -> CenteredNote("No ${kind.label.lowercase()} in your library yet.")
            shown.isEmpty() -> CenteredNote("Nothing matches these filters.", actionLabel = "Clear filters") {
                change(BrowseOptions(sort = options.sort))
            }
            else -> Box(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(POSTER_WIDTH),
                    state = gridState,
                    contentPadding = PaddingValues(start = 32.dp, end = 32.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown, key = { it.id }) { card -> PosterCard(card) { onOpen(card) } }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(gridState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

private val FILTER_HEIGHT = 36.dp

/** All / Unwatched / In progress / Watched as one control; the chosen one is light on dark, never brand red. */
@Composable
private fun WatchFilterControl(selected: WatchFilter, onSelect: (WatchFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .height(FILTER_HEIGHT)
            .background(DesktopColors.Surface, CircleShape)
            .padding(3.dp)
            .selectableGroup()
    ) {
        WatchFilter.entries.forEach { filter -> Segment(filter.label, selected = filter == selected) { onSelect(filter) } }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .desktopFocusRing(focused, (FILTER_HEIGHT - 6.dp) / 2)
            .clip(CircleShape)
            .background(
                when {
                    selected -> DesktopColors.TextPrimary
                    hovered -> DesktopColors.SurfaceRaised
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            label,
            color = when {
                selected -> DesktopColors.Background
                hovered -> DesktopColors.TextPrimary
                else -> DesktopColors.TextSecondary
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/**
 * Pill that opens a menu of [choices], with a check on the [selected] one (genre, sort). While [active] (it filters)
 * it is tinted red, and with [onClear] its chevron becomes a × that clears it.
 */
@Composable
private fun <T> OptionMenu(
    label: String,
    active: Boolean,
    choices: List<T>,
    choiceLabel: (T) -> String,
    selected: T,
    onChoose: (T) -> Unit,
    onClear: (() -> Unit)? = null,
    icon: ImageVector? = null,
    tooltip: String? = null
) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Box {
        val pill = @Composable {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .height(FILTER_HEIGHT)
                    .desktopFocusRing(focused, FILTER_HEIGHT / 2)
                    .clip(CircleShape)
                    .background(
                        when {
                            active -> DesktopColors.AccentContainer
                            hovered || open -> DesktopColors.SurfaceRaised
                            else -> DesktopColors.Surface
                        }
                    )
                    .border(1.dp, if (active) DesktopColors.Accent.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interaction, indication = null, role = Role.DropdownList) { open = true }
                    .padding(start = if (icon != null) 12.dp else 14.dp, end = 9.dp)
            ) {
                icon?.let { Icon(it, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(18.dp)) }
                Text(label, color = DesktopColors.TextPrimary, fontSize = 13.sp)
                if (active && onClear != null) {
                    Icon(
                        DesktopIcons.Close,
                        contentDescription = "Clear",
                        tint = DesktopColors.TextPrimary,
                        modifier = Modifier.size(18.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear)
                    )
                } else {
                    Icon(DesktopIcons.ExpandMore, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (tooltip != null) WithTooltip(tooltip) { pill() } else pill()
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = DesktopColors.Surface
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choiceLabel(choice), color = DesktopColors.TextPrimary, fontSize = 14.sp) },
                    leadingIcon = {
                        if (choice == selected) Icon(DesktopIcons.Check, contentDescription = "Selected", tint = DesktopColors.Accent, modifier = Modifier.size(20.dp))
                        else Spacer(Modifier.size(20.dp))
                    },
                    onClick = { open = false; onChoose(choice) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }
}

@Composable
private fun CenteredNote(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = DesktopColors.TextSecondary, fontSize = 15.sp)
            actionLabel?.let {
                Spacer(Modifier.height(12.dp))
                DesktopButton(it, onClick = onAction, kind = DcButtonKind.Secondary)
            }
        }
    }
}
