package com.drivecinema.app.core.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** What the device reports about itself; each signal alone is enough to treat it as a TV. */
data class DeviceSignals(
    val hasLeanbackFeature: Boolean,
    val isTelevisionUiMode: Boolean,
    val isFireTv: Boolean,
    val hasTouchscreen: Boolean
)

/**
 * Decides whether DriveCinema shows its TV (D-pad) layout.
 *
 * Google TV / Android TV report the leanback feature or the TV UI mode. Fire TV reports its own feature,
 * and generic Android boxes often report neither - but phones and tablets always have a touchscreen
 * (Android CDD), so a device without one is driven by a remote.
 */
object TvDeviceDetector {

    const val FEATURE_FIRE_TV = "amazon.hardware.fire_tv"

    fun isTv(signals: DeviceSignals): Boolean =
        signals.hasLeanbackFeature ||
            signals.isTelevisionUiMode ||
            signals.isFireTv ||
            !signals.hasTouchscreen

    fun signalsOf(context: Context): DeviceSignals {
        val packageManager = context.packageManager
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        return DeviceSignals(
            hasLeanbackFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            isTelevisionUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION,
            isFireTv = packageManager.hasSystemFeature(FEATURE_FIRE_TV),
            hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        )
    }

    fun isTv(context: Context): Boolean = isTv(signalsOf(context))
}
