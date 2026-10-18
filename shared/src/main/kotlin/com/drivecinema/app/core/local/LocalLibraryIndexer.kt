package com.drivecinema.app.core.local

import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PendingMatchEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SourceKind
import java.security.MessageDigest
import java.util.Locale

data class LocalIndexResult(
    val videosFound: Int,
    val linkedToExisting: Int,
    val addedAsNew: Int,
    val possibleDuplicates: Int
)

/**
 * Indexes one local library folder into the unified catalog (Task C.4, design model A).
 *
 * - A local file becomes a `media_sources` row (kind LOCAL, key = its content URI = progress key).
 * - It is linked to an existing catalog title only when the match is unambiguous (same title + year, or same
 *   show + season + episode). Ambiguous → own entry + a `pending_matches` row for the user. No match → own entry.
 * - Existing source→title links are never changed by a rescan. Titles the app generated itself from a file name
 *   (local-only entries) follow parser improvements on the next scan; Drive-linked titles are never touched.
 * - Visibility swap is scoped to this folder's local sources and runs only after a complete crawl; an empty
 *   crawl never hides anything. Nothing is ever deleted and `playback_progress` is never touched.
 */
class LocalLibraryIndexer(
    private val db: DriveCinemaDatabase,
    private val crawler: LocalLibraryCrawler,
    /** Runs catalog writes in one database transaction (Android: Room's Android transaction; desktop: writer connection). */
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao get() = db.mediaSourceDao()

    suspend fun indexFolder(treeUri: String): LocalIndexResult {
        val tree = treeUri
        val library = crawler.crawl(treeUri) // throws on any listing failure → nothing changes
        if (library.isEmpty) return LocalIndexResult(0, 0, 0, 0)

        val now = clock()
        var linked = 0
        var added = 0
        val newItems = mutableListOf<MediaItemEntity>()
        val newSeries = mutableListOf<SeriesEntity>()
        val newSources = mutableListOf<MediaSourceEntity>()
        val pending = mutableListOf<PendingMatchEntity>()

        runInTransaction {
            val allKeys = library.films.map { it.sourceKey } + library.shows.flatMap { s -> s.episodes.map { it.video.sourceKey } }
            val known = allKeys.chunked(500).flatMap { dao.getExistingSourceKeys(it) }.toSet()
            val movies = dao.getAllMovieItems()
            val seriesRows = dao.getAllSeriesRows()

            fun source(video: LocalVideo, itemId: String) = MediaSourceEntity(
                sourceKey = video.sourceKey,
                mediaItemId = itemId,
                kind = SourceKind.LOCAL,
                libraryRootId = tree,
                sizeBytes = video.sizeBytes,
                lastSeenAt = now
            )

            for (film in library.films.filter { it.sourceKey !in known }) {
                val parsed = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(film.fileName)
                val sameTitle = movies.filter { normalize(it.title) == normalize(parsed.cleanTitle) }
                val candidates = when {
                    parsed.year != null -> sameTitle.filter { it.year == parsed.year }.ifEmpty { sameTitle.filter { it.year == null } }
                    else -> sameTitle
                }
                val unambiguous = parsed.year != null && candidates.size == 1 && candidates[0].year == parsed.year
                if (unambiguous) {
                    newSources += source(film, candidates[0].id)
                    linked++
                } else {
                    val itemId = localId(film.sourceKey)
                    newItems += MediaItemEntity(
                        id = itemId,
                        type = MediaType.MOVIE,
                        title = parsed.cleanTitle,
                        year = parsed.year,
                        driveFileId = itemId,
                        parentFolderId = tree
                    )
                    newSources += source(film, itemId)
                    added++
                    if (candidates.isNotEmpty()) {
                        pending += PendingMatchEntity(film.sourceKey, candidates.joinToString(",") { it.id }, now)
                    }
                }
            }

            for (show in library.shows) {
                val matchingSeries = seriesRows.filter {
                    normalize(it.title) == normalize(show.title) && (show.year == null || it.year == null || it.year == show.year)
                }
                val existingSeries = matchingSeries.singleOrNull()
                val seriesId = existingSeries?.id ?: localSeriesId(tree, show.folderName).also { id ->
                    newSeries += SeriesEntity(id = id, title = show.title, year = show.year)
                }
                val existingEpisodes = matchingSeries.flatMap { dao.getAllEpisodesOfSeries(it.id) }

                for (ep in show.episodes.filter { it.video.sourceKey !in known }) {
                    val candidates = existingEpisodes.filter { it.seasonNumber == ep.season && it.episodeNumber == ep.episode }
                    if (existingSeries != null && candidates.size == 1) {
                        newSources += source(ep.video, candidates[0].id)
                        linked++
                    } else {
                        val itemId = localId(ep.video.sourceKey)
                        newItems += MediaItemEntity(
                            id = itemId,
                            type = MediaType.EPISODE,
                            title = ep.title ?: "Episode ${ep.episode}",
                            driveFileId = itemId,
                            parentFolderId = tree,
                            seriesId = seriesId,
                            seasonNumber = ep.season,
                            episodeNumber = ep.episode
                        )
                        newSources += source(ep.video, itemId)
                        added++
                        if (candidates.isNotEmpty()) {
                            pending += PendingMatchEntity(ep.video.sourceKey, candidates.joinToString(",") { it.id }, now)
                        }
                    }
                }
            }

            // Local-only entries keep up with parser fixes (e.g. "720p VARYG" -> "Episode 1").
            for (film in library.films.filter { it.sourceKey in known }) {
                val title = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(film.fileName).cleanTitle
                if (title.isNotEmpty()) dao.updateTitle(localId(film.sourceKey), title)
            }
            for (ep in library.shows.flatMap { it.episodes }.filter { it.video.sourceKey in known }) {
                dao.updateTitle(localId(ep.video.sourceKey), ep.title ?: "Episode ${ep.episode}")
            }

            dao.markLocalTreeUnavailable(tree)
            dao.insertSeriesIfAbsent(newSeries)
            dao.insertItemsIfAbsent(newItems)
            dao.insertSourcesIfAbsent(newSources)
            dao.insertPendingMatchesIfAbsent(pending)
            allKeys.chunked(500).forEach { dao.markSourcesAvailable(it, tree, now) }
            dao.refreshVisibilityFromSources()
        }

        val total = library.films.size + library.shows.sumOf { it.episodes.size }
        return LocalIndexResult(total, linked, added, pending.size)
    }

    /** Called with the currently saved folders: copies in removed/revoked folders stop being playable. */
    suspend fun retireUnsavedFolders(savedTreeUris: Collection<String>) {
        runInTransaction {
            dao.markLocalSourcesOutsideUnavailable(savedTreeUris.toList())
            dao.refreshVisibilityFromSources()
        }
    }

    companion object {
        const val LOCAL_ID_PREFIX = com.drivecinema.app.core.player.LocalIds.PREFIX

        fun normalize(title: String): String =
            title.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

        fun localId(sourceKey: String) = LOCAL_ID_PREFIX + sha1(sourceKey)

        fun localSeriesId(treeUri: String, showFolderName: String) =
            LOCAL_ID_PREFIX + "series:" + sha1(treeUri + "|" + showFolderName.trim().lowercase(Locale.ROOT))

        private fun sha1(value: String): String =
            MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
