package com.drivecinema.app.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Task C.0: schema v2 is exported as the migration baseline, and opening/upgrading an on-disk database
 * never destroys playback_progress (watch history).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DatabaseMigrationTest {

    private lateinit var context: Context
    private val dbName = "migration-test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun exportedSchemaMatchesCompiledSchema() {
        val db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java).build()
        try {
            val sqlite = db.openHelper.readableDatabase
            val version = sqlite.version
            val hash = sqlite.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use {
                it.moveToFirst()
                it.getString(0)
            }
            val exported = schema(version)
            assertEquals(version, exported.getInt("version"))
            assertEquals(
                "Entities changed without a version bump + exported schema; see DriveCinemaMigrations",
                exported.getString("identityHash"),
                hash
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun everyVersionAfterBaselineHasAnExplicitMigration() {
        val current = currentVersion()
        for (from in BASELINE_VERSION until current) {
            assertTrue(
                "Missing Migration($from, ${from + 1}) in DriveCinemaMigrations.ALL",
                DriveCinemaMigrations.ALL.any { it.startVersion == from && it.endVersion == from + 1 }
            )
            assertTrue("Missing exported schema $from.json", schemaFile(from).exists())
        }
        assertTrue("Missing exported schema $current.json", schemaFile(current).exists())
    }

    @Test
    fun existingV2DatabaseKeepsWatchHistoryAndCatalogWhenOpened() = runBlocking {
        createDatabaseFromSchema(BASELINE_VERSION)
        seedRawRows()

        val db = DriveCinemaDatabase.build(context, dbName)
        try {
            assertWatchHistoryIntact(db)
            assertNotNull(db.mediaDao().getMediaItemById("m1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun everyRegisteredMigrationPreservesWatchHistory() = runBlocking {
        // Runs each migration path from its exported start schema up to the current version.
        for (migration in DriveCinemaMigrations.ALL) {
            context.deleteDatabase(dbName)
            createDatabaseFromSchema(migration.startVersion)
            seedRawRows()

            val db = DriveCinemaDatabase.build(context, dbName)
            try {
                assertWatchHistoryIntact(db)
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun migration2To3BackfillsDriveSourcesAndKeepsContinueWatching() = runBlocking {
        createDatabaseFromSchema(2)
        seedRawRows()
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL(
                "INSERT INTO media_items (id, type, title, driveFileId, parentFolderId, isDeleted, lastSyncedAt) " +
                    "VALUES ('hidden1', 'MOVIE', 'Old Library Film', 'hidden1', 'filmsOld', 1, 0)"
            )
        }

        val db = DriveCinemaDatabase.build(context, dbName)
        try {
            val visibleSource = db.mediaSourceDao().getSource("m1")
            assertNotNull(visibleSource)
            assertEquals("m1", visibleSource!!.mediaItemId)
            assertEquals(com.drivecinema.app.core.database.entity.SourceKind.DRIVE, visibleSource.kind)
            assertTrue(visibleSource.isAvailable)
            assertEquals(false, db.mediaSourceDao().getSource("hidden1")!!.isAvailable)

            val continueWatching = db.mediaDao().getContinueWatchingFlow().first()
            assertEquals(listOf("m1"), continueWatching.map { it.id })
            assertEquals(120_000L, continueWatching.single().positionMs)
            assertWatchHistoryIntact(db)
        } finally {
            db.close()
        }
    }

    @Test
    fun unknownSchemaVersionFailsInsteadOfWipingWatchHistory() {
        // A version with no migration path (here: a newer, downgraded install) must never be dropped.
        val futureVersion = currentVersion() + 1
        createDatabaseFromSchema(currentVersion(), userVersion = futureVersion)
        seedRawRows()

        val db = DriveCinemaDatabase.build(context, dbName)
        try {
            db.openHelper.writableDatabase
            fail("Opening a database without a migration path must fail, not silently rebuild")
        } catch (expected: IllegalStateException) {
            // expected
        } finally {
            db.close()
        }

        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { raw ->
            raw.rawQuery("SELECT positionMs FROM playback_progress WHERE mediaItemId = 'm1'", null).use {
                assertTrue("playback_progress must survive a failed open", it.moveToFirst())
                assertEquals(120_000L, it.getLong(0))
            }
        }
    }

    @Test
    fun legacyUnexportedV1DatabaseIsStillRebuilt() = runBlocking {
        // Pre-baseline v1 installs keep their previous behaviour (rebuilt), since v1 was never exported.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }, null).use { raw ->
            raw.execSQL("CREATE TABLE legacy_v1 (id TEXT PRIMARY KEY)")
            raw.version = 1
        }

        val db = DriveCinemaDatabase.build(context, dbName)
        try {
            assertEquals(currentVersion(), db.openHelper.readableDatabase.version)
            assertTrue(db.mediaDao().getAllMovies().isEmpty())
        } finally {
            db.close()
        }
    }

    private suspend fun assertWatchHistoryIntact(db: DriveCinemaDatabase) {
        val progress = db.playbackProgressDao().getProgress("m1")
        assertNotNull("playback_progress must survive", progress)
        assertEquals(120_000L, progress!!.positionMs)
        assertEquals(7_200_000L, progress.durationMs)
        val orphan = db.playbackProgressDao().getProgress("gone_from_catalog")
        assertNotNull("progress without a catalog row must survive too", orphan)
    }

    /** Builds an on-disk database exactly as Room would have created it at [version]. */
    private fun createDatabaseFromSchema(version: Int, userVersion: Int = version) {
        val schema = schema(version)
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
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
            raw.version = userVersion
        }
    }

    private fun seedRawRows() {
        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
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

    private fun currentVersion(): Int {
        val db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java).build()
        return try {
            db.openHelper.readableDatabase.version
        } finally {
            db.close()
        }
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/${DriveCinemaDatabase::class.java.name}/$version.json"
        // Schemas are exported by :shared (where the @Database class lives); tests run from app/ or the root.
        return listOf(File("../shared/$relative"), File("shared/$relative")).firstOrNull { it.exists() }
            ?: File("../shared/$relative")
    }

    private fun schema(version: Int): JSONObject {
        val file = schemaFile(version)
        assertTrue("Exported schema not found: ${file.absolutePath}", file.exists())
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private companion object {
        const val BASELINE_VERSION = 2
    }
}
