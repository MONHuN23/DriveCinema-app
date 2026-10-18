package com.drivecinema.app.core.pairing

import android.content.Context
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TvPairingServerTest {

    @Test
    fun testPinGenerationHasExpectedLengthAndCharset() {
        val context: Context = RuntimeEnvironment.getApplication()
        val server = TvPairingServer(context, onCredentialsReceived = {})
        val pin = server.generatePin()

        assertEquals(6, pin.length)
        assertTrue(pin.all { it in "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" })
    }

    @Test
    fun testSocketPairingEndToEnd() = runBlocking {
        val context: Context = RuntimeEnvironment.getApplication()
        val pin = "8F3K9A"
        var receivedCredentials: AuthCredentials? = null
        val latch = CountDownLatch(1)
        var serverPort = 0

        val server = TvPairingServer(context) { creds ->
            receivedCredentials = creds
        }

        val serverJob = async(Dispatchers.IO) {
            server.startServer(
                pin = pin,
                onListening = { port ->
                    serverPort = port
                    latch.countDown()
                },
                onSuccess = {},
                onError = {}
            )
        }

        assertTrue("Server must start listening within 5s", latch.await(5, TimeUnit.SECONDS))
        assertTrue(serverPort > 0)

        val client = PhonePairingClient(context)
        val originalCreds = AuthCredentials(refreshToken = "test_refresh_12345", rootFolderId = "folder_root_abcde")
        val clientResult = client.pairWithTv("127.0.0.1", serverPort, pin, originalCreds)

        serverJob.await()

        assertTrue("Client pairing must succeed: ${clientResult.exceptionOrNull()?.message}", clientResult.isSuccess)
        assertNotNull(receivedCredentials)
        assertEquals(originalCreds.refreshToken, receivedCredentials?.refreshToken)
        assertEquals(originalCreds.rootFolderId, receivedCredentials?.rootFolderId)
    }
}
