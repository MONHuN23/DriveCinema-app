package com.drivecinema.app.ui.details

import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task P.5: the facts, genres and button labels the phone's and the TV's details pages share. */
class DetailsPartsTest {

    @Test
    fun theFactsLineJoinsWhatIsKnownInOrder() {
        assertEquals("2012 · 3 seasons · rated 7.2", facts("2012", seasonCountText(3), voteAverage = 7.23).text)
        assertEquals("2024 · rated 8.4", facts("2024", runtimeText(null), voteAverage = 8.4).text)
        assertEquals(Facts(listOf("2024", "2h 46m"), "8.4"), facts("2024", runtimeText(166 * 60), voteAverage = 8.4))
        assertTrue(facts(null, seasonCountText(0), voteAverage = 0.0).isEmpty)
    }

    @Test
    fun ratingsRuntimesAndSeasonCountsReadNaturally() {
        assertEquals("7.0", ratingText(7.0))
        assertNull(ratingText(null))
        assertEquals("2h 46m", runtimeText(166 * 60))
        assertEquals("48m", runtimeText(48 * 60 + 30))
        assertNull(runtimeText(20))
        assertEquals("1 season", seasonCountText(1))
        assertNull(seasonCountText(0))
    }

    @Test
    fun genresAreTextNotChips() {
        assertEquals("Drama · Crime · Thriller", genresText("Drama, Crime ,Thriller"))
        assertNull(genresText(" , "))
        assertNull(genresText(null))
    }

    @Test
    fun theSeriesPlayButtonKeepsTodaysWording() {
        val episode = EpisodeItem(id = "e", title = "Pilot", season = 1, episode = 1)
        assertEquals("Play S1E1 • Pilot", playEpisodeLabel(episode))
        assertEquals("Resume S1E1 • Pilot", playEpisodeLabel(episode.copy(positionMs = 60_000)))
        assertEquals("Replay S1E1 • Pilot", playEpisodeLabel(episode.copy(isCompleted = true)))
        assertEquals("Play S2E5", playEpisodeLabel(EpisodeItem(id = "x", season = 2, episode = 5)))
    }

    @Test
    fun theFilmPlayButtonResumesOnlyAfterTenSeconds() {
        fun progress(position: Long, completed: Boolean = false) =
            PlaybackProgressEntity(mediaItemId = "m", positionMs = position, durationMs = 7_200_000, isCompleted = completed)
        assertEquals("Play Movie" to 0L, moviePlay(null))
        assertEquals("Play Movie" to 0L, moviePlay(progress(9_000)))
        assertEquals("Resume from 61:05" to 3_665_000L, moviePlay(progress(3_665_000)))
        assertEquals("Play Movie" to 0L, moviePlay(progress(3_665_000, completed = true)))
    }

    @Test
    fun anEpisodesFactsAreItsCopyAndItsRating() {
        val episode = EpisodeItem(id = "e", season = 1, episode = 3, info = "21 min · 720p", voteAverage = 7.8)
        assertEquals("21 min · 720p · rated 7.8", episodeFacts(episode)?.text)
        assertNull(episodeFacts(episode.copy(info = null, voteAverage = null)))
        assertEquals("E3. Episode 3", episodeTitle(episode))
        assertEquals("E3. Pilot", episodeTitle(episode.copy(title = "Pilot")))
    }
}
