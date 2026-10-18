package com.drivecinema.desktop.security

import org.json.JSONObject

/** What the desktop app keeps between runs: the refresh token and the account shown in the UI. */
data class StoredCredentials(
    val refreshToken: String,
    val displayName: String,
    val email: String,
    val photoLink: String? = null
) {
    fun toJson(): String = JSONObject()
        .put("refreshToken", refreshToken)
        .put("displayName", displayName)
        .put("email", email)
        .put("photoLink", photoLink ?: JSONObject.NULL)
        .toString()

    // Never print the refresh token.
    override fun toString(): String = "StoredCredentials(email=$email, displayName=$displayName)"

    companion object {
        /** Returns null for anything unreadable (then the user simply signs in again). */
        fun fromJson(json: String): StoredCredentials? = runCatching {
            val o = JSONObject(json)
            StoredCredentials(
                refreshToken = o.getString("refreshToken").also { require(it.isNotBlank()) },
                displayName = o.optString("displayName"),
                email = o.optString("email"),
                photoLink = o.optString("photoLink").takeIf { it.isNotEmpty() && !o.isNull("photoLink") }
            )
        }.getOrNull()
    }
}

/** Where [StoredCredentials] live. Implementations must never log the token. */
interface CredentialStore {
    /** Shown in the UI/logs, e.g. "GNOME Keyring (Secret Service)". */
    val description: String

    /** False for the plain-file fallback: the UI tells the user. */
    val isSystemKeyring: Boolean

    fun load(): StoredCredentials?
    fun save(credentials: StoredCredentials)
    fun clear()
}

class InMemoryCredentialStore(private var stored: StoredCredentials? = null) : CredentialStore {
    override val description = "memory"
    override val isSystemKeyring = false
    override fun load() = stored
    override fun save(credentials: StoredCredentials) { stored = credentials }
    override fun clear() { stored = null }
}
