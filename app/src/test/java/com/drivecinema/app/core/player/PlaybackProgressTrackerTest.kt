package com.drivecinema.app.core.player

import androidx.media3.common.Player
import com.drivecinema.app.core.database.dao.PlaybackProgressDao
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackProgressTrackerTest {

    private class FakePlaybackProgressDao : PlaybackProgressDao {
        val database = mutableMapOf<String, PlaybackProgressEntity>()
        var lastThreadName: String? = null

        override suspend fun saveProgress(progress: PlaybackProgressEntity) {
            lastThreadName = Thread.currentThread().name
            database[progress.mediaItemId] = progress
        }

        override suspend fun getProgress(mediaItemId: String): PlaybackProgressEntity? {
            lastThreadName = Thread.currentThread().name
            return database[mediaItemId]
        }

        override fun getProgressFlow(mediaItemId: String): Flow<PlaybackProgressEntity?> {
            return flowOf(database[mediaItemId])
        }

        override suspend fun clearProgress(mediaItemId: String) {
            database.remove(mediaItemId)
        }
    }

    private fun createThreadGuardedPlayer(
        forbiddenThreadSubstring: String? = null,
        positionMs: Long = 45_000L,
        durationMs: Long = 100_000L,
        onListenerAdded: ((Player.Listener) -> Unit)? = null,
        onListenerRemoved: ((Player.Listener) -> Unit)? = null
    ): Player {
        val listeners = mutableListOf<Player.Listener>()
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> {
                    if (forbiddenThreadSubstring != null && Thread.currentThread().name.contains(forbiddenThreadSubstring)) {
                        throw IllegalStateException(
                            "Player is accessed on the wrong thread. Current thread: '${Thread.currentThread().name}'"
                        )
                    }
                    positionMs
                }
                "getDuration" -> {
                    if (forbiddenThreadSubstring != null && Thread.currentThread().name.contains(forbiddenThreadSubstring)) {
                        throw IllegalStateException(
                            "Player is accessed on the wrong thread. Current thread: '${Thread.currentThread().name}'"
                        )
                    }
                    durationMs
                }
                "addListener" -> {
                    val l = args[0] as Player.Listener
                    listeners.add(l)
                    onListenerAdded?.invoke(l)
                    null
                }
                "removeListener" -> {
                    val l = args[0] as Player.Listener
                    listeners.remove(l)
                    onListenerRemoved?.invoke(l)
                    null
                }
                else -> null
            }
        } as Player
    }

    // --- Completion & Resume Threshold Tests ---

    @Test
    fun testCompletionThresholdAt90Percent() {
        val durationMs = 100_000L
        val positionMs = 91_000L
        val isCompleted = PlaybackProgressTracker.calculateCompletion(positionMs, durationMs)
        assertTrue("Watched > 90% must mark completed", isCompleted)
    }

    @Test
    fun testUncompletedBelow85Percent() {
        // Below the 85 % floor nothing counts as watched, even with under 3 minutes left (Milestone J.1).
        val durationMs = 100_000L
        val positionMs = 84_000L
        val isCompleted = PlaybackProgressTracker.calculateCompletion(positionMs, durationMs)
        assertFalse("Watched < 85% must not mark completed", isCompleted)
    }

    @Test
    fun testCompletionEdgeCasesZeroOrNegative() {
        assertFalse(PlaybackProgressTracker.calculateCompletion(0L, 100_000L))
        assertFalse(PlaybackProgressTracker.calculateCompletion(50_000L, 0L))
        assertFalse(PlaybackProgressTracker.calculateCompletion(-10L, 100_000L))
    }

    @Test
    fun testCompletedVideoResumesFromStart() {
        val resumePos = PlaybackProgressTracker.resolveResumeOffset(isCompleted = true, savedPositionMs = 95_000L)
        assertEquals(0L, resumePos)
    }

    @Test
    fun testEarlyVideoResumesFromStartIfUnderTenSeconds() {
        val resumePos = PlaybackProgressTracker.resolveResumeOffset(isCompleted = false, savedPositionMs = 5_000L)
        assertEquals("Watching less than 10 seconds should restart from 0", 0L, resumePos)
    }

    @Test
    fun testPartiallyWatchedVideoResumesAtSavedPosition() {
        val resumePos = PlaybackProgressTracker.resolveResumeOffset(isCompleted = false, savedPositionMs = 450_000L)
        assertEquals(450_000L, resumePos)
    }

    // --- Threading & Separation Tests ---

    @Test
    fun testCaptureSnapshotExtractsPlayerPropertiesWithoutDatabaseAccess() {
        val player = createThreadGuardedPlayer(positionMs = 30_000L, durationMs = 120_000L)
        val dao = FakePlaybackProgressDao()
        val tracker = PlaybackProgressTracker(dao)

        val snapshot = tracker.captureSnapshot(player)

        assertNotNull(snapshot)
        assertEquals(30_000L, snapshot?.positionMs)
        assertEquals(120_000L, snapshot?.durationMs)
        assertFalse(snapshot!!.isCompleted)
        assertTrue("Capture must not touch the database", dao.database.isEmpty())
    }

    @Test
    fun testCaptureSnapshotReturnsNullWhenPositionAndDurationZero() {
        val player = createThreadGuardedPlayer(positionMs = 0L, durationMs = 0L)
        val tracker = PlaybackProgressTracker(FakePlaybackProgressDao())

        val snapshot = tracker.captureSnapshot(player)
        assertNull(snapshot)
    }

    @Test
    fun testSaveSnapshotPersistsToDatabase() = runTest {
        val dao = FakePlaybackProgressDao()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tracker = PlaybackProgressTracker(
            progressDao = dao,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            scope = this
        )

        val snapshot = PlaybackSnapshot(
            positionMs = 50_000L,
            durationMs = 100_000L,
            isCompleted = false
        )

        tracker.saveSnapshot("movie_123", snapshot)

        val saved = dao.database["movie_123"]
        assertNotNull(saved)
        assertEquals(50_000L, saved?.positionMs)
        assertEquals(100_000L, saved?.durationMs)
        assertFalse(saved!!.isCompleted)
    }

    @Test
    fun testFlushCoordinatesSnapshotCaptureAndSave() = runTest {
        val dao = FakePlaybackProgressDao()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tracker = PlaybackProgressTracker(
            progressDao = dao,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            scope = this
        )

        val player = createThreadGuardedPlayer(positionMs = 95_000L, durationMs = 100_000L)
        tracker.flush(player, "movie_456")

        testScheduler.advanceUntilIdle()

        val saved = dao.database["movie_456"]
        assertNotNull(saved)
        assertEquals(95_000L, saved?.positionMs)
        assertTrue(saved!!.isCompleted)
    }

    @Test
    fun nextEpisodeMarksTheCurrentEpisodeWatchedAndTheClosingFlushKeepsIt() = runTest {
        // Milestone J.1: Next Episode at 50 % → watched; finishing the player flushes again (onStop) and must not undo it.
        val dao = FakePlaybackProgressDao()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tracker = PlaybackProgressTracker(dao, mainDispatcher = testDispatcher, ioDispatcher = testDispatcher, scope = this)
        val player = createThreadGuardedPlayer(positionMs = 50_000L, durationMs = 100_000L)

        tracker.markWatched(player, "episode_1")
        tracker.detach(player, "episode_1")
        testScheduler.advanceUntilIdle()

        val saved = dao.database["episode_1"]!!
        assertTrue(saved.isCompleted)
        assertEquals(50_000L, saved.positionMs)
    }

    @Test
    fun markingOneTitleWatchedDoesNotAffectAnother() = runTest {
        val dao = FakePlaybackProgressDao()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tracker = PlaybackProgressTracker(dao, mainDispatcher = testDispatcher, ioDispatcher = testDispatcher, scope = this)
        val player = createThreadGuardedPlayer(positionMs = 50_000L, durationMs = 100_000L)

        tracker.markWatched(player, "episode_1")
        tracker.flush(player, "episode_2")
        testScheduler.advanceUntilIdle()

        assertFalse(dao.database["episode_2"]!!.isCompleted)
    }

    @Test
    fun testPlayerNeverAccessedOnForbiddenIoWorkerThread() = runTest {
        val dao = FakePlaybackProgressDao()
        val testDispatcher = StandardTestDispatcher(testScheduler)

        var listenerAdded = false
        var listenerRemoved = false
        val playerWithTracking = createThreadGuardedPlayer(
            forbiddenThreadSubstring = "forbidden-worker",
            positionMs = 40_000L,
            durationMs = 100_000L,
            onListenerAdded = { listenerAdded = true },
            onListenerRemoved = { listenerRemoved = true }
        )

        val tracker = PlaybackProgressTracker(
            progressDao = dao,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            scope = this
        )

        // Attach player
        tracker.attach(playerWithTracking, "file_789")
        assertTrue("Listener should be registered on attach", listenerAdded)

        // Advance 5 seconds to simulate one polling cycle
        advanceTimeBy(5000)
        testScheduler.runCurrent()

        // Detach player (cancels polling loop and flushes final progress)
        tracker.detach(playerWithTracking, "file_789")
        testScheduler.advanceUntilIdle()

        assertTrue("Listener should be unregistered on detach", listenerRemoved)

        // Verify progress was saved without throwing wrong-thread exception
        val saved = dao.database["file_789"]
        assertNotNull(saved)
        assertEquals(40_000L, saved?.positionMs)
    }

    @Test
    fun testGetResumePositionDelegatesToDaoAndResolvesOffset() = runTest {
        val dao = FakePlaybackProgressDao()
        dao.saveProgress(
            PlaybackProgressEntity(
                mediaItemId = "video_resume",
                positionMs = 80_000L,
                durationMs = 100_000L,
                isCompleted = false,
                lastWatchedAt = System.currentTimeMillis()
            )
        )

        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tracker = PlaybackProgressTracker(
            progressDao = dao,
            mainDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            scope = this
        )

        val resumePos = tracker.getResumePosition("video_resume")
        assertEquals(80_000L, resumePos)
    }
}
