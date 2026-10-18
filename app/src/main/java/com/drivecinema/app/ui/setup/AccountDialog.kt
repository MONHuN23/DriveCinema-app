package com.drivecinema.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.net.Uri
import coil.compose.AsyncImage
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.ui.mobile.home.AccountUiState

@Composable
fun AccountDialog(
    account: AccountUiState,
    onSignOutClick: (() -> Unit)? = null,
    onSwitchLibraryClick: (() -> Unit)? = null,
    onRefreshLibraryClick: (() -> Unit)? = null,
    isRefreshingLibrary: Boolean = false,
    onOpenGuideClick: (() -> Unit)? = null,
    pendingDuplicateCount: Int = 0,
    onReviewDuplicatesClick: (() -> Unit)? = null,
    onAddLocalFolderClick: (() -> Unit)? = null,
    savedLocalFolders: List<LocalFolder> = emptyList(),
    onRemoveLocalFolderClick: ((Uri) -> Unit)? = null,
    isTvDevice: Boolean = false,
    isTvStorageSupported: Boolean = true,
    onTvStorageUnsupportedClick: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val closeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocus()
    }
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0xFF2A2E39), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // Scrollable so Sign Out / Close stay reachable (D-pad focus scrolls) with many saved folders.
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Account",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF0F2F5)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Avatar or Initials Circle
                if (!account.photoLink.isNullOrEmpty()) {
                    AsyncImage(
                        model = account.photoLink,
                        contentDescription = "User Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFF4D88FF), CircleShape)
                    )
                } else {
                    val initial = (account.displayName?.firstOrNull() ?: account.email?.firstOrNull() ?: 'U').uppercaseChar()
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4D88FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (!account.displayName.isNullOrEmpty()) {
                    Text(
                        text = account.displayName,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF0F2F5),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!account.email.isNullOrEmpty()) {
                    Text(
                        text = account.email,
                        fontSize = 14.sp,
                        color = Color(0xFF9AA0A6),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connection status chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF13151B))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (account.isConnected) Color(0xFF34A853) else Color(0xFF9AA0A6))
                    )
                    Text(
                        text = if (account.isConnected) "Google Drive Connected" else "Not Connected",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (account.isConnected) Color(0xFF81C995) else Color(0xFF9AA0A6)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Re-sync the saved Drive folder without restarting the app (Task H.1)
                if (onRefreshLibraryClick != null) {
                    DialogActionButton(
                        text = if (isRefreshingLibrary) "Refreshing library…" else "Refresh Library",
                        onClick = { if (!isRefreshingLibrary) onRefreshLibraryClick() }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Switch library folder (keeps the current Google session)
                if (onSwitchLibraryClick != null) {
                    DialogActionButton(
                        text = "Switch Library Folder",
                        onClick = onSwitchLibraryClick
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (onReviewDuplicatesClick != null && pendingDuplicateCount > 0) {
                    DialogActionButton(
                        text = "Review possible duplicates ($pendingDuplicateCount)",
                        onClick = onReviewDuplicatesClick
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (onOpenGuideClick != null) {
                    DialogActionButton(
                        text = "Setup Guide",
                        onClick = onOpenGuideClick
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Local media folder (SAF on phone / TV storage check)
                if (isTvDevice && !isTvStorageSupported) {
                    DialogActionButton(
                        text = "Local Storage (Not Supported)",
                        onClick = { onTvStorageUnsupportedClick?.invoke() }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Local folder selection is not supported by this TV firmware",
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                } else if (onAddLocalFolderClick != null) {
                    DialogActionButton(
                        text = "Add Local Folder",
                        onClick = onAddLocalFolderClick
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Saved local folders with D-pad reachable removal
                if (savedLocalFolders.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        savedLocalFolders.forEach { folder ->
                            // Keyed so per-row focus/interaction state follows the folder, not its position.
                            key(folder.uri) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF13151B))
                                        .border(1.dp, Color(0xFF2A2E39), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(text = "📁", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = folder.displayName,
                                            color = Color(0xFFF0F2F5),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (onRemoveLocalFolderClick != null) {
                                        val removeInteractionSource = remember { MutableInteractionSource() }
                                        val isRemoveFocused by removeInteractionSource.collectIsFocusedAsState()

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isRemoveFocused) Color(0xFFB71C1C) else Color(0x33E50914))
                                                .border(
                                                    width = if (isRemoveFocused) 2.dp else 1.dp,
                                                    color = if (isRemoveFocused) Color.White else Color(0xFFE50914),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable(
                                                    interactionSource = removeInteractionSource,
                                                    indication = null,
                                                    onClick = { onRemoveLocalFolderClick(folder.uri) }
                                                )
                                                .focusable(interactionSource = removeInteractionSource)
                                                .onKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                                        (keyEvent.key == Key.DirectionCenter ||
                                                         keyEvent.key == Key.Enter ||
                                                         keyEvent.key == Key.NumPadEnter)
                                                    ) {
                                                        onRemoveLocalFolderClick(folder.uri)
                                                        true
                                                    } else false
                                                }
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Remove",
                                                color = if (isRemoveFocused) Color.White else Color(0xFFFF6B6B),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Videos in these folders appear in your library and play from this device",
                            color = Color(0xFF81C995),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Actions: Sign out (if provided) & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onSignOutClick != null) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused = interactionSource.collectIsFocusedAsState().value

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isFocused) Color(0xFFB71C1C) else Color(0x33E50914))
                                .border(if (isFocused) 2.dp else 1.dp, if (isFocused) Color.White else Color(0xFFE50914), RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = onSignOutClick
                                )
                                .focusable(interactionSource = interactionSource)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.key == Key.DirectionCenter ||
                                         keyEvent.key == Key.Enter ||
                                         keyEvent.key == Key.NumPadEnter)
                                    ) {
                                        onSignOutClick()
                                        true
                                    } else false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign Out",
                                color = if (isFocused) Color.White else Color(0xFFFF6B6B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    val closeInteractionSource = remember { MutableInteractionSource() }
                    val isCloseFocused = closeInteractionSource.collectIsFocusedAsState().value

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCloseFocused) Color(0xFF353A47) else Color(0xFF2A2E39))
                            .border(if (isCloseFocused) 2.dp else 1.dp, if (isCloseFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                            .focusRequester(closeFocusRequester)
                            .clickable(
                                interactionSource = closeInteractionSource,
                                indication = null,
                                onClick = onDismissRequest
                            )
                            .focusable(interactionSource = closeInteractionSource)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.DirectionCenter ||
                                     keyEvent.key == Key.Enter ||
                                     keyEvent.key == Key.NumPadEnter)
                                ) {
                                    onDismissRequest()
                                    true
                                } else false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Close",
                            color = Color(0xFFF0F2F5),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0xFF2B4C8C) else Color(0x334D88FF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF4D88FF),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter ||
                     keyEvent.key == Key.Enter ||
                     keyEvent.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.White else Color(0xFF8AB4FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

