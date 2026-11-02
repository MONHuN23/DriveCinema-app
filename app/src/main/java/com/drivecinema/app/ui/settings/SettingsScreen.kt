package com.drivecinema.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drivecinema.app.core.naming.NamingTexts
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcChevron
import com.drivecinema.app.ui.design.DcConfirmDialog
import com.drivecinema.app.ui.design.DcGroup
import com.drivecinema.app.ui.design.DcGroupDivider
import com.drivecinema.app.ui.design.DcGroupTitle
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcStatusChip
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcSwitchRow
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.design.DcTopBar
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.DeviceSyncTexts
import com.drivecinema.app.ui.setup.SkipIntroTexts

/**
 * Task P.2: the phone's Settings screen (was the Account popup), opened from the profile chip on Home: the account,
 * then Library, Sources, Playback and Help groups, and Sign out, which asks first (decision D-P2).
 */
@Composable
fun SettingsScreen(state: SettingsScreenState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
        DcTopBar(SettingsLabels.SETTINGS, onBack = actions.back)
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)
        ) {
            item(key = "account") { SettingsAccountCard(state.account) }

            val showRefresh = state.account.isConnected
            val showDuplicates = state.pendingDuplicateCount > 0
            val openNamingHelper = actions.openNamingHelper
            if (showRefresh || showDuplicates || openNamingHelper != null) {
                item(key = "library") {
                    DcGroupTitle(SettingsLabels.LIBRARY)
                    DcGroup {
                        if (showRefresh) {
                            RefreshLibraryRow(state.isRefreshingLibrary, actions.refreshLibrary)
                            DcGroupDivider()
                            DcListRow(
                                SettingsLabels.SWITCH_LIBRARY_FOLDER,
                                icon = AppIcons.SwapHoriz,
                                onClick = actions.switchLibraryFolder,
                                trailing = { DcChevron() }
                            )
                        }
                        if (showDuplicates) {
                            if (showRefresh) DcGroupDivider()
                            DuplicatesRow(state.pendingDuplicateCount, actions.reviewDuplicates)
                        }
                        if (openNamingHelper != null) {
                            if (showRefresh || showDuplicates) DcGroupDivider()
                            NamingHelperRow(openNamingHelper)
                        }
                    }
                }
            }

            item(key = "sources") {
                DcGroupTitle(SettingsLabels.SOURCES)
                DcGroup {
                    DcListRow(
                        SettingsLabels.LOCAL_FOLDERS,
                        supporting = localFolderSummary(state.localFolderCount, state.canAddLocalFolders, isTv = false),
                        icon = AppIcons.Storage,
                        onClick = actions.openLocalFolders,
                        trailing = { DcChevron() }
                    )
                    DcGroupDivider()
                    DcListRow(
                        SettingsLabels.MY_PCS,
                        supporting = state.pcSummary,
                        icon = AppIcons.DesktopWindows,
                        onClick = actions.openPcs,
                        trailing = { DcChevron() }
                    )
                    DcGroupDivider()
                    DcListRow(
                        SettingsLabels.PAIR_A_TV,
                        supporting = "Sign a TV in with this phone",
                        icon = AppIcons.Tv,
                        onClick = actions.pairTv,
                        trailing = { DcChevron() }
                    )
                    DcGroupDivider()
                    DcListRow(
                        SettingsLabels.MY_TVS,
                        supporting = "Play on TV: TVs this phone can send videos to",
                        icon = AppIcons.Tv,
                        onClick = actions.openLinkedDevices,
                        trailing = { DcChevron() }
                    )
                }
            }

            item(key = "playback") {
                DcGroupTitle(SettingsLabels.PLAYBACK)
                DcGroup {
                    SkipIntroRow(state.skipIntroEnabled, actions.setSkipIntro)
                    // Milestone Q: needs the Google sign-in, like Refresh.
                    if (state.account.isConnected) {
                        DcGroupDivider()
                        DeviceSyncRow(state.deviceSyncEnabled, state.deviceSyncStatus, actions.setDeviceSync)
                    }
                }
            }

            item(key = "help") {
                DcGroupTitle("Help")
                DcGroup {
                    DcListRow(
                        SettingsLabels.HELP_CENTER,
                        supporting = "Setup, devices, sharing, problems",
                        icon = AppIcons.HelpOutline,
                        onClick = actions.openHelp,
                        trailing = { DcChevron() }
                    )
                    DcGroupDivider()
                    DcListRow(SettingsLabels.ABOUT, icon = AppIcons.Info, onClick = actions.openAbout, trailing = { DcChevron() })
                }
            }

            item(key = "signOut") {
                DcGroup(Modifier.padding(top = 22.dp)) {
                    DcListRow(
                        SettingsLabels.SIGN_OUT,
                        icon = AppIcons.Logout,
                        destructive = true,
                        onClick = { confirmSignOut = true }
                    )
                }
            }

            item(key = "credits") {
                Text(
                    SkipIntroTexts.ATTRIBUTION,
                    style = DcTheme.type.caption.copy(fontSize = 11.sp),
                    color = DcColors.Text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).navigationBarsPadding()
                )
            }
        }
    }
    if (confirmSignOut) {
        SignOutDialog(
            onConfirm = {
                confirmSignOut = false
                actions.signOut()
            },
            onDismiss = { confirmSignOut = false }
        )
    }
}

@Composable
internal fun SignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DcConfirmDialog(
        title = "Sign out of DriveCinema?",
        text = SIGN_OUT_QUESTION,
        confirmLabel = SettingsLabels.SIGN_OUT,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** Refresh stays focusable while it runs (focus never vanishes from under the remote) but a second press waits. */
@Composable
internal fun RefreshLibraryRow(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, container: Color = Color.Transparent) {
    DcListRow(
        SettingsLabels.REFRESH_LIBRARY,
        supporting = if (isRefreshing) "Refreshing…" else "Reads your Drive folder again",
        icon = AppIcons.Refresh,
        onClick = { if (!isRefreshing) onRefresh() },
        container = container,
        modifier = modifier.testTag("settings_refresh"),
        trailing = if (isRefreshing) {
            { CircularProgressIndicator(color = DcColors.Text2, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
        } else null
    )
}

/** Milestone R: the naming helper (TMDB search and the names the app recognises); needs no sign-in. */
@Composable
internal fun NamingHelperRow(onOpen: () -> Unit, modifier: Modifier = Modifier, container: Color = Color.Transparent) {
    DcListRow(
        SettingsLabels.NAMING_HELPER,
        supporting = NamingTexts.HELPER_SUPPORTING,
        icon = AppIcons.Article,
        onClick = onOpen,
        container = container,
        modifier = modifier.testTag("settings_naming_helper"),
        trailing = { DcChevron() }
    )
}

@Composable
internal fun DuplicatesRow(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier, container: Color = Color.Transparent) {
    DcListRow(
        SettingsLabels.REVIEW_DUPLICATES,
        supporting = if (count == 1) "1 title to check" else "$count titles to check",
        icon = AppIcons.ContentCopy,
        onClick = onReview,
        container = container,
        modifier = modifier,
        trailing = {
            Box(
                Modifier.background(DcColors.Brand, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) { Text("$count", style = DcTheme.type.caption, color = DcColors.OnBrand) }
            DcChevron()
        }
    )
}

/** Milestone O's switch; the text says what is sent, word for word as before ([SkipIntroTexts.SWITCH_CAPTION]). */
@Composable
internal fun SkipIntroRow(enabled: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, container: Color = Color.Transparent) {
    DcSwitchRow(
        SettingsLabels.SKIP_INTRO,
        checked = enabled,
        onCheckedChange = onChange,
        supporting = SkipIntroTexts.SWITCH_CAPTION,
        icon = AppIcons.FastForward,
        container = container,
        modifier = modifier.testTag("settings_skip_intro_switch")
    )
}

/** Milestone Q's switch; under it what sync stores where, or how the last sync went. */
@Composable
internal fun DeviceSyncRow(
    enabled: Boolean,
    status: String?,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Color.Transparent
) {
    DcSwitchRow(
        SettingsLabels.SYNC_WITH_MY_DEVICES,
        checked = enabled,
        onCheckedChange = onChange,
        supporting = status ?: DeviceSyncTexts.SWITCH_CAPTION,
        icon = AppIcons.Refresh,
        container = container,
        modifier = modifier.testTag("settings_device_sync_switch")
    )
}

/** Avatar, name, e-mail and whether Google Drive is connected. */
@Composable
internal fun SettingsAccountCard(account: AccountUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DcColors.Surface1, RoundedCornerShape(DcTokens.Radius.CARD.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsAvatar(account, size = 52.dp)
        AccountText(account, Modifier.weight(1f))
    }
}

@Composable
internal fun AccountText(account: AccountUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        account.displayName?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = DcTheme.type.rowTitle, color = DcColors.Text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        account.email?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = DcTheme.type.caption, color = DcColors.Text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DcStatusChip(
            if (account.isConnected) "Google Drive connected" else "Not connected",
            kind = if (account.isConnected) DcStatusKind.Success else DcStatusKind.Neutral,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
internal fun SettingsAvatar(account: AccountUiState, size: Dp) {
    if (!account.photoLink.isNullOrEmpty()) {
        AsyncImage(
            model = account.photoLink,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        val initial = (account.displayName?.firstOrNull() ?: account.email?.firstOrNull() ?: 'U').uppercaseChar()
        Box(
            modifier = Modifier.size(size).background(DcColors.Surface3, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initial.toString(), style = DcTheme.type.title, color = DcColors.Text1)
        }
    }
}
