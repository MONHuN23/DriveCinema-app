package com.drivecinema.app.core.skipintro

import android.content.Context
import com.drivecinema.app.BuildConfig
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.security.AuthCredentialStore
import java.io.File

/** The "Skip Intro" switch (Account dialog), on by default (D3). Per device; off means no lookups at all. */
class SkipIntroSetting(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        const val PREFS = "drivecinema_settings"
        const val KEY_ENABLED = "skip_intro_enabled"
    }
}

/** One [SkipIntroLookup] per app process, so its cache and the "one request per episode" rule cover every player. */
object AndroidSkipIntro {
    @Volatile
    private var lookup: SkipIntroLookup? = null
    @Volatile
    private var tmdb: Pair<String, TmdbClient>? = null

    fun lookup(context: Context): SkipIntroLookup = lookup ?: synchronized(this) {
        lookup ?: create(context.applicationContext).also { lookup = it }
    }

    private fun create(context: Context): SkipIntroLookup {
        val http = SkipIntroHttp.newClient("DriveCinema/${BuildConfig.VERSION_NAME} (Android)")
        val credentials = AuthCredentialStore(context)
        return SkipIntroLookup(
            // The key is read per show (it can arrive later, with the TV's pairing); the client is reused.
            imdbIdOf = { tmdbShowId -> tmdbClient(tmdbKey(credentials)).getTvImdbId(tmdbShowId) },
            skipDb = SkipDbClient(http),
            introDb = IntroDbClient(http),
            cache = SkipIntroCache(File(context.filesDir, "skip-intro-cache.json"))
        )
    }

    /** Same key choice as MainActivity: the one saved with the sign-in, else the build's. */
    private fun tmdbKey(credentials: AuthCredentialStore): String =
        credentials.getCredentials()?.tmdbApiKey?.ifEmpty { null } ?: BuildConfig.TMDB_API_KEY

    private fun tmdbClient(key: String): TmdbClient {
        tmdb?.let { (cachedKey, client) -> if (cachedKey == key) return client }
        return TmdbClient(apiKey = key).also { tmdb = key to it }
    }
}
