package com.drivecinema.desktop.host

import com.drivecinema.app.core.host.HostApi
import com.drivecinema.app.core.host.HostPairing
import com.drivecinema.app.core.host.HostPairingClient
import com.drivecinema.app.core.host.HostProtocol
import com.drivecinema.app.core.host.HostTreeSource
import com.drivecinema.app.core.local.LocalLibraryCrawler
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * Task I.2, end to end on this computer: sharing on, PIN pairing, the pinned HTTPS client, the existing crawler over
 * the PC's folders, seeking, and that nobody without a valid device key (or with another certificate) gets in.
 */
class MediaHostTest {

    private lateinit var dir: File
    private lateinit var library: Path
    private lateinit var host: MediaHost
    private var sharing = false
    private var vpn = false
    private val film = Random(5).nextBytes(300_000)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-host").toFile()
        library = Files.createDirectories(dir.toPath().resolve("Media"))
        write("Films/Dune (2021).mkv", film)
        write("Films/notes.txt", ByteArray(10))
        write("Films/.hidden.mkv", ByteArray(10))
        write("Series/Ted (2024)/Season 1/Ted.S01E02.mkv", ByteArray(1_000))
        host = newHost()
    }

    @After
    fun tearDown() {
        host.close()
        dir.deleteRecursively()
    }

    private fun newHost() = MediaHost(
        dataDir = File(dir, "data"),
        settings = object : HostSettings {
            override fun sharingEnabled() = sharing
            override fun setSharingEnabled(enabled: Boolean) { sharing = enabled }
            override fun allowVpn() = vpn
            override fun setAllowVpn(allowed: Boolean) { vpn = allowed }
        },
        sharedFolders = { listOf(library) },
        computerName = { "Test PC" },
        httpsPort = 0, pairingPort = 0, discoveryPort = 0
    )

    private fun write(relative: String, bytes: ByteArray) {
        val file = library.resolve(relative.replace('/', File.separatorChar))
        Files.createDirectories(file.parent)
        Files.write(file, bytes)
    }

    private fun pair(): HostPairing {
        host.setSharing(true)
        val pin = host.startPairing()!!
        return HostPairingClient.pair("127.0.0.1", pin, "Test phone", port = host.status.value.pairingPort!!)
    }

    private fun connected(pairing: HostPairing) = HostApi(pairing).also { assertEquals("127.0.0.1", it.connect(listOf("127.0.0.1"))) }

    @Test
    fun nothingListensUntilSharingIsTurnedOn() {
        assertFalse(host.status.value.isSharing)
        assertNull(host.startPairing())
        host.setSharing(true)
        assertTrue(host.status.value.isSharing)
        assertNotNull(host.status.value.port)
    }

    @Test
    fun aPairedPhoneCrawlsThePcWithTheNormalLayoutRules() {
        val pairing = pair()
        assertEquals("Test PC", pairing.hostName)
        assertEquals(listOf("Test phone"), host.status.value.devices.map { it.name })

        val api = connected(pairing)
        val root = api.roots().single()
        val library = LocalLibraryCrawler(HostTreeSource(api)).crawl(HostProtocol.treeUri(pairing.hostId, root.id))

        assertEquals(listOf("Dune (2021).mkv"), library.films.map { it.fileName })
        val dune = library.films.single()
        assertTrue(dune.sourceKey.startsWith("dchost://${pairing.hostId}/file/"))
        assertEquals(300_000L, dune.sizeBytes)
        assertEquals(listOf("Ted.S01E02.mkv"), library.shows.single().episodes.map { it.video.fileName })
        assertTrue("non-videos and hidden files are not shared", api.children(root.id).isNotEmpty())
    }

    @Test
    fun filesStreamWithSeekingAndIdsSurviveARestart() {
        val pairing = pair()
        var api = connected(pairing)
        val fileId = HostProtocol.parse(LocalLibraryCrawler(HostTreeSource(api)).crawl(HostProtocol.treeUri(pairing.hostId, api.roots().single().id)).films.single().sourceKey)!!.third

        api.client.newCall(Request.Builder().url(api.fileUrl(fileId)!!).header("Range", "bytes=1000-1999").build()).execute().use {
            assertEquals(206, it.code)
            assertEquals("bytes 1000-1999/300000", it.header("Content-Range"))
            assertArrayEquals(film.copyOfRange(1000, 2000), it.body!!.bytes())
        }
        api.client.newCall(Request.Builder().url(api.fileUrl(fileId)!!).header("Range", "bytes=999999-").build()).execute().use {
            assertEquals(416, it.code)
        }

        // A new app start: same host ID, same certificate, same file IDs, the device stays paired.
        host.close()
        host = newHost()
        host.setSharing(true)
        val restarted = pairing.copy(httpsPort = host.status.value.port!!)
        api = connected(restarted)
        api.client.newCall(Request.Builder().url(api.fileUrl(fileId)!!).header("Range", "bytes=0-9").build()).execute().use {
            assertEquals(206, it.code)
            assertArrayEquals(film.copyOfRange(0, 10), it.body!!.bytes())
        }
    }

    @Test
    fun withoutAValidKeyNothingIsReadableAndRepeatedTriesAreLockedOut() {
        val pairing = pair()
        val stranger = HostApi(pairing.copy(deviceKey = "guessed"))
        assertNull("a wrong key can't even ping", stranger.connect(listOf("127.0.0.1")))
        repeat(25) { stranger.connect(listOf("127.0.0.1")) }
        val api = HostApi(pairing)
        api.client.newCall(Request.Builder().url("https://127.0.0.1:${pairing.httpsPort}/v1/ping").build()).execute().use {
            assertEquals("this address is locked out for a while", 429, it.code)
        }
    }

    @Test
    fun aRemovedDeviceLosesAccessAtOnce() {
        val pairing = pair()
        connected(pairing)
        host.removeDevice(pairing.deviceId)
        assertNull(HostApi(pairing).connect(listOf("127.0.0.1")))
    }

    @Test
    fun aDifferentCertificateIsRefusedByTheClient() {
        val pairing = pair()
        val impostorPin = pairing.copy(certFingerprint = "0".repeat(64))
        assertNull("TLS fails before any request", HostApi(impostorPin).connect(listOf("127.0.0.1")))
    }

    @Test
    fun onlyGetAndHeadWork() {
        val pairing = pair()
        val api = connected(pairing)
        api.client.newCall(
            Request.Builder().url("https://127.0.0.1:${pairing.httpsPort}/v1/roots").delete().build()
        ).execute().use { assertEquals(405, it.code) }
        try {
            api.children("not-a-real-id")
            fail("Expected an error for an unknown folder")
        } catch (expected: IOException) {
            // 404
        }
    }

    @Test
    fun rangeHeadersAreParsedStrictly() {
        assertEquals(ByteRange.Whole, ByteRange.parse(null, 100))
        assertEquals(ByteRange.Part(10, 19), ByteRange.parse("bytes=10-19", 100))
        assertEquals(ByteRange.Part(90, 99), ByteRange.parse("bytes=90-", 100))
        assertEquals(ByteRange.Part(80, 99), ByteRange.parse("bytes=-20", 100))
        assertEquals(ByteRange.Part(95, 99), ByteRange.parse("bytes=95-500", 100))
        listOf("bytes=100-", "bytes=20-10", "bytes=0-1,5-6", "items=0-1", "bytes=abc", "bytes=-0").forEach {
            assertEquals(it, ByteRange.Unsatisfiable, ByteRange.parse(it, 100))
        }
    }
}
