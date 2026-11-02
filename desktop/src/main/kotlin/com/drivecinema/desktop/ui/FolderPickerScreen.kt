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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.FolderPickerSections
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.desktop.library.LibraryState
import com.drivecinema.desktop.ui.design.DesktopBanner
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.DesktopStatusChip
import com.drivecinema.desktop.ui.design.desktopFocusRing
import java.text.Normalizer
import java.util.Locale

/** The picker's "Filter folders" field: folders whose name or sharer contains the text. */
internal object FolderFilter {
    /** Ignores case and accents, so "tanulasi" finds "Tanulási"; a blank [query] keeps every folder. */
    fun apply(sections: FolderPickerSections, query: String): FolderPickerSections {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return sections
        fun matches(folder: DriveFolder) = fold(folder.name).contains(needle) || folder.ownerName?.let { fold(it).contains(needle) } == true
        return FolderPickerSections(sections.mine.filter(::matches), sections.shared.filter(::matches))
    }

    private val marks = Regex("\\p{Mn}+")

    private fun fold(text: String): String = marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
}

/**
 * "Choose your library folder" (desktop polish 2026-11): a fixed top bar with Back (Esc, only when a library already
 * exists), the title and a filter field; the folders scroll under it.
 */
@Composable
fun FolderPickerScreen(
    state: LibraryState.ChoosingFolder,
    onChoose: (DriveFolder) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(state.sections, query) { FolderFilter.apply(state.sections, query) }
    val listShown = !state.isLoading && state.switchingTo == null && !state.sections.isEmpty

    Column(Modifier.fillMaxSize().background(DesktopColors.Background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT + 4.dp).padding(horizontal = 32.dp)
        ) {
            if (state.canCancel && state.switchingTo == null) BackButton(onCancel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Choose your library folder", style = DesktopType.title, color = DesktopColors.TextPrimary)
                Text(
                    "Pick the Drive folder that holds \"Films\" and/or \"Series\". A folder named \"Streaming\" is recommended.",
                    style = DesktopType.caption,
                    color = DesktopColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (listShown) FilterField(query, onQueryChange = { query = it })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesktopColors.Divider))

        val listState = rememberLazyListState()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val column = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 32.dp)
                state.errorMessage?.let { message ->
                    item(key = "error") { DesktopBanner(message, column.padding(top = 16.dp), kind = DcBannerKind.Error) }
                }
                when {
                    state.switchingTo != null -> item(key = "switching") {
                        Row(column.padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = DesktopColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text("Reading \"${state.switchingTo}\"… this can take a moment for big libraries.", style = DesktopType.body, color = DesktopColors.TextPrimary)
                        }
                    }
                    state.isLoading -> item(key = "loading") {
                        Box(column.padding(top = 56.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DesktopColors.Accent)
                        }
                    }
                    state.sections.isEmpty -> item(key = "empty") {
                        Column(column.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("No folders found in your Drive.", style = DesktopType.body, color = DesktopColors.TextSecondary)
                            DesktopButton("Try again", onClick = onRetry, kind = DcButtonKind.Secondary, icon = DesktopIcons.Refresh)
                        }
                    }
                    shown.isEmpty -> item(key = "no-match") {
                        Text("No folders match \"${query.trim()}\".", style = DesktopType.body, color = DesktopColors.TextSecondary, modifier = column.padding(top = 28.dp))
                    }
                    else -> {
                        folderSection("YOUR DRIVE", shown.mine, state.currentFolderId, column, onChoose)
                        folderSection("SHARED WITH YOU", shown.shared, state.currentFolderId, column, onChoose)
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

private fun LazyListScope.folderSection(
    title: String,
    folders: List<DriveFolder>,
    currentFolderId: String?,
    column: Modifier,
    onChoose: (DriveFolder) -> Unit
) {
    if (folders.isEmpty()) return
    item(key = "title-$title") {
        Row(column.padding(top = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = DesktopColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(folders.size.toString(), color = DesktopColors.TextSecondary, fontSize = 12.sp)
        }
    }
    items(folders, key = { "$title-${it.id}" }) { folder ->
        FolderRow(folder, isCurrent = folder.id == currentFolderId, modifier = column.padding(bottom = 8.dp)) { onChoose(folder) }
    }
}

@Composable
private fun FolderRow(folder: DriveFolder, isCurrent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val recommended = FolderPickerSections.isRecommended(folder)
    val radius = DcTokens.Radius.CARD.dp
    // The padding sits outside the row, so the focus ring hugs the card.
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .desktopFocusRing(focused, radius)
                .background(if (hovered) DesktopColors.SurfaceRaised else DesktopColors.Surface, RoundedCornerShape(radius))
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Box(
                Modifier.size(36.dp).background(
                    when {
                        recommended -> DesktopColors.AccentContainer
                        hovered -> DesktopColors.SurfaceSelected
                        else -> DesktopColors.SurfaceRaised
                    },
                    RoundedCornerShape(DcTokens.Radius.CONTROL.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    DesktopIcons.Folder,
                    contentDescription = null,
                    tint = if (recommended) DesktopColors.Accent else DesktopColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(folder.name, style = DesktopType.rowTitle, color = DesktopColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!folder.ownedByMe && folder.ownerName != null) {
                    Text("Shared by ${folder.ownerName}", style = DesktopType.caption, color = DesktopColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (recommended) DesktopStatusChip("Recommended", kind = DcStatusKind.Brand)
            if (isCurrent) DesktopStatusChip("Current", kind = DcStatusKind.Neutral)
            if (hovered || focused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use folder", style = DesktopType.label, color = DesktopColors.TextPrimary)
                    Icon(DesktopIcons.ChevronRight, contentDescription = null, tint = DesktopColors.TextPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Takes the focus when it appears, so typing filters straight away; Esc still leaves the picker. */
@Composable
private fun FilterField(query: String, onQueryChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .width(260.dp)
            .height(36.dp)
            .background(DesktopColors.Surface, CircleShape)
            .border(1.dp, if (focused) DesktopColors.TextSecondary else DesktopColors.Outline, CircleShape)
            .padding(start = 14.dp, end = 10.dp)
    ) {
        Icon(DesktopIcons.Search, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Filter folders", color = DesktopColors.TextSecondary, fontSize = 14.sp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = DesktopColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(DesktopColors.TextPrimary),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                DesktopIcons.Close,
                contentDescription = "Clear filter",
                tint = DesktopColors.TextSecondary,
                modifier = Modifier.size(18.dp).pointerHoverIcon(PointerIcon.Hand).clickable { onQueryChange("") }
            )
        }
    }
}
