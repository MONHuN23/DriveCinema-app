package com.drivecinema.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Task M.3 (F-38): the phone and TV screens, as Navigation 3 keys in [AppNavigator]'s back stack. Keys hold IDs, not
 * entities, so the back stack is small, survives a rotation or process death, and a screen always shows what the
 * database has now.
 */
@Serializable
sealed interface Screen : NavKey {
    @Serializable data object SignIn : Screen
    @Serializable data object FolderPicker : Screen
    @Serializable data object Home : Screen
    /** The TV's A–Z grid of all films or all series. */
    @Serializable data class Grid(val kind: GridKind) : Screen
    /** The TV's search screen (the phone searches in a tab of Home). */
    @Serializable data object Search : Screen
    @Serializable data class MovieDetails(val id: String) : Screen
    @Serializable data class SeriesDetails(val id: String) : Screen
    /** The Help Center's home (the setup guide before Task P.4; the name stays so a saved back stack still reads). */
    @Serializable data object SetupGuide : Screen
    /** Task P.4: one Help Center topic, by its `SetupGuideContent` id. */
    @Serializable data class HelpTopic(val id: String) : Screen
    @Serializable data object Duplicates : Screen
    /** Task P.2: the account, library and playback settings (was the Account popup), opened from Home's profile chip. */
    @Serializable data object Settings : Screen
    /** Task P.2: the phone's or TV's saved local folders. */
    @Serializable data object LocalFolders : Screen
    /** Task P.3: the PCs this device streams from, and pairing a new one (was the My PCs popup). */
    @Serializable data object Pcs : Screen
    /** Task R.2: Settings → Naming helper (search TMDB, see the names the app recognises). */
    @Serializable data object NamingHelper : Screen
    /** Task R.2: a series page's Naming check (its Drive folder's files against TMDB). */
    @Serializable data class NamingCheck(val seriesId: String) : Screen
}

@Serializable
enum class GridKind(val title: String) {
    FILMS("Films"),
    SERIES("Series")
}
