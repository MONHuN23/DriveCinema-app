package com.drivecinema.app.core.sync

import com.drivecinema.app.core.metadata.MediaInfoFormat
import com.drivecinema.app.core.metadata.SeriesFolderListing
import com.drivecinema.app.core.metadata.SeriesLayout
import com.drivecinema.app.core.metadata.VideoFiles
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

data class CrawledLibrary(
    val movies: List<MediaItemEntity>,
    val series: List<SeriesEntity>,
    val episodes: List<MediaItemEntity>,
    val folderScope: Set<String>,
    /** Task L.3: what Drive knows about each crawled video, by file ID. */
    val fileInfo: Map<String, DriveFileInfo> = emptyMap(),
    /** Task M.0: Drive listing requests this crawl made (pages included). */
    val driveRequests: Int = 0
)

/**
 * Task L.3: size and, for videos Drive has processed, resolution and length, from the same listing request
 * (`size`, `videoMediaMetadata`); no extra Drive calls. Null = Drive didn't say.
 */
data class DriveFileInfo(
    val sizeBytes: Long? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationMs: Long? = null,
    val container: String? = null
)

/**
 * Lists a Drive library: Films (any depth), videos in the library root, and Series (show → season → episode).
 *
 * Task N.1: the crawl goes level by level, and the folders of one level are listed together, up to
 * [maxParentsPerQuery] per request (`'a' in parents or 'b' in parents …`, with each file's `parents`), so the number of
 * requests follows the depth of the library instead of its number of folders. A first page Drive rejects (HTTP 400,
 * e.g. a query too long) is split in two and retried. The result is the same as listing folder by folder.
 */
class FolderQueueCrawler(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://www.googleapis.com/drive/v3",
    private val maxParentsPerQuery: Int = DEFAULT_MAX_PARENTS_PER_QUERY
) {

    // Every listing request; a crawl reports how much it grew (Task M.0).
    private val requestCount = AtomicInteger()

    companion object {
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

        /** Folders per listing request: about 2.5 KB of query. Not measured against Drive yet (N.1 spike). */
        const val DEFAULT_MAX_PARENTS_PER_QUERY = 50

        val VIDEO_EXTENSIONS = VideoFiles.VIDEO_EXTENSIONS

        fun isVideo(mimeType: String?, name: String?): Boolean = VideoFiles.isVideo(mimeType, name)

        fun isFolder(mimeType: String?): Boolean = mimeType == FOLDER_MIME_TYPE
    }

    suspend fun crawl(
        filmsFolderId: String? = null,
        seriesFolderId: String? = null,
        accessToken: String,
        rootFolderId: String? = null
    ): CrawledLibrary {
        val movies = mutableListOf<MediaItemEntity>()
        val seriesList = mutableListOf<SeriesEntity>()
        val episodes = mutableListOf<MediaItemEntity>()
        val folderScope = mutableSetOf<String>()
        val fileInfo = mutableMapOf<String, DriveFileInfo>()
        val requestsAtStart = requestCount.get()
        fun remember(file: RawDriveFile) { fileInfo[file.id] = file.info }

        filmsFolderId?.let { folderScope.add(it) }
        seriesFolderId?.let { folderScope.add(it) }
        rootFolderId?.let { folderScope.add(it) }

        // Children of every folder listed so far, in Drive's order. Each folder is listed once.
        val children = mutableMapOf<String, List<RawDriveFile>>()
        suspend fun listAll(folderIds: List<String>) {
            val missing = folderIds.distinct().filterNot { it in children }
            if (missing.isNotEmpty()) children += fetchChildrenOf(missing, accessToken)
        }
        fun childrenOf(folderId: String) = children[folderId].orEmpty()
        fun subfolders(folderId: String) = childrenOf(folderId).filter { isFolder(it.mimeType) }.map { it.id }

        // 1. Films are walked breadth-first, the order the old folder-by-folder queue used. Series have three fixed
        //    levels (the Series folder, the show folders, the season folders); both advance in the same requests,
        //    and the library root rides along with the first level.
        var filmLevel = listOfNotNull(filmsFolderId)
        val filmsQueued = filmLevel.toMutableSet()
        var seriesLevel = listOfNotNull(seriesFolderId)
        var seriesDepth = 0
        var firstLevel = true
        while (filmLevel.isNotEmpty() || seriesLevel.isNotEmpty()) {
            listAll(filmLevel + seriesLevel + listOfNotNull(rootFolderId?.takeIf { firstLevel }))
            firstLevel = false

            val nextFilmLevel = mutableListOf<String>()
            for (currentFolder in filmLevel) {
                for (item in childrenOf(currentFolder)) {
                    if (isFolder(item.mimeType)) {
                        folderScope.add(item.id)
                        if (filmsQueued.add(item.id)) nextFilmLevel.add(item.id)
                    } else if (isVideo(item.mimeType, item.name)) {
                        remember(item)
                        movies.add(movie(item, parentFolderId = currentFolder))
                    }
                }
            }
            filmLevel = nextFilmLevel

            seriesDepth++
            seriesLevel = if (seriesDepth < SERIES_LEVELS) seriesLevel.flatMap { subfolders(it) } else emptyList()
        }

        // 2. Direct root videos, after the films as before
        if (rootFolderId != null) {
            listAll(listOf(rootFolderId))
            for (item in childrenOf(rootFolderId)) {
                if (isVideo(item.mimeType, item.name)) {
                    remember(item)
                    movies.add(movie(item, parentFolderId = rootFolderId))
                }
            }
        }

        // 3. Series: SeriesRoot -> SeriesFolder -> SeasonFolder -> EpisodeVideo, from the listings above
        if (seriesFolderId != null) {
            for (seriesFolder in childrenOf(seriesFolderId)) {
                if (!isFolder(seriesFolder.mimeType)) continue
                folderScope.add(seriesFolder.id)

                val parsedSeries = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(seriesFolder.name)
                seriesList.add(
                    SeriesEntity(
                        id = seriesFolder.id,
                        title = parsedSeries.cleanTitle,
                        year = parsedSeries.year
                    )
                )

                // Series subfolders (Seasons or direct episodes)
                for (seasonChild in childrenOf(seriesFolder.id)) {
                    if (isFolder(seasonChild.mimeType)) {
                        folderScope.add(seasonChild.id)
                        for (epItem in childrenOf(seasonChild.id)) {
                            if (isVideo(epItem.mimeType, epItem.name)) {
                                remember(epItem)
                                val key = SeriesLayout.episodeKey(seasonChild.name, epItem.name)
                                episodes.add(
                                    MediaItemEntity(
                                        id = epItem.id,
                                        type = MediaType.EPISODE,
                                        title = key.episodeTitle ?: cleanTitle(epItem.name),
                                        parentFolderId = seasonChild.id,
                                        seriesId = seriesFolder.id,
                                        seasonNumber = key.season,
                                        episodeNumber = key.episode
                                    )
                                )
                            }
                        }
                    } else if (isVideo(seasonChild.mimeType, seasonChild.name)) {
                        remember(seasonChild)
                        val key = SeriesLayout.episodeKey(null, seasonChild.name)
                        episodes.add(
                            MediaItemEntity(
                                id = seasonChild.id,
                                type = MediaType.EPISODE,
                                title = key.episodeTitle ?: cleanTitle(seasonChild.name),
                                parentFolderId = seriesFolder.id,
                                seriesId = seriesFolder.id,
                                seasonNumber = key.season,
                                episodeNumber = key.episode
                            )
                        )
                    }
                }
            }
        }

        return CrawledLibrary(movies, seriesList, episodes, folderScope, fileInfo, requestCount.get() - requestsAtStart)
    }

    /**
     * Task R.1 (the naming helper's folder check): one series folder's videos, directly in it or in its season
     * folders, as the crawl sees them. Two listing requests at most (the folder, then all its subfolders at once).
     */
    suspend fun listSeriesFolder(seriesFolderId: String, seriesFolderName: String, accessToken: String): SeriesFolderListing {
        val top = fetchChildrenOf(listOf(seriesFolderId), accessToken)[seriesFolderId].orEmpty()
        val seasonFolders = top.filter { isFolder(it.mimeType) }
        val inSeasons = if (seasonFolders.isEmpty()) emptyMap() else fetchChildrenOf(seasonFolders.map { it.id }, accessToken)
        val files = top.filter { isVideo(it.mimeType, it.name) }
            .map { SeriesFolderListing.ListedFile(it.id, it.name, seriesFolderId, seasonFolderName = null) } +
            seasonFolders.flatMap { folder ->
                inSeasons[folder.id].orEmpty().filter { isVideo(it.mimeType, it.name) }
                    .map { SeriesFolderListing.ListedFile(it.id, it.name, folder.id, folder.name) }
            }
        return SeriesFolderListing(seriesFolderId, seriesFolderName, files, seasonFolders.map { it.name })
    }

    /**
     * Tasks R.2/R.3: a series folder's name on Drive, which the catalog doesn't keep (its title is TMDB's after a
     * sync, without "(Anime)" or the year), for [listSeriesFolder]. One `files.get` request, name only.
     */
    suspend fun folderName(folderId: String, accessToken: String): String {
        currentCoroutineContext().ensureActive()
        val url = "$baseUrl/files/$folderId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "name")
            .addQueryParameter("supportsAllDrives", "true")
            .build()
        val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").get().build()
        requestCount.incrementAndGet()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to read folder $folderId: code ${response.code}")
            return JSONObject(response.body?.string() ?: "").getString("name")
        }
    }

    /** [folderName], then [listSeriesFolder]: what the naming check reads (three requests at most). */
    suspend fun readSeriesFolder(seriesFolderId: String, accessToken: String): SeriesFolderListing =
        listSeriesFolder(seriesFolderId, folderName(seriesFolderId, accessToken), accessToken)

    private fun movie(item: RawDriveFile, parentFolderId: String): MediaItemEntity {
        val parsedMovie = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(item.name)
        return MediaItemEntity(
            id = item.id,
            type = MediaType.MOVIE,
            title = parsedMovie.cleanTitle,
            year = parsedMovie.year,
            parentFolderId = parentFolderId
        )
    }

    /** Children of each of [folderIds], [maxParentsPerQuery] folders per request. */
    private suspend fun fetchChildrenOf(folderIds: List<String>, accessToken: String): Map<String, List<RawDriveFile>> {
        val result = LinkedHashMap<String, List<RawDriveFile>>()
        for (chunk in folderIds.chunked(maxParentsPerQuery.coerceAtLeast(1))) result += fetchChunk(chunk, accessToken)
        return result
    }

    private suspend fun fetchChunk(folderIds: List<String>, accessToken: String): Map<String, List<RawDriveFile>> =
        try {
            fetchFolderChildren(folderIds, accessToken)
        } catch (e: QueryRejected) {
            // Too many folders for one query: halve it until Drive accepts it (one folder per request at worst).
            if (folderIds.size == 1) throw IOException("Failed to query folder ${folderIds[0]}: code ${e.code}")
            val half = folderIds.size / 2
            fetchChunk(folderIds.subList(0, half), accessToken) + fetchChunk(folderIds.subList(half, folderIds.size), accessToken)
        }

    private class QueryRejected(val code: Int) : Exception()

    private suspend fun fetchFolderChildren(folderIds: List<String>, accessToken: String): Map<String, List<RawDriveFile>> {
        val results = LinkedHashMap<String, MutableList<RawDriveFile>>()
        folderIds.forEach { results[it] = mutableListOf() }
        val parentsQuery = folderIds.joinToString(" or ") { "'$it' in parents" }
        var pageToken: String? = null

        do {
            // A cancelled crawl (library switch, sign-out, refresh) stops before its next Drive request.
            currentCoroutineContext().ensureActive()
            val urlBuilder = "$baseUrl/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "($parentsQuery) and trashed = false")
                .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, parents, size, videoMediaMetadata(width, height, durationMillis))")
                .addQueryParameter("supportsAllDrives", "true")
                .addQueryParameter("includeItemsFromAllDrives", "true")
                .addQueryParameter("pageSize", "1000")

            if (pageToken != null) {
                urlBuilder.addQueryParameter("pageToken", pageToken)
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            requestCount.incrementAndGet()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == 400 && pageToken == null) throw QueryRejected(response.code)
                if (!response.isSuccessful) {
                    throw IOException("Failed to query folders ${folderIds.joinToString()}: code ${response.code}")
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                pageToken = json.optString("nextPageToken", "").ifEmpty { null }
                val files = json.optJSONArray("files") ?: org.json.JSONArray()
                for (i in 0 until files.length()) {
                    val obj = files.getJSONObject(i)
                    val name = obj.getString("name")
                    // int64 values (size, durationMillis) arrive as JSON strings.
                    val video = obj.optJSONObject("videoMediaMetadata")
                    val file = RawDriveFile(
                        id = obj.getString("id"),
                        name = name,
                        mimeType = obj.optString("mimeType", ""),
                        info = DriveFileInfo(
                            sizeBytes = obj.optString("size", "").toLongOrNull(),
                            widthPx = video?.optInt("width", 0)?.takeIf { it > 0 },
                            heightPx = video?.optInt("height", 0)?.takeIf { it > 0 },
                            durationMs = video?.optString("durationMillis", "")?.toLongOrNull()?.takeIf { it > 0 },
                            container = MediaInfoFormat.containerOf(name)
                        )
                    )
                    // A file in two listed folders (old multi-parent files) is listed under each, as before.
                    val parents = obj.optJSONArray("parents")
                    val listedUnder = when {
                        parents != null -> (0 until parents.length()).map { parents.getString(it) }.filter { it in results }
                        folderIds.size == 1 -> folderIds // one folder asked: it is the parent even without the field
                        else -> emptyList()
                    }
                    listedUnder.forEach { results.getValue(it).add(file) }
                }
            }
        } while (pageToken != null)

        return results
    }

    private fun cleanTitle(name: String): String {
        return name.substringBeforeLast('.')
            .replace('.', ' ')
            .replace('_', ' ')
            .trim()
    }

    data class RawDriveFile(val id: String, val name: String, val mimeType: String, val info: DriveFileInfo = DriveFileInfo())
}

/** The Series folder, its show folders and their season folders; deeper folders are not part of a series. */
private const val SERIES_LEVELS = 3
