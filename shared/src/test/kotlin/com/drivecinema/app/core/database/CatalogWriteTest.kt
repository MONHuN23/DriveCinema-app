package com.drivecinema.app.core.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.sync.DriveFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Task N.1: M.0 measured a refresh of an unchanged library rewriting every item, source and file-info row (about
 * 850 statements, 0.6–0.9 s on the TV), all inside the one transaction of `replaceVisibleLibrary`. Now only rows that
 * change are written; what the catalog ends up holding is the same.
 */
class CatalogWriteTest {

    /** Counts executed INSERT / UPDATE / DELETE steps (Room's own invalidation bookkeeping left out). */
    private class CountingDriver(private val delegate: SQLiteDriver = BundledSQLiteDriver()) : SQLiteDriver {
        val writes = AtomicInteger()

        override fun open(fileName: String): SQLiteConnection {
            val connection = delegate.open(fileName)
            return object : SQLiteConnection by connection {
                override fun prepare(sql: String): SQLiteStatement {
                    val statement = connection.prepare(sql)
                    val isWrite = sql.trimStart().let { it.startsWith("INSERT") || it.startsWith("UPDATE") || it.startsWith("DELETE") } &&
                        "room_table_modification_log" !in sql
                    if (!isWrite) return statement
                    return object : SQLiteStatement by statement {
                        override fun step(): Boolean {
                            writes.incrementAndGet()
                            return statement.step()
                        }
                    }
                }
            }
        }
    }

    private lateinit var dir: File
    private lateinit var db: DriveCinemaDatabase
    private val driver = CountingDriver()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-n1").toFile()
        db = Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "n1.db").absolutePath)
            .setDriver(driver).setQueryCoroutineContext(Dispatchers.IO).build()
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun movie(id: String, title: String, syncedAt: Long) =
        MediaItemEntity(id = id, type = MediaType.MOVIE, title = title, parentFolderId = "films", lastSyncedAt = syncedAt)

    private fun episode(id: String, show: String, n: Int, syncedAt: Long) = MediaItemEntity(
        id = id, type = MediaType.EPISODE, title = "Episode $n", parentFolderId = "${show}s1", seriesId = show,
        seasonNumber = 1, episodeNumber = n, lastSyncedAt = syncedAt
    )

    /** A crawl result like the M.0 library: 9 films, 8 shows, 179 episodes, each with Drive's file info. */
    private fun write(syncedAt: Long, sizeOfM0: Long = 1_000, renameM1: Boolean = false, dropM2: Boolean = false) = runBlocking {
        val movies = (0 until 9).map { movie("m$it", if (it == 1 && renameM1) "Renamed" else "Film $it", syncedAt) }
            .filterNot { dropM2 && it.id == "m2" }
        val series = (0 until 8).map { SeriesEntity(id = "show$it", title = "Show $it") }
        val episodes = (0 until 179).map { episode("e$it", "show${it % 8}", it / 8 + 1, syncedAt) }
        val info = (movies + episodes).associate { it.id to DriveFileInfo(sizeBytes = if (it.id == "m0") sizeOfM0 else 1_000, heightPx = 1080, container = "MKV") }
        db.mediaDao().replaceVisibleLibrary(movies, series, episodes, libraryRootId = "root", seenAt = syncedAt, fileInfo = info)
    }

    @Test
    fun anUnchangedLibraryIsNotWrittenAgain() {
        write(syncedAt = 1)
        val firstWrites = driver.writes.getAndSet(0)
        assertTrue("first sync writes every row ($firstWrites)", firstWrites >= 3 * 188)

        write(syncedAt = 2)

        // Mark Drive copies unavailable, mark the crawled ones available again, recompute visibility.
        assertEquals(3, driver.writes.get())
        runBlocking {
            assertEquals("unchanged rows keep their first sync time", 1L, db.mediaDao().getMediaItemById("e5")!!.lastSyncedAt)
            assertEquals("the copy was still seen by the second sync", 2L, db.mediaSourceDao().getSource("e5")!!.lastSeenAt)
        }
    }

    @Test
    fun changesAreStillWritten() = runBlocking {
        write(syncedAt = 1)
        val dao = db.mediaDao()
        dao.insertMediaItem(dao.getMediaItemById("m3")!!.copy(tmdbId = 33, posterPath = "/m3.jpg")) // enriched
        dao.insertMediaItem(dao.getMediaItemById("m1")!!.copy(tmdbId = 11, posterPath = "/m1.jpg"))
        driver.writes.set(0)

        write(syncedAt = 2, sizeOfM0 = 2_000, renameM1 = true, dropM2 = true)

        assertEquals("new file size", 2_000L, db.mediaSourceDao().getSource("m0")!!.sizeBytes)
        val renamed = dao.getMediaItemById("m1")!!
        assertEquals("Renamed", renamed.title)
        assertNull("a renamed file is matched again", renamed.tmdbId)
        assertEquals(2L, renamed.lastSyncedAt)
        assertEquals("unchanged and enriched: kept as it was", "/m3.jpg", dao.getMediaItemById("m3")!!.posterPath)
        assertTrue("a file gone from Drive is hidden", dao.getMediaItemById("m2")!!.isDeleted)
        assertEquals(8, dao.getAllMovies().size)
        // The 3 statements of an unchanged sync, plus the renamed film and the new size.
        assertEquals(5, driver.writes.get())
    }

    @Test
    fun aNewFileIsAdded() = runBlocking {
        write(syncedAt = 1)
        val dao = db.mediaDao()
        dao.replaceVisibleLibrary(
            movies = (0 until 9).map { movie("m$it", "Film $it", 2) } + movie("m9", "Film 9", 2),
            series = (0 until 8).map { SeriesEntity(id = "show$it", title = "Show $it") },
            episodes = (0 until 179).map { episode("e$it", "show${it % 8}", it / 8 + 1, 2) },
            libraryRootId = "root",
            seenAt = 2,
            fileInfo = mapOf("m9" to DriveFileInfo(sizeBytes = 5))
        )
        assertEquals("Film 9", dao.getMediaItemById("m9")!!.title)
        assertEquals(5L, db.mediaSourceDao().getSource("m9")!!.sizeBytes)
        assertEquals(10, dao.getAllMovies().size)
    }
}
