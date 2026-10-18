package com.drivecinema.app.core.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/** Report F-06: a token refresh slower than 5 s used to skip the startup sync silently. */
class StartupTokenTest {

    @Test
    fun slowRefreshStillGivesAToken() = runTest {
        // 6 s: longer than the old 5 s latch, which returned "" and skipped the crawl.
        val token = StartupToken.obtain(cachedToken = "", refresh = { delay(6_000); Result.success("fresh") })
        assertEquals("fresh", token)
    }

    @Test
    fun cachedTokenIsUsedWithoutRefreshing() = runTest {
        var refreshed = false
        val token = StartupToken.obtain(cachedToken = "cached", refresh = { refreshed = true; Result.success("fresh") })
        assertEquals("cached", token)
        assertEquals(false, refreshed)
    }

    @Test
    fun failedRefreshGivesNoToken() = runTest {
        assertNull(StartupToken.obtain(cachedToken = "", refresh = { Result.failure(IOException("offline")) }))
    }

    @Test
    fun emptyTokenCountsAsNoToken() = runTest {
        assertNull(StartupToken.obtain(cachedToken = "", refresh = { Result.success("") }))
    }

    @Test
    fun refreshThatNeverAnswersTimesOut() = runTest {
        assertNull(StartupToken.obtain(cachedToken = "", refresh = { delay(Long.MAX_VALUE); Result.success("never") }))
    }
}
