package com.drivecinema.app.core.sync

import com.drivecinema.app.core.logging.AppLog
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Access token for the startup library sync (report F-06). Uses the cached token when there is one; otherwise
 * waits for a refresh without blocking a thread. The wait is long enough for a slow network (the old 5 s limit
 * silently skipped the sync), and a failure is logged instead of passing unnoticed.
 */
object StartupToken {
    const val REFRESH_TIMEOUT_MS = 30_000L

    suspend fun obtain(
        cachedToken: String,
        refresh: suspend () -> Result<String>,
        timeoutMs: Long = REFRESH_TIMEOUT_MS
    ): String? {
        if (cachedToken.isNotEmpty()) return cachedToken
        val result = withTimeoutOrNull(timeoutMs) { refresh() }
        val token = result?.getOrNull()?.takeIf { it.isNotEmpty() }
        if (token == null) {
            val reason = result?.exceptionOrNull()?.message ?: if (result == null) "timed out after $timeoutMs ms" else "empty token"
            AppLog.w("StartupToken", "No access token for the startup sync ($reason); library not refreshed this time")
        }
        return token
    }
}
