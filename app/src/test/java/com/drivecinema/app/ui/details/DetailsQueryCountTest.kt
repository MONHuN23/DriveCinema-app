package com.drivecinema.app.ui.details

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Task M.2 (F-36), against a real Room database with the app's wiring: a series page with 54 episodes used to run
 * 59 queries on the TV (one progress query per episode, `docs/audit-history.md` §42); the target is at most 5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DetailsQueryCountTest {

    private lateinit var db: DriveCinemaDatabase
    private lateinit var main: ExecutorCoroutineDispatcher
    private val queries: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Before
    fun setup() {
        // viewModelScope runs on Main; the test thread (Robolectric's main looper) waits in runBlocking.
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries += sql }, Runnable::run)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        main.close()
    }

    @Test
    fun seriesPageRunsAtMostFiveQueriesWithOneProgressQuery() = runBlocking {
        val episodes = (1..54).map {
            MediaItemEntity(
                id = "ep$it", type = MediaType.EPISODE, title = "Episode $it", parentFolderId = "season",
                seasonNumber = 21 + (it - 1) / 20, episodeNumber = (it - 1) % 20 + 1, seriesId = "fg"
            )
        }
        db.mediaDao().insertSingleSeries(SeriesEntity(id = "fg", title = "Family Guy"))
        db.mediaDao().insertMediaItems(episodes)
        db.mediaDao().insertSourcesIfAbsent(episodes.map { MediaSourceEntity("drive-${it.id}", it.id, SourceKind.DRIVE) })
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive-ep1", 1_320_000, 1_320_000, isCompleted = true))
        queries.clear()

        val viewModel = DetailsViewModel.from(db)
        viewModel.loadSeries("fg")
        val state = withTimeout(10_000) { viewModel.seriesState.first { it.series != null && !it.isLoading } }

        // Watch progress is keyed by the Drive copy and shown on its catalog episode.
        assertTrue(state.episodes.first { it.id == "ep1" }.isCompleted)
        assertEquals("ep2", state.nextEpisode?.id)
        // Room's own invalidation bookkeeping is not a query of the screen.
        val readQueries = queries.filter {
            it.trimStart().startsWith("SELECT", ignoreCase = true) && "room_table_modification_log" !in it
        }
        assertTrue("queries: $readQueries", readQueries.size <= 5)
        assertEquals("queries: $readQueries", 1, readQueries.count { "playback_progress" in it })
    }
}
