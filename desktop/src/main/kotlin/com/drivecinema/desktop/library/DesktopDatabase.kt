package com.drivecinema.desktop.library

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drivecinema.app.core.database.DriveCinemaDatabase
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Opens the shared DriveCinema database in the desktop data directory (bundled SQLite driver). */
object DesktopDatabase {
    fun open(dataDir: File): DriveCinemaDatabase =
        Room.databaseBuilder<DriveCinemaDatabase>(name = File(dataDir, DriveCinemaDatabase.DATABASE_NAME).absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
