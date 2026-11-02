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

/** The player's "Previous episode" button: the same lookup as Next, backwards, across seasons. */
class PreviousEpisodeFindTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-previous").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        fun ep(id: String, season: Int, episode: Int, title: String) =
            MediaItemEntity(id, MediaType.EPISODE, title, parentFolderId = "s$season", seasonNumber = season, episodeNumber = episode, seriesId = "dark")
        db.mediaDao().insertSingleSeries(SeriesEntity("dark", "Dark"))
        db.mediaDao().insertMediaItems(
            listOf(
                ep("e3", 2, 1, "Ghosts"), ep("e2", 1, 2, "Lies"), ep("e1", 1, 1, "Secrets"), ep("e4", 2, 2, "Sic Mundus"),
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
    fun findsThePrecedingEpisodeWithItsLabel() = runBlocking {
        assertEquals("e1" to "Dark - S1E1 Secrets", PreviousEpisode.find(db.mediaDao(), "e2"))
        assertEquals("first episode of a season", "e3" to "Dark - S2E1 Ghosts", PreviousEpisode.find(db.mediaDao(), "e4"))
        assertEquals("across seasons", "e2" to "Dark - S1E2 Lies", PreviousEpisode.find(db.mediaDao(), "e3"))
        assertNull("first episode overall", PreviousEpisode.find(db.mediaDao(), "e1"))
        assertNull("films have no previous episode", PreviousEpisode.find(db.mediaDao(), "film"))
        assertNull("unknown item", PreviousEpisode.find(db.mediaDao(), "missing"))
    }

    @Test
    fun beforeIgnoresAnIdThatIsNotInTheList() {
        val episodes = listOf(MediaItemEntity("a", MediaType.EPISODE, "A", parentFolderId = "s", seasonNumber = 1, episodeNumber = 1))
        assertNull(PreviousEpisode.before(episodes, "b"))
        assertNull(PreviousEpisode.before(emptyList(), "a"))
    }
}
