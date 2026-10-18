package com.drivecinema.app.ui.setup

import com.drivecinema.app.core.model.FolderPickerSections
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecinema.app.core.model.DriveFolder

@Composable
fun FolderPickerScreen(
    folders: List<DriveFolder>,
    isLoading: Boolean,
    isValidating: Boolean,
    errorMessage: String? = null,
    onFolderSelected: (DriveFolder) -> Unit,
    onRefreshClick: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
    currentFolderId: String? = null,
    busyMessage: String? = null,
    onCancelClick: (() -> Unit)? = null,
    onOpenGuideClick: (() -> Unit)? = null
) {
    val sections = remember(folders) { FolderPickerSections.from(folders) }
    val firstItemFocusRequester = remember { FocusRequester() }
    val firstFolderId = (sections.mine + sections.shared).firstOrNull()?.id

    LaunchedEffect(firstFolderId, isLoading, isValidating) {
        if (firstFolderId != null && !isLoading && !isValidating) {
            try {
                firstItemFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // Not attached yet (e.g. list still laying out); focus stays where it is.
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header
            Text(
                text = "Choose your library",
                color = Color(0xFFF0F2F5),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick the Google Drive folder that contains your Films and Series folders.",
                color = Color(0xFF9AA0A6),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            // Scrolls sideways on narrow phones; D-pad focus brings off-screen buttons into view.
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onOpenGuideClick != null) {
                    FolderActionButton(text = "? Setup guide", onClick = onOpenGuideClick)
                }
                FolderActionButton(text = "↻ Refresh", onClick = onRefreshClick)
                if (onCancelClick != null) {
                    FolderActionButton(text = "Cancel", onClick = onCancelClick)
                }
                FolderActionButton(text = "Sign Out", onClick = onSignOutClick)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Error banner
            if (!errorMessage.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33E50914))
                        .border(1.dp, Color(0xFFE50914), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFFB4AB),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            when {
                isLoading || isValidating -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFE50914))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when {
                                    isValidating && busyMessage != null -> busyMessage
                                    isValidating -> "Validating folder structure..."
                                    else -> "Loading Google Drive folders..."
                                },
                                color = Color(0xFFB0B3B8),
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                sections.isEmpty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.widthIn(max = 440.dp)
                        ) {
                            Text(
                                text = "No folders found",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Create a folder named 'Streaming' in your Google Drive with 'Films' and 'Series' " +
                                    "folders inside, or ask the owner of a library to share their folder with this account. " +
                                    "Then tap Refresh.",
                                color = Color(0xFF9AA0A6),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FolderActionButton(text = "↻ Refresh Folders", onClick = onRefreshClick)
                                if (onOpenGuideClick != null) {
                                    FolderActionButton(text = "How to set up folders", onClick = onOpenGuideClick)
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (sections.mine.isNotEmpty()) {
                            item(key = "header_mine") { SectionHeader("Your Drive") }
                            items(sections.mine, key = { it.id }) { folder ->
                                FolderItemRow(
                                    folder = folder,
                                    isCurrent = folder.id == currentFolderId,
                                    onClick = { onFolderSelected(folder) },
                                    modifier = if (folder.id == firstFolderId) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                )
                            }
                        }
                        if (sections.shared.isNotEmpty()) {
                            item(key = "header_shared") { SectionHeader("Shared with you") }
                            items(sections.shared, key = { it.id }) { folder ->
                                FolderItemRow(
                                    folder = folder,
                                    isCurrent = folder.id == currentFolderId,
                                    onClick = { onFolderSelected(folder) },
                                    modifier = if (folder.id == firstFolderId) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0xFF8AB4FF),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun FolderItemRow(
    folder: DriveFolder,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val recommended = FolderPickerSections.isRecommended(folder)
    val subtitle = if (folder.ownedByMe) {
        "In your Google Drive"
    } else {
        "Shared by ${folder.ownerName ?: "another account"}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF2A2D35) else Color(0xFF16181D))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = when {
                    isFocused -> Color(0xFF4D88FF)
                    isCurrent -> Color(0xFF34A853)
                    else -> Color(0xFF22252C)
                },
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (folder.ownedByMe) Color(0x334D88FF) else Color(0x33A142F4)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = if (folder.ownedByMe) "📁" else "👥", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.name,
                    color = if (isFocused) Color.White else Color(0xFFF0F2F5),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (recommended) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(text = "Recommended", color = Color(0xFFFBBC04))
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF9AA0A6),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isCurrent) "✓ Current" else "Select →",
            color = when {
                isFocused -> Color(0xFF4D88FF)
                isCurrent -> Color(0xFF81C995)
                else -> Color(0xFF9AA0A6)
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FolderActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isFocused) Color(0xFF2A2D35) else Color(0xFF1E2128))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF4D88FF) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.White else Color(0xFFB0B3B8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
