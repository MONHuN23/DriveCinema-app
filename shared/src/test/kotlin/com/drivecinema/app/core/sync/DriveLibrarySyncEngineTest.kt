package com.drivecinema.app.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLibrarySyncEngineTest {
    @Test
    fun testRemovedChangeMarksEntityDeleted() {
        val change = DriveChange(fileId = "file123", removed = true, trashed = false)
        val action = DriveLibrarySyncEngine.determineAction(change, parentScope = setOf("folder_films"))
        assertEquals(SyncAction.MarkDeleted("file123"), action)
    }

    @Test
    fun testTrashedChangeMarksEntityDeleted() {
        val change = DriveChange(fileId = "file123", removed = false, trashed = true)
        val action = DriveLibrarySyncEngine.determineAction(change, parentScope = setOf("folder_films"))
        assertEquals(SyncAction.MarkDeleted("file123"), action)
    }

    @Test
    fun testFileMovedOutOfLibraryMarksEntityDeleted() {
        val change = DriveChange(
            fileId = "file123",
            removed = false,
            trashed = false,
            parents = listOf("outside_folder")
        )
        val action = DriveLibrarySyncEngine.determineAction(change, parentScope = setOf("folder_films"))
        assertEquals(SyncAction.MarkDeleted("file123"), action)
    }

    @Test
    fun testVideoFileWithinLibraryUpserts() {
        val change = DriveChange(
            fileId = "file123",
            name = "Inception (2010).mkv",
            mimeType = "video/x-matroska",
            removed = false,
            trashed = false,
            parents = listOf("folder_films")
        )
        val action = DriveLibrarySyncEngine.determineAction(change, parentScope = setOf("folder_films"))
        assertTrue("Valid video in library must be upserted", action is SyncAction.Upsert)
        assertEquals("file123", (action as SyncAction.Upsert).change.fileId)
    }

    @Test
    fun testNonMediaFileWithinLibraryIsIgnored() {
        val change = DriveChange(
            fileId = "file123",
            name = "notes.txt",
            mimeType = "text/plain",
            removed = false,
            trashed = false,
            parents = listOf("folder_films")
        )
        val action = DriveLibrarySyncEngine.determineAction(change, parentScope = setOf("folder_films"))
        assertEquals(SyncAction.Ignore, action)
    }

    @Test
    fun testSubfolderWithinLibraryUpserts() {
        val change = DriveChange(
            fileId = "subfolder_season1",
            name = "Season 1",
            mimeType = "application/vnd.google-apps.folder",
            removed = false,
            trashed = false,
            parents = listOf("series_breaking_bad")
        )
        val action = DriveLibrarySyncEngine.determineAction(change, parentScope = setOf("series_breaking_bad"))
        assertTrue("Subfolder within series must be upserted", action is SyncAction.Upsert)
    }

    @Test
    fun testSyncIncrementalChangesHandles410ByFallingBackToInitialCrawl() = kotlinx.coroutines.runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.start()

        // 1. Initial 410 on changes.list
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(410).setBody("Gone"))

        // 2. Fallback crawl responses:
        // Films folder query
        server.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"m1","name":"Matrix.mp4","mimeType":"video/mp4"}]}"""
            )
        )
        // Series root folder query
        server.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("""{"files":[]}""")
        )
        // startPageToken query
        server.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("""{"startPageToken":"new_token_456"}""")
        )

        val fakeMediaDao = object : com.drivecinema.app.core.database.dao.MediaDao {
            val inserted = mutableListOf<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            val deleted = mutableListOf<String>()

            override suspend fun insertMediaItems(items: List<com.drivecinema.app.core.database.entity.MediaItemEntity>) {
                inserted.addAll(items)
            }
            override suspend fun insertMediaItem(item: com.drivecinema.app.core.database.entity.MediaItemEntity) {
                inserted.add(item)
            }
            override suspend fun insertSeries(series: List<com.drivecinema.app.core.database.entity.SeriesEntity>) {}
            override suspend fun insertSingleSeries(series: com.drivecinema.app.core.database.entity.SeriesEntity) {}
            override suspend fun getSeriesById(id: String) = null
            override fun getSeriesByIdFlow(id: String) = kotlinx.coroutines.flow.emptyFlow<com.drivecinema.app.core.database.entity.SeriesEntity?>()
            override suspend fun getMediaItemById(id: String) = null
            override suspend fun getMediaItemsByIds(ids: List<String>) = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override suspend fun getSeriesByIds(ids: List<String>) = emptyList<com.drivecinema.app.core.database.entity.SeriesEntity>()
            override fun getAllMoviesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override suspend fun getAllMovies() = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override suspend fun getUnenrichedMovies() = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override fun getAllSeriesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.SeriesEntity>>()
            override suspend fun getAllSeries() = emptyList<com.drivecinema.app.core.database.entity.SeriesEntity>()
            override suspend fun getUnenrichedSeries() = emptyList<com.drivecinema.app.core.database.entity.SeriesEntity>()
            override fun searchMoviesFlow(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override fun searchSeriesFlow(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.SeriesEntity>>()
            override fun getEpisodesForSeriesFlow(seriesId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override suspend fun getEpisodesForSeries(seriesId: String) = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override suspend fun getItemsInFolder(folderId: String) = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override suspend fun markDeleted(id: String) { deleted.add(id) }
            override suspend fun markFolderItemsDeleted(folderId: String) {}
            override suspend fun markSourcesUnavailable(kind: com.drivecinema.app.core.database.entity.SourceKind) {}
            override suspend fun insertSourcesIfAbsent(sources: List<com.drivecinema.app.core.database.entity.MediaSourceEntity>) {}
            override suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long) {}
            override suspend fun refreshVisibilityFromSources() {}
            override fun getContinueWatchingFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.dao.ContinueWatchingItem>>()
        }

        var savedToken: String? = "expired_token_123"
        val fakeSyncMetadataDao = object : com.drivecinema.app.core.database.dao.SyncMetadataDao {
            override suspend fun setMetadata(metadata: com.drivecinema.app.core.database.entity.SyncMetadataEntity) {
                savedToken = metadata.syncToken
            }
            override suspend fun getMetadata(key: String): com.drivecinema.app.core.database.entity.SyncMetadataEntity? {
                return com.drivecinema.app.core.database.entity.SyncMetadataEntity(key, savedToken, 0L)
            }
            override suspend fun clearMetadata(key: String) {
                savedToken = null
            }
        }

        val baseUrl = server.url("/drive/v3").toString().removeSuffix("/")
        val crawler = FolderQueueCrawler(baseUrl = baseUrl)
        val engine = DriveLibrarySyncEngine(
            crawler = crawler,
            mediaDao = fakeMediaDao,
            syncMetadataDao = fakeSyncMetadataDao,
            baseUrl = baseUrl
        )

        val result = engine.syncIncrementalChanges(
            accessToken = "token_xyz",
            filmsFolderId = "f_films",
            seriesFolderId = "f_series",
            parentScope = setOf("f_films", "f_series")
        )

        assertTrue("Fallback must succeed", result.isSuccess)
        assertEquals("new_token_456", savedToken)
        assertEquals(1, fakeMediaDao.inserted.size)
        assertEquals("m1", fakeMediaDao.inserted[0].id)

        server.shutdown()
    }

    @Test
    fun testInitialCrawlEnrichesMovieAndSeriesWithTmdbMetadata() = kotlinx.coroutines.runBlocking {
        val driveServer = okhttp3.mockwebserver.MockWebServer()
        driveServer.start()

        // 1. Films folder listing: 1 movie
        driveServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"m1","name":"Dune Part Two (2024).mkv","mimeType":"video/x-matroska"}]}"""
            )
        )
        // 2. Series folder listing: 1 series folder
        driveServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"s1","name":"Breaking Bad","mimeType":"application/vnd.google-apps.folder"}]}"""
            )
        )
        // 3. Series folder children: Season 1 folder
        driveServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"season1","name":"Season 01","mimeType":"application/vnd.google-apps.folder"}]}"""
            )
        )
        // 4. Season 1 children: Episode 1
        driveServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """{"files":[{"id":"ep1","name":"Breaking.Bad.S01E01.mkv","mimeType":"video/x-matroska"}]}"""
            )
        )
        // 5. startPageToken query
        driveServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody("""{"startPageToken":"token_123"}""")
        )

        val tmdbServer = okhttp3.mockwebserver.MockWebServer()
        tmdbServer.start()

        // TMDB search movie
        tmdbServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """
                {
                    "results": [
                        {
                            "id": 693134,
                            "title": "Dune: Part Two",
                            "overview": "Paul Atreides unites with Chani...",
                            "poster_path": "/dune_poster.jpg",
                            "backdrop_path": "/dune_backdrop.jpg",
                            "release_date": "2024-02-27",
                            "genre_ids": [878, 12],
                            "vote_average": 8.2,
                            "vote_count": 5200
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        // TMDB search tv series
        tmdbServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """
                {
                    "results": [
                        {
                            "id": 1396,
                            "name": "Breaking Bad",
                            "overview": "Walter White produces crystal meth...",
                            "poster_path": "/bb_poster.jpg",
                            "backdrop_path": "/bb_backdrop.jpg",
                            "first_air_date": "2008-01-20",
                            "genre_ids": [18, 80],
                            "vote_average": 8.9,
                            "vote_count": 14000
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        // TMDB get season 1 episodes
        tmdbServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """
                {
                    "season_number": 1,
                    "episodes": [
                        {
                            "id": 62085,
                            "season_number": 1,
                            "episode_number": 1,
                            "name": "Pilot",
                            "overview": "Walter White is diagnosed with stage III cancer...",
                            "still_path": "/pilot_still.jpg",
                            "vote_average": 8.3,
                            "runtime": 58
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        val fakeMediaDao = object : com.drivecinema.app.core.database.dao.MediaDao {
            val items = mutableMapOf<String, com.drivecinema.app.core.database.entity.MediaItemEntity>()
            val seriesMap = mutableMapOf<String, com.drivecinema.app.core.database.entity.SeriesEntity>()

            override suspend fun insertMediaItems(itemList: List<com.drivecinema.app.core.database.entity.MediaItemEntity>) {
                itemList.forEach { items[it.id] = it }
            }
            override suspend fun insertMediaItem(item: com.drivecinema.app.core.database.entity.MediaItemEntity) {
                items[item.id] = item
            }
            override suspend fun insertSeries(seriesList: List<com.drivecinema.app.core.database.entity.SeriesEntity>) {
                seriesList.forEach { seriesMap[it.id] = it }
            }
            override suspend fun insertSingleSeries(series: com.drivecinema.app.core.database.entity.SeriesEntity) {
                seriesMap[series.id] = series
            }
            override suspend fun getSeriesById(id: String) = seriesMap[id]
            override fun getSeriesByIdFlow(id: String) = kotlinx.coroutines.flow.flowOf(seriesMap[id])
            override suspend fun getMediaItemById(id: String) = items[id]
            override suspend fun getMediaItemsByIds(ids: List<String>) = ids.mapNotNull { items[it] }
            override suspend fun getSeriesByIds(ids: List<String>) = ids.mapNotNull { seriesMap[it] }
            override fun getAllMoviesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override suspend fun getAllMovies() = items.values.filter { it.type == com.drivecinema.app.core.database.entity.MediaType.MOVIE }
            override suspend fun getUnenrichedMovies() = items.values.filter { it.type == com.drivecinema.app.core.database.entity.MediaType.MOVIE && it.tmdbId == null && !it.isDeleted }
            override fun getAllSeriesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.SeriesEntity>>()
            override suspend fun getAllSeries() = seriesMap.values.toList()
            override suspend fun getUnenrichedSeries() = seriesMap.values.filter { it.tmdbId == null }
            override fun searchMoviesFlow(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override fun searchSeriesFlow(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.SeriesEntity>>()
            override fun getEpisodesForSeriesFlow(seriesId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override suspend fun getEpisodesForSeries(seriesId: String) = items.values.filter { it.seriesId == seriesId && !it.isDeleted }
            override suspend fun getItemsInFolder(folderId: String) = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override suspend fun markDeleted(id: String) {}
            override suspend fun markFolderItemsDeleted(folderId: String) {}
            override suspend fun markSourcesUnavailable(kind: com.drivecinema.app.core.database.entity.SourceKind) {}
            override suspend fun insertSourcesIfAbsent(sources: List<com.drivecinema.app.core.database.entity.MediaSourceEntity>) {}
            override suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long) {}
            override suspend fun refreshVisibilityFromSources() {}
            override fun getContinueWatchingFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.dao.ContinueWatchingItem>>()
        }

        val fakeSyncMetadataDao = object : com.drivecinema.app.core.database.dao.SyncMetadataDao {
            override suspend fun setMetadata(metadata: com.drivecinema.app.core.database.entity.SyncMetadataEntity) {}
            override suspend fun getMetadata(key: String) = null
            override suspend fun clearMetadata(key: String) {}
        }

        val driveBaseUrl = driveServer.url("/drive/v3").toString().removeSuffix("/")
        val tmdbBaseUrl = tmdbServer.url("/3").toString().removeSuffix("/")

        val tmdbClient = com.drivecinema.app.core.metadata.TmdbClient(
            apiKey = "test_key",
            baseUrl = tmdbBaseUrl
        )

        val crawler = FolderQueueCrawler(baseUrl = driveBaseUrl)
        val engine = DriveLibrarySyncEngine(
            crawler = crawler,
            mediaDao = fakeMediaDao,
            syncMetadataDao = fakeSyncMetadataDao,
            tmdbClient = tmdbClient,
            baseUrl = driveBaseUrl
        )

        val result = engine.executeInitialCrawl("f_films", "f_series", "access_token_123")
        assertTrue("Initial crawl must succeed", result.isSuccess)

        // Verify movie was enriched
        val movie = fakeMediaDao.items["m1"]
        org.junit.Assert.assertNotNull(movie)
        assertEquals(693134, movie?.tmdbId)
        assertEquals("/dune_poster.jpg", movie?.posterPath)
        assertEquals("/dune_backdrop.jpg", movie?.backdropPath)
        assertEquals("Science Fiction, Adventure", movie?.genres)
        assertEquals(8.2, movie?.voteAverage ?: 0.0, 0.01)

        // Verify series was enriched
        val series = fakeMediaDao.seriesMap["s1"]
        org.junit.Assert.assertNotNull(series)
        assertEquals(1396, series?.tmdbId)
        assertEquals("/bb_poster.jpg", series?.posterPath)
        assertEquals("/bb_backdrop.jpg", series?.backdropPath)
        assertEquals("Drama, Crime", series?.genres)
        assertEquals(8.9, series?.voteAverage ?: 0.0, 0.01)

        // Verify episode was enriched
        val episode = fakeMediaDao.items["ep1"]
        org.junit.Assert.assertNotNull(episode)
        assertEquals("Pilot", episode?.title)
        assertEquals("/pilot_still.jpg", episode?.backdropPath)
        assertEquals(8.3, episode?.voteAverage ?: 0.0, 0.01)
        assertEquals(58 * 60, episode?.runtimeSeconds)

        driveServer.shutdown()
        tmdbServer.shutdown()
    }

    @Test
    fun testEnrichExistingCatalogEnrichesUnenrichedMoviesAndSeries() = kotlinx.coroutines.runBlocking {
        val tmdbServer = okhttp3.mockwebserver.MockWebServer()
        tmdbServer.start()

        // 1. TMDB search for movie "Dune Part Two"
        tmdbServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """
                {
                    "results": [
                        {
                            "id": 693134,
                            "title": "Dune: Part Two",
                            "overview": "Paul Atreides unites with Chani...",
                            "poster_path": "/dune_poster.jpg",
                            "backdrop_path": "/dune_backdrop.jpg",
                            "release_date": "2024-02-27",
                            "genre_ids": [878, 12],
                            "vote_average": 8.2,
                            "vote_count": 5200
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        // 2. TMDB search for series "Breaking Bad"
        tmdbServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """
                {
                    "results": [
                        {
                            "id": 1396,
                            "name": "Breaking Bad",
                            "overview": "Walter White produces crystal meth...",
                            "poster_path": "/bb_poster.jpg",
                            "backdrop_path": "/bb_backdrop.jpg",
                            "first_air_date": "2008-01-20",
                            "genre_ids": [18, 80],
                            "vote_average": 8.9,
                            "vote_count": 14000
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        // 3. TMDB season 1 episodes
        tmdbServer.enqueue(
            okhttp3.mockwebserver.MockResponse().setResponseCode(200).setBody(
                """
                {
                    "season_number": 1,
                    "episodes": [
                        {
                            "id": 62085,
                            "season_number": 1,
                            "episode_number": 1,
                            "name": "Pilot",
                            "overview": "Walter White is diagnosed with stage III cancer...",
                            "still_path": "/pilot_still.jpg",
                            "vote_average": 8.3,
                            "runtime": 58
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        val unenrichedMovie = com.drivecinema.app.core.database.entity.MediaItemEntity(
            id = "drive_file_dune",
            type = com.drivecinema.app.core.database.entity.MediaType.MOVIE,
            title = "Dune Part Two",
            year = 2024,
            parentFolderId = "films_folder",
            tmdbId = null,
            posterPath = null,
            backdropPath = null
        )

        val unenrichedSeries = com.drivecinema.app.core.database.entity.SeriesEntity(
            id = "drive_folder_bb",
            title = "Breaking Bad",
            year = 2008,
            tmdbId = null,
            posterPath = null,
            backdropPath = null
        )

        val unenrichedEpisode = com.drivecinema.app.core.database.entity.MediaItemEntity(
            id = "drive_file_bb_ep1",
            type = com.drivecinema.app.core.database.entity.MediaType.EPISODE,
            title = "Breaking Bad S01E01",
            parentFolderId = "season_1_folder",
            seriesId = "drive_folder_bb",
            seasonNumber = 1,
            episodeNumber = 1,
            backdropPath = null
        )

        val itemsMap = mutableMapOf(
            unenrichedMovie.id to unenrichedMovie,
            unenrichedEpisode.id to unenrichedEpisode
        )
        val seriesMap = mutableMapOf(
            unenrichedSeries.id to unenrichedSeries
        )

        val fakeMediaDao = object : com.drivecinema.app.core.database.dao.MediaDao {
            override suspend fun insertMediaItems(items: List<com.drivecinema.app.core.database.entity.MediaItemEntity>) {
                items.forEach { itemsMap[it.id] = it }
            }
            override suspend fun insertMediaItem(item: com.drivecinema.app.core.database.entity.MediaItemEntity) {
                itemsMap[item.id] = item
            }
            override suspend fun insertSeries(series: List<com.drivecinema.app.core.database.entity.SeriesEntity>) {
                series.forEach { seriesMap[it.id] = it }
            }
            override suspend fun insertSingleSeries(series: com.drivecinema.app.core.database.entity.SeriesEntity) {
                seriesMap[series.id] = series
            }
            override suspend fun getSeriesById(id: String) = seriesMap[id]
            override fun getSeriesByIdFlow(id: String) = kotlinx.coroutines.flow.flowOf(seriesMap[id])
            override suspend fun getMediaItemById(id: String) = itemsMap[id]
            override suspend fun getMediaItemsByIds(ids: List<String>) = ids.mapNotNull { itemsMap[it] }
            override suspend fun getSeriesByIds(ids: List<String>) = ids.mapNotNull { seriesMap[it] }
            override fun getAllMoviesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override suspend fun getAllMovies() = itemsMap.values.filter { it.type == com.drivecinema.app.core.database.entity.MediaType.MOVIE }
            override suspend fun getUnenrichedMovies() = itemsMap.values.filter { it.type == com.drivecinema.app.core.database.entity.MediaType.MOVIE && it.tmdbId == null && !it.isDeleted }
            override fun getAllSeriesFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.SeriesEntity>>()
            override suspend fun getAllSeries() = seriesMap.values.toList()
            override suspend fun getUnenrichedSeries() = seriesMap.values.filter { it.tmdbId == null }
            override fun searchMoviesFlow(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override fun searchSeriesFlow(query: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.SeriesEntity>>()
            override fun getEpisodesForSeriesFlow(seriesId: String) = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.entity.MediaItemEntity>>()
            override suspend fun getEpisodesForSeries(seriesId: String) = itemsMap.values.filter { it.seriesId == seriesId && !it.isDeleted }
            override suspend fun getItemsInFolder(folderId: String) = emptyList<com.drivecinema.app.core.database.entity.MediaItemEntity>()
            override suspend fun markDeleted(id: String) {}
            override suspend fun markFolderItemsDeleted(folderId: String) {}
            override suspend fun markSourcesUnavailable(kind: com.drivecinema.app.core.database.entity.SourceKind) {}
            override suspend fun insertSourcesIfAbsent(sources: List<com.drivecinema.app.core.database.entity.MediaSourceEntity>) {}
            override suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long) {}
            override suspend fun refreshVisibilityFromSources() {}
            override fun getContinueWatchingFlow() = kotlinx.coroutines.flow.emptyFlow<List<com.drivecinema.app.core.database.dao.ContinueWatchingItem>>()
        }

        val fakeSyncMetadataDao = object : com.drivecinema.app.core.database.dao.SyncMetadataDao {
            override suspend fun setMetadata(metadata: com.drivecinema.app.core.database.entity.SyncMetadataEntity) {}
            override suspend fun getMetadata(key: String) = null
            override suspend fun clearMetadata(key: String) {}
        }

        val tmdbBaseUrl = tmdbServer.url("/3").toString().removeSuffix("/")
        val tmdbClient = com.drivecinema.app.core.metadata.TmdbClient(
            apiKey = "test_key",
            baseUrl = tmdbBaseUrl
        )

        val engine = DriveLibrarySyncEngine(
            crawler = FolderQueueCrawler(),
            mediaDao = fakeMediaDao,
            syncMetadataDao = fakeSyncMetadataDao,
            tmdbClient = tmdbClient
        )

        val enrichResult = engine.enrichExistingCatalog()
        assertTrue(enrichResult.isSuccess)
        assertEquals(2, enrichResult.getOrNull())

        // Verify movie was updated and original Drive ID was preserved
        val updatedMovie = itemsMap["drive_file_dune"]!!
        assertEquals("drive_file_dune", updatedMovie.id)
        assertEquals(693134, updatedMovie.tmdbId)
        assertEquals("/dune_poster.jpg", updatedMovie.posterPath)
        assertEquals("/dune_backdrop.jpg", updatedMovie.backdropPath)
        assertEquals("Science Fiction, Adventure", updatedMovie.genres)
        assertEquals(8.2, updatedMovie.voteAverage ?: 0.0, 0.01)

        // Verify series was updated
        val updatedSeries = seriesMap["drive_folder_bb"]!!
        assertEquals("drive_folder_bb", updatedSeries.id)
        assertEquals(1396, updatedSeries.tmdbId)
        assertEquals("/bb_poster.jpg", updatedSeries.posterPath)
        assertEquals("/bb_backdrop.jpg", updatedSeries.backdropPath)
        assertEquals("Drama, Crime", updatedSeries.genres)

        // Verify episode was updated
        val updatedEp = itemsMap["drive_file_bb_ep1"]!!
        assertEquals("drive_file_bb_ep1", updatedEp.id)
        assertEquals("Pilot", updatedEp.title)
        assertEquals("/pilot_still.jpg", updatedEp.backdropPath)

        tmdbServer.shutdown()
    }
}
