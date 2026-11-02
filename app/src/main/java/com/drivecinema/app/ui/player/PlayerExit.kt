package com.drivecinema.app.ui.player

import android.content.Intent
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.ui.navigation.Screen

/**
 * What [PlayerActivity] hands back to MainActivity when it closes for a reason other than Back: an episode it couldn't
 * play in place (a slow PC connection or no copy needs MainActivity's dialog or message), or the Details button.
 */
sealed interface PlayerExit {
    /** Play this catalog item (the next or previous episode) from MainActivity. */
    data class PlayEpisode(val mediaItemId: String, val title: String?) : PlayerExit

    /** Open the details screen of this catalog item: the film's, or its series' for an episode. */
    data class OpenDetails(val mediaItemId: String) : PlayerExit

    fun toIntent(): Intent = Intent().apply {
        when (this@PlayerExit) {
            is PlayEpisode -> {
                putExtra(EXTRA_PLAY_ID, mediaItemId)
                putExtra(EXTRA_PLAY_TITLE, title)
            }
            is OpenDetails -> putExtra(EXTRA_DETAILS_ID, mediaItemId)
        }
    }

    companion object {
        private const val EXTRA_PLAY_ID = "com.drivecinema.app.RESULT_PLAY_EPISODE_ID"
        private const val EXTRA_PLAY_TITLE = "com.drivecinema.app.RESULT_PLAY_EPISODE_TITLE"
        private const val EXTRA_DETAILS_ID = "com.drivecinema.app.RESULT_OPEN_DETAILS_ID"

        /** The exit in a player result, or null for a plain Back. */
        fun from(data: Intent?): PlayerExit? {
            data ?: return null
            data.getStringExtra(EXTRA_DETAILS_ID)?.let { return OpenDetails(it) }
            data.getStringExtra(EXTRA_PLAY_ID)?.let { return PlayEpisode(it, data.getStringExtra(EXTRA_PLAY_TITLE)) }
            return null
        }

        /** The details screen for [item]: a film's own page, an episode's series page; null when there is none. */
        fun detailsScreen(item: MediaItemEntity?): Screen? = when {
            item == null -> null
            item.type == MediaType.MOVIE -> Screen.MovieDetails(item.id)
            item.type == MediaType.EPISODE -> item.seriesId?.let { Screen.SeriesDetails(it) }
            else -> null
        }
    }
}
