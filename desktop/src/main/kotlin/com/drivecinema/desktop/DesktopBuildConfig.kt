package com.drivecinema.desktop

import com.drivecinema.desktop.auth.DesktopOAuthConfig
import java.util.Properties

/** Values bundled at build time from local.properties (see the generateDesktopConfig task). */
object DesktopBuildConfig {
    private val properties: Properties by lazy {
        Properties().apply {
            DesktopBuildConfig::class.java.getResourceAsStream(DesktopOAuthConfig.RESOURCE)?.use { load(it) }
        }
    }

    /** Same TMDB key as the Android app; empty means no posters/metadata (the library still works). */
    val tmdbApiKey: String get() = properties.getProperty("TMDB_API_KEY").orEmpty().trim()
}
