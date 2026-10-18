package com.drivecinema.app.ui.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailsViewModelTest {
    @Test
    fun testSmartPlayTargetsFirstUnwatchedEpisode() {
        val episodes = listOf(
            EpisodeItem("ep1", season = 1, episode = 1, isCompleted = true),
            EpisodeItem("ep2", season = 1, episode = 2, isCompleted = false)
        )
        val target = DetailsViewModel.findNextTargetEpisode(episodes)
        assertEquals("ep2", target?.id)
    }

    @Test
    fun testSmartPlayWhenAllCompletedReturnsFirstEpisode() {
        val episodes = listOf(
            EpisodeItem("ep1", season = 1, episode = 1, isCompleted = true),
            EpisodeItem("ep2", season = 1, episode = 2, isCompleted = true)
        )
        val target = DetailsViewModel.findNextTargetEpisode(episodes)
        assertEquals("ep1", target?.id)
    }

    @Test
    fun testSmartPlayEmptyListReturnsNull() {
        val target = DetailsViewModel.findNextTargetEpisode(emptyList())
        assertNull(target)
    }

    @Test
    fun testSmartPlaySkipsUnavailableEpisodes() {
        val episodes = listOf(
            EpisodeItem(id = null, title = "Ep 1", season = 1, episode = 1, isAvailable = false),
            EpisodeItem(id = "ep2", title = "Ep 2", season = 1, episode = 2, isAvailable = true, isCompleted = false)
        )
        val target = DetailsViewModel.findNextTargetEpisode(episodes)
        assertEquals("ep2", target?.id)
    }

    @Test
    fun testEpisodeItemWithRichMetadata() {
        val ep = EpisodeItem(
            id = "drive_file_101",
            title = "Ozymandias",
            season = 5,
            episode = 14,
            overview = "Walt goes on the run as consequences catch up.",
            stillPath = "/ozy.jpg",
            voteAverage = 10.0,
            isCompleted = false,
            positionMs = 30000L,
            durationMs = 2800000L
        )
        assertEquals("drive_file_101", ep.id)
        assertEquals("Ozymandias", ep.title)
        assertEquals(5, ep.season)
        assertEquals(14, ep.episode)
        assertEquals("/ozy.jpg", ep.stillPath)
        assertEquals(10.0, ep.voteAverage ?: 0.0, 0.01)
        assertEquals(true, ep.isAvailable)
    }
}
