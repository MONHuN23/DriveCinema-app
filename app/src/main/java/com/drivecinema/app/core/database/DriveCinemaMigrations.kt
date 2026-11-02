package com.drivecinema.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, non-destructive schema migrations for [DriveCinemaDatabase] on Android.
 *
 * Schema v2 is the exported baseline (`shared/schemas/.../2.json`). The SQL of every step lives in
 * [DriveCinemaSchemaSteps] (`:shared`), so the desktop (`DesktopDatabase`) runs exactly the same upgrades.
 * Every version bump after v2 MUST:
 *  1. add a [SchemaStep] there that preserves `playback_progress` (and ideally all catalog data),
 *  2. commit the newly exported `shared/schemas/.../<n + 1>.json`.
 * `DatabaseMigrationTest` fails if a step is missing or if a migration loses watch history.
 */
object DriveCinemaMigrations {

    /** v2 → v3 (Task C.4, [DriveCinemaSchemaSteps.STEP_2_3]) and every later step. */
    val ALL: Array<Migration> = DriveCinemaSchemaSteps.ALL.map { it.toMigration() }.toTypedArray()

    private fun SchemaStep.toMigration(): Migration = object : Migration(from, to) {
        override fun migrate(db: SupportSQLiteDatabase) {
            statements.forEach { db.execSQL(it) }
        }
    }
}
