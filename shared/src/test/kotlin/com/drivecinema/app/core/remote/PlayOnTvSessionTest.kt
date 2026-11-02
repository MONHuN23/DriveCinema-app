package com.drivecinema.app.core.remote

import com.drivecinema.app.core.host.PairingLines
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Milestone D: the authenticated exchange between a linked phone and the TV (design §4). */
class PlayOnTvSessionTest {

    private val pool = Executors.newCachedThreadPool()
    private val key = DeviceLink.newKey()
    private val phone = DeviceLink("phone0001", "Pixel", key)
    private val tv = DeviceLink("tv000001", "TCL", key)
    private val request = PlayRequest("driveFile1", isEpisode = false, tmdbId = 348, season = null, episode = null, title = "Alien", positionMs = 600_000)

    @After
    fun tearDown() {
        pool.shutdownNow()
    }

    private class Ends(val input: InputStream, val output: OutputStream)

    /** A connected pair of in-memory streams: (server end, client end). */
    private fun pipe(): Pair<Ends, Ends> {
        val toServer = PipedInputStream(64 * 1024)
        val toClient = PipedInputStream(64 * 1024)
        val clientOut = PipedOutputStream(toServer)
        val serverOut = PipedOutputStream(toClient)
        return Ends(toServer, serverOut) to Ends(toClient, clientOut)
    }

    private fun serve(
        session: PlayOnTvServerSession,
        server: Ends,
        dispatch: (PlayRequest, DeviceLink) -> PlayStatus
    ): Future<PlayOnTvServerSession.Result> = pool.submit<PlayOnTvServerSession.Result> {
        session.handle(server.input, server.output, dispatch).also { server.output.close() }
    }

    private fun session(links: Map<String, DeviceLink> = mapOf(phone.id to phone)) = PlayOnTvServerSession(tv.id, { links[it] })

    @Test
    fun linkedPhoneDeliversTheRequestAndReadsTheReply() {
        val (server, client) = pipe()
        val seen = mutableListOf<PlayRequest>()
        val result = serve(session(), server) { r, p -> seen += r; assertEquals(phone.id, p.id); PlayStatus.OK }

        val status = PlayOnTvClient.exchange(client.input, client.output, phone.id, tv, request)

        assertEquals(PlayStatus.OK, status)
        assertEquals(listOf(request), seen)
        assertEquals(PlayOnTvServerSession.Result.Delivered(request, phone, PlayStatus.OK), result.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun theTvsStatusReachesThePhone() {
        val (server, client) = pipe()
        serve(session(), server) { _, _ -> PlayStatus.NOT_IN_LIBRARY }
        assertEquals(PlayStatus.NOT_IN_LIBRARY, PlayOnTvClient.exchange(client.input, client.output, phone.id, tv, request))
    }

    @Test
    fun unknownPhoneIsRefusedWithoutDispatch() {
        val (server, client) = pipe()
        var dispatched = false
        val result = serve(session(emptyMap()), server) { _, _ -> dispatched = true; PlayStatus.OK }

        assertKind(PlayOnTvException.Kind.UNKNOWN_PHONE) { PlayOnTvClient.exchange(client.input, client.output, phone.id, tv, request) }
        assertEquals(PlayOnTvServerSession.Result.Unauthorized, result.get(5, TimeUnit.SECONDS))
        assertFalse(dispatched)
    }

    @Test
    fun wrongKeyIsRefusedWithoutDispatch() {
        val (server, client) = pipe()
        var dispatched = false
        val result = serve(session(), server) { _, _ -> dispatched = true; PlayStatus.OK }

        val stale = tv.copy(key = DeviceLink.newKey())
        assertKind(PlayOnTvException.Kind.UNKNOWN_PHONE) { PlayOnTvClient.exchange(client.input, client.output, phone.id, stale, request) }
        assertEquals(PlayOnTvServerSession.Result.Unauthorized, result.get(5, TimeUnit.SECONDS))
        assertFalse(dispatched)
    }

    @Test
    fun aRecordedBoxDoesNotOpenOnTheNextConnection() {
        // First connection: a real phone; record the line it sends.
        val (server1, client1) = pipe()
        serve(session(), server1) { _, _ -> PlayStatus.OK }
        val recorder = object : OutputStream() {
            val bytes = java.io.ByteArrayOutputStream()
            override fun write(b: Int) { bytes.write(b); client1.output.write(b) }
            override fun flush() = client1.output.flush()
        }
        PlayOnTvClient.exchange(client1.input, recorder, phone.id, tv, request)
        val recorded = recorder.bytes.toString(Charsets.UTF_8.name()).trim()

        // Second connection: replay that line after the TV's new hello.
        val (server2, client2) = pipe()
        var dispatched = false
        val result = serve(session(), server2) { _, _ -> dispatched = true; PlayStatus.OK }
        PairingLines.read(client2.input)
        PairingLines.write(client2.output, recorded)

        assertEquals(PlayOnTvServerSession.Result.Unauthorized, result.get(5, TimeUnit.SECONDS))
        assertFalse(dispatched)
        assertEquals(PlayOnTvServerSession.ERROR_UNKNOWN_PHONE, JSONObject(PairingLines.read(client2.input)!!).getString("error"))
    }

    @Test
    fun aTamperedBoxIsRefused() {
        val (server, client) = pipe()
        val result = serve(session(), server) { _, _ -> PlayStatus.OK }
        val hello = JSONObject(PairingLines.read(client.input)!!)
        val sessionKey = PlayOnTvCrypto.sessionKey(key, org.bouncycastle.util.encoders.Hex.decode(hello.getString("nonce")))
        val box = PlayOnTvCrypto.seal(sessionKey, request.toJson())
        val flipped = box.dropLast(2) + (if (box.endsWith("00")) "01" else "00")
        PairingLines.write(client.output, JSONObject().put("phone", phone.id).put("box", flipped).toString())

        assertEquals(PlayOnTvServerSession.Result.Unauthorized, result.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun anAuthenticButInvalidRequestIsAnsweredBadRequest() {
        val (server, client) = pipe()
        var dispatched = false
        val result = serve(session(), server) { _, _ -> dispatched = true; PlayStatus.OK }
        val hello = JSONObject(PairingLines.read(client.input)!!)
        val sessionKey = PlayOnTvCrypto.sessionKey(key, org.bouncycastle.util.encoders.Hex.decode(hello.getString("nonce")))
        PairingLines.write(client.output, JSONObject().put("phone", phone.id).put("box", PlayOnTvCrypto.seal(sessionKey, """{"type":"PLAY"}""")).toString())

        assertEquals(PlayOnTvServerSession.Result.BadRequest, result.get(5, TimeUnit.SECONDS))
        assertFalse(dispatched)
        val reply = PlayOnTvCrypto.open(sessionKey, JSONObject(PairingLines.read(client.input)!!).getString("box"))
        assertEquals("BAD_REQUEST", JSONObject(reply!!).getString("status"))
    }

    @Test
    fun anOversizedLineIsRejected() {
        val (server, client) = pipe()
        val result = serve(session(), server) { _, _ -> PlayStatus.OK }
        PairingLines.read(client.input)
        pool.submit { runCatching { client.output.write(ByteArray(PairingLines.MAX_LINE_BYTES + 10) { 'a'.code.toByte() }) } }
        assertEquals(PlayOnTvServerSession.Result.Rejected, result.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun anotherDeviceAtTheAddressIsReportedAsWrongTv() {
        val (server, client) = pipe()
        serve(PlayOnTvServerSession("othertv01", { phone }), server) { _, _ -> PlayStatus.OK }
        assertKind(PlayOnTvException.Kind.WRONG_TV) { PlayOnTvClient.exchange(client.input, client.output, phone.id, tv, request) }
    }

    @Test
    fun aReplyThatDoesNotOpenIsAProtocolError() {
        val (server, client) = pipe()
        // A fake TV: right ID, but it doesn't have the key, so its reply can't be sealed with it.
        pool.submit {
            PairingLines.write(server.output, JSONObject().put("tv", tv.id).put("nonce", "00".repeat(16)).toString())
            PairingLines.read(server.input)
            PairingLines.write(server.output, JSONObject().put("box", PlayOnTvCrypto.seal(ByteArray(32), """{"status":"OK"}""")).toString())
        }
        assertKind(PlayOnTvException.Kind.PROTOCOL) { PlayOnTvClient.exchange(client.input, client.output, phone.id, tv, request) }
    }

    @Test
    fun throttlePausesAfterRepeatedFailuresAndRecovers() {
        var now = 0L
        val throttle = AuthFailureThrottle(clock = { now }, maxFailures = 5, windowMs = 60_000, pauseMs = 60_000)
        repeat(4) { throttle.recordFailure(); now += 1_000 }
        assertTrue("4 failures: still open", throttle.allowed())
        throttle.recordFailure()
        assertFalse("5th failure within a minute: paused", throttle.allowed())
        now += 60_000
        assertTrue("open again after the pause", throttle.allowed())
    }

    @Test
    fun throttleForgetsOldFailures() {
        var now = 0L
        val throttle = AuthFailureThrottle(clock = { now })
        repeat(4) { throttle.recordFailure() }
        now += 61_000
        throttle.recordFailure()
        assertTrue("failures older than the window don't count", throttle.allowed())
    }

    private fun assertKind(kind: PlayOnTvException.Kind, block: () -> Unit) {
        try {
            block()
            fail("expected $kind")
        } catch (e: PlayOnTvException) {
            assertEquals(kind, e.kind)
        }
    }
}
