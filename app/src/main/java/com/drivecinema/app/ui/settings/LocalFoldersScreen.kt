package com.drivecinema.app.ui.settings

import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.core.local.LocalFolderLabels
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButton
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcConfirmDialog
import com.drivecinema.app.ui.design.DcGroup
import com.drivecinema.app.ui.design.DcGroupDivider
import com.drivecinema.app.ui.design.DcGroupTitle
import com.drivecinema.app.ui.design.DcIconButton
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcTopBar
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.setup.SettingsLabels

/** What a TV without a system folder picker can't do, and why (was the TV's "Local Storage Not Supported" dialog). */
const val TV_NO_FOLDER_PICKER =
    "This TV can't add local folders: its firmware has no system folder picker (DocumentsUI), which Android needs to " +
        "give an app access to a folder. Your Drive library and PCs still work."

/** The list's add button once folders exist (the empty state's says [SettingsLabels.ADD_FOLDER]). */
const val ADD_ANOTHER_FOLDER = "Add another folder"
const val HOW_TO_NAME_FILES = "How to name files"

/**
 * Task P.2: the phone's or TV's saved local folders (they were listed inside the Account popup). Videos in them join
 * the library and play from this device; removing a folder asks first and keeps the watch history.
 *
 * UI polish: with no folders, an empty state shows the Films/Series layout and one Add folder button; with folders,
 * each row says where the folder is and what the last scan found ([videoCounts], by folder URI; a folder the scan
 * hasn't read yet says "Scanning…" while [isScanning]).
 */
@Composable
fun LocalFoldersScreen(
    folders: List<LocalFolder>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    videoCounts: Map<String, Int> = emptyMap(),
    isScanning: Boolean = false,
    onOpenGuide: (() -> Unit)? = null
) {
    val form = DcTheme.formFactor
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    if (form.isTv) {
        // The remote starts on the first thing it can do here: Add folder, else the first Remove. They are lazy items,
        // which can be placed a frame after this effect starts (as on the series page, F-37): try on the next frames.
        LaunchedEffect(Unit) {
            repeat(5) {
                if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                withFrameNanos { }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(DcColors.Canvas), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = form.sideMargin, end = form.sideMargin, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "top") { DcTopBar(SettingsLabels.LOCAL_FOLDERS, onBack = onBack) }
            if (folders.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        canAdd = canAdd,
                        onAdd = onAdd,
                        onOpenGuide = onOpenGuide,
                        addModifier = Modifier.focusRequester(firstFocus).testTag("local_folders_add")
                    )
                }
            } else {
                item(key = "intro") {
                    Text(
                        "Videos in these folders join your library and play from this ${deviceName()}, even offline.",
                        style = DcTheme.type.body,
                        color = DcColors.Text2
                    )
                }
                item(key = "list") {
                    Column {
                        DcGroupTitle("On this ${deviceName()} · ${folders.size}")
                        DcGroup {
                            folders.forEachIndexed { index, folder ->
                                if (index > 0) DcGroupDivider()
                                val details = listOfNotNull(
                                    readablePath(folder.uri),
                                    LocalFolderLabels.videoCount(videoCounts[folder.uri.toString()], isScanning)
                                )
                                DcListRow(
                                    folder.displayName,
                                    supporting = details.joinToString("\n"),
                                    icon = AppIcons.Folder,
                                    trailing = {
                                        DcIconButton(
                                            AppIcons.DeleteOutline,
                                            contentDescription = "Remove ${folder.displayName}",
                                            onClick = { removing = folder.uri.toString() },
                                            modifier = if (index == 0 && !canAdd) Modifier.focusRequester(firstFocus) else Modifier
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                item(key = "add") {
                    if (canAdd) {
                        DcButton(
                            ADD_ANOTHER_FOLDER,
                            onClick = onAdd,
                            kind = DcButtonKind.Secondary,
                            icon = AppIcons.Add,
                            modifier = Modifier.fillMaxWidth().focusRequester(firstFocus).testTag("local_folders_add")
                        )
                    } else {
                        DcBanner(TV_NO_FOLDER_PICKER, kind = DcBannerKind.Warning)
                    }
                }
                item(key = "note") {
                    Text(
                        "Removing a folder hides its videos; nothing is deleted and your watch history is kept.",
                        style = DcTheme.type.caption,
                        color = DcColors.Text3,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    val folderToRemove = folders.find { it.uri.toString() == removing }
    if (folderToRemove != null) {
        DcConfirmDialog(
            title = "Remove \"${folderToRemove.displayName}\"?",
            text = "Its videos leave your library. Nothing is deleted from the device, and your watch history is kept.",
            confirmLabel = "Remove",
            onConfirm = {
                removing = null
                onRemove(folderToRemove.uri)
            },
            onDismiss = { removing = null }
        )
    }
}

@Composable
private fun deviceName(): String = if (DcTheme.formFactor.isTv) "TV" else "phone"

/** "Internal storage › Movies" for a storage tree URI; null for anything else (no raw ids on screen). */
private fun readablePath(uri: Uri): String? =
    runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()?.let(LocalFolderLabels::readablePath)

@Composable
private fun EmptyState(canAdd: Boolean, onAdd: () -> Unit, onOpenGuide: (() -> Unit)?, addModifier: Modifier) {
    val type = DcTheme.type
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("local_folders_empty"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The TV's 540 dp screen has no room for the picture above the Add button; the phone has.
            if (!DcTheme.formFactor.isTv) {
                Box(
                    Modifier.size(80.dp).background(DcColors.Surface1, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(AppIcons.Smartphone, contentDescription = null, tint = DcColors.Text2, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
            Text("Play videos stored on this ${deviceName()}", style = type.title, color = DcColors.Text1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "They join your library next to Drive and play even offline. Pick a folder laid out like your Drive library:",
                style = type.body,
                color = DcColors.Text2,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            LayoutExample()
            Spacer(Modifier.height(24.dp))
            if (canAdd) {
                DcButton(SettingsLabels.ADD_FOLDER, onClick = onAdd, icon = AppIcons.Add, modifier = addModifier.fillMaxWidth())
            } else {
                DcBanner(TV_NO_FOLDER_PICKER, kind = DcBannerKind.Warning)
            }
            if (onOpenGuide != null) {
                Spacer(Modifier.height(8.dp))
                DcButton(HOW_TO_NAME_FILES, onClick = onOpenGuide, kind = DcButtonKind.Tertiary)
            }
        }
    }
}

/** The folder you pick → Films and Series, with one example file each. */
@Composable
private fun LayoutExample() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DcColors.Surface1, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExampleLine(AppIcons.Folder, "The folder you pick", example = null, indent = false)
        ExampleLine(AppIcons.Folder, "Films", example = "Dune (2021).mkv", indent = true)
        ExampleLine(AppIcons.Folder, "Series", example = "Severance / Season 1 / S01E01.mkv", indent = true)
    }
}

@Composable
private fun ExampleLine(icon: ImageVector, name: String, example: String?, indent: Boolean) {
    val type = DcTheme.type
    Row(
        modifier = Modifier.padding(start = if (indent) 28.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = if (example == null) Alignment.CenterVertically else Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = if (indent) DcColors.Text3 else DcColors.Text2, modifier = Modifier.size(20.dp))
        Column {
            Text(
                name,
                style = type.body.copy(fontWeight = if (indent) FontWeight.Medium else FontWeight.SemiBold),
                color = DcColors.Text1
            )
            if (example != null) Text(example, style = type.caption, color = DcColors.Text3)
        }
    }
}
