package com.drivecinema.app.core.pairing

import android.content.Context
import com.drivecinema.app.core.security.AuthCredentials
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.bouncycastle.util.encoders.Hex
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Report F-07: hardening of the TV pairing server against other devices on the LAN.
 * Real phone ↔ TV pairing still needs a manual check on the devices.
 */
@RunWith(RobolectricTestRunner::class)
class TvPairingServerHardeningTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val pin = "8F3K9A"
    private val creds = AuthCredentials(refreshToken = "refresh_abc", rootFolderId = "root_xyz")
    private var received: AuthCredentials? = null
    private val errors = mutableListOf<String>()
    private lateinit var server: TvPairingServer
    private var port = 0


    private fun runServer(block: suspend () -> Unit) = runBlocking {
        server = TvPairingServer(context) { received = it }
        val listening = CountDownLatch(1)
        val job = async(Dispatchers.IO) {
            server.startServer(pin, onListening = { port = it; listening.countDown() }, onSuccess = {}, onError = { synchronized(errors) { errors += it } })
        }
        assertTrue("server must start", listening.await(5, TimeUnit.SECONDS))
        try {
            block()
        } finally {
            server.stopServer()
            withTimeout(20_000) { job.await() }
        }
    }

    @After
    fun tearDown() {
        if (::server.isInitialized) server.stopServer()
    }

    // Not written with `Socket().apply { ... port ... }`: inside apply, `port` would be the Socket's own port (0).
    private fun connect(): Socket {
        val socket = Socket()
        socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        socket.soTimeout = 5_000
        return socket
    }

    private suspend fun pairWith(pinToUse: String) = PhonePairingClient(context).pairWithTv("127.0.0.1", port, pinToUse, creds)

    @Test
    fun oversizedLineIsCutOffQuickly() = runServer {
        connect().use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            reader.readLine() // challenge
            socket.soTimeout = 3_000
            val out = socket.getOutputStream()
            val chunk = ByteArray(64 * 1024) { 'a'.code.toByte() }
            val started = System.currentTimeMillis()
            // Up to 1 MB without a newline: the server must give up long before its 15 s idle timeout.
            val closed = runCatching { repeat(16) { out.write(chunk); out.flush() } }.isFailure ||
                runCatching { reader.readLine() }.getOrNull().let { it == null || it.startsWith("ERROR") }
            assertTrue("server must reject the oversized line", closed)
            assertTrue("rejected within 3 s", System.currentTimeMillis() - started < 3_000)
        }
        val result = pairWith(pin)
        assertTrue("valid pairing still works afterwards: ${result.exceptionOrNull()}", result.isSuccess)
    }

    @Test
    fun unrelatedConnectionsDoNotUseUpPairingAttempts() = runServer {
        repeat(3) { connect().close() }                                   // port scanner: connect and leave
        repeat(2) {
            connect().use { s ->
                BufferedReader(InputStreamReader(s.getInputStream())).readLine()
                PrintWriter(s.getOutputStream(), true).println("GET / HTTP/1.1") // not our protocol
                BufferedReader(InputStreamReader(s.getInputStream())).readLine()
            }
        }
        val result = pairWith(pin)
        assertTrue("valid pairing must still work after junk connections: ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals("refresh_abc", received?.refreshToken)
    }

    @Test
    fun threeWrongPinsLockPairing() = runServer {
        repeat(3) { assertTrue(pairWith("WRONG1").isFailure) }
        val result = pairWith(pin)
        assertTrue("after 3 wrong PINs even the right PIN is refused", result.isFailure)
        assertNull(received)
    }

    @Test
    fun clientSeesOnlyAGenericError() = runServer {
        connect().use { s ->
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream(), true)
            val challenge = JSONObject(reader.readLine())
            val crypto = SrpPairingClientCrypto("WRONG1")
            val (a, m1) = crypto.processChallenge(Hex.decode(challenge.getString("salt")), BigInteger(challenge.getString("serverB"), 16))
            writer.println(JSONObject().put("clientA", a.toString(16)).put("clientM1", m1.toString(16)).toString())
            val reply = reader.readLine()
            assertEquals("ERROR", reply)
        }
        assertFalse("the TV-side log may keep the detail", errors.isEmpty())
    }

    @Test
    fun validPinPairsAndDeliversCredentials() = runServer {
        val result = pairWith(pin)
        assertTrue(result.isSuccess)
        assertEquals("root_xyz", received?.rootFolderId)
    }
}
