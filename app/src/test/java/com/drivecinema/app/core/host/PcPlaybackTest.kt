package com.drivecinema.app.core.host

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.player.HostRoutingDataSource
import com.drivecinema.app.ui.player.PlaybackResolver
import com.drivecinema.app.ui.player.PlaybackSource
import com.drivecinema.app.ui.player.PlayerUriBuilder
import com.drivecinema.app.ui.player.ResolvedPlayback
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

/** Task I.2 on Android: PC copies play first, fall back to Drive when the PC is off, and stream only via the PC's pinned client. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PcPlaybackTest {

    @get:Rule val temp = TemporaryFolder()
    private lateinit var db: DriveCinemaDatabase
    private val hostKey = HostProtocol.fileSourceKey("host1", "file1")

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DriveCinemaDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.mediaDao().insertMediaItem(MediaItemEntity(id = "d1", type = MediaType.MOVIE, title = "Dune", parentFolderId = "f"))
            db.mediaDao().insertSourcesIfAbsent(
                listOf(MediaSourceEntity("d1", "d1", SourceKind.DRIVE), MediaSourceEntity(hostKey, "d1", SourceKind.HOST))
            )
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun thePcCopyPlaysFirstWhenThePcAnswers() = runBlocking {
        val resolver = PlaybackResolver(db.mediaSourceDao(), hostUrl = { "https://192.168.1.20:8768/v1/file/file1" })
        val resolved = resolver.resolve("d1") as ResolvedPlayback.Playable
        assertEquals(PlaybackSource.Host(hostKey, "https://192.168.1.20:8768/v1/file/file1"), resolved.source)
        assertEquals("progress is keyed by the stable PC key, not the address", hostKey, resolved.source.progressKey)
    }

    @Test
    fun whenThePcIsOffDrivePlaysInstead() = runBlocking {
        val resolver = PlaybackResolver(db.mediaSourceDao(), hostUrl = { null })
        assertEquals(PlaybackSource.Drive("d1"), (resolver.resolve("d1") as ResolvedPlayback.Playable).source)
    }

    @Test
    fun aTooSlowConnectionOffersDriveInsteadAndAFastOneJustPlays() = runBlocking {
        val url = "https://192.168.1.20:8768/v1/file/file1"
        val slow = PlaybackResolver(db.mediaSourceDao(), hostUrl = { url }, checkHost = { _, _ -> ConnectionVerdict(8.0, 3.0) })
        val result = slow.resolve("d1") as ResolvedPlayback.SlowConnection
        assertEquals(PlaybackSource.Host(hostKey, url), result.pc.source)
        assertEquals(PlaybackSource.Drive("d1"), result.fallback!!.source)

        val fast = PlaybackResolver(db.mediaSourceDao(), hostUrl = { url }, checkHost = { _, _ -> ConnectionVerdict(8.0, 80.0) })
        assertEquals(PlaybackSource.Host(hostKey, url), (fast.resolve("d1") as ResolvedPlayback.Playable).source)

        val unknown = PlaybackResolver(db.mediaSourceDao(), hostUrl = { url }, checkHost = { _, _ -> null })
        assertTrue("no measurement: just play", unknown.resolve("d1") is ResolvedPlayback.Playable)
    }

    @Test
    fun thePlayerAcceptsOnlyValidPcKeysAndHttpsUrls() {
        assertEquals(
            PlaybackSource.Host(hostKey, "https://10.0.0.2:8768/v1/file/file1"),
            PlayerUriBuilder.resolveSource(null, null, hostKey, "https://10.0.0.2:8768/v1/file/file1")
        )
        assertNull(PlayerUriBuilder.resolveSource(null, null, hostKey, "http://10.0.0.2:8768/v1/file/file1"))
        assertNull(PlayerUriBuilder.resolveSource(null, null, "file:///sdcard/x.mkv", "https://10.0.0.2/x"))
        assertEquals(PlaybackSource.Drive("abc"), PlayerUriBuilder.resolveSource("abc", null, "dchost://h/root/r", "https://x/y"))
    }

    @Test
    fun pairingsAreStoredEncryptedAndReadBack() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val file = File(temp.root, "paired_hosts.enc")
        val pairing = HostPairing("host1", "Living room PC", "a".repeat(64), 8768, "dev1", "super-secret-device-key")
        AndroidPairedHostStore(file) { key }.save(PairedHost(pairing, "192.168.1.20", "100.101.102.103"))

        assertFalse("the key is not readable in the file", String(file.readBytes(), Charsets.ISO_8859_1).contains("super-secret-device-key"))
        assertEquals(listOf(PairedHost(pairing, "192.168.1.20", "100.101.102.103")), AndroidPairedHostStore(file) { key }.hosts())
        assertTrue("another key can't read it", AndroidPairedHostStore(file) { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() }.hosts().isEmpty())
    }

    @Test
    fun withoutAKeyStoreNothingIsWrittenToDisk() {
        val file = File(temp.root, "paired_hosts.enc")
        val store = AndroidPairedHostStore(file) { null }
        store.save(PairedHost(HostPairing("h", "PC", "a".repeat(64), 8768, "d", "k"), "10.0.0.2", null))
        assertEquals(1, store.hosts().size)
        assertFalse(file.exists())
    }

    @Test
    fun pcUrlsGoThroughThePcClientAndEverythingElseThroughDrive() {
        val opened = mutableListOf<String>()
        val drive = object : DataSource {
            override fun addTransferListener(transferListener: TransferListener) = Unit
            override fun open(dataSpec: DataSpec): Long { opened += "drive:${dataSpec.uri}"; return 0 }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = -1
            override fun getUri(): Uri? = null
            override fun close() = Unit
        }
        val pcClient = OkHttpClient.Builder().addInterceptor { chain ->
            opened += "pc:${chain.request().url}"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("video".toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        val source = HostRoutingDataSource(drive) { host, port -> pcClient.takeIf { host == "192.168.1.20" && port == 8768 } }

        source.open(DataSpec(Uri.parse("https://192.168.1.20:8768/v1/file/file1")))
        source.close()
        source.open(DataSpec(Uri.parse("https://www.googleapis.com/drive/v3/files/x?alt=media")))
        source.close()

        assertEquals(listOf("pc:https://192.168.1.20:8768/v1/file/file1", "drive:https://www.googleapis.com/drive/v3/files/x?alt=media"), opened)
    }

    @Test
    fun theFirstPcFoundFillsTheEmptyAddressField() {
        // Found on the device: the search found the PC, but its address still had to be typed in.
        val found = listOf(DiscoveredHost("h1", "Living room PC", "192.168.1.20", 8768), DiscoveredHost("h2", "Study", "192.168.1.30", 8768))
        assertEquals("192.168.1.20", com.drivecinema.app.ui.settings.firstFoundAddress("", found))
        assertNull("never overwrites what the user typed", com.drivecinema.app.ui.settings.firstFoundAddress("10.0.0.5", found))
        assertNull(com.drivecinema.app.ui.settings.firstFoundAddress("", emptyList()))
    }
}
