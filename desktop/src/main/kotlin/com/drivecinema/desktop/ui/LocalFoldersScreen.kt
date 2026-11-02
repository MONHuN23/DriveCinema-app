package com.drivecinema.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.desktop.local.LocalFolderStatus
import com.drivecinema.desktop.local.LocalFoldersState
import com.drivecinema.desktop.ui.design.DesktopBanner
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.DesktopConfirmDialog
import com.drivecinema.desktop.ui.design.DesktopGroup
import com.drivecinema.desktop.ui.design.DesktopGroupDivider
import com.drivecinema.desktop.ui.design.DesktopListRow
import com.drivecinema.desktop.ui.design.DesktopSectionHeader
import com.drivecinema.desktop.ui.design.DesktopStatusChip

/**
 * Local folders (Task P.6: in the settings shell): Add folder as the page's one primary action; each folder by its
 * name, its path shortened in the middle and its video count; Remove asks first (D-P2); the layout rule with a link
 * to its Help topic.
 */
@Composable
fun LocalFoldersScreen(
    state: LocalFoldersState,
    errorMessage: String?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRescan: () -> Unit,
    onOpenHelp: () -> Unit
) {
    var removing by remember { mutableStateOf<LocalFolderStatus?>(null) }
    PageHeader(
        "Local folders",
        "Folders on this computer join your library. When a title is both here and on Drive, the local copy plays: " +
            "instantly, and offline."
    ) {
        DesktopButton("Add folder", onClick = onAdd, icon = DesktopIcons.Add)
    }
    errorMessage?.let { DesktopBanner(it, Modifier.padding(top = 20.dp), kind = DcBannerKind.Warning) }
    state.message?.let { DesktopBanner(it, Modifier.padding(top = 20.dp)) }

    DesktopSectionHeader("Your folders") {
        if (state.isScanning) {
            CircularProgressIndicator(color = DesktopColors.TextSecondary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Text("Scanning…", style = DesktopType.caption, color = DesktopColors.TextSecondary)
        }
        DesktopButton(
            "Rescan",
            onClick = onRescan,
            kind = DcButtonKind.Tertiary,
            icon = DesktopIcons.Refresh,
            enabled = !state.isScanning && state.folders.isNotEmpty(),
            small = true
        )
    }
    if (state.folders.isEmpty()) {
        Text("No local folders yet.", style = DesktopType.body, color = DesktopColors.TextSecondary)
    } else {
        DesktopGroup {
            state.folders.forEachIndexed { index, folder ->
                if (index > 0) DesktopGroupDivider()
                FolderRow(folder, onRemove = { removing = folder })
            }
        }
    }

    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesktopBanner(
            "Use the same layout as on Drive: a Films and a Series folder inside the folder you add " +
                "(Films/, Series/<Show>/Season 1/)."
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DesktopButton("How to set up your folders", onClick = onOpenHelp, kind = DcButtonKind.Tertiary, icon = DesktopIcons.HelpOutline, small = true)
        }
    }

    removing?.let { folder ->
        DesktopConfirmDialog(
            title = "Remove \"${folderName(folder.path)}\"?",
            text = "Its videos leave your library. Nothing is deleted from this computer, and your watch history is kept.",
            confirmLabel = "Remove",
            onConfirm = {
                removing = null
                onRemove(folder.path)
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun FolderRow(folder: LocalFolderStatus, onRemove: () -> Unit) {
    DesktopListRow(
        folderName(folder.path),
        supporting = listOfNotNull(middleEllipsis(folder.path, 72), folderNotes(folder)).joinToString("\n"),
        icon = if (folder.isAvailable) DesktopIcons.Folder else DesktopIcons.Warning,
        trailing = {
            val (chip, kind) = folderChip(folder)
            DesktopStatusChip(chip, kind = kind)
            DesktopButton("Remove", onClick = onRemove, kind = DcButtonKind.Destructive, small = true)
        }
    )
}

/** The folder's chip: how many videos, or why there is no count. */
internal fun folderChip(folder: LocalFolderStatus): Pair<String, DcStatusKind> = when {
    !folder.isAvailable -> "Not found" to DcStatusKind.Warning
    folder.videoCount == null -> "Not scanned yet" to DcStatusKind.Neutral
    else -> (if (folder.videoCount == 1) "1 video" else "${folder.videoCount} videos") to DcStatusKind.Neutral
}

/** What else is worth knowing about the folder, or null. */
internal fun folderNotes(folder: LocalFolderStatus): String? = when {
    !folder.isAvailable -> "Plug in the drive, or remove the folder"
    else -> listOfNotNull(
        folder.linkedToDrive?.takeIf { it > 0 }?.let { "$it also on Drive" },
        folder.possibleDuplicates?.takeIf { it > 0 }?.let { "$it possible duplicate${if (it == 1) "" else "s"} kept separate" }
    ).joinToString(" · ").ifEmpty { null }
}
