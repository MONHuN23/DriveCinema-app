package com.drivecinema.app.core.sync

import com.drivecinema.app.core.logging.AppLog
import com.drivecinema.app.core.logging.Perf
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.dao.SyncMetadataDao
import com.drivecinema.app.core.database.entity.SyncMetadataEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

data class DriveChange(
    val fileId: String,
    val removed: Boolean = false,
    val trashed: Boolean = false,
    val name: String? = null,
    val mimeType: String? = null,
    val parents: List<String> = emptyList()
)

sealed interface SyncAction {
    data class Upsert(val change: DriveChange) : SyncAction
    data class MarkDeleted(val fileId: String) : SyncAction
    data object Ignore : SyncAction
}

class DriveLibrarySyncEngine(
    private val crawler: FolderQueueCrawler,
    private val mediaDao: MediaDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val tmdbClient: com.drivecinema.app.core.metadata.TmdbClient? = null,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://www.googleapis.com/drive/v3",
    private val clock: () -> Long = System::currentTimeMillis
) {
    // Serialises catalog writes so a stale crawl can never interleave with a library switch.
    private val catalogWriteMutex = Mutex()

    companion object {
        const val SYNC_TOKEN_KEY = "DRIVE_CHANGES_PAGE_TOKEN"
        private const val TAG = "DriveLibrarySyncEngine"

        /**
         * Why a crawl failed, for the log: the exception type and its message (e.g. "IOException: Failed to query
         * folder … code 503"). A JSON error's message can quote Drive's answer, file names included, so it is left out.
         */
        internal fun describeFailure(e: Exception): String = when (e) {
            is org.json.JSONException -> "JSONException (unreadable answer from Drive)"
            else -> "${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}"
        }

        /** `sync_metadata` rows `TMDB_NO_MATCH|<title key>`, timestamp = when TMDB answered "no match" (Task N.2). */
        const val TMDB_NO_MATCH_PREFIX = "TMDB_NO_MATCH|"

        /** A "no match" is asked again after a week: TMDB may have added the title by then. */
        const val TMDB_NO_MATCH_RETRY_MS = 7L * 24 * 60 * 60 * 1000

        /** `sync_metadata` row whose token is the [SERIES_MATCHING_VERSION] the stored series matches were made with. */
        const val SERIES_MATCHING_KEY = "TMDB_SERIES_MATCHING"

        /**
         * Raise it when the way series are matched changes: the next crawl matches every series again, from its file
         * names, and forgets the series "no match" answers (F-48: the desktop kept an older build's "no match" for
         * "One Piece Anime" for a week after F-43, and a series matched by older rules keeps them until it's renamed).
         * 2: F-43 (anime, arcs, running numbers) and F-48 (bare TMDB entries).
         */
        const val SERIES_MATCHING_VERSION = "2"

        fun determineAction(change: DriveChange, parentScope: Set<String>): SyncAction {
            if (change.removed || change.trashed) {
                return SyncAction.MarkDeleted(change.fileId)
            }

            if (change.parents.isNotEmpty() && change.parents.none { it in parentScope }) {
                return SyncAction.MarkDeleted(change.fileId)
            }

            if (FolderQueueCrawler.isVideo(change.mimeType, change.name) ||
                FolderQueueCrawler.isFolder(change.mimeType)
            ) {
                return SyncAction.Upsert(change)
            }

            return SyncAction.Ignore
        }
    }

    /**
     * Full crawl of the configured library. After a fully successful crawl the visible catalog is
     * atomically replaced by exactly the crawled items (others are soft-deleted, never removed), so a
     * catalog left mixed by an interrupted library switch self-heals on the next launch. A failed crawl
     * changes nothing, and an empty crawl result never hides the existing catalog (it may be transient).
     */
    suspend fun executeInitialCrawl(
        filmsFolderId: String? = null,
        seriesFolderId: String? = null,
        accessToken: String,
        rootFolderId: String? = null
    ): Result<Unit> {
        return try {
            val library = Perf.measure("sync.crawl") { span ->
                crawler.crawl(filmsFolderId, seriesFolderId, accessToken, rootFolderId).also {
                    span.put("requests", it.driveRequests).put("movies", it.movies.size)
                        .put("series", it.series.size).put("episodes", it.episodes.size)
                }
            }
            Perf.measure("sync.dbWrite") {
                catalogWriteMutex.withLock {
                    // A cancelled (superseded) crawl must not write its now-stale results.
                    currentCoroutineContext().ensureActive()
                    if (library.movies.isEmpty() && library.episodes.isEmpty()) {
                        mediaDao.upsertCrawledSeries(library.series)
                    } else {
                        val rematchAll = tmdbClient != null &&
                            syncMetadataDao.getMetadata(SERIES_MATCHING_KEY)?.syncToken != SERIES_MATCHING_VERSION
                        mediaDao.replaceVisibleLibrary(
                            library.movies, library.series, library.episodes, libraryRootId = rootFolderId,
                            fileInfo = library.fileInfo, rematchAllSeries = rematchAll
                        )
                        if (rematchAll) {
                            syncMetadataDao.deleteWithPrefixUpTo("${TMDB_NO_MATCH_PREFIX}series|", Long.MAX_VALUE)
                            syncMetadataDao.setMetadata(
                                SyncMetadataEntity(SERIES_MATCHING_KEY, syncToken = SERIES_MATCHING_VERSION, lastSyncTimestamp = clock())
                            )
                        }
                    }
                }
            }

            // Enrich all unenriched catalog items with TMDB metadata
            enrichExistingCatalog()

            // Task N.1: no `changes/startPageToken` request any more (one Drive request, ~0.4 s, per sync): only
            // [syncIncrementalChanges] read the token, and nothing calls it.
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The callers only show "not refreshed": without this line a failed startup crawl left no trace (N.2 device check).
            AppLog.w(TAG, "Library crawl failed, catalog left unchanged: ${describeFailure(e)}")
            Result.failure(e)
        }
    }

    /**
     * Re-indexes the catalog from a newly selected library folder. The crawl runs first; only if it
     * fully succeeds are the visible catalog items atomically replaced (old items are soft-deleted,
     * never removed) and [onCommitted] invoked in the same non-cancellable critical section, so the
     * catalog and the persisted folder selection cannot diverge. Any failure leaves the catalog untouched.
     *
     * @return the number of playable items (movies + episodes) in the new library.
     */
    suspend fun switchLibrary(
        filmsFolderId: String?,
        seriesFolderId: String?,
        accessToken: String,
        rootFolderId: String,
        onCommitted: () -> Unit = {}
    ): Result<Int> {
        return try {
            val library = crawler.crawl(filmsFolderId, seriesFolderId, accessToken, rootFolderId)
            catalogWriteMutex.withLock {
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    mediaDao.replaceVisibleLibrary(library.movies, library.series, library.episodes, libraryRootId = rootFolderId, fileInfo = library.fileInfo)
                    // The previous library's Changes page token (never used since N.1) must not outlive the switch.
                    syncMetadataDao.clearMetadata(SYNC_TOKEN_KEY)
                    onCommitted()
                }
            }
            Result.success(library.movies.size + library.episodes.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Titles TMDB answered with "no match" keep `tmdbId = NULL`, so without a memory every enrichment pass (app
     * start, crawl, refresh) would search them again. The answer is kept in `sync_metadata` (key: id + title + year),
     * so it survives app restarts (Task N.2, F-40), and expires after [TMDB_NO_MATCH_RETRY_MS]. A TMDB error is not
     * remembered and is retried; a renamed title has a new key and is searched again.
     */
    private suspend fun rememberNoMatch(key: String) {
        syncMetadataDao.setMetadata(SyncMetadataEntity(TMDB_NO_MATCH_PREFIX + key, syncToken = null, lastSyncTimestamp = clock()))
    }

    suspend fun enrichExistingCatalog(): Result<Int> {
        val tmdb = tmdbClient ?: return Result.success(0)
        val span = Perf.begin("sync.tmdb")
        val requestsAtStart = tmdb.requestCount
        var searched = 0
        var enrichedCount = 0
        return try {
            syncMetadataDao.deleteWithPrefixUpTo(TMDB_NO_MATCH_PREFIX, clock() - TMDB_NO_MATCH_RETRY_MS)
            val tmdbNoMatch = syncMetadataDao.getKeysWithPrefix(TMDB_NO_MATCH_PREFIX)
                .mapTo(HashSet()) { it.removePrefix(TMDB_NO_MATCH_PREFIX) }
            span.put("knownNoMatch", tmdbNoMatch.size)

            // 1. Enrich unenriched movies
            val unenrichedMovies = mediaDao.getUnenrichedMovies()
            for (movie in unenrichedMovies) {
                val noMatchKey = "movie|${movie.id}|${movie.title}|${movie.year}"
                if (noMatchKey in tmdbNoMatch) continue
                try {
                    searched++
                    val searchRes = tmdb.searchMovie(movie.title, movie.year)
                    val meta = searchRes.getOrNull()
                    if (searchRes.isSuccess && meta == null) rememberNoMatch(noMatchKey)
                    if (meta != null) {
                        val enrichedMovie = movie.copy(
                            tmdbId = meta.tmdbId,
                            // A year from the filename wins so the next crawl can tell an unchanged file from a renamed one.
                            year = movie.year ?: meta.year,
                            posterPath = meta.posterPath,
                            backdropPath = meta.backdropPath,
                            overview = meta.overview,
                            genres = meta.genres.joinToString(", ").ifEmpty { null },
                            voteAverage = meta.voteAverage,
                            voteCount = meta.voteCount
                        )
                        mediaDao.insertMediaItem(enrichedMovie)
                        enrichedCount++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("DriveLibrarySyncEngine", "Failed to enrich movie '${movie.title}'", e)
                }
            }

            // 2. Enrich unenriched series and their episodes
            val unenrichedSeries = mediaDao.getUnenrichedSeries()
            for (series in unenrichedSeries) {
                val noMatchKey = "series|${series.id}|${series.title}"
                if (noMatchKey in tmdbNoMatch) continue
                try {
                    searched++
                    // "One Piece (Anime)": search "One Piece" among animated shows; the full title if that finds nothing.
                    val searchRes = com.drivecinema.app.core.metadata.SeriesFolderSearch.find(tmdb, series.title, series.year)
                    val found = searchRes.getOrNull()
                    if (searchRes.isSuccess && found == null) rememberNoMatch(noMatchKey)
                    if (found != null) {
                        val meta = found.show
                        val searchTitle = found.searchTitle
                        val seriesEpisodes = mediaDao.getEpisodesForSeries(series.id)
                        val numbered = seriesEpisodes.mapNotNull { ep ->
                            val season = ep.seasonNumber ?: return@mapNotNull null
                            val number = ep.episodeNumber ?: return@mapNotNull null
                            season to number
                        }
                        val match = com.drivecinema.app.core.metadata.SeriesEpisodeMatcher(tmdb).match(meta.tmdbId, meta.title, searchTitle, numbered)

                        val enrichedSeries = series.copy(
                            tmdbId = meta.tmdbId,
                            year = series.year ?: meta.year,
                            posterPath = match.arc?.posterPath ?: meta.posterPath,
                            backdropPath = meta.backdropPath,
                            overview = match.arc?.overview ?: meta.overview,
                            genres = meta.genres.joinToString(", ").ifEmpty { null },
                            voteAverage = meta.voteAverage,
                            voteCount = meta.voteCount
                        )
                        mediaDao.insertSingleSeries(enrichedSeries)
                        enrichedCount++

                        for (ep in seriesEpisodes) {
                            val tmdbEp = match.episodes[(ep.seasonNumber ?: continue) to (ep.episodeNumber ?: continue)]
                                ?: continue
                            val enrichedEp = ep.copy(
                                title = tmdbEp.name.ifEmpty { ep.title },
                                overview = tmdbEp.overview ?: ep.overview,
                                backdropPath = tmdbEp.stillPath ?: ep.backdropPath,
                                voteAverage = tmdbEp.voteAverage ?: ep.voteAverage,
                                runtimeSeconds = tmdbEp.runtimeSeconds ?: ep.runtimeSeconds
                            )
                            mediaDao.insertMediaItem(enrichedEp)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("DriveLibrarySyncEngine", "Failed to enrich series '${series.title}'", e)
                }
            }

            Result.success(enrichedCount)
        } catch (e: CancellationException) {
            span.put("aborted", true)
            throw e
        } catch (e: Exception) {
            span.put("aborted", true)
            Result.failure(e)
        } finally {
            span.put("requests", tmdb.requestCount - requestsAtStart).put("searched", searched).put("enriched", enrichedCount)
            span.end()
        }
    }

    suspend fun syncIncrementalChanges(
        accessToken: String,
        filmsFolderId: String? = null,
        seriesFolderId: String? = null,
        parentScope: Set<String>,
        rootFolderId: String? = null
    ): Result<Int> {
        val metadata = syncMetadataDao.getMetadata(SYNC_TOKEN_KEY)
        val savedToken = metadata?.syncToken

        if (savedToken.isNullOrEmpty()) {
            executeInitialCrawl(filmsFolderId, seriesFolderId, accessToken, rootFolderId)
            return Result.success(0)
        }

        var pageToken = savedToken
        var changesProcessed = 0

        try {
            do {
                val urlBuilder = "$baseUrl/changes".toHttpUrl().newBuilder()
                    .addQueryParameter("pageToken", pageToken)
                    .addQueryParameter("supportsAllDrives", "true")
                    .addQueryParameter("includeItemsFromAllDrives", "true")
                    .addQueryParameter("pageSize", "1000")
                    .addQueryParameter("fields", "nextPageToken, newStartPageToken, changes(fileId, removed, file(id, name, mimeType, trashed, parents))")

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.code == 410) {
                    response.close()
                    executeInitialCrawl(filmsFolderId, seriesFolderId, accessToken, rootFolderId)
                    return Result.success(changesProcessed)
                }

                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    return Result.failure(IOException("Changes API call failed: code $code"))
                }

                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val changesArray = json.optJSONArray("changes") ?: org.json.JSONArray()

                for (i in 0 until changesArray.length()) {
                    val changeObj = changesArray.getJSONObject(i)
                    val fileId = changeObj.getString("fileId")
                    val removed = changeObj.optBoolean("removed", false)
                    val fileObj = changeObj.optJSONObject("file")

                    val trashed = fileObj?.optBoolean("trashed", false) ?: false
                    val name = if (fileObj?.has("name") == true) fileObj.getString("name") else null
                    val mimeType = if (fileObj?.has("mimeType") == true) fileObj.getString("mimeType") else null
                    val parentsList = mutableListOf<String>()
                    val parentsArray = fileObj?.optJSONArray("parents")
                    if (parentsArray != null) {
                        for (p in 0 until parentsArray.length()) {
                            parentsList.add(parentsArray.getString(p))
                        }
                    }

                    val driveChange = DriveChange(
                        fileId = fileId,
                        removed = removed,
                        trashed = trashed,
                        name = name,
                        mimeType = mimeType,
                        parents = parentsList
                    )

                    val action = determineAction(driveChange, parentScope)
                    when (action) {
                        is SyncAction.MarkDeleted -> {
                            mediaDao.markDeleted(action.fileId)
                            changesProcessed++
                        }
                        is SyncAction.Upsert -> {
                            changesProcessed++
                        }
                        SyncAction.Ignore -> {}
                    }
                }

                val nextPageToken = json.optString("nextPageToken", "").ifEmpty { null }
                val newStartPageToken = json.optString("newStartPageToken", "").ifEmpty { null }

                if (newStartPageToken != null) {
                    syncMetadataDao.setMetadata(
                        SyncMetadataEntity(
                            key = SYNC_TOKEN_KEY,
                            syncToken = newStartPageToken,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    )
                    break
                }

                pageToken = nextPageToken
            } while (pageToken != null)

            return Result.success(changesProcessed)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
