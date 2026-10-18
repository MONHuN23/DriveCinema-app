package com.drivecinema.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, non-destructive schema migrations for [DriveCinemaDatabase].
 *
 * Schema v2 is the exported baseline (`app/schemas/.../2.json`). Every version bump after v2 MUST:
 *  1. add a `Migration(n, n + 1)` here that preserves `playback_progress` (and ideally all catalog data),
 *  2. commit the newly exported `app/schemas/.../<n + 1>.json`.
 * `DatabaseMigrationTest` fails if a step is missing or if a migration loses watch history.
 */
object DriveCinemaMigrations {

    /**
     * v2 → v3 (Task C.4): adds `media_sources` and `pending_matches`. Additive only — no existing table,
     * column or key changes. Every existing catalog item becomes its own Drive source (Drive file ID),
     * so all existing `playback_progress` rows keep matching 1:1.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `media_sources` (`sourceKey` TEXT NOT NULL, `mediaItemId` TEXT NOT NULL, " +
                    "`kind` TEXT NOT NULL, `libraryRootId` TEXT, `sizeBytes` INTEGER, `isAvailable` INTEGER NOT NULL, " +
                    "`lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_sources_mediaItemId` ON `media_sources` (`mediaItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_sources_libraryRootId` ON `media_sources` (`libraryRootId`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `pending_matches` (`sourceKey` TEXT NOT NULL, " +
                    "`candidateItemIds` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `media_sources` " +
                    "(`sourceKey`, `mediaItemId`, `kind`, `libraryRootId`, `sizeBytes`, `isAvailable`, `lastSeenAt`) " +
                    "SELECT `id`, `id`, 'DRIVE', NULL, NULL, CASE WHEN `isDeleted` = 1 THEN 0 ELSE 1 END, `lastSyncedAt` " +
                    "FROM `media_items`"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_2_3)
}
