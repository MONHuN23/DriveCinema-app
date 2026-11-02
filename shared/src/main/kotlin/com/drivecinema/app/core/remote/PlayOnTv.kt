package com.drivecinema.app.core.remote

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64

/** Milestone D ("Play on TV", design `docs/design/d1-play-on-tv.md`): constants shared by the phone and the TV. */
object PlayOnTvProtocol {
    const val VERSION = 1
    /** TCP, on the TV while DriveCinema is visible there (after the PC host's 8766–8768). */
    const val PORT = 8769
    const val SERVICE_TYPE = "_drivecinema-play._tcp."
    /** NSD service name prefix; the TV's link ID follows, so the phone resolves only its own TV. */
    const val SERVICE_PREFIX = "DriveCinema-"
    const val TXT_ID = "id"
    const val MAX_POSITION_MS = 48L * 3600 * 1000
    const val MAX_TITLE = 200
    const val MAX_NAME = 60

    private val ID = Regex("[A-Za-z0-9_-]{4,64}")
    fun isValidId(id: String) = ID.matches(id)
}

/**
 * One linked device: on the phone a TV (with [port] and [lastAddress]), on the TV a phone. [key] is the shared 256-bit
 * link key (base64url), a secret: encrypted store only, never logged.
 */
data class DeviceLink(
    val id: String,
    val name: String,
    val key: String,
    val port: Int = PlayOnTvProtocol.PORT,
    val lastAddress: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("key", key).put("port", port).put("address", lastAddress ?: "")

    override fun toString() = "DeviceLink(id=$id, name=$name)"

    companion object {
        private val random = SecureRandom()

        fun fromJson(o: JSONObject): DeviceLink? = runCatching {
            val id = o.getString("id")
            val key = o.getString("key")
            if (!PlayOnTvProtocol.isValidId(id) || runCatching { decodeKey(key) }.getOrNull()?.size != 32) return null
            val port = o.optInt("port", PlayOnTvProtocol.PORT)
            DeviceLink(
                id = id,
                name = o.optString("name").take(PlayOnTvProtocol.MAX_NAME).ifBlank { "Device" },
                key = key,
                port = if (port in 1..65535) port else PlayOnTvProtocol.PORT,
                lastAddress = o.optString("address").ifBlank { null }
            )
        }.getOrNull()

        /** A new device ID (not secret, 16 hex characters). */
        fun newId(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        /** A new 256-bit link key. */
        fun newKey(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

        fun decodeKey(key: String): ByteArray = Base64.getUrlDecoder().decode(key)
    }
}

/**
 * The link exchanged inside the PIN-protected pairing channel (design §2, L1). The phone offers its ID, name and a
 * new key; the TV answers with its own ID, name and receiver port. The key travels only in the offer.
 */
object PairingLink {
    /** Challenge field: this TV can link (an older TV never sends it, so the phone doesn't wait for an answer). */
    const val CAN_LINK = "link"
    /** Challenge field: the TV is already signed in and only links; the phone sends no credentials. */
    const val MODE = "mode"
    const val MODE_LINK_ONLY = "link"
    /** Payload field holding the offer, next to (or instead of) the credentials. */
    const val OFFER = "link"

    fun offer(phone: DeviceLink): JSONObject = JSONObject().put("id", phone.id).put("name", phone.name).put("key", phone.key)

    fun parseOffer(o: JSONObject?): DeviceLink? = o?.let(DeviceLink::fromJson)?.copy(port = PlayOnTvProtocol.PORT, lastAddress = null)

    fun answer(tvId: String, tvName: String, port: Int): String =
        JSONObject().put("id", tvId).put("name", tvName).put("port", port).toString()

    /** The phone's record of the TV: the TV's answer plus the key the phone offered and the address it paired at. */
    fun parseAnswer(json: String, key: String, address: String): DeviceLink? = runCatching {
        DeviceLink.fromJson(JSONObject(json).put("key", key).put("address", address))
    }.getOrNull()
}

/** What the TV answers to a request. */
enum class PlayStatus { OK, NOT_IN_LIBRARY, NOT_SIGNED_IN, BAD_REQUEST }

/**
 * "Play this title at this position" (design §4). [mediaItemId] is the phone's catalog ID (a Drive file ID for Drive
 * titles, the same on the TV); [tmdbId] (the series' for an episode), [season] and [episode] find titles the TV has
 * under another ID. [positionMs] is the phone's resume position.
 */
data class PlayRequest(
    val mediaItemId: String,
    val isEpisode: Boolean,
    val tmdbId: Int?,
    val season: Int?,
    val episode: Int?,
    val title: String,
    val positionMs: Long
) {
    fun toJson(): String = JSONObject()
        .put("type", TYPE_PLAY)
        .put("mediaItemId", mediaItemId)
        .put("kind", if (isEpisode) "EPISODE" else "MOVIE")
        .apply {
            tmdbId?.let { put("tmdbId", it) }
            season?.let { put("season", it) }
            episode?.let { put("episode", it) }
        }
        .put("title", title)
        .put("positionMs", positionMs)
        .toString()

    companion object {
        const val TYPE_PLAY = "PLAY"
        private val MEDIA_ID = Regex("[A-Za-z0-9_:.-]{1,128}")

        /** Null for anything that isn't a valid PLAY request (unknown type, bad ID, out-of-range numbers). */
        fun parse(json: String): PlayRequest? = runCatching {
            val o = JSONObject(json)
            if (o.optString("type") != TYPE_PLAY) return null
            val id = o.getString("mediaItemId")
            if (!MEDIA_ID.matches(id)) return null
            val isEpisode = when (o.optString("kind")) {
                "EPISODE" -> true
                "MOVIE" -> false
                else -> return null
            }
            val position = o.getLong("positionMs")
            if (position !in 0..PlayOnTvProtocol.MAX_POSITION_MS) return null
            val tmdbId = optInt(o, "tmdbId")?.also { if (it <= 0) return null }
            val season = optInt(o, "season")?.also { if (it !in 0..999) return null }
            val episode = optInt(o, "episode")?.also { if (it !in 0..999) return null }
            val title = o.optString("title").trim().take(PlayOnTvProtocol.MAX_TITLE).ifEmpty { "DriveCinema" }
            PlayRequest(id, isEpisode, tmdbId, season, episode, title, position)
        }.getOrNull()

        private fun optInt(o: JSONObject, name: String): Int? = if (o.has(name)) o.getInt(name) else null
    }
}
