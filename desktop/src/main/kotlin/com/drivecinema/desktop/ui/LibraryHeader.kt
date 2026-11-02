package com.drivecinema.desktop.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.desktop.browse.BrowseKind
import com.drivecinema.desktop.library.LibraryState
import com.drivecinema.desktop.ui.design.desktopFocusRing

/** The header's height; every control in it is [CONTROL_HEIGHT] high. */
internal val HEADER_HEIGHT = 72.dp
private val CONTROL_HEIGHT = 36.dp
private val MENU_WIDTH = 296.dp
private val MENU_ITEM_HEIGHT = 40.dp

/**
 * The top bar of Home, Films and Series (Task L.1; desktop polish 2026-11): logo, section tabs, then search and the
 * account button, one height. The library folder is in the account menu; while it syncs, "Syncing…" sits left of
 * search.
 */
@Composable
internal fun TopBar(top: LibraryTopBar) {
    val ready = top.library as? LibraryState.Ready
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(horizontal = 32.dp)
    ) {
        Text("DriveCinema", color = DesktopColors.Accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(8.dp))
        SectionTab("Home", "Ctrl+1", selected = top.section == null) { top.onSection(null) }
        SectionTab(BrowseKind.FILMS.label, "Ctrl+2", selected = top.section == BrowseKind.FILMS) { top.onSection(BrowseKind.FILMS) }
        SectionTab(BrowseKind.SERIES.label, "Ctrl+3", selected = top.section == BrowseKind.SERIES) { top.onSection(BrowseKind.SERIES) }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ready?.isSyncing == true) SyncingIndicator(ready.folderName)
            SearchButton(top.onSearch)
            if (top.account != null) {
                AccountButton(top.account, ready, top.menu)
            } else {
                HeaderPill(onClick = top.menu.onOpenGuide) {
                    Text(SettingsLabels.HELP_CENTER, color = DesktopColors.TextPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}

/** Home / Films / Series: bold with a red underline when selected. */
@Composable
private fun SectionTab(label: String, shortcut: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    WithTooltip(tooltipText(label, shortcut)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                label,
                color = if (selected || hovered) DesktopColors.TextPrimary else DesktopColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) DesktopColors.Accent else Color.Transparent)
            )
        }
    }
}

/** Shown only while the library syncs; the folder name is in the tooltip. */
@Composable
private fun SyncingIndicator(folderName: String) {
    WithTooltip("Syncing \"$folderName\"") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(CONTROL_HEIGHT).padding(horizontal = 4.dp)
        ) {
            CircularProgressIndicator(color = DesktopColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Text("Syncing…", color = DesktopColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

/** A [CONTROL_HEIGHT] pill on Surface 1 that lifts on hover, with the keyboard focus ring. */
@Composable
private fun HeaderPill(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(CONTROL_HEIGHT)
            .desktopFocusRing(focused, CONTROL_HEIGHT / 2)
            .clip(CircleShape)
            .background(if (hovered) DesktopColors.SurfaceRaised else DesktopColors.Surface)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        content = content
    )
}

@Composable
private fun SearchButton(onSearch: () -> Unit) {
    HeaderPill(onClick = onSearch, modifier = Modifier.width(208.dp)) {
        Icon(DesktopIcons.Search, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search", color = DesktopColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            "Ctrl+F",
            color = DesktopColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier
                .border(1.dp, DesktopColors.SurfaceSelected, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** The avatar alone; it opens the account menu, which starts with the account and the library folder. */
@Composable
private fun AccountButton(account: AccountInfo, library: LibraryState.Ready?, menu: AccountMenuActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val focused by interaction.collectIsFocusedAsState()
        WithTooltip(account.name) {
            Avatar(
                account,
                CONTROL_HEIGHT,
                Modifier
                    .hoverRing(hovered || open)
                    .desktopFocusRing(focused, CONTROL_HEIGHT / 2)
                    .hoverable(interaction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { open = true }
                    .semantics { contentDescription = "Account menu" }
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = DesktopColors.Surface
        ) {
            fun pick(action: () -> Unit) { open = false; action() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(MENU_WIDTH).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
            ) {
                Avatar(account, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        account.name,
                        style = DesktopType.rowTitle,
                        color = DesktopColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(account.email, style = DesktopType.caption, color = DesktopColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            library?.let { LibraryCard(it, onChange = menu.onChangeFolder?.let { change -> { pick(change) } }) }
            menu.onRefresh?.let { MenuEntry(DesktopIcons.Refresh, "Refresh library") { pick(it) } }
            MenuDivider()
            MenuEntry(DesktopIcons.Storage, "Local folders") { pick(menu.onLocalFolders) }
            menu.onImport?.let { MenuEntry(DesktopIcons.CloudUpload, "Import to Drive") { pick(it) } }
            menu.onSharing?.let { MenuEntry(DesktopIcons.WifiTethering, "Share with my devices") { pick(it) } }
            menu.onPcs?.let { MenuEntry(DesktopIcons.DesktopWindows, "Play from another PC") { pick(it) } }
            menu.onNamingHelper?.let { MenuEntry(DesktopIcons.Article, SettingsLabels.NAMING_HELPER) { pick(it) } }
            // Toggling a switch keeps the menu open.
            menu.skipIntroEnabled?.let { enabled ->
                MenuEntry(DesktopIcons.SkipNext, "Skip intro", trailing = if (enabled) "On" else "Off", onClick = menu.onToggleSkipIntro)
            }
            menu.deviceSyncEnabled?.let { enabled ->
                MenuEntry(DesktopIcons.Refresh, "Sync with my devices", trailing = if (enabled) "On" else "Off", onClick = menu.onToggleDeviceSync)
                Text(
                    menu.deviceSyncStatus ?: com.drivecinema.app.core.devicesync.DeviceSyncController.SWITCH_CAPTION,
                    color = DesktopColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.width(MENU_WIDTH).padding(start = 52.dp, end = 16.dp, bottom = 6.dp)
                )
            }
            MenuEntry(DesktopIcons.HelpOutline, SettingsLabels.HELP_CENTER) { pick(menu.onOpenGuide) }
            menu.onSignOut?.let {
                MenuDivider()
                MenuEntry(DesktopIcons.Logout, "Sign out", color = DesktopColors.Danger) { pick(it) }
            }
        }
    }
}

/** The library folder at the top of the account menu, with Change ([onChange] null while it syncs). */
@Composable
private fun LibraryCard(library: LibraryState.Ready, onChange: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            .width(MENU_WIDTH - 16.dp)
            .background(DesktopColors.SurfaceRaised, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Icon(DesktopIcons.Folder, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text("LIBRARY", color = DesktopColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Text(library.folderName, style = DesktopType.label, color = DesktopColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onChange != null) {
            SmallPillButton("Change", onChange)
        } else if (library.isSyncing) {
            Text("Syncing…", style = DesktopType.caption, color = DesktopColors.TextSecondary)
        }
    }
}

@Composable
private fun SmallPillButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    Text(
        label,
        color = DesktopColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .desktopFocusRing(focused, 14.dp)
            .clip(CircleShape)
            .background(if (hovered) DesktopColors.SurfaceSelected else DesktopColors.Surface)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun MenuEntry(
    icon: ImageVector,
    label: String,
    color: Color = DesktopColors.TextPrimary,
    trailing: String? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, color = color, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) },
        trailingIcon = trailing?.let {
            {
                Text(
                    it,
                    color = if (it == "On") DesktopColors.TextPrimary else DesktopColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.width(MENU_WIDTH).height(MENU_ITEM_HEIGHT).pointerHoverIcon(PointerIcon.Hand)
    )
}

@Composable
private fun MenuDivider() {
    Box(Modifier.width(MENU_WIDTH).padding(vertical = 4.dp).height(1.dp).background(DesktopColors.Divider))
}

@Composable
private fun Avatar(account: AccountInfo, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(DesktopColors.Focus),
        contentAlignment = Alignment.Center
    ) {
        Text(account.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
        account.photoUrl?.let { RemoteImage(it, account.name, Modifier.fillMaxSize()) }
    }
}

/** A soft ring just outside a round control while it is hovered or its menu is open. */
private fun Modifier.hoverRing(show: Boolean): Modifier = drawWithContent {
    drawContent()
    if (show) {
        val ring = 2.dp.toPx()
        drawCircle(
            color = DesktopColors.TextPrimary.copy(alpha = 0.45f),
            radius = size.minDimension / 2 + 2.dp.toPx() + ring / 2,
            style = Stroke(ring)
        )
    }
}
