package com.drivecinema.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MediaDaoSearchTest {

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
    fun testSearchMoviesReturnsMatchingTitlesOnly() = runBlocking {
        val mediaDao = db.mediaDao()

        mediaDao.insertMediaItems(
            listOf(
                MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Spider-Man: Homecoming", parentFolderId = "f1"),
                MediaItemEntity(id = "m2", type = MediaType.MOVIE, title = "Dune: Part Two", parentFolderId = "f1"),
                MediaItemEntity(id = "m3", type = MediaType.MOVIE, title = "The Amazing Spider-Man", parentFolderId = "f1"),
                MediaItemEntity(id = "m4", type = MediaType.MOVIE, title = "Interstellar", parentFolderId = "f1")
            )
        )

        val results = mediaDao.searchMoviesFlow("Spider").first()
        assertEquals(2, results.size)
        assertEquals("Spider-Man: Homecoming", results[0].title)
        assertEquals("The Amazing Spider-Man", results[1].title)
    }

    @Test
    fun testSearchMoviesIsCaseInsensitive() = runBlocking {
        val mediaDao = db.mediaDao()

        mediaDao.insertMediaItem(
            MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Dune: Part Two", parentFolderId = "f1")
        )

        val resultsLower = mediaDao.searchMoviesFlow("dune").first()
        assertEquals(1, resultsLower.size)

        val resultsUpper = mediaDao.searchMoviesFlow("DUNE").first()
        assertEquals(1, resultsUpper.size)
    }

    @Test
    fun testSearchMoviesExcludesDeleted() = runBlocking {
        val mediaDao = db.mediaDao()

        mediaDao.insertMediaItems(
            listOf(
                MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Dune", parentFolderId = "f1", isDeleted = false),
                MediaItemEntity(id = "m2", type = MediaType.MOVIE, title = "Dune 1984", parentFolderId = "f1", isDeleted = true)
            )
        )

        val results = mediaDao.searchMoviesFlow("Dune").first()
        assertEquals(1, results.size)
        assertEquals("m1", results[0].id)
    }

    @Test
    fun testSearchSeriesReturnsMatchingTitles() = runBlocking {
        val mediaDao = db.mediaDao()

        mediaDao.insertSeries(
            listOf(
                SeriesEntity(id = "s1", title = "Breaking Bad"),
                SeriesEntity(id = "s2", title = "Better Call Saul"),
                SeriesEntity(id = "s3", title = "Dark")
            )
        )

        val results = mediaDao.searchSeriesFlow("Bad").first()
        assertEquals(1, results.size)
        assertEquals("Breaking Bad", results[0].title)
    }

    @Test
    fun testEmptyQueryReturnsAllUnfiltered() = runBlocking {
        val mediaDao = db.mediaDao()

        mediaDao.insertMediaItems(
            listOf(
                MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Movie A", parentFolderId = "f1"),
                MediaItemEntity(id = "m2", type = MediaType.MOVIE, title = "Movie B", parentFolderId = "f1")
            )
        )
        mediaDao.insertSeries(
            listOf(
                SeriesEntity(id = "s1", title = "Series A"),
                SeriesEntity(id = "s2", title = "Series B")
            )
        )

        val movies = mediaDao.searchMoviesFlow("").first()
        val series = mediaDao.searchSeriesFlow("").first()

        assertEquals(2, movies.size)
        assertEquals(2, series.size)
    }
}
