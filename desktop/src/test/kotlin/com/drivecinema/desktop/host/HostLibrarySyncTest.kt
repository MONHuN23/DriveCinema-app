package com.drivecinema.desktop.host

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.host.HostLibrarySync
import com.drivecinema.app.core.host.PairedHost
import com.drivecinema.app.core.host.PairedHostStore
import com.drivecinema.app.core.player.PlaybackSelector
import com.drivecinema.desktop.library.DesktopDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Task I.2, client side against a real PC host: the PC's titles join the catalog next to Drive, merge with the Drive
 * copy of the same film, play from the PC first, fall back to Drive when the PC is off, and watch history stays.
 */
class HostLibrarySyncTest {

    private lateinit var dir: File
    private lateinit var library: Path
    private lateinit var host: MediaHost
    private lateinit var db: DriveCinemaDatabase
    private var sharing = false
    private val stored = mutableMapOf<String, PairedHost>()
    private val store = object : PairedHostStore {
        override fun hosts() = stored.values.toList()
        override fun save(host: PairedHost) { stored[host.pairing.hostId] = host }
        override fun remove(hostId: String) { stored.remove(hostId) }
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-hostsync").toFile()
        library = Files.createDirectories(dir.toPath().resolve("Media"))
        listOf("Films/Dune (2021).mkv", "Films/Heat (1995).mkv", "Series/Ted (2024)/Season 1/Ted.S01E01.mkv").forEach {
            val file = library.resolve(it.replace('/', File.separatorChar))
            Files.createDirectories(file.parent)
            Files.write(file, ByteArray(2_000))
        }
        host = MediaHost(
            File(dir, "pc"),
            object : HostSettings {
                override fun sharingEnabled() = sharing
                override fun setSharingEnabled(enabled: Boolean) { sharing = enabled }
                override fun allowVpn() = false
                override fun setAllowVpn(allowed: Boolean) = Unit
            },
            sharedFolders = { listOf(library) },
            computerName = { "Living room PC" },
            httpsPort = 0, pairingPort = 0, discoveryPort = 0
        )
        host.setSharing(true)
        db = DesktopDatabase.open(File(dir, "phone-db").apply { mkdirs() })
        runBlocking {
            // The phone already has "Dune" from Drive, with watch progress.
            db.mediaDao().insertMediaItems(listOf(MediaItemEntity(id = "drive-dune", type = MediaType.MOVIE, title = "Dune", year = 2021, parentFolderId = "films")))
            db.mediaSourceDao().insertSourcesIfAbsent(listOf(MediaSourceEntity("drive-dune", "drive-dune", SourceKind.DRIVE)))
            db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive-dune", 60_000, 7_200_000))
        }
    }

    @After
    fun tearDown() {
        host.close()
        db.close()
        dir.deleteRecursively()
    }

    private fun sync() = HostLibrarySync(db, store, runInTransaction = { block -> db.useWriterConnection { it.immediateTransaction { block() } } })

    private fun pairedSync(): HostLibrarySync = runBlocking {
        val pin = host.startPairing()!!
        val sync = sync()
        // The pairing port is only known at run time in tests; the real app uses the fixed default port.
        val pairing = com.drivecinema.app.core.host.HostPairingClient.pair("127.0.0.1", pin, "Pixel", port = host.status.value.pairingPort!!)
        store.save(PairedHost(pairing, homeAddress = "127.0.0.1", vpnAddress = null))
        sync
    }

    @Test
    fun thePcsTitlesJoinTheCatalogAndPlayFromThePcFirst() = runBlocking {
        val sync = pairedSync()
        val result = sync.syncAll().single()
        assertEquals("127.0.0.1", result.reachableAt)
        assertEquals(3, result.videosFound)

        val dune = PlaybackSelector(db.mediaSourceDao()).candidates("drive-dune")
        assertEquals("the PC copy merged into the Drive title", listOf(SourceKind.HOST, SourceKind.DRIVE), dune.map { it.kind })
        assertEquals("resume comes from the Drive copy's history", 60_000L, dune.first().resumePositionMs)
        assertTrue(db.mediaDao().getAllMovies().any { it.title == "Heat" })

        val (url, api) = sync.stream(dune.first().sourceKey)!!
        assertTrue(url.startsWith("https://127.0.0.1:"))
        assertEquals("127.0.0.1", api.activeAddress)
    }

    @Test
    fun theConnectionCheckMeasuresTheRealConnectionToThePc() = runBlocking {
        // Bigger than the 2 MB sample, so there is something to time after the first bytes.
        Files.write(library.resolve("Films").resolve("Big (2000).mkv"), ByteArray(3 * 1024 * 1024))
        val sync = pairedSync()
        sync.syncAll()
        val big = db.mediaDao().getAllMovies().single { it.title == "Big" }
        val key = PlaybackSelector(db.mediaSourceDao()).candidates(big.id).single().sourceKey
        val verdict = sync.checkConnection(big.id, key)!!
        // 3 MB over the typical 110-minute length: a tiny bitrate that a local connection easily beats.
        assertTrue(verdict.measuredMbps > 0)
        assertFalse(verdict.tooSlow)
    }

    @Test
    fun hiddenLeftoversDoNotTurnAPcCopyIntoADuplicate() = runBlocking {
        // Found on the device: after a show folder was merged on Drive, an emptied second row made the match ambiguous.
        db.mediaDao().insertSeries(listOf(SeriesEntity(id = "drive-ted", title = "Ted", year = 2024), SeriesEntity(id = "emptied-ted", title = "Ted", year = 2024)))
        db.mediaDao().insertMediaItems(listOf(
            MediaItemEntity(id = "drive-ted-e1", type = MediaType.EPISODE, title = "Pilot", parentFolderId = "s1", seriesId = "drive-ted", seasonNumber = 1, episodeNumber = 1),
            MediaItemEntity(id = "old-dune", type = MediaType.MOVIE, title = "Dune", year = 2021, parentFolderId = "old", isDeleted = true)
        ))
        db.mediaSourceDao().insertSourcesIfAbsent(listOf(MediaSourceEntity("drive-ted-e1", "drive-ted-e1", SourceKind.DRIVE)))

        pairedSync().syncAll()

        assertEquals("one Ted, with both copies of the episode", listOf("drive-ted"), db.mediaDao().getAllSeriesFlow().first().filter { it.title == "Ted" }.map { it.id })
        assertEquals(listOf(SourceKind.HOST, SourceKind.DRIVE), PlaybackSelector(db.mediaSourceDao()).candidates("drive-ted-e1").map { it.kind })
        assertEquals("the PC's Dune joins the visible Dune, not the deleted one", listOf(SourceKind.HOST, SourceKind.DRIVE),
            PlaybackSelector(db.mediaSourceDao()).candidates("drive-dune").map { it.kind })
        assertEquals(1, db.mediaDao().getAllMovies().count { it.title == "Dune" })
    }

    @Test
    fun copiesIndexedBeforeTheirDriveCopyAppearedMergeOnTheNextSync() = runBlocking {
        // Found on the device: "Avatar Seven Havens" ep 1 from the PC stayed a show of its own after the Drive show came.
        val sync = pairedSync()
        sync.syncAll()
        val pcOnlyTed = db.mediaDao().getAllSeriesFlow().first().single { it.title == "Ted" }
        assertTrue(pcOnlyTed.id.startsWith("local:"))

        // Now the Drive sync brings the same show (with episode 1) and the same film.
        db.mediaDao().insertSeries(listOf(SeriesEntity(id = "drive-ted", title = "Ted", year = 2024)))
        db.mediaDao().insertMediaItems(listOf(
            MediaItemEntity(id = "drive-ted-e1", type = MediaType.EPISODE, title = "Pilot", parentFolderId = "s1", seriesId = "drive-ted", seasonNumber = 1, episodeNumber = 1),
            MediaItemEntity(id = "drive-heat", type = MediaType.MOVIE, title = "Heat", year = 1995, parentFolderId = "films")
        ))
        db.mediaSourceDao().insertSourcesIfAbsent(listOf(
            MediaSourceEntity("drive-ted-e1", "drive-ted-e1", SourceKind.DRIVE), MediaSourceEntity("drive-heat", "drive-heat", SourceKind.DRIVE)
        ))
        sync.syncAll()

        assertEquals("one Ted", listOf("drive-ted"), db.mediaDao().getAllSeriesFlow().first().filter { it.title == "Ted" }.map { it.id })
        assertEquals(listOf(SourceKind.HOST, SourceKind.DRIVE), PlaybackSelector(db.mediaSourceDao()).candidates("drive-ted-e1").map { it.kind })
        assertEquals("one Heat", listOf("drive-heat"), db.mediaDao().getAllMovies().filter { it.title == "Heat" }.map { it.id })
        assertEquals(listOf(SourceKind.HOST, SourceKind.DRIVE), PlaybackSelector(db.mediaSourceDao()).candidates("drive-heat").map { it.kind })
    }

    @Test
    fun whenThePcIsOffItsCopiesStopAndDriveTakesOver() = runBlocking {
        val sync = pairedSync()
        sync.syncAll()
        host.setSharing(false)

        assertNull(sync().syncAll().single().reachableAt)
        val dune = PlaybackSelector(db.mediaSourceDao()).candidates("drive-dune")
        assertEquals(listOf(SourceKind.DRIVE), dune.map { it.kind })
        assertFalse("a PC-only title is hidden while the PC is off", db.mediaDao().getAllMovies().any { it.title == "Heat" })
        assertNotNull("watch history kept", db.playbackProgressDao().getProgress("drive-dune"))

        host.setSharing(true)
        // Tests use a random port; the real app always uses the same one, so the pairing stays valid.
        stored.replaceAll { _, h -> h.copy(pairing = h.pairing.copy(httpsPort = host.status.value.port!!)) }
        val back = sync()
        back.syncAll()
        assertTrue("back when the PC is on again", db.mediaDao().getAllMovies().any { it.title == "Heat" })
    }

    @Test
    fun aDeviceThePcRemovedForgetsThePcOnTheNextSync() = runBlocking {
        // Found on the device: after Remove on the PC, the phone still listed the PC as paired.
        val sync = pairedSync()
        sync.syncAll()
        val hostId = stored.keys.single()
        host.removeDevice(host.status.value.devices.single().deviceId)

        val result = sync.syncAll().single()
        assertTrue(result.removedByPc)
        assertTrue("the pairing is forgotten", stored.isEmpty())
        assertEquals(listOf(SourceKind.DRIVE), PlaybackSelector(db.mediaSourceDao()).candidates("drive-dune").map { it.kind })
        assertFalse(db.mediaDao().getAllMovies().any { it.title == "Heat" })
        assertNull(sync.stream("dchost://$hostId/file/abc"))
    }

    @Test
    fun aPcThatIsMerelyOffIsKept() = runBlocking {
        val sync = pairedSync()
        sync.syncAll()
        host.setSharing(false)
        val result = sync.syncAll().single()
        assertFalse(result.removedByPc)
        assertEquals("still paired, just unreachable", 1, stored.size)
    }

    @Test
    fun removingThePcForgetsItsKeyAndItsCopies() = runBlocking {
        val sync = pairedSync()
        sync.syncAll()
        val hostId = stored.keys.single()
        sync.remove(hostId)
        assertTrue(stored.isEmpty())
        assertEquals(listOf(SourceKind.DRIVE), PlaybackSelector(db.mediaSourceDao()).candidates("drive-dune").map { it.kind })
        assertNull(sync.stream("dchost://$hostId/file/abc"))
    }
}
