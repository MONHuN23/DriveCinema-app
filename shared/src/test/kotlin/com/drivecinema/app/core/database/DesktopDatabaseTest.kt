package com.drivecinema.app.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Task F.2: the shared Room database works on the desktop JVM with the bundled SQLite driver. */
class DesktopDatabaseTest {

    private lateinit var dir: File
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-db").toFile()
        dbFile = File(dir, DriveCinemaDatabase.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun open(): DriveCinemaDatabase =
        Room.databaseBuilder<DriveCinemaDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private val dune = MediaItemEntity(
        id = "drive-dune",
        type = MediaType.MOVIE,
        title = "Dune Part Two",
        year = 2024,
        parentFolderId = "films"
    )

    @Test
    fun freshDatabaseStoresCatalogAndSearches() = runBlocking {
        val db = open()
        try {
            db.mediaDao().insertMediaItems(listOf(dune))

            assertEquals(listOf("drive-dune"), db.mediaDao().getAllMovies().map { it.id })
            assertEquals(listOf("drive-dune"), db.mediaDao().searchMoviesFlow("Dune").first().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun watchProgressSurvivesClosingAndReopening() = runBlocking {
        val first = open()
        first.mediaDao().insertMediaItems(listOf(dune))
        first.playbackProgressDao().saveProgress(
            PlaybackProgressEntity(mediaItemId = "drive-dune", positionMs = 4_200_000L, durationMs = 9_960_000L)
        )
        first.close()

        val reopened = open()
        try {
            val progress = reopened.playbackProgressDao().getProgress("drive-dune")
            assertNotNull(progress)
            assertEquals(4_200_000L, progress!!.positionMs)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun createsTheCurrentSchemaVersion() {
        runBlocking { open().apply { mediaDao().getAllMovies() }.close() }
        assertTrue(dbFile.exists())

        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            val version = connection.prepare("PRAGMA user_version").use { statement ->
                statement.step()
                statement.getLong(0)
            }
            assertEquals(3L, version)
        } finally {
            connection.close()
        }
    }
}
