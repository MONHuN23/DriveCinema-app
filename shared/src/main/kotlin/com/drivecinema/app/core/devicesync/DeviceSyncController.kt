package com.drivecinema.app.core.devicesync

import com.drivecinema.app.core.devicesync.DeviceSyncRunner.Trigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The switch's stored value (Android prefs, desktop `settings.json`). */
interface DeviceSyncSwitch {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
}

/**
 * Milestone Q on every platform: the "Sync with my devices" switch, its status line, and the runs the app triggers
 * (library ready, player closed, Refresh, back in front). The phone and desktop turn sync on after Google grants the app
 * folder scope ([completeGrant] / [turnOn]); the TV has only its paired sign-in, so it just tries ([turnOn]).
 */
class DeviceSyncController(
    private val switch: DeviceSyncSwitch,
    /** Shown when the sign-in has no app folder scope (it differs per platform: the TV has to pair again). */
    private val needsPermissionText: String,
    /** A fresh access token, or null when there is none (offline, signed out). */
    private val accessToken: suspend () -> String?,
    private val sync: suspend (accessToken: String) -> DeviceSyncResult,
    private val runner: DeviceSyncRunner = DeviceSyncRunner(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _enabled = MutableStateFlow(switch.isEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _status = MutableStateFlow<String?>(null)
    /** Null shows the switch's caption. */
    val status: StateFlow<String?> = _status.asStateFlow()

    /** Null when sync is off or the run was skipped (throttled, or one is running). */
    suspend fun syncNow(trigger: Trigger): DeviceSyncResult? {
        if (!switch.isEnabled()) return null
        val result = runner.run(trigger) {
            val token = accessToken() ?: return@run DeviceSyncResult.Failed("no connection to Google")
            sync(token)
        } ?: return null
        report(result)
        return result
    }

    /** Turns sync on and runs it (the TV, or after a grant the platform has already checked). */
    suspend fun turnOn() {
        setEnabled(true)
        _status.value = null
        syncNow(Trigger.REFRESH)
    }

    /**
     * The consent page came back with a new grant. It must be the signed-in account ([accountEmail] of the new token =
     * [expectedEmail]); then [saveRefreshToken] runs and sync is turned on. Returns the message shown when refused, or null.
     */
    suspend fun completeGrant(accountEmail: String?, expectedEmail: String?, saveRefreshToken: () -> Unit): String? {
        if (expectedEmail != null && !expectedEmail.equals(accountEmail, ignoreCase = true)) {
            val message = "Sync has to be turned on with $expectedEmail, the account signed in to DriveCinema."
            _status.value = message
            return message
        }
        saveRefreshToken()
        turnOn()
        return null
    }

    /** Waiting for the browser or Google's page; [message] is shown in place of the caption. */
    fun showStatus(message: String?) {
        _status.value = message
    }

    fun grantRefused(message: String) {
        setEnabled(false)
        _status.value = message
    }

    fun disable() {
        setEnabled(false)
        _status.value = null
    }

    private fun setEnabled(enabled: Boolean) {
        switch.setEnabled(enabled)
        _enabled.value = enabled
    }

    private fun report(result: DeviceSyncResult) {
        _status.value = statusText(result, needsPermissionText, clock())
        if (result == DeviceSyncResult.NeedsPermission) setEnabled(false)
    }

    companion object {
        const val SWITCH_CAPTION = "Resume points and watched marks on your phone, TV and PC, through a hidden app folder in your Google Drive."

        fun statusText(result: DeviceSyncResult, needsPermissionText: String, now: Long): String = when (result) {
            is DeviceSyncResult.Synced -> "Last synced at " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
            DeviceSyncResult.NeedsPermission -> needsPermissionText
            is DeviceSyncResult.Failed -> "Couldn't sync (${result.message}). It tries again later."
        }
    }
}
