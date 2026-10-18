package com.drivecinema.desktop.player

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.player.PlaybackSelector
import com.drivecinema.app.core.player.SelectedCopy
import com.drivecinema.desktop.library.DesktopDatabase
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

class ProgressRecorderTest {

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("drivecinema-progress").toFile()
        db = DesktopDatabase.open(dir)
        db.mediaDao().insertMediaItems(listOf(MediaItemEntity("drive-1", MediaType.MOVIE, "Film", parentFolderId = "f")))
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun savesPositionAndMarksWatchedAtNinetyPercent() = runBlocking {
        val recorder = ProgressRecorder(db.playbackProgressDao(), "drive-1", clock = { 42L })

        val partial = recorder.record(3_600_000, 7_200_000)!!
        assertFalse(partial.isCompleted)
        assertEquals(42L, db.playbackProgressDao().getProgress("drive-1")!!.lastWatchedAt)

        assertTrue(recorder.record(6_500_000, 7_200_000)!!.isCompleted)
        assertNull("Same position is not written twice", recorder.record(6_500_000, 7_200_000))
        assertNull("Nothing to save before playback starts", ProgressRecorder(db.playbackProgressDao(), "x").record(0, 0))
    }

    @Test
    fun finishedIsAlwaysWatched() = runBlocking {
        val recorder = ProgressRecorder(db.playbackProgressDao(), "drive-1")
        assertTrue(recorder.recordFinished(7_200_000)!!.isCompleted)
    }

    @Test
    fun nextEpisodeMarksWatchedAndTheClosingSaveKeepsIt() = runBlocking {
        // Milestone J.1: Next Episode at half-time → watched; the save when the player closes must not undo it.
        val recorder = ProgressRecorder(db.playbackProgressDao(), "drive-1")
        recorder.record(600_000, 1_440_000)

        assertTrue(recorder.markWatched()!!.isCompleted)
        assertEquals(600_000L, db.playbackProgressDao().getProgress("drive-1")!!.positionMs)

        recorder.record(605_000, 1_440_000)
        assertTrue(db.playbackProgressDao().getProgress("drive-1")!!.isCompleted)
    }

    @Test
    fun selectorResumesFromProgressAndRestartsWatchedFilms() = runBlocking {
        val selector = PlaybackSelector(db.mediaSourceDao())
        assertEquals(SelectedCopy(SourceKind.DRIVE, "drive-1", 0L), selector.select("drive-1"))

        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive-1", 1_234_000, 7_200_000))
        assertEquals(1_234_000L, selector.select("drive-1")!!.resumePositionMs)

        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive-1", 7_000_000, 7_200_000, isCompleted = true))
        assertEquals(0L, selector.select("drive-1")!!.resumePositionMs)

        assertNull("Local titles without a copy are unavailable", selector.select("local:abc"))
    }
}
