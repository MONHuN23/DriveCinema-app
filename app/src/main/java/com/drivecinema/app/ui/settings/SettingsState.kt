package com.drivecinema.app.ui.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.ui.mobile.home.AccountUiState

/**
 * Task P.2: the Settings screens' state (was the Account popup's, kept in `MainActivity`). The activity's refresh,
 * local-folder, PC and Skip Intro flows write it; [SettingsEntry] reads it.
 */
class SettingsUiState {
    val isRefreshingLibrary = mutableStateOf(false)
    val localFolders = mutableStateOf<List<LocalFolder>>(emptyList())
    /** Videos the last scan found in each local folder, by folder URI; a folder the scan couldn't read is missing. */
    val localFolderVideoCounts = mutableStateOf<Map<String, Int>>(emptyMap())
    val isScanningLocalFolders = mutableStateOf(false)
    val pendingDuplicateCount = mutableStateOf(0)
    /** Milestone O's "Skip intro" setting; read from its preference each time Settings opens. */
    val skipIntroEnabled = mutableStateOf(true)
    /** Task I.2: the PCs this device streams from, with their last known status; My PCs' state (Task P.3). */
    val pcs = PcsUiState()
    /** False on a TV whose firmware has no system folder picker (checked once at start). */
    val canAddLocalFolders = mutableStateOf(true)
    /** Milestone Q: "Sync with my devices" and its status line (null shows the caption); `DeviceSyncController` writes them. */
    val deviceSyncEnabled = mutableStateOf(false)
    val deviceSyncStatus = mutableStateOf<String?>(null)
}

/** What Settings shows at one moment, built from [SettingsUiState] and the signed-in account. */
@Immutable
data class SettingsScreenState(
    val account: AccountUiState,
    val isRefreshingLibrary: Boolean = false,
    val pendingDuplicateCount: Int = 0,
    val localFolderCount: Int = 0,
    val pcSummary: String = pcSummary(emptyList()),
    val skipIntroEnabled: Boolean = true,
    val canAddLocalFolders: Boolean = true,
    val deviceSyncEnabled: Boolean = false,
    val deviceSyncStatus: String? = null
)

/** What the Settings and Local folders screens ask of the activity. */
class SettingsActions(
    val refreshLibrary: () -> Unit,
    val switchLibraryFolder: () -> Unit,
    val reviewDuplicates: () -> Unit,
    val openLocalFolders: () -> Unit,
    val addLocalFolder: () -> Unit,
    val removeLocalFolder: (Uri) -> Unit,
    /** Local folders' "How to name files": the Help Center's folders topic; null hides the link. */
    val openFoldersHelp: (() -> Unit)? = null,
    val openPcs: () -> Unit,
    /** The phone's "Pair a TV" (the pairing dialog). */
    val pairTv: () -> Unit,
    val setSkipIntro: (Boolean) -> Unit,
    /** Milestone Q: turning "Sync with my devices" on asks Google first (phone); the TV just tries. */
    val setDeviceSync: (Boolean) -> Unit = {},
    /** Milestone D: "My TVs" (phone) / "Linked phones" (TV), the Play on TV links. */
    val openLinkedDevices: () -> Unit = {},
    /** Milestone R: Settings → Naming helper; null hides the row. */
    val openNamingHelper: (() -> Unit)? = null,
    val openHelp: () -> Unit,
    val openAbout: () -> Unit,
    /** Settings opened: re-read what may have changed elsewhere (the Skip Intro preference, the PCs' status). */
    val onOpen: () -> Unit,
    /** Signs out at once; the screens ask first (decision D-P2). */
    val signOut: () -> Unit,
    val back: () -> Unit
)

/** The My PCs row's supporting line: "DESKTOP-ELT3101 · Connected at home", "2 PCs", or what the screen is for. */
fun pcSummary(pcs: List<PcRow>): String = when (pcs.size) {
    0 -> "Play videos stored on a PC"
    1 -> {
        val pc = pcs.single()
        if (pc.connection == PcConnection.NotChecked) pc.name else "${pc.name} · ${pc.connection.label}"
    }
    else -> "${pcs.size} PCs"
}

/** The Local folders row's supporting line. */
fun localFolderSummary(count: Int, canAdd: Boolean, isTv: Boolean): String = when {
    !canAdd -> "Not supported on this TV"
    count == 0 -> "Add folders from this ${if (isTv) "TV" else "phone"}'s storage"
    count == 1 -> "1 folder"
    else -> "$count folders"
}

/** The sign-out question (decision D-P2): what signing out does and doesn't remove. */
const val SIGN_OUT_QUESTION = "Your watch history stays on this device. You can sign in again at any time."
