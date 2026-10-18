package com.drivecinema.desktop.player

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.desktop.library.DesktopDatabase
import com.drivecinema.desktop.ui.PlaybackRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopPlaybackTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private lateinit var proxy: DriveStreamProxy

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-play").toFile()
        db = DesktopDatabase.open(dir)
        db.mediaDao().insertMediaItems(listOf(MediaItemEntity("drive-1", MediaType.MOVIE, "Film", parentFolderId = "f")))
        proxy = DriveStreamProxy(object : TokenProvider {
            override fun getAccessToken() = "t"
            override fun forceRefresh() = "t"
        })
    }

    @After
    fun tearDown() {
        proxy.close()
        db.close()
        dir.deleteRecursively()
    }

    private fun playback(vlc: Boolean = true) = DesktopPlayback(db, vlcAvailable = { vlc })

    @Test
    fun driveTitleStreamsThroughTheProxyFromTheResumePoint() = runBlocking {
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive-1", 1_500_000, 7_200_000))

        val resumed = playback().resolve("drive-1", fromStart = false, proxy)
        val ready = resumed.request as PlaybackRequest.Ready
        assertEquals(proxy.urlFor("drive-1"), ready.url)
        assertEquals(1_500_000L, ready.startMs)
        assertEquals("drive-1", resumed.progressKey)

        val restarted = playback().resolve("drive-1", fromStart = true, proxy).request as PlaybackRequest.Ready
        assertEquals(0L, restarted.startMs)
    }

    @Test
    fun missingVlcOffersToInstallIt() = runBlocking {
        val resolved = playback(vlc = false).resolve("drive-1", false, proxy)
        assertEquals(PlaybackRequest.VlcMissing, resolved.request)
        assertNull(resolved.progressKey)
    }

    @Test
    fun signedOutOrMissingTitlesCannotPlay() = runBlocking {
        assertTrue(playback().resolve("drive-1", false, proxy = null).request is PlaybackRequest.Unavailable)
        assertTrue(playback().resolve("local:gone", false, proxy).request is PlaybackRequest.Unavailable)
    }
}
