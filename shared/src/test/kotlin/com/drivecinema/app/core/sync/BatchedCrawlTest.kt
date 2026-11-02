package com.drivecinema.app.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Task N.1: the crawler lists all folders of one level together (`'a' in parents or 'b' in parents …`). It must find
 * exactly what the folder-by-folder crawler found ([FolderByFolderCrawler], the code before N.1), with a number of
 * requests that follows the library's depth, not its number of folders.
 */
class BatchedCrawlTest {

    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    private fun serve(tree: FakeDriveTree): String {
        server.dispatcher = tree.dispatcher
        server.start()
        return server.url("/drive/v3").toString().removeSuffix("/")
    }

    private suspend fun batched(base: String, maxParents: Int = FolderQueueCrawler.DEFAULT_MAX_PARENTS_PER_QUERY) =
        FolderQueueCrawler(baseUrl = base, maxParentsPerQuery = maxParents).crawl("films", "series", "t", "root")

    private suspend fun folderByFolder(base: String) = FolderByFolderCrawler(baseUrl = base).crawl("films", "series", "t", "root")

    /** Compares what was found; the request count and the crawl time (`lastSyncedAt`) are expected to differ. */
    private fun CrawledLibrary.found() = copy(
        movies = movies.map { it.copy(lastSyncedAt = 0) },
        episodes = episodes.map { it.copy(lastSyncedAt = 0) },
        driveRequests = 0
    )

    /** Nested films, videos in the root, shows with and without season folders, Extras, a season of 1,205 episodes. */
    private fun variedLibrary() = FakeDriveTree().apply {
        folder("root", "films", "Films")
        folder("root", "series", "Series")
        video("root", "r1", "Loose Film (2001).mkv", size = 1_000)
        file("root", "doc", "notes.txt", "text/plain")
        video("films", "m1", "Alpha (2010).mkv", size = 2_000)
        video("films", "m2", "Beta.2012.1080p.mp4")
        folder("films", "coll", "Collection")
        video("coll", "m3", "Gamma (2014).mkv")
        folder("coll", "nested", "Nested")
        video("nested", "m4", "Delta (2016).mkv")
        folder("nested", "deeper", "Deeper")
        video("deeper", "m5", "Epsilon (2018).avi")
        folder("films", "empty", "Empty")
        folder("series", "showA", "Show A (2005)")
        folder("showA", "a1", "Season 1")
        folder("showA", "a2", "Season 02")
        video("a1", "a1e1", "Show.A.S01E01.mkv")
        video("a1", "a1e2", "Show.A.S01E02.mkv")
        video("a2", "a2e1", "Show A S02E01.mkv")
        folder("a2", "a2x", "Featurettes") // deeper than a season: not part of the library
        video("a2x", "a2x1", "Making of.mkv")
        folder("series", "showB", "Show B")
        video("showB", "b1", "Show.B.S01E01.mkv") // no season folders
        video("showB", "b2", "Show.B.S02E03.mkv")
        file("showB", "bsub", "Show.B.S01E01.srt", "application/x-subrip")
        folder("series", "showC", "Show C")
        folder("showC", "c1", "Season 1")
        folder("showC", "cx", "Extras")
        video("c1", "c1e1", "Show C - 1x01.mkv")
        video("cx", "cx1", "Bloopers.mkv")
        folder("series", "showD", "Show D")
        folder("showD", "d1", "Season 1")
        repeat(1_205) { video("d1", "d1e$it", "Show.D.S01E${it + 1}.mkv") }
        video("series", "stray", "Stray video.mkv") // a video next to the show folders is ignored
    }

    @Test
    fun findsExactlyWhatTheFolderByFolderCrawlFound() = runBlocking {
        val tree = variedLibrary()
        val base = serve(tree)
        val before = folderByFolder(base)
        val after = batched(base)
        assertEquals(before.found(), after.found())
        assertEquals("3 + 2 + 2 (Extras counts as a season folder, as before) + 1,205", 1_212, after.episodes.size)
        assertEquals(6, after.movies.size)
        // Levels: root+Films+Series; Collection+Empty+shows; Nested+seasons (the 1,205 episodes need a second page); Deeper.
        assertEquals(5, after.driveRequests)
        assertTrue("was ${before.driveRequests}", before.driveRequests > 3 * after.driveRequests)
    }

    @Test
    fun requestsFollowTheLevelsNotTheFolders() = runBlocking {
        for ((folders, expected) in listOf(8 to 3, 160 to 5, 820 to 19)) {
            val tree = scaledLibrary(folders)
            assertEquals(folders, tree.folderCount - 1) // the root is not counted
            val local = MockWebServer().apply { dispatcher = tree.dispatcher; start() }
            try {
                val base = local.url("/drive/v3").toString().removeSuffix("/")
                val before = FolderByFolderCrawler(baseUrl = base).crawl("films", "series", "t", "root")
                val after = FolderQueueCrawler(baseUrl = base).crawl("films", "series", "t", "root")
                assertEquals(before.found(), after.found())
                assertEquals("folder by folder: every folder, the root included", folders + 1, before.driveRequests)
                assertEquals("$folders folders", expected, after.driveRequests)
                // The bound: per level, one request per 50 folders (no level here needs a second page).
                val levels = listOf(3, 2 + showsFor(folders), showsFor(folders) * SEASONS)
                assertEquals(levels.sumOf { (it + 49) / 50 }, after.driveRequests)
            } finally {
                local.shutdown()
            }
        }
    }

    @Test
    fun aQueryDriveRejectsIsSplitUntilItFits() = runBlocking {
        val tree = FakeDriveTree(maxParentsAccepted = 10).apply { copyFrom(scaledLibrary(160)) }
        val base = serve(tree)
        val reference = FolderByFolderCrawler(baseUrl = base).crawl("films", "series", "t", "root")
        val after = batched(base)
        assertEquals(reference.found(), after.found())
        assertTrue(tree.rejected.get() > 0)
        assertTrue("every accepted query named at most 10 folders", tree.asked.all { it.size <= 10 })
    }

    @Test
    fun aFolderDriveRejectsOnItsOwnFailsTheCrawl() = runBlocking {
        val tree = FakeDriveTree(maxParentsAccepted = 0).apply { copyFrom(scaledLibrary(8)) }
        val base = serve(tree)
        val error = runCatching { batched(base) }.exceptionOrNull()
        assertTrue(error.toString(), error is java.io.IOException)
    }

    @Test
    fun aCancelledCrawlStopsBeforeTheNextLevel() = runBlocking {
        val tree = scaledLibrary(160)
        val secondLevelAsked = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        tree.onRequest = { ids ->
            if ("show0" in ids) {
                secondLevelAsked.complete(Unit)
                release.await(5, TimeUnit.SECONDS)
            }
        }
        val base = serve(tree)
        val crawl = async(Dispatchers.IO) { batched(base) }
        withTimeout(5_000) { secondLevelAsked.await() }
        crawl.cancel() // e.g. the user switched library during the crawl
        release.countDown()
        crawl.cancelAndJoin()
        assertEquals("the first level and the request already in flight", 2, tree.requests.get())
    }

    @Test
    fun onASlowConnectionTheBatchedCrawlIsMuchFaster() = runBlocking {
        // Each answer takes 20 ms (M.0 measured about 400 ms per Drive request on the TV's Wi-Fi).
        val tree = scaledLibrary(160).apply { latencyMs = 20 }
        val base = serve(tree)
        val oldStart = System.nanoTime()
        val before = folderByFolder(base)
        val oldMs = (System.nanoTime() - oldStart) / 1_000_000
        val newStart = System.nanoTime()
        val after = batched(base)
        val newMs = (System.nanoTime() - newStart) / 1_000_000
        assertEquals(before.found(), after.found())
        assertTrue("folder by folder ${oldMs} ms, batched ${newMs} ms", newMs * 5 < oldMs)
        // The same model with M.0's 0.4 s per request: 161 requests = 64 s before, 5 requests = 2 s after.
        assertEquals(5, after.driveRequests)
    }

    private fun showsFor(folders: Int) = (folders - 4) / (1 + SEASONS)

    /** Films (2 collections) and Series with shows of [SEASONS] seasons, [folders] folders in all (8, 160, 820). */
    private fun scaledLibrary(folders: Int) = FakeDriveTree().apply {
        val shows = showsFor(folders)
        require(4 + shows * (1 + SEASONS) == folders) { "$folders folders can't be built" }
        folder("root", "films", "Films")
        folder("root", "series", "Series")
        video("root", "r1", "Loose (2001).mkv")
        repeat(2) { c ->
            folder("films", "coll$c", "Collection $c")
            video("coll$c", "m$c", "Film $c (20${10 + c}).mkv")
        }
        repeat(shows) { s ->
            folder("series", "show$s", "Show $s")
            repeat(SEASONS) { n ->
                folder("show$s", "show${s}s$n", "Season ${n + 1}")
                repeat(2) { e -> video("show${s}s$n", "show${s}s${n}e$e", "Show.$s.S0${n + 1}E0${e + 1}.mkv") }
            }
        }
    }

    private companion object {
        const val SEASONS = 3
    }
}
