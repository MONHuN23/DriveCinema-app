package com.drivecinema.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivecinema.app.ui.mobile.home.MobileHomeUiState
import kotlinx.coroutines.flow.StateFlow

/** Task P.2: the Settings entry of the back stack, the phone's list or the TV's two panes. */
@Composable
fun SettingsEntry(
    isTv: Boolean,
    homeState: StateFlow<MobileHomeUiState>,
    settings: SettingsUiState,
    actions: SettingsActions
) {
    val home by homeState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { actions.onOpen() }
    val state = SettingsScreenState(
        account = home.account,
        isRefreshingLibrary = settings.isRefreshingLibrary.value,
        pendingDuplicateCount = settings.pendingDuplicateCount.value,
        localFolderCount = settings.localFolders.value.size,
        pcSummary = pcSummary(settings.pcs.rows.value),
        skipIntroEnabled = settings.skipIntroEnabled.value,
        canAddLocalFolders = settings.canAddLocalFolders.value,
        deviceSyncEnabled = settings.deviceSyncEnabled.value,
        deviceSyncStatus = settings.deviceSyncStatus.value
    )
    if (isTv) TvSettingsScreen(state, actions) else SettingsScreen(state, actions)
}

/** Task P.2: the Local folders entry of the back stack. */
@Composable
fun LocalFoldersEntry(settings: SettingsUiState, actions: SettingsActions) {
    LocalFoldersScreen(
        folders = settings.localFolders.value,
        canAdd = settings.canAddLocalFolders.value,
        onAdd = actions.addLocalFolder,
        onRemove = actions.removeLocalFolder,
        onBack = actions.back,
        videoCounts = settings.localFolderVideoCounts.value,
        isScanning = settings.isScanningLocalFolders.value,
        onOpenGuide = actions.openFoldersHelp
    )
}

/** Task P.3: the My PCs entry of the back stack, the phone's list or the TV's two panes. */
@Composable
fun PcsEntry(isTv: Boolean, pcs: PcsUiState, actions: PcsActions) {
    LaunchedEffect(Unit) { actions.onOpen() }
    val state = PcsScreenState(
        rows = pcs.rows.value,
        discovered = pcs.discovered.value,
        isSearching = pcs.isSearching.value,
        isPairing = pcs.isPairing.value,
        isChecking = pcs.isChecking.value,
        notice = pcs.notice.value,
        pairedCount = pcs.pairedCount.value
    )
    if (isTv) TvPcsScreen(state, actions) else PcsScreen(state, actions)
}
