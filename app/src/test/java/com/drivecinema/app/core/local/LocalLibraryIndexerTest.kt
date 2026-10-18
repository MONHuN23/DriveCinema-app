package com.drivecinema.app.core.local

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.ui.player.PlaybackResolver
import com.drivecinema.app.ui.player.PlaybackSource
import com.drivecinema.app.ui.player.ResolvedPlayback
import com.drivecinema.app.ui.setup.DuplicateReviews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task C.4 phase 2: local folders join the unified catalog without ever losing data or watch history. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalLibraryIndexerTest {

    private lateinit var db: DriveCinemaDatabase
    private val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
    private lateinit var fake: FakeDocumentTree
    private lateinit var indexer: LocalLibraryIndexer

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java).allowMainThreadQueries().build()
        fake = FakeDocumentTree()
        indexer = LocalLibraryIndexer(db, LocalLibraryCrawler(fake))
    }

    @After
    fun tearDown() = db.close()

    private val mediaDao get() = db.mediaDao()
    private val sourceDao get() = db.mediaSourceDao()

    private suspend fun driveMovies(vararg movies: MediaItemEntity) =
        mediaDao.replaceVisibleLibrary(movies.toList(), emptyList(), emptyList(), libraryRootId = "driveRoot")

    private fun movie(id: String, title: String, year: Int?) =
        MediaItemEntity(id = id, type = MediaType.MOVIE, title = title, year = year, parentFolderId = "films")

    private suspend fun visibleMovies() = mediaDao.getAllMoviesFlow().first()
    private fun localKey(path: String) = fake.documentUri(tree.toString(), "root:$path")

    @Test
    fun localOnlyFilmsBecomeVisibleTitlesWithLocalSources() = runBlocking {
        fake.file("Films/Arrival (2016).mkv")

        val result = indexer.indexFolder(tree)

        assertEquals(LocalIndexResult(videosFound = 1, linkedToExisting = 0, addedAsNew = 1, possibleDuplicates = 0), result)
        val item = visibleMovies().single()
        assertEquals("Arrival", item.title)
        assertTrue(item.id.startsWith(LocalLibraryIndexer.LOCAL_ID_PREFIX))
        val source = sourceDao.getSourcesForItem(item.id).single()
        assertEquals(SourceKind.LOCAL, source.kind)
        assertEquals(tree.toString(), source.libraryRootId)
        assertEquals(localKey("Films/Arrival (2016).mkv"), source.sourceKey)
    }

    @Test
    fun exactTitleAndYearLinksToTheDriveTitleAndPlaysLocally() = runBlocking {
        driveMovies(movie("drive_dune", "Dune Part Two", 2024))
        fake.file("Films/Dune.Part.Two.2024.1080p.mkv")

        val result = indexer.indexFolder(tree)

        assertEquals(1, result.linkedToExisting)
        assertEquals(listOf("drive_dune"), visibleMovies().map { it.id })
        val resolved = PlaybackResolver(sourceDao).resolve("drive_dune") as ResolvedPlayback.Playable
        assertEquals(PlaybackSource.Local(localKey("Films/Dune.Part.Two.2024.1080p.mkv")), resolved.source)
    }

    @Test
    fun titleWithoutYearIsAddedSeparatelyAndQueuedForReview() = runBlocking {
        driveMovies(movie("drive_dune", "Dune", 2021))
        fake.file("Films/Dune.mkv")

        val result = indexer.indexFolder(tree)

        assertEquals(1, result.possibleDuplicates)
        assertEquals(2, visibleMovies().size)
        val pending = sourceDao.getPendingMatches().single()
        assertEquals(localKey("Films/Dune.mkv"), pending.sourceKey)
        assertEquals("drive_dune", pending.candidateItemIds)
    }

    @Test
    fun twoPossibleDriveTitlesAreNeverGuessed() = runBlocking {
        driveMovies(movie("a", "Hamlet", 1996), movie("b", "Hamlet", 1996))
        fake.file("Films/Hamlet (1996).mkv")

        val result = indexer.indexFolder(tree)

        assertEquals(0, result.linkedToExisting)
        assertEquals(1, result.possibleDuplicates)
        assertEquals(setOf("a", "b"), sourceDao.getPendingMatches().single().candidateItemIds.split(',').toSet())
    }

    @Test
    fun differentYearIsADifferentFilm() = runBlocking {
        driveMovies(movie("dune84", "Dune", 1984))
        fake.file("Films/Dune (2021).mkv")

        val result = indexer.indexFolder(tree)

        assertEquals(LocalIndexResult(1, 0, 1, 0), result)
    }

    @Test
    fun episodesJoinTheExistingDriveShow() = runBlocking {
        mediaDao.replaceVisibleLibrary(
            emptyList(),
            listOf(SeriesEntity(id = "bb", title = "Breaking Bad")),
            listOf(
                MediaItemEntity(id = "bb_s01e01", type = MediaType.EPISODE, title = "Pilot", parentFolderId = "s1",
                    seriesId = "bb", seasonNumber = 1, episodeNumber = 1)
            ),
            libraryRootId = "driveRoot"
        )
        fake.file("Series/Breaking Bad/Season 01/Breaking Bad - S01E01.mkv")
            .file("Series/Breaking Bad/Season 01/Breaking Bad - S01E02.mkv")

        val result = indexer.indexFolder(tree)

        assertEquals(1, result.linkedToExisting)
        assertEquals(1, result.addedAsNew)
        val episodes = mediaDao.getEpisodesForSeries("bb")
        assertEquals(listOf(1, 2), episodes.map { it.episodeNumber })
        assertEquals(listOf("bb"), mediaDao.getAllSeriesFlow().first().map { it.id })
    }

    @Test
    fun rescanKeepsLinksAndRemovedFilesAreHiddenNotDeleted() = runBlocking {
        fake.file("Films/Arrival (2016).mkv").file("Films/Heat (1995).mkv")
        indexer.indexFolder(tree)
        val heatId = visibleMovies().single { it.title == "Heat" }.id
        db.playbackProgressDao().saveProgress(
            PlaybackProgressEntity(localKey("Films/Heat (1995).mkv"), positionMs = 42_000L, durationMs = 6_000_000L)
        )

        fake.remove("Films/Heat (1995).mkv")
        indexer.indexFolder(tree)

        assertEquals(listOf("Arrival"), visibleMovies().map { it.title })
        assertNotNull("Hidden, never deleted", mediaDao.getMediaItemById(heatId))
        assertFalse(sourceDao.getSourcesForItem(heatId).single().isAvailable)
        assertEquals(42_000L, db.playbackProgressDao().getProgress(localKey("Films/Heat (1995).mkv"))!!.positionMs)

        fake.file("Films/Heat (1995).mkv")
        indexer.indexFolder(tree)
        assertEquals(setOf("Arrival", "Heat"), visibleMovies().map { it.title }.toSet())
        assertEquals("Same title id comes back", heatId, visibleMovies().single { it.title == "Heat" }.id)
    }

    @Test
    fun emptyOrFailedScansNeverHideTheLibrary() = runBlocking {
        fake.file("Films/Arrival (2016).mkv")
        indexer.indexFolder(tree)

        val emptyIndexer = LocalLibraryIndexer(db, LocalLibraryCrawler(FakeDocumentTree()))
        assertEquals(0, emptyIndexer.indexFolder(tree).videosFound)
        assertEquals(1, visibleMovies().size)

        fake.failingFolders += "root:Films/"
        try {
            indexer.indexFolder(tree)
        } catch (_: IllegalStateException) {
        }
        assertEquals(1, visibleMovies().size)
    }

    @Test
    fun removingAFolderRetiresOnlyItsCopies() = runBlocking {
        driveMovies(movie("drive_heat", "Heat", 1995))
        fake.file("Films/Heat (1995).mkv").file("Films/Arrival (2016).mkv")
        indexer.indexFolder(tree)

        indexer.retireUnsavedFolders(emptyList())

        assertEquals("Drive copy keeps the merged title visible", listOf("drive_heat"), visibleMovies().map { it.id })
        val resolved = PlaybackResolver(sourceDao).resolve("drive_heat") as ResolvedPlayback.Playable
        assertEquals(PlaybackSource.Drive("drive_heat"), resolved.source)
    }

    @Test
    fun driveFolderSwitchNeverHidesLocalTitles() = runBlocking {
        fake.file("Films/Arrival (2016).mkv")
        indexer.indexFolder(tree)

        driveMovies(movie("other", "Other", 2000))

        assertEquals(setOf("Arrival", "Other"), visibleMovies().map { it.title }.toSet())
    }

    @Test
    fun confirmingADuplicateMergesAndCombinesProgress() = runBlocking {
        driveMovies(movie("drive_dune", "Dune", 2021))
        fake.file("Films/Dune.mkv")
        indexer.indexFolder(tree)
        val key = localKey("Films/Dune.mkv")
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive_dune", 10_000L, 9_000_000L, lastWatchedAt = 1L))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity(key, 70_000L, 9_000_000L, lastWatchedAt = 2L))

        val review = DuplicateReviews.load(sourceDao).single()
        assertEquals("Dune.mkv", review.fileName)
        assertEquals("Dune (2021)", review.candidates.single().label)

        sourceDao.confirmSameTitle(review.sourceKey, "drive_dune")

        assertEquals(listOf("drive_dune"), visibleMovies().map { it.id })
        assertTrue(sourceDao.getPendingMatches().isEmpty())
        val resolved = PlaybackResolver(sourceDao).resolve("drive_dune") as ResolvedPlayback.Playable
        assertEquals(PlaybackSource.Local(key), resolved.source)
        assertEquals("Resumes from the most recent copy", 70_000L, resolved.resumePositionMs)
        assertEquals(10_000L, db.playbackProgressDao().getProgress("drive_dune")!!.positionMs)
    }

    @Test
    fun keepingSeparateOnlyClearsTheQuestion() = runBlocking {
        driveMovies(movie("drive_dune", "Dune", 2021))
        fake.file("Films/Dune.mkv")
        indexer.indexFolder(tree)

        sourceDao.deletePendingMatch(localKey("Films/Dune.mkv"))
        indexer.indexFolder(tree)

        assertTrue("Known sources are not re-queued on rescan", sourceDao.getPendingMatches().isEmpty())
        assertEquals(2, visibleMovies().size)
    }
}
