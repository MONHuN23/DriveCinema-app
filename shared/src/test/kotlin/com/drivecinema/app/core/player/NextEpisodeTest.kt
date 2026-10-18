package com.drivecinema.app.core.player

import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEpisodeTest {

    private fun ep(id: String, season: Int, episode: Int, title: String = "") = MediaItemEntity(
        id = id, type = MediaType.EPISODE, title = title,
        parentFolderId = "f", seasonNumber = season, episodeNumber = episode, seriesId = "s"
    )

    private val episodes = listOf(ep("s2e1", 2, 1), ep("s1e2", 1, 2), ep("s1e1", 1, 1))

    @Test
    fun `next episode in the same season`() = assertEquals("s1e2", NextEpisode.after(episodes, "s1e1")?.id)

    @Test
    fun `last episode of a season moves to the next season`() = assertEquals("s2e1", NextEpisode.after(episodes, "s1e2")?.id)

    @Test
    fun `last episode has no next`() = assertNull(NextEpisode.after(episodes, "s2e1"))

    @Test
    fun `unknown item has no next`() = assertNull(NextEpisode.after(episodes, "film"))

    @Test
    fun `label matches the episode list`() {
        assertEquals("Show - S1E2 Pilot", NextEpisode.label("Show", ep("x", 1, 2, "Pilot")))
        assertEquals("Show - S1E2", NextEpisode.label("Show", ep("x", 1, 2)))
    }
}
