package com.drivecinema.app.core.devicesync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.devicesync.DeviceSyncRunner.Trigger
import com.drivecinema.app.ui.setup.DeviceSyncTexts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Q.2: the phone/TV switch, its status line, and when a sync runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceSyncControllerTest {

    private class MemorySwitch(var on: Boolean = false) : DeviceSyncSwitch {
        override fun isEnabled() = on
        override fun setEnabled(enabled: Boolean) { on = enabled }
    }

    private val ok = DeviceSyncResult.Synced(1, 2, true, 0)

    private fun controller(
        switch: MemorySwitch,
        isTv: Boolean = false,
        token: String? = "tok",
        result: DeviceSyncResult = ok,
        runs: MutableList<String> = mutableListOf()
    ) = DeviceSyncController(
        switch,
        if (isTv) DeviceSyncTexts.TV_NEEDS_PAIRING else DeviceSyncTexts.PHONE_NEEDS_PERMISSION,
        accessToken = { token },
        sync = { runs += it; result }
    )

    @Test
    fun nothingRunsWhileTheSwitchIsOff() = runBlocking {
        val runs = mutableListOf<String>()
        val c = controller(MemorySwitch(on = false), runs = runs)
        assertNull(c.syncNow(Trigger.STARTUP))
        assertTrue(runs.isEmpty())
    }

    @Test
    fun aRunUsesAFreshTokenAndShowsWhenItSynced() = runBlocking {
        val runs = mutableListOf<String>()
        val c = controller(MemorySwitch(on = true), runs = runs)
        assertEquals(ok, c.syncNow(Trigger.PLAYER_CLOSED))
        assertEquals(listOf("tok"), runs)
        assertTrue(c.status.value!!.startsWith("Last synced at "))
    }

    @Test
    fun withoutATokenNothingIsSentAndTheErrorIsShown() = runBlocking {
        val runs = mutableListOf<String>()
        val c = controller(MemorySwitch(on = true), token = null, runs = runs)
        assertTrue(c.syncNow(Trigger.STARTUP) is DeviceSyncResult.Failed)
        assertTrue(runs.isEmpty())
        assertTrue(c.status.value!!.startsWith("Couldn't sync"))
        assertTrue("stays on, tries again later", c.enabled.value)
    }

    @Test
    fun aTvPairedWithoutTheScopeIsToldToPairAgainAndTheSwitchGoesOff() = runBlocking {
        val switch = MemorySwitch()
        val c = controller(switch, isTv = true, result = DeviceSyncResult.NeedsPermission)
        c.turnOn()
        assertFalse(switch.on)
        assertFalse(c.enabled.value)
        assertEquals(DeviceSyncTexts.TV_NEEDS_PAIRING, c.status.value)
    }

    @Test
    fun thePhoneTurnsSyncOnOnlyForTheSignedInAccount() = runBlocking {
        val switch = MemorySwitch()
        val runs = mutableListOf<String>()
        val c = controller(switch, runs = runs)
        var saved = 0

        assertNotNull(c.completeGrant("other@example.com", "me@example.com") { saved++ })
        assertEquals(0, saved)
        assertFalse(switch.on)
        assertTrue(runs.isEmpty())

        assertNull(c.completeGrant("ME@example.com", "me@example.com") { saved++ })
        assertEquals(1, saved)
        assertTrue(switch.on)
        assertEquals("synced right away", 1, runs.size)
    }

    @Test
    fun aRefusedGrantLeavesSyncOff() {
        val switch = MemorySwitch(on = true)
        val c = controller(switch)
        c.grantRefused("Sync stays off: cancelled")
        assertFalse(switch.on)
        assertEquals("Sync stays off: cancelled", c.status.value)
    }

    @Test
    fun theSettingIsOffByDefaultAndTheDeviceIdStaysTheSame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val setting = DeviceSyncSetting(context)
        assertFalse(setting.isEnabled())
        setting.setEnabled(true)
        assertTrue(DeviceSyncSetting(context).isEnabled())
        val id = setting.deviceId()
        assertEquals(id, DeviceSyncSetting(context).deviceId())
        assertEquals("android-tv", setting.identity(isTv = true).platform)
        assertTrue("not in backed-up storage", java.io.File(context.noBackupFilesDir, DeviceSyncSetting.ID_FILE).exists())
    }
}
