package com.drivecinema.desktop.library

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.DriveCinemaSchemaSteps
import com.drivecinema.app.core.database.entity.SourceKind
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Milestone I prerequisite: the desktop database has an upgrade path. It runs the same SQL steps as Android
 * ([DriveCinemaSchemaSteps]), keeps watch history, and a version without a path fails instead of being rebuilt.
 */
class DesktopMigrationTest {

    private lateinit var dir: File
    private val dbFile get() = File(dir, DriveCinemaDatabase.DATABASE_NAME)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("drivecinema-migration").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun everyVersionAfterTheBaselineHasAStepAndAnExportedSchema() {
        val current = currentVersion()
        for (from in BASELINE_VERSION until current) {
            assertTrue("Missing SchemaStep($from, ${from + 1})", DriveCinemaSchemaSteps.ALL.any { it.from == from && it.to == from + 1 })
            assertTrue("Missing exported schema $from.json", schemaFile(from).exists())
        }
        assertTrue("Missing exported schema $current.json", schemaFile(current).exists())
        assertEquals(DriveCinemaSchemaSteps.ALL.size, DesktopDatabase.MIGRATIONS.size)
    }

    @Test
    fun everyStepKeepsWatchHistoryOnTheDesktop() = runBlocking {
        for (step in DriveCinemaSchemaSteps.ALL) {
            dbFile.delete()
            createDatabaseFromSchema(step.from)
            seedRawRows()
            val db = DesktopDatabase.open(dir)
            try {
                assertWatchHistoryIntact(db)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun step2To3BackfillsDriveSourcesOnTheDesktop() = runBlocking {
        createDatabaseFromSchema(2)
        seedRawRows()
        val db = DesktopDatabase.open(dir)
        try {
            val source = db.mediaSourceDao().getSource("m1")
            assertNotNull(source)
            assertEquals(SourceKind.DRIVE, source!!.kind)
            assertTrue(source.isAvailable)
            assertWatchHistoryIntact(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun step3To4AddsAnEmptyImportQueueAndKeepsWatchHistory() = runBlocking {
        createDatabaseFromSchema(3)
        seedRawRows()
        val db = DesktopDatabase.open(dir)
        try {
            assertWatchHistoryIntact(db)
            assertNull(db.importQueueDao().nextQueued("any-library"))
        } finally {
            db.close()
        }
    }

    @Test
    fun step4To5AddsUnknownCopyDetailsAndKeepsSources() = runBlocking {
        // Task L.3: the new media_sources columns start as unknown (null); the copy, its size and the history stay.
        createDatabaseFromSchema(4)
        seedRawRows()
        BundledSQLiteDriver().open(dbFile.absolutePath).use { raw ->
            raw.execSQL(
                "INSERT INTO media_sources (sourceKey, mediaItemId, kind, libraryRootId, sizeBytes, isAvailable, lastSeenAt) " +
                    "VALUES ('m1', 'm1', 'DRIVE', NULL, 4096, 1, 0)"
            )
        }
        val db = DesktopDatabase.open(dir)
        try {
            assertWatchHistoryIntact(db)
            val copy = db.mediaSourceDao().getSource("m1")!!
            assertEquals(4096L, copy.sizeBytes)
            assertNull(copy.widthPx)
            assertNull(copy.videoCodec)
            assertNull(copy.isHdr)
        } finally {
            db.close()
        }
    }

    @Test
    fun aVersionWithoutAnUpgradePathFailsAndKeepsWatchHistory() {
        createDatabaseFromSchema(currentVersion(), userVersion = currentVersion() + 1)
        seedRawRows()

        val db = DesktopDatabase.open(dir)
        try {
            runBlocking { db.playbackProgressDao().getProgress("m1") }
            fail("Opening a database without an upgrade path must fail, not silently rebuild")
        } catch (expected: IllegalStateException) {
            // expected
        } finally {
            db.close()
        }

        BundledSQLiteDriver().open(dbFile.absolutePath).use { raw ->
            raw.prepare("SELECT positionMs FROM playback_progress WHERE mediaItemId = 'm1'").use {
                assertTrue("playback_progress must survive a failed open", it.step())
                assertEquals(120_000L, it.getLong(0))
            }
        }
    }

    @Test
    fun aDatabaseFromANewerAppIsRecognisedBeforeOpening() {
        // Report 2026-10-26: a development build upgraded the data to v5, then the installed 1.1.0 (v4) crashed
        // with Room's "migration from 5 to 4" error. The desktop now checks first and explains.
        assertNull("no database yet", DesktopDatabase.newerSchemaVersion(dir))

        createDatabaseFromSchema(currentVersion())
        assertNull("the current version opens normally", DesktopDatabase.newerSchemaVersion(dir))

        dbFile.delete()
        createDatabaseFromSchema(currentVersion(), userVersion = currentVersion() + 1)
        assertEquals(currentVersion() + 1, DesktopDatabase.newerSchemaVersion(dir))
    }

    @Test
    fun theSchemaVersionConstantMatchesTheNewestExportedSchema() {
        assertEquals(currentVersion(), DriveCinemaDatabase.SCHEMA_VERSION)
    }

    @Test
    fun aFreshDesktopDatabaseStartsAtTheCurrentVersion() = runBlocking {
        val db = DesktopDatabase.open(dir)
        try {
            assertNull(db.playbackProgressDao().getProgress("m1"))
        } finally {
            db.close()
        }
        BundledSQLiteDriver().open(dbFile.absolutePath).use { raw ->
            raw.prepare("PRAGMA user_version").use {
                it.step()
                assertEquals(currentVersion().toLong(), it.getLong(0))
            }
        }
    }

    private suspend fun assertWatchHistoryIntact(db: DriveCinemaDatabase) {
        val progress = db.playbackProgressDao().getProgress("m1")
        assertNotNull("playback_progress must survive", progress)
        assertEquals(120_000L, progress!!.positionMs)
        assertEquals(7_200_000L, progress.durationMs)
        assertNotNull("progress without a catalog row must survive too", db.playbackProgressDao().getProgress("gone_from_catalog"))
        assertNotNull(db.mediaDao().getMediaItemById("m1"))
    }

    /** Builds an on-disk database exactly as Room would have created it at [version]. */
    private fun createDatabaseFromSchema(version: Int, userVersion: Int = version) {
        val schema = schema(version)
        BundledSQLiteDriver().open(dbFile.absolutePath).use { raw ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    val index = indices.getJSONObject(j)
                    raw.execSQL(
                        index.getString("createSql")
                            .replace("\${TABLE_NAME}", table)
                            .replace("\${INDEX_NAME}", index.getString("name"))
                    )
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
            raw.execSQL("PRAGMA user_version = $userVersion")
        }
    }

    private fun seedRawRows() {
        BundledSQLiteDriver().open(dbFile.absolutePath).use { raw ->
            raw.execSQL(
                "INSERT INTO media_items (id, type, title, driveFileId, parentFolderId, isDeleted, lastSyncedAt) " +
                    "VALUES ('m1', 'MOVIE', 'Interstellar', 'm1', 'films', 0, 0)"
            )
            raw.execSQL(
                "INSERT INTO playback_progress (mediaItemId, positionMs, durationMs, isCompleted, lastWatchedAt) " +
                    "VALUES ('m1', 120000, 7200000, 0, 1000)"
            )
            raw.execSQL(
                "INSERT INTO playback_progress (mediaItemId, positionMs, durationMs, isCompleted, lastWatchedAt) " +
                    "VALUES ('gone_from_catalog', 5000, 10000, 0, 2000)"
            )
        }
    }

    /** The newest exported schema is the version the compiled database uses (checked by Android's identity-hash test). */
    private fun currentVersion(): Int =
        schemaDir().listFiles { f -> f.name.endsWith(".json") }!!.maxOf { it.nameWithoutExtension.toInt() }

    private fun schemaDir(): File {
        val relative = "schemas/${DriveCinemaDatabase::class.java.name}"
        return listOf(File("../shared/$relative"), File("shared/$relative")).firstOrNull { it.isDirectory }
            ?: File("../shared/$relative")
    }

    private fun schemaFile(version: Int) = File(schemaDir(), "$version.json")

    private fun schema(version: Int): JSONObject {
        val file = schemaFile(version)
        assertTrue("Exported schema not found: ${file.absolutePath}", file.exists())
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private companion object {
        const val BASELINE_VERSION = 2
    }
}
