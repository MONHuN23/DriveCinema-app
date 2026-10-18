package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone J.2: one Continue Watching card per series, with "Up next" after a finished episode. */
class ContinueWatchingTest {

    private val dark = SeriesEntity("s-dark", "Dark", posterPath = "/dark.jpg")
    private val episodes = (1..5).map { n ->
        MediaItemEntity("e$n", MediaType.EPISODE, "Episode $n", parentFolderId = "s1", seriesId = "s-dark", seasonNumber = 1, episodeNumber = n)
    }

    private fun episodeRow(n: Int, at: Long, position: Long = 600_000, completed: Boolean = false) = WatchHistoryRow(
        id = "e$n", title = "Episode $n", type = MediaType.EPISODE, posterPath = null, backdropPath = null,
        seriesId = "s-dark", seasonNumber = 1, episodeNumber = n,
        positionMs = position, durationMs = 1_440_000, isCompleted = completed, lastWatchedAt = at
    )

    private fun filmRow(id: String, at: Long, completed: Boolean = false) = WatchHistoryRow(
        id = id, title = "Film $id", type = MediaType.MOVIE, posterPath = "/$id.jpg", backdropPath = null,
        seriesId = null, seasonNumber = null, episodeNumber = null,
        positionMs = 3_600_000, durationMs = 7_200_000, isCompleted = completed, lastWatchedAt = at
    )

    /** Rows must be newest first, as the query returns them. */
    private suspend fun build(vararg rows: WatchHistoryRow) = ContinueWatching.build(
        rows.sortedByDescending { it.lastWatchedAt },
        episodesOf = { if (it == "s-dark") episodes else emptyList() },
        seriesOf = { if (it == "s-dark") dark else null }
    )

    @Test
    fun twoStartedEpisodesGiveOneCardForTheLastWatched() = runTest {
        val cards = build(episodeRow(2, at = 1), episodeRow(5, at = 2))

        assertEquals(listOf("e5"), cards.map { it.id })
        val card = cards.single()
        assertEquals("Dark", card.title)
        assertEquals("S1E5", card.episodeCode)
        assertEquals("/dark.jpg", card.posterPath)
        assertFalse(card.isUpNext)
        assertEquals("S1E5 · 14 min left", ContinueWatching.subtitle(card))
    }

    @Test
    fun finishedEpisodeBecomesUpNextForTheFollowingOne() = runTest {
        val card = build(episodeRow(4, at = 1, position = 1_440_000, completed = true)).single()

        assertEquals("e5", card.id)
        assertTrue(card.isUpNext)
        assertEquals(0L, card.positionMs)
        assertEquals("Up next · S1E5", ContinueWatching.subtitle(card))
        assertEquals("Dark - S1E5", ContinueWatching.playerTitle(card))
    }

    @Test
    fun upNextResumesTheFollowingEpisodeWhenItWasStartedBefore() = runTest {
        // E5 was started first, then E4 was (re)watched to the end.
        val card = build(episodeRow(5, at = 1, position = 300_000), episodeRow(4, at = 2, completed = true)).single()

        assertEquals("e5", card.id)
        assertFalse(card.isUpNext)
        assertEquals(300_000L, card.positionMs)
    }

    @Test
    fun finishingTheLastEpisodeRemovesTheSeries() = runTest {
        assertTrue(build(episodeRow(5, at = 1, completed = true), episodeRow(3, at = 0)).isEmpty())
    }

    @Test
    fun filmsKeepTheirOwnCardsAndTheRowStaysNewestFirst() = runTest {
        val cards = build(
            filmRow("m1", at = 1),
            episodeRow(2, at = 2),
            filmRow("m2", at = 3, completed = true),
            filmRow("m3", at = 4)
        )
        assertEquals(listOf("m3", "e2", "m1"), cards.map { it.id })
        assertEquals("60 min left", ContinueWatching.subtitle(cards.first()))
    }

    @Test
    fun limitIsAppliedAfterCombining() = runTest {
        val rows = (1..30).map { filmRow("m$it", at = it.toLong()) } + episodeRow(1, at = 100) + episodeRow(2, at = 99)
        val cards = build(*rows.toTypedArray())
        assertEquals(ContinueWatching.LIMIT, cards.size)
        assertEquals("e1", cards.first().id)
        assertEquals(1, cards.count { it.seriesId == "s-dark" })
    }
}
