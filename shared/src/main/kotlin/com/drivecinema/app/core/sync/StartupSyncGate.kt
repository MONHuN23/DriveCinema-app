package com.drivecinema.app.core.sync

/**
 * Decides whether a newly created screen should run the startup library sync (report F-04). Rotating the phone
 * recreates `MainActivity`, which used to refresh the token and re-read the whole Drive library every time.
 *
 * The gate lives as long as the app process: a cold start (new process, also after a force-stop) always syncs;
 * a recreated screen skips only when the same library folder already finished a sync in this process. A sync
 * that was cancelled or failed is never marked, so the next recreation retries it.
 */
class StartupSyncGate {
    @Volatile
    private var syncedRootFolderId: String? = null

    /** [isRecreated]: the screen is being rebuilt from saved state (rotation, theme change, process restore). */
    fun shouldSync(rootFolderId: String, isRecreated: Boolean): Boolean =
        !isRecreated || syncedRootFolderId != rootFolderId

    fun markSynced(rootFolderId: String) {
        syncedRootFolderId = rootFolderId
    }

    companion object {
        /** One gate per app process; survives activity recreation, reset when the process dies. */
        val process = StartupSyncGate()
    }
}
