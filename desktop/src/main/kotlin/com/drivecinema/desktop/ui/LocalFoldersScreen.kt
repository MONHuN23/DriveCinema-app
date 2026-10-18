package com.drivecinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.desktop.local.LocalFolderStatus
import com.drivecinema.desktop.local.LocalFoldersState

@Composable
fun LocalFoldersScreen(
    state: LocalFoldersState,
    errorMessage: String?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(DesktopColors.Background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.width(720.dp).padding(vertical = 32.dp).verticalScroll(rememberScrollState())) {
            OutlinedButton(onClick = onBack) { Text("←  Back  (Esc)", color = DesktopColors.TextPrimary) }
            Spacer(Modifier.height(24.dp))
            Text("Local folders", color = DesktopColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Folders on this computer join your library, with the same layout as on Drive (Films/, Series/<Show>/Season 1/). " +
                    "When a title is both here and on Drive, the local copy plays — instantly and offline.",
                color = DesktopColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Accent)) {
                    Text("+  Add folder", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRescan, enabled = !state.isScanning && state.folders.isNotEmpty()) {
                    Text("↻  Rescan", color = DesktopColors.TextPrimary)
                }
                if (state.isScanning) {
                    CircularProgressIndicator(color = DesktopColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    Text("Scanning…", color = DesktopColors.TextSecondary, fontSize = 13.sp)
                }
            }
            (errorMessage ?: state.message)?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    it,
                    color = DesktopColors.TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                        .background(DesktopColors.Accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            if (state.folders.isEmpty()) {
                Text("No local folders yet.", color = DesktopColors.TextSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.folders.forEach { folder -> FolderRow(folder, onRemove = { onRemove(folder.path) }) }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: LocalFolderStatus, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(DesktopColors.Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (folder.isAvailable) Text("📁", fontSize = 18.sp)
        else Text("⚠", fontSize = 18.sp, color = androidx.compose.ui.graphics.Color(0xFFD97706))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.path, color = DesktopColors.TextPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(describe(folder), color = DesktopColors.TextSecondary, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onRemove) { Text("Remove", color = DesktopColors.TextPrimary) }
    }
}

private fun describe(folder: LocalFolderStatus): String = when {
    !folder.isAvailable -> "Not found — plug in the drive, or remove the folder"
    folder.videoCount == null -> "Not scanned yet"
    else -> buildString {
        append("${folder.videoCount} video${if (folder.videoCount == 1) "" else "s"}")
        folder.linkedToDrive?.takeIf { it > 0 }?.let { append(" · $it also on Drive") }
        folder.possibleDuplicates?.takeIf { it > 0 }?.let { append(" · $it possible duplicate${if (it == 1) "" else "s"} kept separate") }
    }
}
