package com.drivecinema.app.core.devicesync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Milestone Q: phone, TV and PC databases synced through one (fake) Drive app folder. */
class DeviceSyncTest {

    /** The Google account's app folder, shared by every simulated device. */
    private class FakeAppFolder : AppFolder {
        val files = LinkedHashMap<String, Pair<String, String>>() // id → (name, content)
        var calls = mutableListOf<String>()
        var failWith: IOException? = null
        private var nextId = 1

        override suspend fun listSyncFiles(accessToken: String): List<AppDataFile> {
            calls += "list"; failWith?.let { throw it }
            return files.map { (id, file) -> AppDataFile(id, file.first, null) }
        }
        override suspend fun download(fileId: String, accessToken: String): String { calls += "get"; return files.getValue(fileId).second }
        override suspend fun create(name: String, content: String, accessToken: String): String {
            calls += "create"; val id = "f${nextId++}"; files[id] = name to content; return id
        }
        override suspend fun update(fileId: String, content: String, accessToken: String) {
            calls += "update"; files[fileId] = files.getValue(fileId).first to content
        }
    }

    private val dirs = mutableListOf<File>()
    private val dbs = mutableListOf<DriveCinemaDatabase>()
    private val folder = FakeAppFolder()
    private var now = 1_761_740_000_000L

    private fun newDatabase(): DriveCinemaDatabase {
        val dir = Files.createTempDirectory("drivecinema-devicesync").toFile().also { dirs += it }
        return Room.databaseBuilder<DriveCinemaDatabase>(name = File(dir, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
            .also { dbs += it }
    }

    /** The same Drive library on every device: a film and an episode, crawled from Drive. */
    private fun device(id: String, extra: suspend (DriveCinemaDatabase) -> Unit = {}): Pair<DriveCinemaDatabase, DeviceSync> = runBlocking {
        val db = newDatabase()
        db.mediaDao().insertMediaItems(
            listOf(
                MediaItemEntity("1film", MediaType.MOVIE, "Alien", parentFolderId = "films", tmdbId = 348),
                MediaItemEntity("1ep", MediaType.EPISODE, "Pilot", parentFolderId = "s1", seasonNumber = 1, episodeNumber = 1)
            )
        )
        db.mediaSourceDao().insertSourcesIfAbsent(
            listOf(
                MediaSourceEntity("1film", "1film", SourceKind.DRIVE),
                MediaSourceEntity("1ep", "1ep", SourceKind.DRIVE)
            )
        )
        extra(db)
        db to DeviceSync(db.deviceSyncDao(), folder, DeviceSyncIdentity(id, "Device $id", "test")) { now }
    }

    private fun DriveCinemaDatabase.progress(key: String) = runBlocking { playbackProgressDao().getProgress(key) }
    private fun DriveCinemaDatabase.play(key: String, position: Long, completed: Boolean = false) = runBlocking {
        playbackProgressDao().saveProgress(PlaybackProgressEntity(key, position, 1_000_000L, completed, now))
    }

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
        dirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun progressFromThePhoneReachesTheTvAndThePc() = runBlocking {
        val (phoneDb, phone) = device("phone")
        val (tvDb, tv) = device("tv")
        val (pcDb, pc) = device("pc")
        phoneDb.play("1film", 600_000)

        assertEquals(DeviceSyncResult.Synced(applied = 0, otherDevices = 0, uploaded = true, unknownTitles = 0), phone.run("token"))
        now += 1_000
        assertEquals(DeviceSyncResult.Synced(applied = 1, otherDevices = 1, uploaded = true, unknownTitles = 0), tv.run("token"))
        pc.run("token")

        for (db in listOf(tvDb, pcDb)) {
            val row = db.progress("1film")!!
            assertEquals(600_000L, row.positionMs)
            assertEquals("lastWatchedAt is the phone's, not the sync time", phoneDb.progress("1film")!!.lastWatchedAt, row.lastWatchedAt)
        }
        assertEquals("one file per device", 3, folder.files.size)
    }

    @Test
    fun aWatchedMarkFromTheTvBeatsTheStaleStateOnThePhone() = runBlocking {
        val (phoneDb, phone) = device("phone")
        val (tvDb, tv) = device("tv")
        phoneDb.play("1ep", 300_000)
        phone.run("token")
        now += 60_000
        tvDb.play("1ep", 990_000, completed = true)
        tv.run("token")
        phone.run("token")

        assertTrue(phoneDb.progress("1ep")!!.isCompleted)
        // Running again on either side changes nothing and uploads nothing.
        folder.calls.clear()
        assertEquals(DeviceSyncResult.Synced(0, 1, uploaded = false, unknownTitles = 0), phone.run("token"))
        assertEquals(DeviceSyncResult.Synced(0, 1, uploaded = false, unknownTitles = 0), tv.run("token"))
        assertFalse(folder.calls.contains("update"))
        assertFalse(folder.calls.contains("create"))
    }

    @Test
    fun aNewerRewatchStartsTheTitleOverEverywhere() = runBlocking {
        val (phoneDb, phone) = device("phone")
        val (tvDb, tv) = device("tv")
        phoneDb.play("1film", 990_000, completed = true)
        phone.run("token"); tv.run("token")
        now += 3_600_000
        tvDb.play("1film", 120_000)
        tv.run("token"); phone.run("token")

        val row = phoneDb.progress("1film")!!
        assertFalse(row.isCompleted)
        assertEquals(120_000L, row.positionMs)
    }

    @Test
    fun syncNeverDeletesRowsOrMovesThemBack() = runBlocking {
        val (phoneDb, phone) = device("phone")
        val (tvDb, tv) = device("tv")
        phoneDb.play("1film", 500_000)
        now += 10_000
        tvDb.play("1film", 100_000) // newer on the TV
        tvDb.play("1ep", 50_000)
        phone.run("token"); tv.run("token")

        assertEquals("the TV keeps its newer row", 100_000L, tvDb.progress("1film")!!.positionMs)
        assertEquals(50_000L, tvDb.progress("1ep")!!.positionMs)
        phone.run("token")
        assertEquals(100_000L, phoneDb.progress("1film")!!.positionMs)
        assertEquals(2, phoneDb.deviceSyncDao().allProgress().size)
    }

    @Test
    fun aSaveByThePlayerDuringTheSyncIsNotOverwritten() = runBlocking {
        val (db, _) = device("phone")
        db.play("1film", 700_000)
        db.deviceSyncDao().saveIfNewer("1film", 100_000, 1_000_000, isCompleted = true, lastWatchedAt = now - 1)
        assertEquals("older", 700_000L, db.progress("1film")!!.positionMs)
        db.deviceSyncDao().saveIfNewer("1film", 100_000, 1_000_000, isCompleted = false, lastWatchedAt = now)
        assertEquals("equal and not further", 700_000L, db.progress("1film")!!.positionMs)
        db.deviceSyncDao().saveIfNewer("1film", 100_000, 1_000_000, isCompleted = true, lastWatchedAt = now)
        assertTrue("same time, watched wins", db.progress("1film")!!.isCompleted)
        db.deviceSyncDao().saveIfNewer("1film", 200_000, 1_000_000, isCompleted = false, lastWatchedAt = now + 1)
        assertEquals("newer", 200_000L, db.progress("1film")!!.positionMs)
        db.deviceSyncDao().saveIfNewer("new", 1, 2, isCompleted = false, lastWatchedAt = 3)
        assertEquals(1L, db.progress("new")!!.positionMs)
    }

    @Test
    fun aPhonesLocalCopyIsSharedAsItsDriveTitleAndAPcOnlyFilmByTmdb() = runBlocking {
        val (phoneDb, phone) = device("phone") { db ->
            db.mediaSourceDao().insertSourcesIfAbsent(listOf(MediaSourceEntity("content://tree/alien.mkv", "1film", SourceKind.LOCAL)))
        }
        val (tvDb, tv) = device("tv") { db ->
            db.mediaDao().insertMediaItems(listOf(MediaItemEntity("local:dune", MediaType.MOVIE, "Dune", parentFolderId = "pc", tmdbId = 438631)))
            db.mediaSourceDao().insertSourcesIfAbsent(listOf(MediaSourceEntity("dchost://pc/7", "local:dune", SourceKind.HOST)))
        }
        val (pcDb, pc) = device("pc") { db ->
            db.mediaDao().insertMediaItems(listOf(MediaItemEntity("local:dune2", MediaType.MOVIE, "Dune", parentFolderId = "c", tmdbId = 438631)))
            db.mediaSourceDao().insertSourcesIfAbsent(listOf(MediaSourceEntity("file:/C:/Films/Dune.mkv", "local:dune2", SourceKind.LOCAL)))
        }
        phoneDb.play("content://tree/alien.mkv", 400_000)
        tvDb.play("dchost://pc/7", 800_000)
        phone.run("token"); tv.run("token"); pc.run("token")

        assertEquals("the Drive key of the phone's local copy", 400_000L, tvDb.progress("1film")!!.positionMs)
        assertEquals(400_000L, pcDb.progress("1film")!!.positionMs)
        assertEquals("the PC's own file of the TV's PC copy", 800_000L, pcDb.progress("file:/C:/Films/Dune.mkv")!!.positionMs)
        phone.run("token")
        assertNull("the phone doesn't have Dune", phoneDb.progress("dchost://pc/7"))
    }

    @Test
    fun aClearedDatabaseGetsItsHistoryBackFromItsOwnFile() = runBlocking {
        val (db, phone) = device("phone")
        db.play("1film", 450_000)
        phone.run("token")
        db.close(); dbs.remove(db)
        val (freshDb, freshPhone) = device("phone")
        val result = freshPhone.run("token") as DeviceSyncResult.Synced
        assertEquals(1, result.applied)
        assertFalse("nothing new to upload", result.uploaded)
        assertEquals(450_000L, freshDb.progress("1film")!!.positionMs)
    }

    @Test
    fun aMissingPermissionAndOtherErrorsAreReportedNotThrown() = runBlocking {
        val (_, phone) = device("phone")
        folder.failWith = AppDataException("HTTP 403", 403, needsPermission = true)
        assertEquals(DeviceSyncResult.NeedsPermission, phone.run("token"))
        folder.failWith = AppDataException("HTTP 500", 500, needsPermission = false)
        assertTrue(phone.run("token") is DeviceSyncResult.Failed)
        folder.failWith = IOException("offline")
        assertEquals(DeviceSyncResult.Failed("offline"), phone.run("token"))
    }

    @Test
    fun unreadableFilesFromOtherDevicesAreSkipped() = runBlocking {
        val (db, phone) = device("phone")
        folder.files["x"] = "progress-newer.json" to """{"format":9,"deviceId":"newer","entries":{"drive:1film":{"p":1,"d":2,"c":true,"t":5}}}"""
        folder.files["y"] = "progress-broken.json" to "{"
        folder.files["z"] = "notes.json" to "{}"
        val result = phone.run("token") as DeviceSyncResult.Synced
        assertEquals(0, result.otherDevices)
        assertNull(db.progress("1film"))
    }
}
