package com.drivecinema.app.core.sync

import com.drivecinema.app.core.network.FolderValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LibraryRefresherTest {

    private val calls = mutableListOf<String>()

    private fun refresher(
        validation: Result<FolderValidationResult> = Result.success(FolderValidationResult.Valid("films", "series")),
        crawlResult: Result<Unit> = Result.success(Unit),
    ) = LibraryRefresher(
        validateFolder = { root, _ -> calls += "validate:$root"; validation },
        crawl = { films, series, _, root -> calls += "crawl:$films,$series,$root"; crawlResult },
    )

    @Test
    fun `refresh validates then crawls the saved folder once`() = runTest {
        val result = refresher().refresh("root", "token")
        assertEquals(LibraryRefreshResult.Refreshed, result)
        // No separate enrichment call: executeInitialCrawl already enriches (report F-03).
        assertEquals(listOf("validate:root", "crawl:films,series,root"), calls)
    }

    @Test
    fun `no token fails without touching the catalog`() = runTest {
        val result = refresher().refresh("root", null)
        assertEquals(LibraryRefreshResult.Failed(LibraryRefresher.OFFLINE_MESSAGE), result)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `offline during validation keeps the catalog`() = runTest {
        val result = refresher(validation = Result.failure(IOException("offline"))).refresh("root", "token")
        assertEquals(LibraryRefreshResult.Failed(LibraryRefresher.OFFLINE_MESSAGE), result)
        assertEquals(listOf("validate:root"), calls)
    }

    @Test
    fun `invalid folder reports the reason`() = runTest {
        val result = refresher(validation = Result.success(FolderValidationResult.Invalid("no videos"))).refresh("root", "token")
        assertEquals(LibraryRefreshResult.Failed("Library folder can't be used: no videos"), result)
    }

    @Test
    fun `crawl failure is reported`() = runTest {
        val result = refresher(crawlResult = Result.failure(IOException("drop"))).refresh("root", "token")
        assertTrue(result is LibraryRefreshResult.Failed)
        assertEquals(listOf("validate:root", "crawl:films,series,root"), calls)
    }
}
