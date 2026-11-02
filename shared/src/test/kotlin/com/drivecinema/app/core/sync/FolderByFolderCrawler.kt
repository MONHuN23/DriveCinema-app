package com.drivecinema.app.core.sync

import com.drivecinema.app.core.metadata.MediaInfoFormat
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

/**
 * Task N.1 reference: the crawler as it was before N.1 (one Drive request per folder, `FolderQueueCrawler` at
 * `claude/n2-startup-order`), kept unchanged so tests can check that the batched crawl gives the same library.
 */
class FolderByFolderCrawler(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://www.googleapis.com/drive/v3"
) {

    // Every listing request; a crawl reports how much it grew (Task M.0).
    private val requestCount = AtomicInteger()


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
        fun remember(file: FolderQueueCrawler.RawDriveFile) { fileInfo[file.id] = file.info }

        filmsFolderId?.let { folderScope.add(it) }
        seriesFolderId?.let { folderScope.add(it) }
        rootFolderId?.let { folderScope.add(it) }

        // 1. Crawl Films (iterative BFS queue) if present
        if (filmsFolderId != null) {
            val filmQueue = ArrayDeque<String>()
            filmQueue.add(filmsFolderId)

            while (filmQueue.isNotEmpty()) {
                val currentFolder = filmQueue.removeFirst()
                val items = fetchFolderChildren(currentFolder, accessToken)
                for (item in items) {
                    if (FolderQueueCrawler.isFolder(item.mimeType)) {
                        folderScope.add(item.id)
                        filmQueue.add(item.id)
                    } else if (FolderQueueCrawler.isVideo(item.mimeType, item.name)) {
                        remember(item)
                        val parsedMovie = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(item.name)
                        movies.add(
                            MediaItemEntity(
                                id = item.id,
                                type = MediaType.MOVIE,
                                title = parsedMovie.cleanTitle,
                                year = parsedMovie.year,
                                parentFolderId = currentFolder
                            )
                        )
                    }
                }
            }
        }

        // 2. Crawl direct root videos if rootFolderId is provided
        if (rootFolderId != null) {
            val rootChildren = fetchFolderChildren(rootFolderId, accessToken)
            for (item in rootChildren) {
                if (FolderQueueCrawler.isVideo(item.mimeType, item.name)) {
                    remember(item)
                    val parsedMovie = com.drivecinema.app.core.metadata.MediaFilenameParser.parseMovie(item.name)
                    movies.add(
                        MediaItemEntity(
                            id = item.id,
                            type = MediaType.MOVIE,
                            title = parsedMovie.cleanTitle,
                            year = parsedMovie.year,
                            parentFolderId = rootFolderId
                        )
                    )
                }
            }
        }

        // 3. Crawl Series: SeriesRoot -> SeriesFolder -> SeasonFolder -> EpisodeVideo
        if (seriesFolderId != null) {
            val seriesRootChildren = fetchFolderChildren(seriesFolderId, accessToken)
            for (seriesFolder in seriesRootChildren) {
            if (!FolderQueueCrawler.isFolder(seriesFolder.mimeType)) continue
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
            val seasonChildren = fetchFolderChildren(seriesFolder.id, accessToken)
            for (seasonChild in seasonChildren) {
                if (FolderQueueCrawler.isFolder(seasonChild.mimeType)) {
                    folderScope.add(seasonChild.id)
                    val seasonNum = parseSeasonNumber(seasonChild.name)
                    val epItems = fetchFolderChildren(seasonChild.id, accessToken)
                    for (epItem in epItems) {
                        if (FolderQueueCrawler.isVideo(epItem.mimeType, epItem.name)) {
                            remember(epItem)
                            val parsedEp = com.drivecinema.app.core.metadata.MediaFilenameParser.parseEpisode(epItem.name)
                            val epNum = if (parsedEp.episode > 0) parsedEp.episode else (parseEpisodeNumber(epItem.name) ?: 1)
                            val epTitle = parsedEp.episodeTitle ?: cleanTitle(epItem.name)
                            episodes.add(
                                MediaItemEntity(
                                    id = epItem.id,
                                    type = MediaType.EPISODE,
                                    title = epTitle,
                                    parentFolderId = seasonChild.id,
                                    seriesId = seriesFolder.id,
                                    seasonNumber = seasonNum,
                                    episodeNumber = epNum
                                )
                            )
                        }
                    }
                } else if (FolderQueueCrawler.isVideo(seasonChild.mimeType, seasonChild.name)) {
                    remember(seasonChild)
                    val parsedEp = com.drivecinema.app.core.metadata.MediaFilenameParser.parseEpisode(seasonChild.name)
                    val sNum = if (parsedEp.cleanShowName.isNotEmpty() && parsedEp.season > 0) parsedEp.season else 1
                    val epNum = if (parsedEp.episode > 0) parsedEp.episode else (parseEpisodeNumber(seasonChild.name) ?: 1)
                    val epTitle = parsedEp.episodeTitle ?: cleanTitle(seasonChild.name)
                    episodes.add(
                        MediaItemEntity(
                            id = seasonChild.id,
                            type = MediaType.EPISODE,
                            title = epTitle,
                            parentFolderId = seriesFolder.id,
                            seriesId = seriesFolder.id,
                            seasonNumber = sNum,
                            episodeNumber = epNum
                        )
                    )
                }
            }
        }
    }

    return CrawledLibrary(movies, seriesList, episodes, folderScope, fileInfo, requestCount.get() - requestsAtStart)
}

    private suspend fun fetchFolderChildren(folderId: String, accessToken: String): List<FolderQueueCrawler.RawDriveFile> {
        val results = mutableListOf<FolderQueueCrawler.RawDriveFile>()
        var pageToken: String? = null

        do {
            // A cancelled crawl (library switch, sign-out, refresh) stops before its next Drive request.
            currentCoroutineContext().ensureActive()
            val urlBuilder = "$baseUrl/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'$folderId' in parents and trashed = false")
                .addQueryParameter("fields", "nextPageToken, files(id, name, mimeType, size, videoMediaMetadata(width, height, durationMillis))")
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
                if (!response.isSuccessful) {
                    throw IOException("Failed to query folder $folderId: code ${response.code}")
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
                    results.add(
                        FolderQueueCrawler.RawDriveFile(
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
                    )
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

    private fun parseSeasonNumber(folderName: String): Int =
        com.drivecinema.app.core.metadata.MediaFilenameParser.parseSeasonFolder(folderName)

    private fun parseEpisodeNumber(fileName: String): Int? {
        val match = Regex("""[se]\d+[e|x](\d+)""", RegexOption.IGNORE_CASE).find(fileName)
            ?: Regex("""episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(fileName)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

}
