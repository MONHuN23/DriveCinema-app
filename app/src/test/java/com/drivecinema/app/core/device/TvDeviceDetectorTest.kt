package com.drivecinema.app.core.device

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvDeviceDetectorTest {

    private val phone = DeviceSignals(
        hasLeanbackFeature = false,
        isTelevisionUiMode = false,
        isFireTv = false,
        hasTouchscreen = true
    )

    @Test
    fun phoneOrTabletIsNotTv() {
        assertFalse(TvDeviceDetector.isTv(phone))
    }

    @Test
    fun googleTvWithLeanbackIsTv() {
        assertTrue(TvDeviceDetector.isTv(phone.copy(hasLeanbackFeature = true, isTelevisionUiMode = true, hasTouchscreen = false)))
    }

    @Test
    fun televisionUiModeAloneIsTv() {
        assertTrue(TvDeviceDetector.isTv(phone.copy(isTelevisionUiMode = true)))
    }

    @Test
    fun fireTvWithoutLeanbackIsTv() {
        assertTrue(TvDeviceDetector.isTv(phone.copy(isFireTv = true)))
    }

    @Test
    fun boxWithoutTouchscreenIsTv() {
        assertTrue(TvDeviceDetector.isTv(phone.copy(hasTouchscreen = false)))
    }

    @Test
    fun readsFeaturesFromPackageManager() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val shadowPm = Shadows.shadowOf(context.packageManager)
        shadowPm.setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, true)
        shadowPm.setSystemFeature(PackageManager.FEATURE_LEANBACK, false)
        shadowPm.setSystemFeature(TvDeviceDetector.FEATURE_FIRE_TV, false)
        assertFalse(TvDeviceDetector.isTv(context))

        shadowPm.setSystemFeature(TvDeviceDetector.FEATURE_FIRE_TV, true)
        assertTrue(TvDeviceDetector.signalsOf(context).isFireTv)
        assertTrue(TvDeviceDetector.isTv(context))
    }
}
