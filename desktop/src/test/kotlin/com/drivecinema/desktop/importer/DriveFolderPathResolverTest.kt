package com.drivecinema.desktop.importer

import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Task I.1, folder creation: reuse what exists, create only missing show/season folders, never twice. */
class DriveFolderPathResolverTest {

    private lateinit var drive: FakeDrive
    private lateinit var root: String
    private lateinit var series: String

    @Before
    fun setUp() {
        drive = FakeDrive()
        root = drive.folder("Streaming")
        drive.folder("Films", root, id = "films")
        series = drive.folder("Series", root, id = "series")
    }

    @After
    fun tearDown() = drive.close()

    private fun resolver() = DriveFolderPathResolver(drive.client(), root)

    @Test
    fun filmsResolveToTheExistingFilmsFolderWithoutCreatingAnything() {
        assertEquals("films", resolver().resolve(listOf("Films")))
        assertTrue(drive.calls.none { it.startsWith("POST") })
    }

    @Test
    fun missingShowAndSeasonAreCreatedOnceAndReused() {
        val resolver = resolver()
        val first = resolver.resolve(listOf("Series", "Ted (2024)", "Season 01"))
        val second = resolver.resolve(listOf("Series", "Ted (2024)", "Season 01"))
        assertEquals(first, second)

        val show = drive.childrenOf(series).single()
        assertEquals("Ted (2024)", show.name)
        assertEquals(listOf("Season 01"), drive.childrenOf(show.id).map { it.name })
        assertEquals(2, drive.calls.count { it == "POST /drive/v3/files" })
        // A fresh resolver (e.g. after a restart) finds them instead of creating copies.
        assertEquals(first, resolver().resolve(listOf("Series", "Ted (2024)", "Season 01")))
        assertEquals(2, drive.calls.count { it == "POST /drive/v3/files" })
    }

    @Test
    fun existingFoldersWithOtherSpellingsAreReused() {
        val show = drive.folder("ted 2024", series)
        val season = drive.folder("S01", show)
        assertEquals(season, resolver().resolve(listOf("Series", "Ted (2024)", "Season 01")))
        assertTrue(drive.calls.none { it.startsWith("POST") })
    }

    @Test
    fun twoPossibleSeasonFoldersNeedReviewAndNothingIsCreated() {
        val show = drive.folder("Ted (2024)", series)
        drive.folder("Season 1", show)
        drive.folder("S1", show)
        try {
            resolver().resolve(listOf("Series", "Ted (2024)", "Season 01"))
            fail("Expected NeedsReviewException")
        } catch (e: NeedsReviewException) {
            assertTrue(e.message!!.contains("Season 1"))
        }
        assertTrue(drive.calls.none { it.startsWith("POST") })
    }

    @Test
    fun aMissingSeriesFolderIsNotCreated() {
        drive.items.removeIf { it.id == "series" }
        try {
            resolver().resolve(listOf("Series", "Show", "Season 01"))
            fail("Expected NeedsReviewException")
        } catch (e: NeedsReviewException) {
            assertTrue(e.message!!.contains("no \"Series\" folder"))
        }
        assertTrue(drive.calls.none { it.startsWith("POST") })
    }

    @Test
    fun pathsOutsideTheLibraryLayoutAreRejectedBeforeAnyRequest() {
        listOf(listOf("Other"), listOf("Series", "Show"), listOf("Series", "Show", "Extras"), listOf("Films", "Sub")).forEach {
            try {
                resolver().resolve(it)
                fail("Expected IllegalArgumentException for $it")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
        assertTrue(drive.calls.isEmpty())
    }

    @Test
    fun aFailedFolderCreationIsNotRemembered() {
        drive.failures += "POST /drive/v3/files" to MockResponse().setResponseCode(503)
        val resolver = resolver()
        try {
            resolver.resolve(listOf("Series", "Show", "Season 01"))
            fail("Expected DriveApiException")
        } catch (e: DriveApiException) {
            assertTrue(e.retryable)
        }
        resolver.resolve(listOf("Series", "Show", "Season 01"))
        assertEquals(1, drive.childrenOf(series).size)
    }

    @Test
    fun theTokenIsSentWithEveryRequest() {
        resolver().resolve(listOf("Series", "Show", "Season 01"))
        assertTrue(drive.authorizations.all { it == "Bearer ya29.test" })
    }
}
