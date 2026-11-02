package com.drivecinema.app.core.logging

import org.junit.Assert.assertEquals
import org.junit.Test

/** Task M.0: an image request's log line names the TMDB size and path (to spot one image in several sizes), never a title. */
class ImageRequestLogTest {

    @Test
    fun tmdbImageGivesSizeAndPath() {
        assertEquals(
            mapOf("size" to "w1280", "path" to "/abc123.jpg"),
            ImageRequestLog.describe("https://image.tmdb.org/t/p/w1280/abc123.jpg")
        )
        assertEquals(
            mapOf("size" to "w342", "path" to "/abc123.jpg"),
            ImageRequestLog.describe("https://image.tmdb.org/t/p/w342/abc123.jpg?x=1")
        )
    }

    @Test
    fun otherUrlGivesOnlyItsHost() {
        assertEquals(mapOf("host" to "lh3.googleusercontent.com"), ImageRequestLog.describe("https://lh3.googleusercontent.com/a/photo=s96"))
    }

    @Test
    fun localOrMissingDataGivesOnlyItsKind() {
        assertEquals(mapOf("data" to "content"), ImageRequestLog.describe("content://media/external/video/1"))
        assertEquals(mapOf("data" to "none"), ImageRequestLog.describe(null))
    }
}
