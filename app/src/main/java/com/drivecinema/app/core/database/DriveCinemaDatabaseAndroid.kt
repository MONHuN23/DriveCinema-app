package com.drivecinema.app.core.database

import android.content.Context
import androidx.room.Room

// Android builder for the shared [DriveCinemaDatabase] (the class itself lives in `:shared`).

@Volatile
private var INSTANCE: DriveCinemaDatabase? = null

fun DriveCinemaDatabase.Companion.getInstance(context: Context): DriveCinemaDatabase {
    return INSTANCE ?: synchronized(DriveCinemaDatabase::class) {
        INSTANCE ?: build(context, DATABASE_NAME).also { INSTANCE = it }
    }
}

/**
 * Production configuration. Only the pre-export v1 schema is rebuilt destructively (as before);
 * any later version change requires an explicit migration in [DriveCinemaMigrations] and fails
 * loudly instead of silently dropping watch history.
 */
internal fun DriveCinemaDatabase.Companion.build(context: Context, name: String): DriveCinemaDatabase =
    Room.databaseBuilder(context.applicationContext, DriveCinemaDatabase::class.java, name)
        .addMigrations(*DriveCinemaMigrations.ALL)
        .fallbackToDestructiveMigrationFrom(true, 1)
        .build()
