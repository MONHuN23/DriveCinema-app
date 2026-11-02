package com.drivecinema.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DcTheme
import com.drivecinema.app.ui.design.DcFocusRoom
import com.drivecinema.app.ui.design.DcFocusRoomScrolling
import com.drivecinema.app.ui.design.DcBanner
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcChevron
import com.drivecinema.app.ui.design.DcListRow
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.icons.AppIcons
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.SkipIntroTexts
import com.drivecinema.app.ui.tv.TvInitialFocus
import com.drivecinema.app.ui.tv.TvReturnFocus
import com.drivecinema.app.ui.tv.rememberTvReturnFocus

/** The TV Settings' left pane. */
enum class SettingsCategory(val label: String, val description: String) {
    Library(SettingsLabels.LIBRARY, "Your Google Drive library folder"),
    Sources(SettingsLabels.SOURCES, "Where videos come from besides Drive"),
    Playback(SettingsLabels.PLAYBACK, "How episodes play"),
    HelpAndAbout(SettingsLabels.HELP_AND_ABOUT, "Guides and credits"),
    Account(SettingsLabels.ACCOUNT, "The Google account this TV uses");

    val icon: ImageVector
        get() = when (this) {
            Library -> AppIcons.VideoLibrary
            Sources -> AppIcons.Storage
            Playback -> AppIcons.FastForward
            HelpAndAbout -> AppIcons.HelpOutline
            Account -> AppIcons.Person
        }
}

/** One focusable row of the right pane; [key] lets Back from the screen it opened put the focus on it again. */
private class PaneRow(val key: String, val content: @Composable (Modifier) -> Unit)

/**
 * Task P.2: the TV's Settings (was the Account popup), two panes (decision D-P4): categories on the left, that
 * category's rows on the right. The first category gets the focus; moving through the categories shows each one's rows;
 * Right enters the rows, Left or Back returns to the category; Back from the categories returns to Home, whose account
 * chip gets the focus again. Back from a screen opened here puts the focus on the row that opened it.
 */
@Composable
fun TvSettingsScreen(state: SettingsScreenState, actions: SettingsActions, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(SettingsCategory.Library) }
    val categoryFocus = remember { SettingsCategory.entries.associateWith { FocusRequester() } }
    val firstRowFocus = remember { FocusRequester() }
    val returnFocus = rememberTvReturnFocus()
    var rowsHaveFocus by remember { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    TvInitialFocus(returnFocus) { runCatching { categoryFocus.getValue(selected).requestFocus() } }
    BackHandler(enabled = rowsHaveFocus) { runCatching { categoryFocus.getValue(selected).requestFocus() } }

    val rows = paneRows(selected, state, actions, returnFocus, onSignOut = { confirmSignOut = true })

    DcFocusRoomScrolling {
        Row(modifier = modifier.fillMaxSize().background(DcColors.Canvas)) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(DcColors.Surface1)
                    .padding(start = 48.dp, end = 20.dp, top = 34.dp, bottom = 27.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsAvatar(state.account, size = 56.dp)
                    AccountText(state.account, Modifier.weight(1f))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsCategory.entries.forEach { category ->
                        // The selection is drawn outside the row so the row's focus look stays the same node.
                        DcListRow(
                            category.label,
                            icon = category.icon,
                            onClick = { if (rows.isNotEmpty()) runCatching { firstRowFocus.requestFocus() } },
                            modifier = Modifier
                                .background(
                                    if (category == selected) DcColors.Surface3 else Color.Transparent,
                                    RoundedCornerShape(DcTokens.Radius.CONTROL.dp)
                                )
                                .focusRequester(categoryFocus.getValue(category))
                                .onFocusChanged { if (it.isFocused) selected = category }
                                .focusProperties { right = if (rows.isNotEmpty()) firstRowFocus else FocusRequester.Cancel }
                                .testTag("tv_settings_category_${category.name}")
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { rowsHaveFocus = it.hasFocus }
                    .focusGroup()
                    .padding(start = 40.dp, end = 48.dp, top = 38.dp, bottom = 27.dp)
            ) {
                Text(selected.label, style = DcTheme.type.headline, color = DcColors.Text1)
                Text(selected.description, style = DcTheme.type.body, color = DcColors.Text2)
                Column(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .testTag("tv_settings_rows")
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = DcFocusRoom),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rows.forEachIndexed { index, row ->
                        key(selected, row.key) {
                            row.content(
                                Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
                                    .then(returnFocus.modifierFor(row.key))
                                    .focusProperties {
                                        left = categoryFocus.getValue(selected)
                                        if (index == 0) up = FocusRequester.Cancel
                                        if (index == rows.lastIndex) down = FocusRequester.Cancel
                                    }
                                    .testTag("tv_settings_row_${row.key}")
                            )
                        }
                    }
                    PaneNote(selected, state)
                }
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

private fun paneRows(
    category: SettingsCategory,
    state: SettingsScreenState,
    actions: SettingsActions,
    returnFocus: TvReturnFocus,
    onSignOut: () -> Unit
): List<PaneRow> {
    /** A row that opens another screen: Back from it focuses the row again. */
    fun opening(key: String, open: () -> Unit): () -> Unit = {
        returnFocus.opened(key)
        open()
    }
    val card = DcColors.Surface1
    return when (category) {
        SettingsCategory.Library -> buildList {
            if (state.account.isConnected) {
                add(PaneRow("refresh") { RefreshLibraryRow(state.isRefreshingLibrary, actions.refreshLibrary, it, container = card) })
                add(PaneRow("switch") {
                    DcListRow(
                        SettingsLabels.SWITCH_LIBRARY_FOLDER,
                        supporting = "Pick another Drive folder as your library",
                        icon = AppIcons.SwapHoriz,
                        onClick = opening("switch", actions.switchLibraryFolder),
                        container = card,
                        modifier = it,
                        trailing = { DcChevron() }
                    )
                })
            }
            if (state.pendingDuplicateCount > 0) {
                add(PaneRow("duplicates") {
                    DuplicatesRow(state.pendingDuplicateCount, opening("duplicates", actions.reviewDuplicates), it, container = card)
                })
            }
            actions.openNamingHelper?.let { open ->
                add(PaneRow("naming") { NamingHelperRow(opening("naming", open), it, container = card) })
            }
        }

        SettingsCategory.Sources -> buildList {
            if (state.canAddLocalFolders) {
                add(PaneRow("local") {
                    DcListRow(
                        SettingsLabels.LOCAL_FOLDERS,
                        supporting = localFolderSummary(state.localFolderCount, canAdd = true, isTv = true),
                        icon = AppIcons.Storage,
                        onClick = opening("local", actions.openLocalFolders),
                        container = card,
                        modifier = it,
                        trailing = { DcChevron() }
                    )
                })
            }
            add(PaneRow("pcs") {
                DcListRow(
                    SettingsLabels.MY_PCS,
                    supporting = state.pcSummary,
                    icon = AppIcons.DesktopWindows,
                    onClick = opening("pcs", actions.openPcs),
                    container = card,
                    modifier = it,
                    trailing = { DcChevron() }
                )
            })
            // Milestone D: the TV links phones once it is signed in (it plays from its own library).
            if (state.account.isConnected) {
                add(PaneRow("linkedPhones") {
                    DcListRow(
                        SettingsLabels.LINKED_PHONES,
                        supporting = "Phones that can start Play on TV here",
                        icon = AppIcons.Tv,
                        onClick = actions.openLinkedDevices,
                        container = card,
                        modifier = it,
                        trailing = { DcChevron() }
                    )
                })
            }
        }

        SettingsCategory.Playback -> buildList {
            add(PaneRow("skipIntro") { SkipIntroRow(state.skipIntroEnabled, actions.setSkipIntro, it, container = card) })
            // Milestone Q: needs the Google sign-in, like Refresh.
            if (state.account.isConnected) {
                add(PaneRow("deviceSync") {
                    DeviceSyncRow(state.deviceSyncEnabled, state.deviceSyncStatus, actions.setDeviceSync, it, container = card)
                })
            }
        }

        SettingsCategory.HelpAndAbout -> listOf(
            PaneRow("help") {
                DcListRow(
                    SettingsLabels.HELP_CENTER,
                    supporting = "Setup, devices, sharing, problems",
                    icon = AppIcons.HelpOutline,
                    onClick = opening("help", actions.openHelp),
                    container = card,
                    modifier = it,
                    trailing = { DcChevron() }
                )
            },
            PaneRow("about") {
                DcListRow(
                    SettingsLabels.ABOUT,
                    icon = AppIcons.Info,
                    onClick = opening("about", actions.openAbout),
                    container = card,
                    modifier = it,
                    trailing = { DcChevron() }
                )
            }
        )

        SettingsCategory.Account -> listOf(
            PaneRow("signOut") {
                DcListRow(
                    SettingsLabels.SIGN_OUT,
                    icon = AppIcons.Logout,
                    destructive = true,
                    onClick = onSignOut,
                    container = card,
                    modifier = it
                )
            }
        )
    }
}

/** What a pane says below its rows (never focusable). */
@Composable
private fun PaneNote(category: SettingsCategory, state: SettingsScreenState) {
    when (category) {
        SettingsCategory.Sources -> if (!state.canAddLocalFolders) {
            DcBanner(TV_NO_FOLDER_PICKER, kind = DcBannerKind.Warning, modifier = Modifier.testTag("tv_settings_no_folder_picker"))
        }
        SettingsCategory.HelpAndAbout -> Text(SkipIntroTexts.ATTRIBUTION, style = DcTheme.type.caption, color = DcColors.Text3)
        SettingsCategory.Account -> Text(
            "Signing out removes this TV's sign-in only; your phone and other devices stay signed in, and the watch " +
                "history stays on this TV.",
            style = DcTheme.type.caption,
            color = DcColors.Text3
        )
        else -> Unit
    }
}
