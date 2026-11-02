package com.drivecinema.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.database.entity.ImportQueueEntity
import com.drivecinema.app.core.database.entity.ImportState
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.desktop.auth.WriteAccessState
import com.drivecinema.desktop.importer.ImportFolderKind
import com.drivecinema.desktop.importer.ImportStatus
import com.drivecinema.desktop.ui.design.DesktopBanner
import com.drivecinema.desktop.ui.design.DesktopButton
import com.drivecinema.desktop.ui.design.DesktopConfirmDialog
import com.drivecinema.desktop.ui.design.DesktopGroup
import com.drivecinema.desktop.ui.design.DesktopGroupDivider
import com.drivecinema.desktop.ui.design.DesktopListRow
import com.drivecinema.desktop.ui.design.DesktopSectionHeader
import com.drivecinema.desktop.ui.design.DesktopStatusChip

/** Everything the import screen can do; the screen itself holds no logic. */
class ImportActions(
    val onGrantWriteAccess: () -> Unit,
    val onRemoveWriteAccess: () -> Unit,
    val onChooseFolder: (ImportFolderKind) -> Unit,
    val onClearFolder: (ImportFolderKind) -> Unit,
    val onPause: (Boolean) -> Unit,
    val onResumeAfterStop: () -> Unit,
    val onRetry: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onUploadAllSkipped: () -> Unit,
    val onClearFinished: () -> Unit
)

/**
 * Task I.1: import local films and episodes into the Drive library. Task P.6: Allow uploads as the page's primary
 * action until it is granted, then a status row with Remove write access (asks first: it signs you out); the watched
 * folders with the watcher's chip and Pause; Change… and Stop as text buttons; paths and file names shortened in the
 * middle; queue rows with a state chip and one action (the seven equal outlined buttons are gone).
 */
@Composable
fun ImportScreen(
    status: ImportStatus,
    writeAccess: WriteAccessState,
    entries: List<ImportQueueEntity>,
    errorMessage: String?,
    actions: ImportActions
) {
    var confirmRemoveAccess by remember { mutableStateOf(false) }
    val canAskForAccess = status.hasWriteAccess == false && writeAccess is WriteAccessState.Idle
    PageHeader(
        "Import to Drive",
        "New videos copied into your import folders are uploaded to ${status.libraryName?.let { "\"$it\"" } ?: "your Drive library"} " +
            "once copying has finished: films into Films/, episodes into Series/<Show>/Season NN/. Your phone and TV see " +
            "them after a sync. Local files are only read: never moved, renamed or deleted."
    ) {
        if (canAskForAccess) DesktopButton("Allow uploads to Drive", onClick = actions.onGrantWriteAccess, icon = DesktopIcons.CloudUpload)
    }

    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (status.hasWriteAccess) {
            null -> Text("Checking Drive access…", style = DesktopType.body, color = DesktopColors.TextSecondary)
            true -> DesktopGroup {
                DesktopListRow(
                    "Uploads to Drive are allowed",
                    supporting = "Removing write access signs you out of DriveCinema on this computer (Google can only revoke " +
                        "the whole sign-in); sign in again afterwards.",
                    icon = DesktopIcons.CheckCircle,
                    trailing = {
                        DesktopButton("Remove write access", onClick = { confirmRemoveAccess = true }, kind = DcButtonKind.Destructive, small = true)
                    }
                )
            }
            false -> {
                Text(
                    "Importing needs permission to add files to your Google Drive. Google's page says \"see, edit, create " +
                        "and delete\": DriveCinema only creates folders and uploads new files, and only from this computer. " +
                        "Google will also show that the app isn't verified; continue with Advanced → Go to DriveCinema.",
                    style = DesktopType.body,
                    color = DesktopColors.TextSecondary
                )
                when (writeAccess) {
                    is WriteAccessState.WaitingForBrowser -> {
                        DesktopBanner("Waiting for your answer in the browser…")
                        if (!writeAccess.browserOpened) writeAccess.authorizationUrl?.let {
                            Text("Open this link if no browser opened:", style = DesktopType.caption, color = DesktopColors.TextSecondary)
                            SelectionContainer { Text(it, style = DesktopType.caption, color = DesktopColors.Focus) }
                        }
                    }
                    is WriteAccessState.Idle -> writeAccess.errorMessage?.let { DesktopBanner(it, kind = DcBannerKind.Error) }
                }
            }
        }
    }

    DesktopSectionHeader("Watched folders") {
        val (chip, kind) = watcherChip(status.isRunning, status.pausedByUser, status.stoppedReason)
        DesktopStatusChip(chip, kind = kind)
        if (status.stoppedReason != null) {
            DesktopButton("Resume", onClick = actions.onResumeAfterStop, kind = DcButtonKind.Secondary, icon = DesktopIcons.PlayArrow, small = true)
        } else {
            DesktopButton(
                if (status.pausedByUser) "Resume" else "Pause",
                onClick = { actions.onPause(!status.pausedByUser) },
                kind = DcButtonKind.Secondary,
                icon = if (status.pausedByUser) DesktopIcons.PlayArrow else DesktopIcons.Pause,
                small = true
            )
        }
    }
    status.stoppedReason?.let { DesktopBanner(it, Modifier.padding(bottom = 10.dp), kind = DcBannerKind.Warning) }
    if (status.stoppedReason == null && !status.isRunning && !status.pausedByUser) {
        val why = when {
            status.hasWriteAccess != true -> "Not running: allow uploads first."
            status.moviesFolder == null && status.seriesFolder == null -> "Not running: choose a folder."
            else -> null
        }
        why?.let { Text(it, style = DesktopType.caption, color = DesktopColors.TextSecondary, modifier = Modifier.padding(bottom = 10.dp)) }
    }
    DesktopGroup {
        WatchedFolderRow("Films folder", status.moviesFolder, ImportFolderKind.MOVIES, actions)
        DesktopGroupDivider()
        WatchedFolderRow("Series folder", status.seriesFolder, ImportFolderKind.SERIES, actions)
    }
    errorMessage?.let { DesktopBanner(it, Modifier.padding(top = 10.dp), kind = DcBannerKind.Warning) }

    DesktopSectionHeader("Queue") {
        val skipped = entries.count { it.state == ImportState.SKIPPED }
        if (skipped > 0) {
            DesktopButton("Upload existing ($skipped)", onClick = actions.onUploadAllSkipped, kind = DcButtonKind.Secondary, small = true)
        }
        if (entries.any { it.state == ImportState.DONE || it.state == ImportState.CANCELLED }) {
            DesktopButton("Clear finished", onClick = actions.onClearFinished, kind = DcButtonKind.Tertiary, small = true)
        }
    }
    if (entries.isEmpty()) {
        Text("Nothing imported yet.", style = DesktopType.body, color = DesktopColors.TextSecondary)
    } else {
        DesktopGroup {
            entries.forEachIndexed { index, entry ->
                if (index > 0) DesktopGroupDivider()
                QueueRow(entry, actions)
            }
        }
    }

    if (confirmRemoveAccess) {
        DesktopConfirmDialog(
            title = "Remove write access?",
            text = "Google can only take back the whole sign-in, so DriveCinema signs out on this computer. Sign in again " +
                "for the normal read-only access; nothing on Drive changes.",
            confirmLabel = "Remove and sign out",
            onConfirm = {
                confirmRemoveAccess = false
                actions.onRemoveWriteAccess()
            },
            onDismiss = { confirmRemoveAccess = false }
        )
    }
}

@Composable
private fun WatchedFolderRow(label: String, path: String?, kind: ImportFolderKind, actions: ImportActions) {
    DesktopListRow(
        label,
        supporting = path?.let { middleEllipsis(it, 64) } ?: "Not chosen",
        icon = DesktopIcons.Folder,
        trailing = {
            DesktopButton(if (path == null) "Choose…" else "Change…", onClick = { actions.onChooseFolder(kind) }, kind = DcButtonKind.Tertiary, small = true)
            if (path != null) {
                DesktopButton("Stop", onClick = { actions.onClearFolder(kind) }, kind = DcButtonKind.Tertiary, small = true)
            }
        }
    )
}

/** One file in the queue: its name and destination, a state chip, one action; a progress bar while it uploads. */
@Composable
private fun QueueRow(entry: ImportQueueEntity, actions: ImportActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(DesktopIcons.Movie, contentDescription = null, tint = DesktopColors.TextSecondary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(middleEllipsis(entry.fileName, 56), style = DesktopType.rowTitle, color = DesktopColors.TextPrimary, maxLines = 1)
                queueDetails(entry)?.let {
                    Text(it, style = DesktopType.caption, color = DesktopColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            val (chip, kind) = importChip(entry.state)
            DesktopStatusChip(chip, kind = kind)
            importRowAction(entry.state)?.let { action ->
                DesktopButton(
                    action.label,
                    onClick = {
                        when (action) {
                            ImportRowAction.CANCEL -> actions.onCancel(entry.importKey)
                            ImportRowAction.RETRY, ImportRowAction.UPLOAD -> actions.onRetry(entry.importKey)
                        }
                    },
                    kind = DcButtonKind.Tertiary,
                    small = true
                )
            }
        }
        if (entry.state == ImportState.UPLOADING && entry.sizeBytes > 0) {
            LinearProgressIndicator(
                progress = { (entry.bytesUploaded.toFloat() / entry.sizeBytes).coerceIn(0f, 1f) },
                color = DesktopColors.Accent,
                trackColor = DesktopColors.SurfaceRaised,
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Where the file goes and what happened to it, or null. */
internal fun queueDetails(entry: ImportQueueEntity): String? = listOfNotNull(
    "Already in the folder when you chose it".takeIf { entry.state == ImportState.SKIPPED },
    entry.targetFolderPath?.let { "→ ${middleEllipsis(it, 60)}" },
    entry.message?.takeIf { it.isNotEmpty() }
).joinToString(" · ").ifEmpty { null }
