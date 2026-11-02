package com.drivecinema.app.ui.details

import androidx.lifecycle.ViewModelStore
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testSmartPlayTargetsFirstUnwatchedEpisode() {
        val episodes = listOf(
            EpisodeItem("ep1", season = 1, episode = 1, isCompleted = true),
            EpisodeItem("ep2", season = 1, episode = 2, isCompleted = false)
        )
        val target = DetailsViewModel.findNextTargetEpisode(episodes)
        assertEquals("ep2", target?.id)
    }

    @Test
    fun testSmartPlayWhenAllCompletedReturnsFirstEpisode() {
        val episodes = listOf(
            EpisodeItem("ep1", season = 1, episode = 1, isCompleted = true),
            EpisodeItem("ep2", season = 1, episode = 2, isCompleted = true)
        )
        val target = DetailsViewModel.findNextTargetEpisode(episodes)
        assertEquals("ep1", target?.id)
    }

    @Test
    fun testSmartPlayEmptyListReturnsNull() {
        val target = DetailsViewModel.findNextTargetEpisode(emptyList())
        assertNull(target)
    }

    @Test
    fun testSmartPlaySkipsUnavailableEpisodes() {
        val episodes = listOf(
            EpisodeItem(id = null, title = "Ep 1", season = 1, episode = 1, isAvailable = false),
            EpisodeItem(id = "ep2", title = "Ep 2", season = 1, episode = 2, isAvailable = true, isCompleted = false)
        )
        val target = DetailsViewModel.findNextTargetEpisode(episodes)
        assertEquals("ep2", target?.id)
    }

    @Test
    fun testEpisodeItemWithRichMetadata() {
        val ep = EpisodeItem(
            id = "drive_file_101",
            title = "Ozymandias",
            season = 5,
            episode = 14,
            overview = "Walt goes on the run as consequences catch up.",
            stillPath = "/ozy.jpg",
            voteAverage = 10.0,
            isCompleted = false,
            positionMs = 30000L,
            durationMs = 2800000L
        )
        assertEquals("drive_file_101", ep.id)
        assertEquals("Ozymandias", ep.title)
        assertEquals(5, ep.season)
        assertEquals(14, ep.episode)
        assertEquals("/ozy.jpg", ep.stillPath)
        assertEquals(10.0, ep.voteAverage ?: 0.0, 0.01)
        assertEquals(true, ep.isAvailable)
    }

    /** Task L.3 / F-30: the phone/TV details show what is known per copy instead of a fixed "1080p". */
    @Test
    fun infoLinesArePerCopyInPlayOrderAndSkipCopiesWithNothingKnown() {
        val drive = MediaSourceEntity(
            "drive-id", "m", SourceKind.DRIVE, durationMs = 7_920_000, widthPx = 3840, heightPx = 2160,
            videoCodec = "HEVC", audioCodec = "E-AC-3", audioChannels = 6, container = "MKV", isHdr = true
        )
        val local = MediaSourceEntity("content://local/Dune.mp4", "m", SourceKind.LOCAL, sizeBytes = 2_147_483_648)
        val unknown = MediaSourceEntity("drive-other", "m", SourceKind.DRIVE)

        assertEquals(
            listOf("MP4 · 2.0 GB · This device", "2 h 12 min · 2160p HDR · HEVC · E-AC-3 5.1 · MKV · Drive"),
            DetailsViewModel.infoLines(listOf(drive, unknown, local))
        )
        assertEquals(emptyList<String>(), DetailsViewModel.infoLines(listOf(unknown)))
    }

    @Test
    fun episodeInfoUsesEachEpisodesBestKnownCopy() {
        val copies = listOf(
            MediaSourceEntity("drive-e1", "e1", SourceKind.DRIVE, durationMs = 2_880_000, widthPx = 1920, heightPx = 1080),
            MediaSourceEntity("content://e1", "e1", SourceKind.LOCAL),
            MediaSourceEntity("drive-e2", "e2", SourceKind.DRIVE)
        )
        assertEquals(mapOf("e1" to "48 min · 1080p"), DetailsViewModel.episodeInfo(copies))
    }

    // ---- Task M.2: live progress (F-36), one progress query per series, nothing left running (F-35) ----

    private val series = SeriesEntity(id = "fg", title = "Family Guy")

    private fun episode(season: Int, number: Int) = MediaItemEntity(
        id = "s${season}e$number", type = MediaType.EPISODE, title = "Episode $number", parentFolderId = "season$season",
        seasonNumber = season, episodeNumber = number, seriesId = "fg"
    )

    private val episodes = (1..20).map { episode(21, it) } + (1..10).map { episode(23, it) }

    private fun progress(id: String, positionMs: Long, completed: Boolean = false) =
        PlaybackProgressEntity(id, positionMs = positionMs, durationMs = 1_320_000, isCompleted = completed)

    /** Only the calls the details screen makes; anything else fails the test. */
    private fun fakeMediaDao(movie: MediaItemEntity? = null): MediaDao {
        val unused = Proxy.newProxyInstance(MediaDao::class.java.classLoader, arrayOf(MediaDao::class.java)) { _, method, _ ->
            error("DetailsViewModel should not call MediaDao.${method.name}")
        } as MediaDao
        return object : MediaDao by unused {
            override suspend fun getSeriesById(id: String): SeriesEntity? = series.takeIf { it.id == id }
            override suspend fun getEpisodesForSeries(seriesId: String): List<MediaItemEntity> = episodes
            override suspend fun getMediaItemById(id: String): MediaItemEntity? = movie
        }
    }

    private class Calls {
        var itemProgress = 0
        var seriesProgress = 0
    }

    private fun seriesViewModel(
        progress: Flow<List<PlaybackProgressEntity>>,
        sources: Flow<List<MediaSourceEntity>> = flowOf(emptyList()),
        calls: Calls = Calls()
    ) = DetailsViewModel(
        fakeMediaDao(),
        progressForItem = { calls.itemProgress++; flowOf(null) },
        progressForSeries = { calls.seriesProgress++; progress },
        sourcesForSeries = { sources }
    )

    @Test
    fun seriesProgressIsOneQueryForTheWholeSeriesNotOnePerEpisode() = runTest(testDispatcher) {
        val calls = Calls()
        val viewModel = seriesViewModel(flowOf(listOf(progress("s21e1", 1_320_000, completed = true))), calls = calls)

        viewModel.loadSeries("fg")
        advanceUntilIdle()

        assertEquals(1, calls.seriesProgress)
        assertEquals(0, calls.itemProgress)
        val state = viewModel.seriesState.value
        assertFalse(state.isLoading)
        assertEquals(30, state.episodes.size)
        assertEquals(listOf(21, 23), state.seasons)
        assertTrue(state.episodes.first { it.id == "s21e1" }.isCompleted)
        assertEquals("s21e2", state.nextEpisode?.id)
    }

    /** F-36: finishing an episode in the player updates the check mark and the Play target without reopening. */
    @Test
    fun seriesProgressAndPlayTargetUpdateLive() = runTest(testDispatcher) {
        val progress = MutableStateFlow(listOf(progress("s21e4", 600_000)))
        val viewModel = seriesViewModel(progress)
        viewModel.loadSeries("fg")
        advanceUntilIdle()
        assertEquals("s21e1", viewModel.seriesState.value.nextEpisode?.id)
        assertEquals(600_000L, viewModel.seriesState.value.episodes.first { it.id == "s21e4" }.positionMs)

        progress.value = (1..4).map { progress("s21e$it", 1_320_000, completed = true) }
        advanceUntilIdle()

        val state = viewModel.seriesState.value
        assertTrue(state.episodes.filter { it.season == 21 && it.episode <= 4 }.all { it.isCompleted })
        assertEquals("s21e5", state.nextEpisode?.id)
    }

    @Test
    fun aReEmissionKeepsTheSeasonTheUserPicked() = runTest(testDispatcher) {
        val progress = MutableStateFlow(emptyList<PlaybackProgressEntity>())
        val viewModel = seriesViewModel(progress)
        viewModel.loadSeries("fg")
        advanceUntilIdle()
        assertEquals(21, viewModel.seriesState.value.selectedSeason)

        viewModel.selectSeason(23)
        progress.value = listOf(progress("s23e1", 60_000))
        advanceUntilIdle()

        assertEquals(23, viewModel.seriesState.value.selectedSeason)
        assertEquals(60_000L, viewModel.seriesState.value.episodes.first { it.id == "s23e1" }.positionMs)
    }

    @Test
    fun episodeInfoLinesFollowTheLiveCopies() = runTest(testDispatcher) {
        val sources = MutableStateFlow(emptyList<MediaSourceEntity>())
        val viewModel = seriesViewModel(flowOf(emptyList()), sources)
        viewModel.loadSeries("fg")
        advanceUntilIdle()
        assertNull(viewModel.seriesState.value.episodes.first { it.id == "s21e1" }.info)

        sources.value = listOf(
            MediaSourceEntity("drive-1", "s21e1", SourceKind.DRIVE, durationMs = 1_320_000, widthPx = 1920, heightPx = 1080)
        )
        advanceUntilIdle()

        assertEquals("22 min · 1080p", viewModel.seriesState.value.episodes.first { it.id == "s21e1" }.info)
    }

    @Test
    fun movieProgressUpdatesLive() = runTest(testDispatcher) {
        val movie = MediaItemEntity(id = "phm", type = MediaType.MOVIE, title = "Project Hail Mary", parentFolderId = "films")
        val progress = MutableStateFlow<PlaybackProgressEntity?>(null)
        val viewModel = DetailsViewModel(fakeMediaDao(movie), progressForItem = { progress })
        viewModel.loadMovie("phm")
        advanceUntilIdle()
        assertEquals("phm", viewModel.movieState.value.movie?.id)
        assertNull(viewModel.movieState.value.progress)
        assertFalse(viewModel.movieState.value.isLoading)

        progress.value = progress("phm", 3_000_000)
        advanceUntilIdle()

        assertEquals(3_000_000L, viewModel.movieState.value.progress?.positionMs)
    }

    /** F-35: once the screen's store is cleared, the database flows have no collector left. */
    @Test
    fun nothingIsCollectedAfterTheScreenCloses() = runTest(testDispatcher) {
        val progress = MutableStateFlow(emptyList<PlaybackProgressEntity>())
        val sources = MutableStateFlow(emptyList<MediaSourceEntity>())
        val store = ViewModelStore()
        val viewModel = screenViewModel(store, DetailsViewModel::class.java) { seriesViewModel(progress, sources) }
        viewModel.loadSeries("fg")
        advanceUntilIdle()
        assertEquals(1, progress.subscriptionCount.value)
        assertEquals(1, sources.subscriptionCount.value)

        store.clear()
        advanceUntilIdle()

        assertEquals(0, progress.subscriptionCount.value)
        assertEquals(0, sources.subscriptionCount.value)
    }

    @Test
    fun loadingAgainReplacesThePreviousCollectors() = runTest(testDispatcher) {
        val progress = MutableStateFlow(emptyList<PlaybackProgressEntity>())
        val viewModel = seriesViewModel(progress)
        viewModel.loadSeries("fg")
        advanceUntilIdle()
        viewModel.loadSeries("fg")
        advanceUntilIdle()

        assertEquals(1, progress.subscriptionCount.value)
    }
}
