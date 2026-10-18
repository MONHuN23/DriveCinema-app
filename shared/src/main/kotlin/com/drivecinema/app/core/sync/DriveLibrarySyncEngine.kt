package com.drivecinema.app.core.sync

import com.drivecinema.app.core.logging.AppLog
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
    private val baseUrl: String = "https://www.googleapis.com/drive/v3"
) {
    // Serialises catalog writes so a stale crawl can never interleave with a library switch.
    private val catalogWriteMutex = Mutex()

    companion object {
        const val SYNC_TOKEN_KEY = "DRIVE_CHANGES_PAGE_TOKEN"

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
            val library = crawler.crawl(filmsFolderId, seriesFolderId, accessToken, rootFolderId)
            catalogWriteMutex.withLock {
                // A cancelled (superseded) crawl must not write its now-stale results.
                currentCoroutineContext().ensureActive()
                if (library.movies.isEmpty() && library.episodes.isEmpty()) {
                    mediaDao.upsertCrawledSeries(library.series)
                } else {
                    mediaDao.replaceVisibleLibrary(library.movies, library.series, library.episodes, libraryRootId = rootFolderId)
                }
            }

            // Enrich all unenriched catalog items with TMDB metadata
            enrichExistingCatalog()

            val startToken = fetchStartPageToken(accessToken)
            syncMetadataDao.setMetadata(
                SyncMetadataEntity(
                    key = SYNC_TOKEN_KEY,
                    syncToken = startToken,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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
            val startToken = fetchStartPageToken(accessToken)
            catalogWriteMutex.withLock {
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    mediaDao.replaceVisibleLibrary(library.movies, library.series, library.episodes, libraryRootId = rootFolderId)
                    syncMetadataDao.setMetadata(
                        SyncMetadataEntity(
                            key = SYNC_TOKEN_KEY,
                            syncToken = startToken,
                            lastSyncTimestamp = System.currentTimeMillis()
                        )
                    )
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
     * Titles TMDB answered with "no match" in this session (key: id + title + year). They keep `tmdbId = NULL`,
     * so without this every enrichment pass (app start, crawl, refresh) would search them again. A TMDB error is
     * not remembered and is retried; a renamed title has a new key and is searched again.
     */
    private val tmdbNoMatch = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    suspend fun enrichExistingCatalog(): Result<Int> {
        val tmdb = tmdbClient ?: return Result.success(0)
        return try {
            var enrichedCount = 0

            // 1. Enrich unenriched movies
            val unenrichedMovies = mediaDao.getUnenrichedMovies()
            for (movie in unenrichedMovies) {
                val noMatchKey = "movie|${movie.id}|${movie.title}|${movie.year}"
                if (noMatchKey in tmdbNoMatch) continue
                try {
                    val searchRes = tmdb.searchMovie(movie.title, movie.year)
                    val meta = searchRes.getOrNull()
                    if (searchRes.isSuccess && meta == null) tmdbNoMatch += noMatchKey
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
                    val searchRes = tmdb.searchSeries(series.title)
                    val meta = searchRes.getOrNull()
                    if (searchRes.isSuccess && meta == null) tmdbNoMatch += noMatchKey
                    if (meta != null) {
                        val enrichedSeries = series.copy(
                            tmdbId = meta.tmdbId,
                            year = series.year ?: meta.year,
                            posterPath = meta.posterPath,
                            backdropPath = meta.backdropPath,
                            overview = meta.overview,
                            genres = meta.genres.joinToString(", ").ifEmpty { null },
                            voteAverage = meta.voteAverage,
                            voteCount = meta.voteCount
                        )
                        mediaDao.insertSingleSeries(enrichedSeries)
                        enrichedCount++

                        val seriesEpisodes = mediaDao.getEpisodesForSeries(series.id)
                        val seasons = seriesEpisodes.mapNotNull { it.seasonNumber }.distinct()
                        for (seasonNum in seasons) {
                            val seasonRes = tmdb.getSeasonEpisodes(meta.tmdbId, seasonNum)
                            val tmdbEps = seasonRes.getOrNull() ?: continue
                            val tmdbMap = tmdbEps.associateBy { it.episodeNumber }

                            val localSeasonEps = seriesEpisodes.filter { it.seasonNumber == seasonNum }
                            for (ep in localSeasonEps) {
                                val epNum = ep.episodeNumber ?: continue
                                val tmdbEp = tmdbMap[epNum] ?: continue
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
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("DriveLibrarySyncEngine", "Failed to enrich series '${series.title}'", e)
                }
            }

            Result.success(enrichedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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

    private fun fetchStartPageToken(accessToken: String): String {
        val url = "$baseUrl/changes/startPageToken".toHttpUrl().newBuilder()
            .addQueryParameter("supportsAllDrives", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to get startPageToken: ${response.code}")
            }
            val body = response.body?.string() ?: ""
            return JSONObject(body).getString("startPageToken")
        }
    }
}
