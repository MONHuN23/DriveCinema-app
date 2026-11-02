package com.drivecinema.app.core.remote

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
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

/** Milestone D: which title the TV plays for a request (design §5, §6) and what the phone sends. */
class PlayOnTvRoutingTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private lateinit var catalog: PlayCatalog

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-playontv").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        catalog = MediaDaoPlayCatalog(db.mediaDao())
        fun ep(id: String, season: Int, episode: Int, seriesId: String = "dark", deleted: Boolean = false) =
            MediaItemEntity(id, MediaType.EPISODE, "Dark", parentFolderId = "s$season", seasonNumber = season, episodeNumber = episode, seriesId = seriesId, isDeleted = deleted)
        db.mediaDao().insertSeries(listOf(SeriesEntity("dark", "Dark", tmdbId = 70523), SeriesEntity("local:series:x", "Dark (local)", tmdbId = 70523)))
        db.mediaDao().insertMediaItems(
            listOf(
                ep("driveE1", 1, 1), ep("driveE2", 1, 2), ep("gone", 1, 3, deleted = true),
                MediaItemEntity("driveAlien", MediaType.MOVIE, "Alien", parentFolderId = "films", tmdbId = 348),
                MediaItemEntity("local:arrival", MediaType.MOVIE, "Arrival", parentFolderId = "local", tmdbId = 329865)
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun film(id: String, tmdbId: Int? = null) = PlayRequest(id, false, tmdbId, null, null, "Film", 0)
    private fun episode(id: String, tmdbId: Int?, season: Int, number: Int) = PlayRequest(id, true, tmdbId, season, number, "Ep", 0)

    @Test
    fun aDriveTitleIsFoundByItsId() = runBlocking {
        assertEquals("driveAlien", PlayOnTvRouting.find(catalog, film("driveAlien")))
        assertEquals("driveE2", PlayOnTvRouting.find(catalog, episode("driveE2", null, 1, 2)))
    }

    @Test
    fun aPhoneOnlyLocalIdIsMatchedThroughTmdb() = runBlocking {
        // The phone's local:… ID means nothing on the TV, even if the TV happens to have a row with that ID.
        assertEquals("driveAlien", PlayOnTvRouting.find(catalog, film("local:phoneAlien", tmdbId = 348)))
        assertEquals("driveE1", PlayOnTvRouting.find(catalog, episode("local:phoneE1", 70523, 1, 1)))
        assertNull("local IDs never match directly", PlayOnTvRouting.find(catalog, film("local:arrival")))
    }

    @Test
    fun missingOrDeletedTitlesAreNotFound() = runBlocking {
        assertNull(PlayOnTvRouting.find(catalog, film("unknown")))
        assertNull("no TMDB ID to fall back on", PlayOnTvRouting.find(catalog, episode("unknown", null, 1, 1)))
        assertNull("deleted episode", PlayOnTvRouting.find(catalog, episode("gone", 70523, 1, 3)))
        assertNull("no such episode", PlayOnTvRouting.find(catalog, episode("x", 70523, 9, 9)))
        assertNull("kind must match", PlayOnTvRouting.find(catalog, episode("driveAlien", null, 1, 1)))
    }

    @Test
    fun decideNeedsSignInAndATitle() = runBlocking {
        assertEquals(PlayDecision(PlayStatus.NOT_SIGNED_IN), PlayOnTvRouting.decide(false, catalog, film("driveAlien")))
        assertEquals(PlayDecision(PlayStatus.NOT_IN_LIBRARY), PlayOnTvRouting.decide(true, catalog, film("nope")))
        assertEquals(PlayDecision(PlayStatus.OK, "driveAlien"), PlayOnTvRouting.decide(true, catalog, film("driveAlien")))
    }

    @Test
    fun thePhoneSendsItsIdentityAndPosition() = runBlocking {
        val progress = db.playbackProgressDao()
        progress.saveProgress(PlaybackProgressEntity("driveE2", positionMs = 25 * 60_000L, durationMs = 50 * 60_000L))
        val ep = PlayOnTvRouting.buildRequest(catalog, db.mediaSourceDao(), "driveE2", "Dark - S1E2")
        assertEquals(PlayRequest("driveE2", true, 70523, 1, 2, "Dark - S1E2", 25 * 60_000L), ep)

        progress.saveProgress(PlaybackProgressEntity("driveAlien", positionMs = 100 * 60_000L, durationMs = 110 * 60_000L, isCompleted = true))
        assertEquals("finished: from the start", 0L, PlayOnTvRouting.buildRequest(catalog, db.mediaSourceDao(), "driveAlien", "Alien").positionMs)
        assertEquals(348, PlayOnTvRouting.buildRequest(catalog, db.mediaSourceDao(), "driveAlien", "Alien").tmdbId)

        progress.saveProgress(PlaybackProgressEntity("driveE1", positionMs = 5_000L, durationMs = 50 * 60_000L))
        assertEquals("first seconds: from the start", 0L, PlayOnTvRouting.buildRequest(catalog, db.mediaSourceDao(), "driveE1", "E1").positionMs)
        assertEquals("never watched", 0L, PlayOnTvRouting.buildRequest(catalog, db.mediaSourceDao(), "local:arrival", "Arrival").positionMs)
    }

    @Test
    fun everyOutcomeHasAMessage() {
        PlayStatus.values().forEach { assert(PlayOnTvMessages.of(PlayOnTvOutcome.Answered(it), "TCL", "Alien").isNotBlank()) }
        listOf(PlayOnTvOutcome.NoWifi, PlayOnTvOutcome.NotFound, PlayOnTvOutcome.UnknownPhone, PlayOnTvOutcome.Failed)
            .forEach { assert(PlayOnTvMessages.of(it, "TCL", "Alien").contains("TCL") || it == PlayOnTvOutcome.NoWifi) }
        assertEquals("Playing on TCL", PlayOnTvMessages.of(PlayOnTvOutcome.Answered(PlayStatus.OK), "TCL", "Alien"))
    }
}
