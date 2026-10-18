package com.drivecinema.app

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task E.1: Android TV launchers show the application banner; without one the tile is blank or generic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvBannerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun applicationDeclaresTvBanner() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNotEquals("<application> must declare android:banner", 0, info.banner)
        assertEquals(R.drawable.tv_banner, info.banner)
    }

    @Test
    fun bannerHasTvLauncherAspectRatio() {
        val banner: Drawable? = ContextCompat.getDrawable(context, R.drawable.tv_banner)
        assertNotNull(banner)
        // Android TV banners are 320x180 px at xhdpi (16:9).
        assertEquals(16f / 9f, banner!!.intrinsicWidth.toFloat() / banner.intrinsicHeight, 0.01f)
    }
}
