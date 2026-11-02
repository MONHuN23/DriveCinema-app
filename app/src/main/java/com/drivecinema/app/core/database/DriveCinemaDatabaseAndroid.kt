package com.drivecinema.app.core.database

import android.content.Context
import androidx.room.Room
import com.drivecinema.app.core.logging.DebugPerf

// Android builder for the shared [DriveCinemaDatabase] (the class itself lives in `:shared`).

@Volatile
private var INSTANCE: DriveCinemaDatabase? = null

fun DriveCinemaDatabase.Companion.getInstance(context: Context): DriveCinemaDatabase {
    return INSTANCE ?: synchronized(DriveCinemaDatabase::class) {
        INSTANCE ?: build(context, DATABASE_NAME, countQueries = true).also { INSTANCE = it }
    }
}

/**
 * Tests only: closes and forgets the app's instance. A test that starts an activity otherwise leaves it open, with
 * Room's IO thread still using a database file Robolectric deletes before the next test (a native SQLite crash).
 */
internal fun DriveCinemaDatabase.Companion.closeInstanceForTests() {
    synchronized(DriveCinemaDatabase::class) {
        INSTANCE?.close()
        INSTANCE = null
    }
}

/**
 * Production configuration. Only the pre-export v1 schema is rebuilt destructively (as before);
 * any later version change requires an explicit migration in [DriveCinemaMigrations] and fails
 * loudly instead of silently dropping watch history. [countQueries]: the app's instance counts its queries in
 * debug builds (Task M.0); tests leave it off.
 */
internal fun DriveCinemaDatabase.Companion.build(context: Context, name: String, countQueries: Boolean = false): DriveCinemaDatabase =
    Room.databaseBuilder(context.applicationContext, DriveCinemaDatabase::class.java, name)
        .let { if (countQueries) DebugPerf.countQueries(it) else it }
        .addMigrations(*DriveCinemaMigrations.ALL)
        .fallbackToDestructiveMigrationFrom(true, 1)
        .build()
