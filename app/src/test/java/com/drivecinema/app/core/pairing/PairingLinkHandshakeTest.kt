package com.drivecinema.app.core.pairing

import android.content.Context
import com.drivecinema.app.core.remote.DeviceLink
import com.drivecinema.app.core.remote.PairingLink
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Milestone D: PIN pairing also links the phone for Play on TV (design `docs/design/d1-play-on-tv.md` §2, L1). */
@RunWith(RobolectricTestRunner::class)
class PairingLinkHandshakeTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val pin = "8F3K9A"
    private val creds = AuthCredentials(refreshToken = "refresh_123", rootFolderId = "root_abc")
    private val phone = DeviceLink("phone0001", "Pixel 9a", DeviceLink.newKey())

    private class Run(val phoneResult: Result<DeviceLink?>, val credentials: AuthCredentials?, val linkedPhone: DeviceLink?)

    private fun pair(linkOnly: Boolean = false, tvCanLink: Boolean = true, offer: DeviceLink? = phone): Run = runBlocking {
        var credentials: AuthCredentials? = null
        var linkedPhone: DeviceLink? = null
        val onPhoneLinked: ((DeviceLink) -> String)? = if (tvCanLink) { p -> linkedPhone = p; PairingLink.answer("tv000001", "TCL TV", 8769) } else null
        val server = TvPairingServer(context, linkOnly = linkOnly, onPhoneLinked = onPhoneLinked) { credentials = it }
        val listening = CountDownLatch(1)
        var port = 0
        val job = async(Dispatchers.IO) {
            server.startServer(pin, onListening = { port = it; listening.countDown() }, onSuccess = {}, onError = {})
        }
        assertTrue(listening.await(5, TimeUnit.SECONDS))
        val result = PhonePairingClient(context).pairWithTv("127.0.0.1", port, pin, creds, offer)
        if (result.isFailure) server.stopServer()
        job.await()
        Run(result, credentials, linkedPhone)
    }

    @Test
    fun pairingSignsInTheTvAndLinksBothWays() {
        val run = pair()
        assertEquals(creds, run.credentials)
        assertEquals(phone, run.linkedPhone)
        assertEquals(DeviceLink("tv000001", "TCL TV", phone.key, 8769, "127.0.0.1"), run.phoneResult.getOrThrow())
    }

    @Test
    fun linkOnlyKeepsTheTvsSignInAndSendsNoCredentials() {
        val run = pair(linkOnly = true)
        assertNull("no credentials in link-only mode", run.credentials)
        assertEquals(phone, run.linkedPhone)
        assertEquals("tv000001", run.phoneResult.getOrThrow()!!.id)
    }

    @Test
    fun anOldTvStillPairsButDoesNotLink() {
        val run = pair(tvCanLink = false)
        assertEquals(creds, run.credentials)
        assertTrue(run.phoneResult.isSuccess)
        assertNull("old TV: no link", run.phoneResult.getOrThrow())
    }

    @Test
    fun anOldPhoneStillPairs() {
        val run = pair(offer = null)
        assertEquals(creds, run.credentials)
        assertNull(run.linkedPhone)
        assertNull(run.phoneResult.getOrThrow())
    }

    @Test
    fun linkOnlyNeedsALinkOffer() {
        val run = pair(linkOnly = true, offer = null)
        assertTrue(run.phoneResult.isFailure)
        assertNull(run.credentials)
        assertNull(run.linkedPhone)
    }
}
