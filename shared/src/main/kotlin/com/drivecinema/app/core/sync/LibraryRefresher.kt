package com.drivecinema.app.core.sync

import com.drivecinema.app.core.network.FolderValidationResult

/** Outcome of a manual "Refresh library" (Task H.1). */
sealed interface LibraryRefreshResult {
    data object Refreshed : LibraryRefreshResult
    data class Failed(val message: String) : LibraryRefreshResult
}

/**
 * Re-runs the app-start sync on demand: check the saved folder, then crawl it (the crawl also fills in TMDB details).
 * Only adds/updates catalog rows through the normal sync, so watch history is never touched; on any failure
 * the current catalog stays as it is.
 */
class LibraryRefresher(
    private val validateFolder: suspend (rootFolderId: String, token: String) -> Result<FolderValidationResult>,
    private val crawl: suspend (filmsFolderId: String?, seriesFolderId: String?, token: String, rootFolderId: String) -> Result<Unit>,
) {
    suspend fun refresh(rootFolderId: String, accessToken: String?): LibraryRefreshResult {
        if (accessToken.isNullOrEmpty()) return LibraryRefreshResult.Failed(OFFLINE_MESSAGE)
        val validation = validateFolder(rootFolderId, accessToken).getOrElse {
            return LibraryRefreshResult.Failed(OFFLINE_MESSAGE)
        }
        if (validation is FolderValidationResult.Invalid) {
            return LibraryRefreshResult.Failed("Library folder can't be used: ${validation.reason}")
        }
        val valid = validation as FolderValidationResult.Valid
        crawl(valid.filmsFolderId, valid.seriesFolderId, accessToken, rootFolderId).getOrElse {
            return LibraryRefreshResult.Failed(OFFLINE_MESSAGE)
        }
        return LibraryRefreshResult.Refreshed
    }

    companion object {
        const val OFFLINE_MESSAGE = "Couldn't refresh the library. Check your connection and try again."
    }
}
