package com.drivecinema.desktop.library

import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.skipintro.IntroTimes
import com.drivecinema.app.core.skipintro.SkipIntroEpisodes
import com.drivecinema.app.core.skipintro.SkipIntroLookup

/**
 * Milestone O on the desktop: the account menu's "Skip intro" switch and the lookup for the player. With the switch
 * off, or for a film, nothing is asked. [lookup] is the shared one (cache, rules, SkipDB then IntroDB).
 */
class DesktopSkipIntro(
    private val settings: DesktopSettings,
    private val lookup: SkipIntroLookup,
    private val mediaDao: MediaDao
) {
    fun isEnabled(): Boolean = settings.skipIntroEnabled()

    fun setEnabled(enabled: Boolean) = settings.setSkipIntroEnabled(enabled)

    /** The intro of catalog item [itemId] for a copy of [lengthMs], or null. */
    suspend fun find(itemId: String, lengthMs: Long): IntroTimes? {
        if (!isEnabled()) return null
        val episode = SkipIntroEpisodes.forItem(mediaDao, itemId) ?: return null
        return lookup.find(episode.request(lengthMs))
    }
}
