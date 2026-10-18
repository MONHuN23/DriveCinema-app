package com.drivecinema.desktop.local

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.player.PlaybackSelector
import com.drivecinema.desktop.library.DesktopDatabase
import com.drivecinema.desktop.library.DesktopSettings
import com.drivecinema.desktop.player.DesktopPlayback
import com.drivecinema.desktop.ui.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalFoldersTest {

    private lateinit var dir: File
    private lateinit var media: File
    private lateinit var db: DriveCinemaDatabase
    private lateinit var settings: DesktopSettings
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-local").toFile()
        media = File(dir, "My Media").apply { mkdirs() }
        // Same layout rules as Drive: Films/..., Series/<Show>/<Season n>/...
        file("Films/Alien (1979).mkv")
        file("Films/Heat (1995)/Heat.1995.1080p.mkv")
        file("Series/Dark (2017)/Season 1/Dark - S01E01.mkv")
        file("Series/Dark (2017)/Season 1/Dark - S01E02.mkv")
        file("Films/.hidden.mkv")
        file("Films/notes.txt")
        db = DesktopDatabase.open(File(dir, "db").apply { mkdirs() })
        settings = DesktopSettings(File(dir, "settings.json"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        dir.deleteRecursively()
    }

    private fun file(relative: String) = File(media, relative).apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }

    private fun folders() = LocalFolders(scope, db, settings)

    private suspend fun visibleMovieTitles() = db.mediaDao().getAllMoviesFlow().first().map { it.title }

    @Test
    fun addedFolderJoinsTheLibraryWithTheDriveLayoutRules() = runBlocking {
        val folders = folders()
        assertNull(folders.add(media.toPath()))
        folders.awaitIdle()

        assertEquals(listOf("Alien", "Heat"), visibleMovieTitles())
        assertEquals(listOf("Dark"), db.mediaDao().getAllSeriesFlow().first().map { it.title })
        val status = folders.state.value.folders.single()
        assertEquals(4, status.videoCount)
        assertTrue(status.isAvailable)
        assertEquals(listOf(media.absolutePath), settings.localFolders())
    }

    @Test
    fun localCopyOfADriveFilmIsLinkedAndPlayedFirst() = runBlocking {
        db.mediaDao().replaceVisibleLibrary(
            listOf(MediaItemEntity("drive-alien", MediaType.MOVIE, "Alien", year = 1979, parentFolderId = "films")),
            emptyList(), emptyList(), libraryRootId = "driveRoot"
        )
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("drive-alien", 600_000, 7_000_000))
        val folders = folders()
        folders.add(media.toPath())
        folders.awaitIdle()

        // One title, two copies; the local one plays, from the position watched on Drive.
        assertEquals(1, visibleMovieTitles().count { it == "Alien" })
        assertEquals(1, folders.state.value.folders.single().linkedToDrive)
        val copy = PlaybackSelector(db.mediaSourceDao()).select("drive-alien")!!
        assertEquals(SourceKind.LOCAL, copy.kind)
        assertTrue(copy.sourceKey.startsWith("file:"))
        assertEquals(600_000L, copy.resumePositionMs)

        val request = DesktopPlayback(db, vlcAvailable = { true }).resolve("drive-alien", false, proxy = null).request
        assertEquals(PlaybackRequest.Ready(copy.sourceKey, 600_000L), request)
    }

    @Test
    fun removingOrUnpluggingHidesLocalOnlyTitlesButKeepsWatchHistory() = runBlocking {
        val folders = folders()
        folders.add(media.toPath())
        folders.awaitIdle()
        val heatId = db.mediaDao().getAllMovies().first { it.title == "Heat" }.id
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("progress-key", 1, 2))

        // The drive is unplugged: next start hides its titles and says so.
        media.renameTo(File(dir, "elsewhere"))
        val restarted = folders()
        restarted.scanAll()
        restarted.awaitIdle()
        assertFalse(visibleMovieTitles().contains("Heat"))
        assertFalse(restarted.state.value.folders.single().isAvailable)
        assertNotNull(restarted.state.value.message)
        assertNotNull("Catalog rows are never deleted", db.mediaDao().getMediaItemById(heatId))
        assertNotNull(db.playbackProgressDao().getProgress("progress-key"))

        // Plugged in again: back. Then removed from the list: hidden again.
        File(dir, "elsewhere").renameTo(media)
        restarted.scanAll()
        restarted.awaitIdle()
        assertTrue(visibleMovieTitles().contains("Heat"))
        restarted.remove(media.absolutePath)
        restarted.awaitIdle()
        assertFalse(visibleMovieTitles().contains("Heat"))
        assertTrue(settings.localFolders().isEmpty())
    }

    @Test
    fun aShowFolderAddedDirectlyBecomesASeriesWithCleanEpisodeTitles() = runBlocking {
        // Regression (user's library): the show's own folder was added; scene names became "films"
        // and episode titles read "720p VARYG".
        val show = File(dir, "Rick And Morty").apply { mkdirs() }
        (1..3).forEach { File(show, "Rick.and.Morty.The.Anime.S01E0$it.720p-VARYG.mkv").writeBytes(ByteArray(10)) }
        var enriched = 0
        val folders = LocalFolders(scope, db, settings, afterScan = { enriched++ })
        folders.add(show.toPath())
        folders.awaitIdle()

        assertTrue("No episode is shown as a film", visibleMovieTitles().isEmpty())
        val series = db.mediaDao().getAllSeriesFlow().first().single()
        assertEquals("Rick and Morty The Anime", series.title)
        val episodes = db.mediaDao().getEpisodesForSeries(series.id)
        assertEquals(listOf(1, 2, 3), episodes.map { it.episodeNumber })
        assertEquals(listOf("Episode 1", "Episode 2", "Episode 3"), episodes.map { it.title })
        assertEquals("TMDB enrichment runs after the scan", 1, enriched)

        // Entries stored by an older parser are corrected on the next scan (only the app's own local entries).
        db.mediaSourceDao().updateTitle(episodes.first().id, "720p VARYG")
        folders.scanAll()
        folders.awaitIdle()
        assertEquals("Episode 1", db.mediaDao().getEpisodesForSeries(series.id).first().title)
    }

    @Test
    fun invalidFoldersAreRefused() {
        val folders = folders()
        assertEquals("That is not a folder.", folders.add(File(media, "Films/Alien (1979).mkv").toPath()))
        assertNull(folders.add(media.toPath()))
        assertEquals("That folder is already in the library.", folders.add(media.toPath()))
    }

    @Test
    fun missingLocalFileGivesAClearMessage() = runBlocking {
        val folders = folders()
        folders.add(media.toPath())
        folders.awaitIdle()
        val alien = db.mediaDao().getAllMovies().first { it.title == "Alien" }
        File(media, "Films/Alien (1979).mkv").delete()

        val request = DesktopPlayback(db, vlcAvailable = { true }).resolve(alien.id, false, proxy = null).request
        assertTrue(request is PlaybackRequest.Unavailable)
    }

    @Test
    fun fileSystemSourceSkipsHiddenFilesAndUsesFileUris() {
        val source = FileSystemTreeSource()
        val tree = FileSystemTreeSource.treeUriOf(media.toPath())
        assertTrue(tree.startsWith("file:///"))
        val films = source.listChildren(tree, File(media, "Films").absolutePath).map { it.name }.sorted()
        assertEquals(listOf("Alien (1979).mkv", "Heat (1995)", "notes.txt"), films)
        assertEquals(media.toPath().toAbsolutePath().toString(), source.rootDocumentId(tree))
    }
}
