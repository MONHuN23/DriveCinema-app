package com.drivecinema.desktop.host

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.host.DiscoveredHost
import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.desktop.library.DesktopDatabase
import com.drivecinema.desktop.player.DesktopPlayback
import com.drivecinema.desktop.player.DriveStreamProxy
import com.drivecinema.desktop.player.HostStreamProxy
import com.drivecinema.desktop.player.SlowChoice
import com.drivecinema.desktop.ui.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

/**
 * Task I.2 follow-up: this computer as a client of another PC. VLC gets a loopback URL, the proxy streams over the PC's
 * pinned HTTPS with seeking; a PC that's off falls back to Drive; a too-slow connection asks first.
 */
class DesktopClientTest {

    private lateinit var dir: File
    private lateinit var library: Path
    private lateinit var otherPc: MediaHost
    private lateinit var db: DriveCinemaDatabase
    private lateinit var hosts: HostLibrarySync
    private lateinit var hostProxy: HostStreamProxy
    private lateinit var driveProxy: DriveStreamProxy
    private lateinit var scope: CoroutineScope
    private var sharing = false
    private val film = Random(9).nextBytes(400_000)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-client").toFile()
        library = Files.createDirectories(dir.toPath().resolve("Media").resolve("Films"))
        Files.write(library.resolve("Dune (2021).mkv"), film)
        otherPc = MediaHost(
            File(dir, "other-pc"),
            object : HostSettings {
                override fun sharingEnabled() = sharing
                override fun setSharingEnabled(enabled: Boolean) { sharing = enabled }
                override fun allowVpn() = false
                override fun setAllowVpn(allowed: Boolean) = Unit
            },
            sharedFolders = { listOf(library.parent) },
            computerName = { "Study PC" },
            httpsPort = 0, pairingPort = 0, discoveryPort = 0
        )
        otherPc.setSharing(true)
        db = DesktopDatabase.open(File(dir, "this-pc").apply { mkdirs() })
        hosts = HostLibrarySync(db, DesktopPairedHostStore(File(dir, "this-pc/paired-hosts.json")),
            runInTransaction = { block -> db.useWriterConnection { it.immediateTransaction { block() } } })
        hostProxy = HostStreamProxy(hosts)
        driveProxy = DriveStreamProxy(object : TokenProvider {
            override fun getAccessToken() = "t"
            override fun forceRefresh() = "t"
        })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        runBlocking {
            db.mediaDao().insertMediaItems(listOf(MediaItemEntity(id = "drive-dune", type = MediaType.MOVIE, title = "Dune", year = 2021, parentFolderId = "films")))
            db.mediaSourceDao().insertSourcesIfAbsent(listOf(MediaSourceEntity("drive-dune", "drive-dune", SourceKind.DRIVE)))
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        hostProxy.close()
        driveProxy.close()
        otherPc.close()
        db.close()
        dir.deleteRecursively()
    }

    private fun pairAndSync() = runBlocking {
        val pin = otherPc.startPairing()!!
        hosts.pair("127.0.0.1", pin, "This PC", pairingPort = otherPc.status.value.pairingPort!!)
        hosts.syncAll()
    }

    private fun playback() = DesktopPlayback(db, vlcAvailable = { true }, hosts = hosts)

    @Test
    fun aPcCopyPlaysThroughTheLoopbackProxyWithSeeking() = runBlocking {
        pairAndSync()
        val resolved = playback().resolve("drive-dune", false, driveProxy, hostProxy)
        val url = (resolved.request as PlaybackRequest.Ready).url
        assertTrue("VLC gets a loopback URL", url.startsWith("http://127.0.0.1:"))
        assertTrue("progress is keyed by the stable PC key", resolved.progressKey!!.startsWith("dchost://"))

        OkHttpClient().newCall(Request.Builder().url(url).header("Range", "bytes=100-199").build()).execute().use {
            assertEquals(206, it.code)
            assertArrayEquals(film.copyOfRange(100, 200), it.body!!.bytes())
        }
    }

    @Test
    fun whenThePcIsOffDrivePlaysInstead() = runBlocking {
        pairAndSync()
        otherPc.setSharing(false)
        val resolved = playback().resolve("drive-dune", false, driveProxy, hostProxy)
        assertEquals("drive-dune", resolved.progressKey)
    }

    @Test
    fun aTooSlowConnectionAsksAndTheChoiceIsKept() = runBlocking {
        pairAndSync()
        // Pretend the PC's copy is enormous (an impossible bitrate), so any real connection is too slow.
        db.useWriterConnection { it.usePrepared("UPDATE media_sources SET sizeBytes = 900000000000000 WHERE kind = 'HOST'") { stmt -> stmt.step() } }
        val ask = playback().resolve("drive-dune", false, driveProxy, hostProxy)
        val slow = ask.request as PlaybackRequest.SlowConnection
        assertTrue(slow.hasFallback)
        assertTrue(slow.message.contains("Drive copy"))

        assertTrue(playback().resolve("drive-dune", false, driveProxy, hostProxy, SlowChoice.PLAY_FROM_PC).progressKey!!.startsWith("dchost://"))
        assertEquals("drive-dune", playback().resolve("drive-dune", false, driveProxy, hostProxy, SlowChoice.PLAY_FALLBACK).progressKey)
    }

    @Test
    fun theProxyRefusesRequestsWithoutItsSecret() = runBlocking {
        pairAndSync()
        val url = (playback().resolve("drive-dune", false, driveProxy, hostProxy).request as PlaybackRequest.Ready).url
        val wrong = url.replace(Regex("""(:\d+/)[^/]+/"""), "$1guessed/")
        OkHttpClient().newCall(Request.Builder().url(wrong).build()).execute().use { assertEquals(404, it.code) }
    }

    @Test
    fun thisComputerIsNeverOfferedOrPairedAsAnotherPc() = runBlocking {
        val own = otherPc.hostId()
        val pcs = DesktopPcs(
            scope, hosts, ownHostId = { own },
            discover = { listOf(DiscoveredHost(own, "Study PC", "127.0.0.1", 8768), DiscoveredHost("other", "Living room", "10.0.0.9", 8768)) },
            pairingPort = { otherPc.status.value.pairingPort!! }
        )
        pcs.search()
        withTimeout(5_000) { while (pcs.state.value.isSearching) delay(10) }
        assertEquals(listOf("other"), pcs.state.value.discovered.map { it.hostId })

        val pin = otherPc.startPairing()!!
        pcs.pair("127.0.0.1", pin, "")
        withTimeout(10_000) { while (pcs.state.value.isPairing || pcs.state.value.message == null) delay(10) }
        assertTrue(pcs.state.value.message!!.contains("this computer"))
        withTimeout(5_000) { while (hosts.hosts().isNotEmpty()) delay(10) }
    }
}
