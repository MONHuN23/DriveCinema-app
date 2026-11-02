package com.drivecinema.desktop.library

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.DriveCinemaSchemaSteps
import com.drivecinema.app.core.database.SchemaStep
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Opens the shared DriveCinema database in the desktop data directory (bundled SQLite driver).
 * Upgrades run the same SQL as Android ([DriveCinemaSchemaSteps]); a version with no upgrade path fails to open
 * instead of being rebuilt, so watch history is never dropped.
 */
object DesktopDatabase {
    fun open(dataDir: File): DriveCinemaDatabase =
        Room.databaseBuilder<DriveCinemaDatabase>(name = File(dataDir, DriveCinemaDatabase.DATABASE_NAME).absolutePath)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*MIGRATIONS)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    /**
     * The schema version of the database in [dataDir] when a newer DriveCinema wrote it (for example a development
     * build), else null. Room can't open it and has no way down, so the caller explains instead of crashing.
     */
    fun newerSchemaVersion(dataDir: File): Int? {
        val file = File(dataDir, DriveCinemaDatabase.DATABASE_NAME)
        if (!file.isFile) return null
        val stored = BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            connection.prepare("PRAGMA user_version").use { if (it.step()) it.getLong(0).toInt() else 0 }
        }
        return stored.takeIf { it > DriveCinemaDatabase.SCHEMA_VERSION }
    }

    val MIGRATIONS: Array<Migration> = DriveCinemaSchemaSteps.ALL.map { it.toMigration() }.toTypedArray()

    private fun SchemaStep.toMigration(): Migration = object : Migration(from, to) {
        override fun migrate(connection: SQLiteConnection) {
            statements.forEach { connection.execSQL(it) }
        }
    }
}
