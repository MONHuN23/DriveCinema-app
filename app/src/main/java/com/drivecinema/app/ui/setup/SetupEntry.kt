package com.drivecinema.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.ui.navigation.Screen

/**
 * The sign-in and setup screens' state (Task M.3, a step toward F-08: moved out of `MainActivity`). The activity's
 * sign-in, folder and duplicate-review flows write it; [SetupEntry] reads it.
 */
class SetupUiState {
    val signInOptions = mutableStateOf(SignInOptions.forDevice(isTv = false, playServicesAvailable = true))
    val isAuthLoading = mutableStateOf(false)
    val authErrorMessage = mutableStateOf<String?>(null)

    val driveFolders = mutableStateOf<List<DriveFolder>>(emptyList())
    val isFolderLoading = mutableStateOf(false)
    val isFolderValidating = mutableStateOf(false)
    val folderErrorMessage = mutableStateOf<String?>(null)
    /** The folder picker was opened from the Account dialog to switch the library (not the first-run choice). */
    val isSwitchingLibrary = mutableStateOf(false)

    val duplicateReviews = mutableStateOf<List<DuplicateReview>>(emptyList())
}

/** What the sign-in and setup screens ask of the activity. */
class SetupActions(
    val signIn: () -> Unit,
    /** TV: shows the PIN for the phone; phone: the "Pair with TV" dialog. */
    val pair: () -> Unit,
    /** The Help Center's home. */
    val openGuide: () -> Unit,
    /** One Help Center topic (Task P.4): the folder picker's guide link opens "Set up your folders". */
    val openHelpTopic: (id: String) -> Unit,
    val chooseFolder: (DriveFolder) -> Unit,
    val reloadFolders: () -> Unit,
    val signOut: () -> Unit,
    val cancelLibrarySwitch: () -> Unit,
    /** The library folder in use, marked in the picker while switching. */
    val currentFolderId: () -> String?,
    val confirmSameTitle: (sourceKey: String, itemId: String) -> Unit,
    val keepSeparate: (sourceKey: String) -> Unit,
    val back: () -> Unit
)

/** The sign-in or setup [screen] of the back stack. */
@Composable
fun SetupEntry(screen: Screen, state: SetupUiState, actions: SetupActions, isTv: Boolean) {
    when (screen) {
        Screen.SignIn -> SignInScreen(
            isLoading = state.isAuthLoading.value,
            errorMessage = state.authErrorMessage.value,
            onSignInClick = actions.signIn,
            onPairingClick = actions.pair,
            pairingButtonText = if (isTv) "Pair with Phone" else "Pair with TV",
            onOpenGuideClick = actions.openGuide,
            options = state.signInOptions.value
        )

        Screen.FolderPicker -> {
            val switching = state.isSwitchingLibrary.value
            if (switching) {
                // Back cancels the switch (and its folder listing), not just the screen.
                BackHandler(onBack = actions.cancelLibrarySwitch)
            }
            FolderPickerScreen(
                folders = state.driveFolders.value,
                isLoading = state.isFolderLoading.value,
                isValidating = state.isFolderValidating.value,
                errorMessage = state.folderErrorMessage.value,
                onFolderSelected = actions.chooseFolder,
                onRefreshClick = actions.reloadFolders,
                onSignOutClick = actions.signOut,
                currentFolderId = if (switching) actions.currentFolderId() else null,
                busyMessage = if (switching) "Indexing library folder..." else null,
                onCancelClick = if (switching) actions.cancelLibrarySwitch else null,
                onOpenGuideClick = { actions.openHelpTopic(SetupGuideContent.FOLDERS) }
            )
        }

        Screen.Duplicates -> DuplicatesScreen(
            reviews = state.duplicateReviews.value,
            onSameTitle = actions.confirmSameTitle,
            onKeepSeparate = actions.keepSeparate,
            onBackClick = actions.back
        )

        else -> Unit
    }
}
