package com.drivecinema.desktop.ui

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ImageLoaderTest {

    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private val bytes = byteArrayOf(1, 2, 3, 4, 5)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dir = Files.createTempDirectory("drivecinema-img").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    @Test
    fun downloadsOnceThenServesFromMemoryAndDisk() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val url = server.url("/t/p/w342/poster.jpg").toString()

        val loader = ImageLoader(dir)
        assertArrayEquals(bytes, loader.loadBytes(url))
        assertArrayEquals(bytes, loader.loadBytes(url))
        assertTrue(loader.cacheFile(url).exists())

        // A new loader (next app start) reads the disk cache: still one request in total.
        assertArrayEquals(bytes, ImageLoader(dir).loadBytes(url))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun failedDownloadsAreNotCached() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val url = server.url("/missing.jpg").toString()
        val loader = ImageLoader(dir)
        assertNull(loader.loadBytes(url))
        assertTrue(!loader.cacheFile(url).exists())
    }

    @Test
    fun tmdbUrlsUseTheAndroidSizes() {
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", TmdbImages.poster("/abc.jpg"))
        assertEquals("https://image.tmdb.org/t/p/w780/abc.jpg", TmdbImages.backdrop("/abc.jpg"))
        assertNull(TmdbImages.poster(null))
        assertNull(TmdbImages.poster(""))
    }
}
