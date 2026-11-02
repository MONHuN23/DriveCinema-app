package com.drivecinema.app.core.devicesync

import com.drivecinema.app.core.database.dao.SyncSourceRow
import com.drivecinema.app.core.database.dao.SyncTitleRow
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind

/**
 * Maps this device's progress keys (the played copy: Drive file ID, `content://`, `file:`, `dchost://`) to keys every
 * device understands, and back (design §4):
 * 1. a Drive copy → `drive:<fileId>`;
 * 2. another copy whose title has a Drive copy → `drive:<that fileId>`;
 * 3. otherwise the title's TMDB identity → `tmdb:movie:<id>` / `tmdb:tv:<seriesId>:s<S>e<E>`;
 * 4. otherwise nothing: the row stays on this device.
 */
class SyncKeys(
    sources: List<SyncSourceRow>,
    titles: List<SyncTitleRow>
) {
    private val sourceByKey = sources.associateBy { it.sourceKey }
    private val sourcesByItem = sources.groupBy { it.mediaItemId }
    private val titleById = titles.associateBy { it.id }
    private val titleIdBySyncKey: Map<String, String> = buildMap {
        // Sorted, so two titles with the same TMDB identity always resolve to the same one.
        for (title in titles.sortedBy { it.id }) {
            val key = tmdbKey(title) ?: continue
            putIfAbsent(key, title.id)
        }
    }

    fun syncKeyOf(progressKey: String): String? {
        val source = sourceByKey[progressKey]
        if (source?.kind == SourceKind.DRIVE || (source == null && isDriveFileId(progressKey))) return DRIVE + progressKey
        val itemId = source?.mediaItemId ?: return null
        val driveCopy = sourcesByItem[itemId].orEmpty()
            .filter { it.kind == SourceKind.DRIVE }
            .sortedWith(compareByDescending<SyncSourceRow> { it.isAvailable }.thenBy { it.sourceKey })
            .firstOrNull()
        if (driveCopy != null) return DRIVE + driveCopy.sourceKey
        return titleById[itemId]?.let(::tmdbKey)
    }

    /**
     * The local progress key a remote entry is written to, or null when this device doesn't know the title (not
     * crawled yet, or not in this library). [progress] picks the title's existing row for `tmdb:` keys.
     */
    fun localKeyOf(syncKey: String, progress: Map<String, PlaybackProgressEntity>): String? {
        if (syncKey.startsWith(DRIVE)) {
            val fileId = syncKey.removePrefix(DRIVE)
            val known = sourceByKey[fileId]?.kind == SourceKind.DRIVE || (sourceByKey[fileId] == null && fileId in titleById)
            return fileId.takeIf { known && isDriveFileId(it) }
        }
        val itemId = titleIdBySyncKey[syncKey] ?: return null
        val copies = sourcesByItem[itemId].orEmpty()
        val keys = copies.map { it.sourceKey } + itemId
        keys.mapNotNull { progress[it] }.maxByOrNull { it.lastWatchedAt }?.let { return it.mediaItemId }
        return copies
            .sortedWith(compareByDescending<SyncSourceRow> { it.isAvailable }.thenBy { it.kind != SourceKind.DRIVE }.thenBy { it.sourceKey })
            .firstOrNull()?.sourceKey
    }

    companion object {
        const val DRIVE = "drive:"
        const val TMDB_MOVIE = "tmdb:movie:"
        const val TMDB_TV = "tmdb:tv:"

        private val DRIVE_FILE_ID = Regex("[A-Za-z0-9_-]+")

        /** Drive file IDs have no scheme; local and PC copies always do (`content://`, `file:`, `dchost://`). */
        fun isDriveFileId(key: String): Boolean = DRIVE_FILE_ID.matches(key)

        fun tmdbKey(title: SyncTitleRow): String? = when (title.type) {
            MediaType.MOVIE -> title.tmdbId?.let { "$TMDB_MOVIE$it" }
            MediaType.EPISODE -> {
                val series = title.seriesTmdbId
                val season = title.seasonNumber
                val episode = title.episodeNumber
                if (series != null && season != null && episode != null) "$TMDB_TV$series:s${season}e$episode" else null
            }
        }
    }
}
