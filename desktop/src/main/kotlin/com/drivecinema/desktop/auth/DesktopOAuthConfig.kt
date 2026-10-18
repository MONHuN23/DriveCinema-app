package com.drivecinema.desktop.auth

import java.util.Properties

/**
 * The *Desktop app* OAuth client from Google Cloud Console. Read at build time from `local.properties`
 * (`GOOGLE_DESKTOP_CLIENT_ID`, `GOOGLE_DESKTOP_CLIENT_SECRET`) or the environment, and bundled as the
 * `desktop-config.properties` resource — never committed to git.
 */
data class DesktopOAuthConfig(val clientId: String, val clientSecret: String) {

    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    override fun toString(): String = "DesktopOAuthConfig(clientId=$clientId, clientSecret=***)"

    companion object {
        const val RESOURCE = "/desktop-config.properties"

        fun fromProperties(properties: Properties) = DesktopOAuthConfig(
            clientId = properties.getProperty("GOOGLE_DESKTOP_CLIENT_ID").orEmpty().trim(),
            clientSecret = properties.getProperty("GOOGLE_DESKTOP_CLIENT_SECRET").orEmpty().trim()
        )

        fun load(): DesktopOAuthConfig {
            val properties = Properties()
            DesktopOAuthConfig::class.java.getResourceAsStream(RESOURCE)?.use { properties.load(it) }
            return fromProperties(properties)
        }
    }
}
