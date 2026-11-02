package com.drivecinema.app.core.database

/** One schema upgrade, `from` → `from + 1`, as plain SQL so Android and the desktop run exactly the same statements. */
class SchemaStep(val from: Int, val to: Int, val statements: List<String>) {
    init {
        require(to == from + 1) { "A schema step goes up one version at a time" }
    }
}

/**
 * The SQL of every non-destructive schema upgrade of [DriveCinemaDatabase]. Each platform wraps these in Room
 * `Migration`s: Android in `DriveCinemaMigrations` (`SupportSQLiteDatabase`), the desktop in `DesktopDatabase`
 * (`SQLiteConnection`). A version bump adds a step here and commits the exported `shared/schemas/.../<n>.json`;
 * `DatabaseMigrationTest` (Android) and `DesktopMigrationTest` (desktop) fail if a step is missing or loses watch history.
 */
object DriveCinemaSchemaSteps {

    /**
     * v2 → v3 (Task C.4): adds `media_sources` and `pending_matches`. Additive only — no existing table,
     * column or key changes. Every existing catalog item becomes its own Drive source (Drive file ID),
     * so all existing `playback_progress` rows keep matching 1:1.
     */
    val STEP_2_3 = SchemaStep(
        2, 3,
        listOf(
            "CREATE TABLE IF NOT EXISTS `media_sources` (`sourceKey` TEXT NOT NULL, `mediaItemId` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `libraryRootId` TEXT, `sizeBytes` INTEGER, `isAvailable` INTEGER NOT NULL, " +
                "`lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))",
            "CREATE INDEX IF NOT EXISTS `index_media_sources_mediaItemId` ON `media_sources` (`mediaItemId`)",
            "CREATE INDEX IF NOT EXISTS `index_media_sources_libraryRootId` ON `media_sources` (`libraryRootId`)",
            "CREATE TABLE IF NOT EXISTS `pending_matches` (`sourceKey` TEXT NOT NULL, " +
                "`candidateItemIds` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))",
            "INSERT OR IGNORE INTO `media_sources` " +
                "(`sourceKey`, `mediaItemId`, `kind`, `libraryRootId`, `sizeBytes`, `isAvailable`, `lastSeenAt`) " +
                "SELECT `id`, `id`, 'DRIVE', NULL, NULL, CASE WHEN `isDeleted` = 1 THEN 0 ELSE 1 END, `lastSyncedAt` " +
                "FROM `media_items`"
        )
    )

    /**
     * v3 → v4 (Task I.1): adds `import_queue`, the desktop's upload queue. Additive only; Android keeps the table
     * empty. No catalog or watch-history change.
     */
    val STEP_3_4 = SchemaStep(
        3, 4,
        listOf(
            "CREATE TABLE IF NOT EXISTS `import_queue` (`importKey` TEXT NOT NULL, `localPath` TEXT NOT NULL, " +
                "`fileName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `lastModifiedMs` INTEGER NOT NULL, " +
                "`targetFolderPath` TEXT, `libraryRootId` TEXT NOT NULL, `state` TEXT NOT NULL, `message` TEXT, " +
                "`sessionUri` TEXT, `bytesUploaded` INTEGER NOT NULL, `driveFileId` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`importKey`))",
            "CREATE INDEX IF NOT EXISTS `index_import_queue_state` ON `import_queue` (`state`)",
            "CREATE INDEX IF NOT EXISTS `index_import_queue_localPath` ON `import_queue` (`localPath`)"
        )
    )

    /**
     * v4 → v5 (Task L.3): technical details per copy on `media_sources` (resolution, length, container, codecs).
     * Additive, nullable columns only: existing rows read as "unknown"; no catalog or watch-history change.
     */
    val STEP_4_5 = SchemaStep(
        4, 5,
        listOf(
            "ALTER TABLE `media_sources` ADD COLUMN `widthPx` INTEGER",
            "ALTER TABLE `media_sources` ADD COLUMN `heightPx` INTEGER",
            "ALTER TABLE `media_sources` ADD COLUMN `durationMs` INTEGER",
            "ALTER TABLE `media_sources` ADD COLUMN `container` TEXT",
            "ALTER TABLE `media_sources` ADD COLUMN `videoCodec` TEXT",
            "ALTER TABLE `media_sources` ADD COLUMN `audioCodec` TEXT",
            "ALTER TABLE `media_sources` ADD COLUMN `audioChannels` INTEGER",
            "ALTER TABLE `media_sources` ADD COLUMN `frameRate` REAL",
            "ALTER TABLE `media_sources` ADD COLUMN `isHdr` INTEGER"
        )
    )

    val ALL: List<SchemaStep> = listOf(STEP_2_3, STEP_3_4, STEP_4_5)
}
