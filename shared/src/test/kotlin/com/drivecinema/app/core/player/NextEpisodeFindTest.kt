package com.drivecinema.app.core.player

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Report F-29: the player and MainActivity find the next episode with the same lookup, across seasons. */
class NextEpisodeFindTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-next").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        fun ep(id: String, season: Int, episode: Int, title: String) =
            MediaItemEntity(id, MediaType.EPISODE, title, parentFolderId = "s$season", seasonNumber = season, episodeNumber = episode, seriesId = "dark")
        db.mediaDao().insertSingleSeries(SeriesEntity("dark", "Dark"))
        db.mediaDao().insertMediaItems(
            listOf(
                ep("e2", 1, 2, "Lies"), ep("e1", 1, 1, "Secrets"), ep("e3", 2, 1, "Ghosts"),
                MediaItemEntity("film", MediaType.MOVIE, "Alien", parentFolderId = "films")
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun findsTheFollowingEpisodeWithItsLabel() = runBlocking {
        assertEquals("e2" to "Dark - S1E2 Lies", NextEpisode.find(db.mediaDao(), "e1"))
        assertEquals("across seasons", "e3" to "Dark - S2E1 Ghosts", NextEpisode.find(db.mediaDao(), "e2"))
        assertNull("last episode", NextEpisode.find(db.mediaDao(), "e3"))
        assertNull("films have no next episode", NextEpisode.find(db.mediaDao(), "film"))
    }
}
