package com.drivecinema.app.core.device

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/** Whether Google Play Services can be used; Fire TV and many generic boxes don't have it. */
interface PlayServicesChecker {
    fun isAvailable(): Boolean
}

class SystemPlayServicesChecker(private val context: Context) : PlayServicesChecker {
    override fun isAvailable(): Boolean = try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    } catch (_: Exception) {
        false
    }
}
