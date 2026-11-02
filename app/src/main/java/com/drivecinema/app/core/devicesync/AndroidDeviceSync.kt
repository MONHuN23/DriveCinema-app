package com.drivecinema.app.core.devicesync

import android.content.Context
import java.io.File

/**
 * Milestone Q: the "Sync with my devices" switch (Account dialog), off by default, and this install's device ID.
 * The ID lives in `noBackupFilesDir`, so a backup restored onto another phone can't make two devices share one sync file.
 */
class DeviceSyncSetting(context: Context) : DeviceSyncSwitch {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    @Synchronized
    fun deviceId(): String {
        val file = File(appContext.noBackupFilesDir, ID_FILE)
        file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return DeviceSyncIdentity.newDeviceId().also { file.writeText(it) }
    }

    fun identity(isTv: Boolean): DeviceSyncIdentity = DeviceSyncIdentity(
        deviceId = deviceId(),
        deviceName = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL).joinToString(" ").trim(),
        platform = if (isTv) "android-tv" else "android-phone"
    )

    companion object {
        const val PREFS = "drivecinema_settings"
        const val KEY_ENABLED = "device_sync_enabled"
        const val ID_FILE = "device_sync_id"
    }
}
