package com.drivecinema.desktop.browse

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.desktop.library.DesktopDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BrowseModelsTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private lateinit var models: BrowseModels

    private val dune = MediaItemEntity("m-dune", MediaType.MOVIE, "Dune Part Two", year = 2024, runtimeSeconds = 9960, parentFolderId = "films")
    private val alien = MediaItemEntity("m-alien", MediaType.MOVIE, "Alien", year = 1979, parentFolderId = "films")
    private val show = SeriesEntity("s-dark", "Dark", year = 2017)
    private val s1e1 = MediaItemEntity("e1", MediaType.EPISODE, "Secrets", parentFolderId = "s1", seasonNumber = 1, episodeNumber = 1, seriesId = "s-dark")
    private val s1e2 = MediaItemEntity("e2", MediaType.EPISODE, "Lies", parentFolderId = "s1", seasonNumber = 1, episodeNumber = 2, seriesId = "s-dark")
    private val s2e1 = MediaItemEntity("e3", MediaType.EPISODE, "Ghosts", parentFolderId = "s2", seasonNumber = 2, episodeNumber = 1, seriesId = "s-dark")

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-browse").toFile()
        db = DesktopDatabase.open(dir)
        models = BrowseModels(db)
        db.mediaDao().insertMediaItems(listOf(dune, alien, s1e1, s1e2, s2e1))
        db.mediaDao().insertSingleSeries(show)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun homeListsFilmsSeriesAndContinueWatching() = runBlocking {
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m-dune", 3_600_000, 9_960_000, lastWatchedAt = 2))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("e2", 600_000, 3_000_000, lastWatchedAt = 3))

        val home = models.home().first()
        assertEquals(listOf("Alien", "Dune Part Two"), home.movies.map { it.title })
        assertEquals(listOf("Dark"), home.series.map { it.title })
        assertEquals(listOf("e2", "m-dune"), home.continueWatching.map { it.id })
    }

    @Test
    fun continueWatchingKeepsOneCardPerSeriesWithUpNextAcrossSeasons() = runBlocking {
        // Milestone J.2, on the real query: S1E1 started, then S1E2 finished → one "Up next: S2E1" card.
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("e1", 600_000, 3_000_000, lastWatchedAt = 1))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("e2", 3_000_000, 3_000_000, isCompleted = true, lastWatchedAt = 5))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m-dune", 60_000, 9_960_000, lastWatchedAt = 3))

        val cards = models.home().first().continueWatching
        assertEquals(listOf("e3", "m-dune"), cards.map { it.id })
        val upNext = cards.first()
        assertTrue(upNext.isUpNext)
        assertEquals("Dark", upNext.title)
        assertEquals("S2E1", upNext.episodeCode)
    }

    @Test
    fun continueWatchingLeadsToTheFilmOrTheSeries() = runBlocking {
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("e2", 600_000, 3_000_000))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m-dune", 60_000, 9_960_000))
        val items = models.home().first().continueWatching.associateBy { it.id }

        assertEquals(BrowseTarget.Series("s-dark", "Dark"), models.target(items.getValue("e2")))
        assertEquals(BrowseTarget.Movie("m-dune", "Dune Part Two"), models.target(items.getValue("m-dune")))
    }

    @Test
    fun searchFindsFilmsAndSeriesAfterDebounce() = runBlocking {
        val query = MutableStateFlow("")
        val results = models.search(query)
        assertTrue(withTimeout(5_000) { results.first() }.isEmpty)

        query.value = " da "
        val found = withTimeout(5_000) { results.first { it.query == "da" } }
        assertEquals(listOf("Dark"), found.series.map { it.title })
        assertTrue(found.movies.isEmpty())

        query.value = "dune"
        assertEquals(listOf("Dune Part Two"), withTimeout(5_000) { results.first { it.query == "dune" } }.movies.map { it.title })
    }

    @Test
    fun movieDetailsCarryResumePosition() = runBlocking {
        assertNull(models.movie("m-dune").first()!!.resumePositionMs)

        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m-dune", 3_723_000, 9_960_000))
        assertEquals(3_723_000L, models.movie("m-dune").first()!!.resumePositionMs)

        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m-dune", 9_900_000, 9_960_000, isCompleted = true))
        assertNull("Finished films start over", models.movie("m-dune").first()!!.resumePositionMs)
        assertNull(models.movie("missing").first())
    }

    @Test
    fun seriesDetailsGroupEpisodesBySeason() = runBlocking {
        val details = models.series("s-dark").first()!!
        assertEquals(listOf(1, 2), details.seasons.keys.toList())
        assertEquals(listOf("e1", "e2"), details.seasons.getValue(1).map { it.id })
    }

    @Test
    fun seriesDetailsCarryTheLatestProgressOfEachEpisodeAcrossCopies() = runBlocking {
        // Milestone J.1: e1 watched on Drive, then restarted from a local copy (newer) → the local progress wins.
        db.mediaSourceDao().insertSourcesIfAbsent(listOf(
            MediaSourceEntity("e1", "e1", SourceKind.DRIVE),
            MediaSourceEntity("local:e1", "e1", SourceKind.LOCAL)
        ))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("e1", 1_400_000, 1_440_000, isCompleted = true, lastWatchedAt = 1))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("local:e1", 300_000, 1_440_000, lastWatchedAt = 2))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("e2", 1_440_000, 1_440_000, isCompleted = true, lastWatchedAt = 3))

        val progress = models.series("s-dark").first()!!.progress
        assertEquals(setOf("e1", "e2"), progress.keys)
        assertEquals(300_000L, progress.getValue("e1").positionMs)
        assertTrue(progress.getValue("e2").isCompleted)
        assertNull(progress["e3"])
    }

    @Test
    fun runtimeFormatting() {
        assertEquals("2h 46m", BrowseModels.formatRuntime(9960))
        assertEquals("45m", BrowseModels.formatRuntime(2700))
        assertNull(BrowseModels.formatRuntime(null))
        assertNull(BrowseModels.formatRuntime(30))
    }
}
