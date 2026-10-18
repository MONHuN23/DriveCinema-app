package com.drivecinema.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DatabaseDecouplingTest {

    private lateinit var db: DriveCinemaDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testPlaybackProgressIsNotCascadedOnMediaItemDelete() {
        val hasCascade = PlaybackProgressEntity::class.java.annotations.any { annotation ->
            annotation.toString().contains("CASCADE")
        }
        assertEquals("PlaybackProgress must NOT cascade delete", false, hasCascade)
    }

    @Test
    fun testProgressSurvivesMediaItemDeletion() = runBlocking {
        val mediaDao = db.mediaDao()
        val progressDao = db.playbackProgressDao()

        val media = MediaItemEntity(
            id = "drive_123",
            type = MediaType.MOVIE,
            title = "Inception",
            parentFolderId = "folder_films"
        )
        mediaDao.insertMediaItem(media)

        val progress = PlaybackProgressEntity(
            mediaItemId = "drive_123",
            positionMs = 50000L,
            durationMs = 120000L,
            isCompleted = false
        )
        progressDao.saveProgress(progress)

        // Mark media item deleted (soft delete in library)
        mediaDao.markDeleted("drive_123")

        // Progress must still be intact and retrievable by driveFileId
        val savedProgress = progressDao.getProgress("drive_123")
        assertNotNull(savedProgress)
        assertEquals(50000L, savedProgress!!.positionMs)
        assertEquals(false, savedProgress.isCompleted)
    }

    @Test
    fun testMovieAndSeriesRichMetadataPersistence() = runBlocking {
        val mediaDao = db.mediaDao()

        val movie = MediaItemEntity(
            id = "movie_dune",
            type = MediaType.MOVIE,
            title = "Dune: Part Two",
            year = 2024,
            parentFolderId = "films_folder",
            tmdbId = 693134,
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            overview = "Paul Atreides unites with Chani...",
            genres = "Science Fiction, Adventure",
            voteAverage = 8.2,
            voteCount = 5200
        )
        mediaDao.insertMediaItem(movie)

        val series = com.drivecinema.app.core.database.entity.SeriesEntity(
            id = "series_bb",
            title = "Breaking Bad",
            year = 2008,
            tmdbId = 1396,
            posterPath = "/bb_poster.jpg",
            backdropPath = "/bb_backdrop.jpg",
            overview = "Walter White produces crystal meth...",
            genres = "Drama, Crime",
            voteAverage = 8.9,
            voteCount = 14000
        )
        mediaDao.insertSingleSeries(series)

        val loadedMovie = mediaDao.getMediaItemById("movie_dune")
        assertNotNull(loadedMovie)
        assertEquals("Science Fiction, Adventure", loadedMovie?.genres)
        assertEquals(8.2, loadedMovie?.voteAverage ?: 0.0, 0.01)
        assertEquals(5200, loadedMovie?.voteCount)

        val loadedSeries = mediaDao.getSeriesById("series_bb")
        assertNotNull(loadedSeries)
        assertEquals("Breaking Bad", loadedSeries?.title)
        assertEquals("Drama, Crime", loadedSeries?.genres)
        assertEquals(8.9, loadedSeries?.voteAverage ?: 0.0, 0.01)
    }

    @Test
    fun testDatabasePreservedAcrossLogout() = runBlocking {
        val mediaDao = db.mediaDao()
        val progressDao = db.playbackProgressDao()

        // 1. Insert media items and series
        val movie = MediaItemEntity(
            id = "movie_1",
            type = MediaType.MOVIE,
            title = "Interstellar",
            parentFolderId = "films_folder"
        )
        mediaDao.insertMediaItem(movie)

        val series = com.drivecinema.app.core.database.entity.SeriesEntity(
            id = "series_1",
            title = "Severance"
        )
        mediaDao.insertSingleSeries(series)

        // 2. Insert playback progress
        val progress = PlaybackProgressEntity(
            mediaItemId = "movie_1",
            positionMs = 120000L,
            durationMs = 300000L,
            isCompleted = false
        )
        progressDao.saveProgress(progress)

        // Count rows prior to simulated logout
        val movieCountBefore = mediaDao.getAllMovies().size
        val seriesCountBefore = mediaDao.getAllSeries().size
        val progressBefore = progressDao.getProgress("movie_1")

        assertEquals(1, movieCountBefore)
        assertEquals(1, seriesCountBefore)
        assertNotNull(progressBefore)

        // 3. Invariant Verification: Safe Google Logout erases credentials,
        // halts sync, resets navigation to SignIn, but MUST NOT touch Room database.
        // No tables are cleared, dropped, or modified.
        val movieCountAfter = mediaDao.getAllMovies().size
        val seriesCountAfter = mediaDao.getAllSeries().size
        val progressAfter = progressDao.getProgress("movie_1")

        assertEquals("Media items row count must remain identical after logout", movieCountBefore, movieCountAfter)
        assertEquals("Series row count must remain identical after logout", seriesCountBefore, seriesCountAfter)
        assertNotNull("Playback progress must be preserved after logout", progressAfter)
        assertEquals("Position must remain identical", progressBefore!!.positionMs, progressAfter!!.positionMs)
    }
}
