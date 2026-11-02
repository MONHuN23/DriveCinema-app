package com.drivecinema.desktop.library

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.skipintro.IntroDbClient
import com.drivecinema.app.core.skipintro.SkipDbClient
import com.drivecinema.app.core.skipintro.SkipIntroCache
import com.drivecinema.app.core.skipintro.SkipIntroHttp
import com.drivecinema.app.core.skipintro.SkipIntroLookup

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** O.3: the desktop's "Skip intro" switch and which items are looked up (no network: the IMDb step is faked). */
class DesktopSkipIntroTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val askedShows = mutableListOf<Int>()

    private val ted = SeriesEntity("s-ted", "Ted", year = 2024, tmdbId = 201834)
    private val unmatched = SeriesEntity("s-home", "Home videos")
    private val episode = MediaItemEntity("e2", MediaType.EPISODE, "My Two Dads", parentFolderId = "s1", seasonNumber = 1, episodeNumber = 2, seriesId = "s-ted")
    private val special = MediaItemEntity("e0", MediaType.EPISODE, "Special", parentFolderId = "s0", seasonNumber = 0, episodeNumber = 1, seriesId = "s-ted")
    private val homeVideo = MediaItemEntity("h1", MediaType.EPISODE, "Holiday", parentFolderId = "h", seasonNumber = 1, episodeNumber = 1, seriesId = "s-home")
    private val film = MediaItemEntity("m1", MediaType.MOVIE, "Dune", year = 2024, parentFolderId = "films")

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-skipintro").toFile()
        db = DesktopDatabase.open(dir)
        db.mediaDao().insertSeries(listOf(ted, unmatched))
        db.mediaDao().insertMediaItems(listOf(episode, special, homeVideo, film))
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun skipIntro(settings: DesktopSettings = DesktopSettings(File(dir, "settings.json"))): DesktopSkipIntro {
        // The IMDb step answers "no IMDb ID", so the intro services are never reached.
        val http = SkipIntroHttp.newClient("DriveCinema/test")
        val lookup = SkipIntroLookup(
            imdbIdOf = { id -> askedShows += id; Result.success(null) },
            skipDb = SkipDbClient(http, "http://127.0.0.1:9"),
            introDb = IntroDbClient(http, "http://127.0.0.1:9"),
            cache = SkipIntroCache(File(dir, "cache/skip-intro-cache.json"))
        )
        return DesktopSkipIntro(settings, lookup, db.mediaDao())
    }

    @Test
    fun onByDefaultAndTheChoiceIsSaved() {
        assertTrue(skipIntro().isEnabled())
        skipIntro().setEnabled(false)
        assertFalse(DesktopSettings(File(dir, "settings.json")).skipIntroEnabled())
    }

    @Test
    fun anEpisodeIsLookedUpByItsShowsTmdbId() = runBlocking {
        assertNull(skipIntro().find("e2", 2_361_942))
        assertEquals(listOf(201834), askedShows)
    }

    @Test
    fun switchedOffNothingIsAsked() = runBlocking {
        val skipIntro = skipIntro()
        skipIntro.setEnabled(false)
        assertNull(skipIntro.find("e2", 2_361_942))
        assertTrue(askedShows.isEmpty())
    }

    @Test
    fun filmsSpecialsAndUnmatchedShowsAreNeverLookedUp() = runBlocking {
        val skipIntro = skipIntro()
        assertNull(skipIntro.find("m1", 9_000_000))
        assertNull(skipIntro.find("e0", 1_300_000))
        assertNull(skipIntro.find("h1", 1_300_000))
        assertNull(skipIntro.find("missing", 1_300_000))
        assertTrue(askedShows.isEmpty())
    }
}
