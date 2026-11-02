package com.drivecinema.app.core.skipintro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SkipIntroCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000_000L
    private val day = 24 * 60 * 60 * 1000L
    private val key = SkipIntroCache.episodeKey("tt0475784", 1, 2, 3515)
    private val found = SkipIntroOutcome.Found(IntroTimes(0, 101_400), IntroSource.SKIPDB)

    private fun cache(file: File = File(folder.root, "skip-intro-cache.json"), max: Int = SkipIntroCache.MAX_ENTRIES) =
        SkipIntroCache(file, now = { clock }, maxEntries = max)

    @Test
    fun answersSurviveANewInstance() {
        val file = File(folder.root, "skip-intro-cache.json")
        cache(file).apply {
            putOutcome(key, found)
            putImdbId(63247, "tt0475784")
            putImdbId(99, null)
        }
        val reopened = cache(file)
        assertEquals(found, reopened.outcome(key))
        assertEquals(CachedImdbId("tt0475784"), reopened.imdbId(63247))
        assertEquals(CachedImdbId(null), reopened.imdbId(99))
        assertNull(reopened.imdbId(1434))
        assertFalse("no temp file left behind", File(folder.root, "skip-intro-cache.json.tmp").exists())
    }

    @Test
    fun eachKindExpiresAfterItsLifetime() {
        val cache = cache()
        cache.putOutcome("found", found)
        cache.putOutcome("unknown", SkipIntroOutcome.Unknown)
        cache.putOutcome("none", SkipIntroOutcome.ConfirmedNone)
        cache.putImdbId(1, "tt0098936")
        cache.putImdbId(2, null)

        clock += 2 * day - 1
        assertEquals(SkipIntroOutcome.Unknown, cache.outcome("unknown"))
        assertEquals(CachedImdbId(null), cache.imdbId(2))
        clock += 1
        assertNull(cache.outcome("unknown"))
        assertNull(cache.imdbId(2))

        clock += 12 * day
        assertNull("found lasts 14 days", cache.outcome("found"))
        assertEquals(SkipIntroOutcome.ConfirmedNone, cache.outcome("none"))

        clock += 16 * day
        assertNull("none lasts 30 days", cache.outcome("none"))
        assertNull("IMDb IDs last 30 days", cache.imdbId(1))
    }

    @Test
    fun aClockGoingBackwardsMakesEntriesStale() {
        val cache = cache()
        cache.putOutcome(key, found)
        clock -= 1
        assertNull(cache.outcome(key))
    }

    @Test
    fun aCorruptFileStartsEmpty() {
        val file = File(folder.root, "skip-intro-cache.json").apply { writeText("{\"episodes\": [truncated") }
        val cache = cache(file)
        assertNull(cache.outcome(key))
        cache.putOutcome(key, found)
        assertEquals(found, cache(file).outcome(key))
    }

    @Test
    fun aDirectoryInPlaceOfTheFileNeverCrashes() {
        val dir = File(folder.root, "skip-intro-cache.json").apply { mkdirs() }
        val cache = cache(dir)
        cache.putOutcome(key, found)
        assertEquals("still answers from memory", found, cache.outcome(key))
    }

    @Test
    fun theOldestEntriesAreDroppedBeyondTheLimit() {
        val file = File(folder.root, "skip-intro-cache.json")
        val cache = cache(file, max = 3)
        for (i in 1..5) {
            cache.putOutcome("k$i", SkipIntroOutcome.Unknown)
            clock += 1
        }
        val reopened = cache(file, max = 3)
        assertNull(reopened.outcome("k1"))
        assertNull(reopened.outcome("k2"))
        assertEquals(SkipIntroOutcome.Unknown, reopened.outcome("k5"))
    }

    @Test
    fun aPauseLastsUntilItsTimeAndIsKeptInTheFile() {
        val file = File(folder.root, "skip-intro-cache.json")
        cache(file).pause(IntroSource.INTRODB, clock + 60_000)
        val reopened = cache(file)
        assertTrue(reopened.isPaused(IntroSource.INTRODB))
        assertFalse(reopened.isPaused(IntroSource.SKIPDB))
        clock += 60_000
        assertFalse(reopened.isPaused(IntroSource.INTRODB))
    }
}
