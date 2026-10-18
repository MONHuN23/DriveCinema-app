package com.drivecinema.app.core.pairing

import android.content.Context
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** TV check 2026-10-18: a wrong PIN showed "Value ERROR of type java.lang.String cannot be converted to JSONObject". */
@RunWith(RobolectricTestRunner::class)
class PhonePairingErrorTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val creds = AuthCredentials(refreshToken = "refresh", rootFolderId = "root")

    @Test
    fun wrongPinGivesReadableRejection() = runBlocking {
        val server = TvPairingServer(context, onCredentialsReceived = {})
        val listening = CountDownLatch(1)
        var port = 0
        val serverJob = async(Dispatchers.IO) {
            server.startServer(pin = "8F3K9A", onListening = { port = it; listening.countDown() }, onSuccess = {}, onError = {})
        }
        assertTrue(listening.await(5, TimeUnit.SECONDS))

        val result = PhonePairingClient(context).pairWithTv("127.0.0.1", port, "999999", creds)
        server.stopServer()
        serverJob.await()

        val error = result.exceptionOrNull()
        assertTrue("expected PairingRejectedException, got $error", error is PairingRejectedException)
        assertFalse(error!!.message!!.contains("JSON"))
    }

    @Test
    fun closedPairingGivesReadableUnreachable() = runBlocking {
        // A port nobody listens on, like the TV after 3 wrong PINs.
        val freePort = ServerSocket(0).use { it.localPort }
        val result = PhonePairingClient(context).pairWithTv("127.0.0.1", freePort, "8F3K9A", creds)
        val error = result.exceptionOrNull()
        assertTrue("expected TvUnreachableException, got $error", error is TvUnreachableException)
    }
}
