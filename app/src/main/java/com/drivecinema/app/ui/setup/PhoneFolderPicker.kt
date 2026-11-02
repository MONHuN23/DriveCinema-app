package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.FolderPickerSections
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcChevron
import com.drivecinema.app.ui.design.DcConfirmDialog
import com.drivecinema.app.ui.design.DcGroup
import com.drivecinema.app.ui.design.DcGroupDivider
import com.drivecinema.app.ui.design.DcGroupTitle
import com.drivecinema.app.ui.design.DcIconButton
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcStatusChip
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTextField
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.settings.SIGN_OUT_QUESTION

/** The phone picker's labels, shared with its tests. */
object PhoneFolderPickerLabels {
    const val TITLE = "Choose your library"
    const val MORE_OPTIONS = "More options"
    const val REFRESH = "Refresh folders"
    const val SETUP_GUIDE = "Setup guide"
    const val SEARCH = "Search folders"
    const val YOUR_DRIVE = "Your Drive"
    const val SHARED_WITH_YOU = "Shared with you"
    const val CURRENT = "Current library"
    const val RECOMMENDED = "Recommended"
}

/**
 * The phone's library picker (UI polish, design Option A): a top bar with Back (only while switching, where Back
 * cancels the switch; at first setup there is nothing to go back to) and an overflow menu for Refresh, the setup
 * guide and Sign out; the user's own folders as a group; "Shared with you" folded away unless it is needed; a search
 * box once there are many folders. Unlike the TV's picker it never takes focus by itself, so no row starts out
 * looking selected.
 */
@Composable
internal fun PhoneFolderPicker(
    sections: FolderPickerSections,
    isLoading: Boolean,
    isValidating: Boolean,
    errorMessage: String?,
    currentFolderId: String?,
    busyMessage: String?,
    onFolderSelected: (DriveFolder) -> Unit,
    onRefreshClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onCancelClick: (() -> Unit)?,
    onOpenGuideClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val form = DcTheme.formFactor
    var query by rememberSaveable { mutableStateOf("") }
    var sharedExpanded by rememberSaveable(sections.mine.isEmpty(), currentFolderId) {
        mutableStateOf(sections.sharedStartsExpanded(currentFolderId))
    }
    var askSignOut by rememberSaveable { mutableStateOf(false) }
    val shown = remember(sections, query) { sections.filter(query) }
    val searching = query.isNotBlank()

    Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        PickerTopBar(
            onBack = onCancelClick,
            onRefresh = onRefreshClick,
            onOpenGuide = onOpenGuideClick,
            onSignOut = { askSignOut = true }
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("folder_picker_list"),
                contentPadding = PaddingValues(start = form.sideMargin, end = form.sideMargin, top = 4.dp, bottom = 28.dp)
            ) {
                item(key = "intro") {
                    Text(
                        "Pick the Drive folder that holds your Films and Series folders.",
                        style = DcTheme.type.body,
                        color = DcColors.Text2
                    )
                }
                if (!errorMessage.isNullOrEmpty()) {
                    item(key = "error") { DcBanner(errorMessage, kind = DcBannerKind.Error, modifier = Modifier.padding(top = 16.dp)) }
                }
                when {
                    isLoading || isValidating -> item(key = "busy") {
                        BusyState(
                            when {
                                isValidating && busyMessage != null -> busyMessage
                                isValidating -> "Validating folder structure..."
                                else -> "Loading Google Drive folders..."
                            }
                        )
                    }

                    sections.isEmpty -> item(key = "empty") { EmptyState(onRefreshClick, onOpenGuideClick) }

                    else -> {
                        if (sections.total > FolderPickerSections.SEARCH_THRESHOLD) {
                            item(key = "search") {
                                DcTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = PhoneFolderPickerLabels.SEARCH,
                                    imeAction = ImeAction.Search,
                                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                                )
                            }
                        }
                        if (shown.mine.isNotEmpty()) {
                            item(key = "title_mine") { DcGroupTitle(PhoneFolderPickerLabels.YOUR_DRIVE) }
                            folderGroup("mine", shown.mine, currentFolderId, onFolderSelected)
                        }
                        when {
                            shown.shared.isEmpty() -> Unit
                            // A search shows every match; folding them away would hide what was asked for.
                            searching -> {
                                item(key = "title_shared") { DcGroupTitle(PhoneFolderPickerLabels.SHARED_WITH_YOU) }
                                folderGroup("shared", shown.shared, currentFolderId, onFolderSelected)
                            }

                            else -> {
                                item(key = "shared_toggle") {
                                    SharedToggle(
                                        count = shown.shared.size,
                                        expanded = sharedExpanded,
                                        onToggle = { sharedExpanded = !sharedExpanded },
                                        modifier = Modifier.padding(top = if (shown.mine.isEmpty()) 16.dp else 14.dp)
                                    )
                                }
                                if (sharedExpanded) {
                                    item(key = "shared_gap") { Spacer(Modifier.height(8.dp)) }
                                    folderGroup("shared", shown.shared, currentFolderId, onFolderSelected)
                                }
                            }
                        }
                        if (searching && shown.isEmpty) {
                            item(key = "no_match") {
                                Text(
                                    "No folders match \"${query.trim()}\".",
                                    style = DcTheme.type.body,
                                    color = DcColors.Text3,
                                    modifier = Modifier.padding(top = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (askSignOut) {
        DcConfirmDialog(
            title = "Sign out?",
            text = SIGN_OUT_QUESTION,
            confirmLabel = "Sign out",
            onConfirm = {
                askSignOut = false
                onSignOutClick()
            },
            onDismiss = { askSignOut = false }
        )
    }
}

/** One group of folder rows; each row is its own lazy item, so a long shared list stays cheap. */
private fun LazyListScope.folderGroup(
    key: String,
    folders: List<DriveFolder>,
    currentFolderId: String?,
    onFolderSelected: (DriveFolder) -> Unit
) {
    folders.forEachIndexed { index, folder ->
        item(key = "${key}_${folder.id}") {
            val first = index == 0
            val last = index == folders.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) 14.dp else 0.dp,
                topEnd = if (first) 14.dp else 0.dp,
                bottomStart = if (last) 14.dp else 0.dp,
                bottomEnd = if (last) 14.dp else 0.dp
            )
            Column(Modifier.fillMaxWidth().background(DcColors.Surface1, shape)) {
                if (!first) DcGroupDivider()
                FolderRow(folder, isCurrent = folder.id == currentFolderId, onClick = { onFolderSelected(folder) })
            }
        }
    }
}

@Composable
private fun FolderRow(folder: DriveFolder, isCurrent: Boolean, onClick: () -> Unit) {
    val supporting = when {
        isCurrent -> PhoneFolderPickerLabels.CURRENT
        !folder.ownedByMe -> "Shared by ${folder.ownerName ?: "another account"}"
        else -> null
    }
    DcListRow(
        folder.name,
        supporting = supporting,
        icon = if (folder.ownedByMe) AppIcons.Folder else AppIcons.Group,
        onClick = onClick,
        trailing = {
            when {
                isCurrent -> Icon(AppIcons.CheckCircle, contentDescription = null, tint = DcColors.Success, modifier = Modifier.size(24.dp))
                FolderPickerSections.isRecommended(folder) -> {
                    DcStatusChip(PhoneFolderPickerLabels.RECOMMENDED, kind = DcStatusKind.Brand)
                    DcChevron()
                }
                else -> DcChevron()
            }
        }
    )
}

@Composable
private fun SharedToggle(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    DcGroup(modifier) {
        DcListRow(
            PhoneFolderPickerLabels.SHARED_WITH_YOU,
            supporting = if (count == 1) "1 folder other people shared with you" else "$count folders other people shared with you",
            icon = AppIcons.Group,
            onClick = onToggle,
            modifier = Modifier.testTag("folder_picker_shared_toggle"),
            trailing = {
                Icon(
                    AppIcons.ExpandMore,
                    contentDescription = if (expanded) "Hide shared folders" else "Show shared folders",
                    tint = DcColors.Text3,
                    modifier = Modifier.size(24.dp).rotate(if (expanded) 180f else 0f)
                )
            }
        )
    }
}

@Composable
private fun PickerTopBar(
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpenGuide: (() -> Unit)?,
    onSignOut: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onBack != null) {
            DcIconButton(AppIcons.ArrowBack, contentDescription = "Back", onClick = onBack)
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Text(
            PhoneFolderPickerLabels.TITLE,
            style = DcTheme.type.headline,
            color = DcColors.Text1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box {
            DcIconButton(AppIcons.MoreVert, contentDescription = PhoneFolderPickerLabels.MORE_OPTIONS, onClick = { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = DcColors.Surface2,
                shape = RoundedCornerShape(10.dp)
            ) {
                MenuItem(PhoneFolderPickerLabels.REFRESH, AppIcons.Refresh) {
                    menuOpen = false
                    onRefresh()
                }
                if (onOpenGuide != null) {
                    MenuItem(PhoneFolderPickerLabels.SETUP_GUIDE, AppIcons.HelpOutline) {
                        menuOpen = false
                        onOpenGuide()
                    }
                }
                HorizontalDivider(color = DcColors.Divider, modifier = Modifier.padding(vertical = 4.dp))
                MenuItem("Sign out", AppIcons.Logout, destructive = true) {
                    menuOpen = false
                    onSignOut()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) DcColors.Danger else DcColors.Text1
    DropdownMenuItem(
        text = { Text(text, style = DcTheme.type.label) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        colors = MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = if (destructive) DcColors.Danger else DcColors.Text2
        ),
        modifier = Modifier.widthIn(min = 200.dp)
    )
}

@Composable
private fun BusyState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = DcColors.Brand)
        Spacer(Modifier.height(16.dp))
        Text(message, style = DcTheme.type.body, color = DcColors.Text2)
    }
}

@Composable
private fun EmptyState(onRefreshClick: () -> Unit, onOpenGuideClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No folders found", style = DcTheme.type.title, color = DcColors.Text1)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a folder named 'Streaming' in your Google Drive with 'Films' and 'Series' folders inside, or ask " +
                "the owner of a library to share their folder with this account. Then tap Refresh.",
            style = DcTheme.type.body,
            color = DcColors.Text2,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        DcButton("Refresh Folders", onClick = onRefreshClick, icon = AppIcons.Refresh)
        if (onOpenGuideClick != null) {
            DcButton("How to set up folders", onClick = onOpenGuideClick, kind = DcButtonKind.Tertiary)
        }
    }
}
