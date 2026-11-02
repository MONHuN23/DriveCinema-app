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

    /** [kind]: LOCAL for this device's folders, HOST for a paired PC's shared folders (Task I.2); same rules for both. */
    suspend fun indexFolder(treeUri: String, kind: SourceKind = SourceKind.LOCAL): LocalIndexResult {
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
            val visibleSeriesIds = dao.getVisibleSeriesIds().toSet()

            fun source(video: LocalVideo, itemId: String) = MediaSourceEntity(
                sourceKey = video.sourceKey,
                mediaItemId = itemId,
                kind = kind,
                libraryRootId = tree,
                sizeBytes = video.sizeBytes,
                lastSeenAt = now
            )

            for (film in library.films.filter { it.sourceKey !in known }) {
                val parsed = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(film.fileName)
                val sameTitle = preferVisible(movies.filter { normalize(it.title) == normalize(parsed.cleanTitle) }) { !it.isDeleted }
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
                val matchingSeries = preferVisible(seriesRows.filter {
                    normalize(it.title) == normalize(show.title) && (show.year == null || it.year == null || it.year == show.year)
                }) { it.id in visibleSeriesIds }
                // A real (Drive) show wins over a show this app made from file names earlier.
                val existingSeries = matchingSeries.singleOrNull() ?: matchingSeries.filterNot { isAppMade(it.id) }.singleOrNull()
                val seriesId = existingSeries?.id ?: localSeriesId(tree, show.folderName).also { id ->
                    newSeries += SeriesEntity(id = id, title = show.title, year = show.year)
                }
                val existingEpisodes = existingSeries?.let { dao.getAllEpisodesOfSeries(it.id) }
                    ?: matchingSeries.flatMap { dao.getAllEpisodesOfSeries(it.id) }

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

            linked += mergeIntoRealTitles(library, known, movies, seriesRows, visibleSeriesIds)

            dao.markTreeUnavailable(kind, tree)
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

    /**
     * Late merge: a copy indexed into a title this app made itself (e.g. before its Drive copy was synced) joins the real
     * title once the match is unambiguous: a film by title + year, an episode by show + season + episode. Found on the
     * device: "Avatar Seven Havens" episode 1 from the PC stayed a show of its own after the Drive show appeared.
     * Ambiguous matches (the only ones that go to "Review possible duplicates") are never merged here, so a merge the
     * user rejected stays rejected. Moving a source keeps its watch history (progress is keyed by the copy).
     */
    private suspend fun mergeIntoRealTitles(
        library: LocalLibrary,
        known: Set<String>,
        movies: List<MediaItemEntity>,
        seriesRows: List<SeriesEntity>,
        visibleSeriesIds: Set<String>
    ): Int {
        val undecided = dao.getPendingMatches().map { it.sourceKey }.toSet()
        suspend fun appMadeItemOf(sourceKey: String): String? =
            if (sourceKey !in known || sourceKey in undecided) null
            else dao.sourceItemId(sourceKey)?.takeIf { isAppMade(it) }
        var merged = 0
        for (film in library.films) {
            val itemId = appMadeItemOf(film.sourceKey) ?: continue
            val parsed = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(film.fileName)
            val year = parsed.year ?: continue
            val target = movies.filter {
                !it.isDeleted && !isAppMade(it.id) && it.year == year && normalize(it.title) == normalize(parsed.cleanTitle)
            }.singleOrNull() ?: continue
            if (target.id != itemId) {
                dao.relinkSource(film.sourceKey, target.id)
                merged++
            }
        }
        for (show in library.shows) {
            val realSeries = seriesRows.filter {
                !isAppMade(it.id) && it.id in visibleSeriesIds && normalize(it.title) == normalize(show.title) &&
                    (show.year == null || it.year == null || it.year == show.year)
            }.singleOrNull() ?: continue
            val realEpisodes = dao.getAllEpisodesOfSeries(realSeries.id).filter { !isAppMade(it.id) }
            for (ep in show.episodes) {
                val itemId = appMadeItemOf(ep.video.sourceKey) ?: continue
                val twin = realEpisodes.filter { it.seasonNumber == ep.season && it.episodeNumber == ep.episode }
                when {
                    twin.size == 1 -> {
                        dao.relinkSource(ep.video.sourceKey, twin[0].id)
                        merged++
                    }
                    twin.isEmpty() -> dao.moveToSeries(itemId, realSeries.id)
                }
            }
        }
        return merged
    }

    private fun isAppMade(id: String) = id.startsWith(LOCAL_ID_PREFIX)

    /**
     * Matches against titles that are visible right now; hidden leftovers (a deleted film, a show folder merged into
     * another on Drive) only count when nothing visible matches, e.g. to bring a hidden title back with its history.
     * Found on the device: an emptied second "Westworld" made the match ambiguous, so a PC copy became a duplicate show.
     */
    private fun <T> preferVisible(matches: List<T>, isVisible: (T) -> Boolean): List<T> =
        matches.filter(isVisible).ifEmpty { matches }

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
