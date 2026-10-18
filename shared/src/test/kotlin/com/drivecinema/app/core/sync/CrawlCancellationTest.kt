package com.drivecinema.app.core.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Report F-05: a cancelled crawl (library switch, sign-out, refresh) kept listing every remaining Drive folder
 * until it finished. It must stop before the next folder request.
 */
class CrawlCancellationTest {

    private val server = MockWebServer()
    private val firstRequestSeen = CompletableDeferred<Unit>()
    private val releaseFirstResponse = CountDownLatch(1)

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val q = request.requestUrl?.queryParameter("q").orEmpty()
                return if (q.startsWith("'films'")) {
                    firstRequestSeen.complete(Unit)
                    releaseFirstResponse.await(5, TimeUnit.SECONDS)
                    // Three sub-folders: a crawl that ignores cancellation lists all of them.
                    MockResponse().setBody(
                        """{"files":[
                          {"id":"a","name":"A","mimeType":"application/vnd.google-apps.folder"},
                          {"id":"b","name":"B","mimeType":"application/vnd.google-apps.folder"},
                          {"id":"c","name":"C","mimeType":"application/vnd.google-apps.folder"}]}"""
                    )
                } else {
                    MockResponse().setBody("""{"files":[]}""")
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun cancelledCrawlStopsRequestingFolders() = runBlocking {
        val crawler = FolderQueueCrawler(baseUrl = server.url("/drive/v3").toString().removeSuffix("/"))
        val crawl = async(Dispatchers.IO) { crawler.crawl(filmsFolderId = "films", accessToken = "t") }
        withTimeout(5_000) { firstRequestSeen.await() }
        crawl.cancel()                 // e.g. the user switched library while the first listing was loading
        releaseFirstResponse.countDown()
        crawl.cancelAndJoin()
        assertEquals("only the folder request already in flight may run", 1, server.requestCount)
    }
}
