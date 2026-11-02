package com.drivecinema.app.core.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Milestone D: the PLAY message and the link records (design `docs/design/d1-play-on-tv.md` §2, §4). */
class PlayOnTvMessageTest {

    private val episode = PlayRequest("1AbC-drive_id", isEpisode = true, tmdbId = 1399, season = 2, episode = 5, title = "Dark - S2E5", positionMs = 1_234_000)

    @Test
    fun requestRoundTrips() {
        assertEquals(episode, PlayRequest.parse(episode.toJson()))
        val film = PlayRequest("local:abc123", isEpisode = false, tmdbId = null, season = null, episode = null, title = "Alien", positionMs = 0)
        assertEquals(film, PlayRequest.parse(film.toJson()))
    }

    @Test
    fun invalidRequestsAreRejected() {
        fun with(change: JSONObject.() -> Unit) = JSONObject(episode.toJson()).apply(change).toString()
        assertNull("junk", PlayRequest.parse("not json"))
        assertNull("other type", PlayRequest.parse(with { put("type", "DELETE") }))
        assertNull("bad ID", PlayRequest.parse(with { put("mediaItemId", "../../etc") }))
        assertNull("empty ID", PlayRequest.parse(with { put("mediaItemId", "") }))
        assertNull("unknown kind", PlayRequest.parse(with { put("kind", "TRAILER") }))
        assertNull("negative position", PlayRequest.parse(with { put("positionMs", -1) }))
        assertNull("over 48 h", PlayRequest.parse(with { put("positionMs", PlayOnTvProtocol.MAX_POSITION_MS + 1) }))
        assertNull("season out of range", PlayRequest.parse(with { put("season", 1000) }))
        assertNull("TMDB ID 0", PlayRequest.parse(with { put("tmdbId", 0) }))
        assertNull("missing position", PlayRequest.parse(with { remove("positionMs") }))
    }

    @Test
    fun titleIsTrimmedAndCapped() {
        val long = PlayRequest.parse(JSONObject(episode.toJson()).put("title", "  " + "x".repeat(500)).toString())
        assertEquals(PlayOnTvProtocol.MAX_TITLE, long!!.title.length)
        assertEquals("DriveCinema", PlayRequest.parse(JSONObject(episode.toJson()).put("title", " ").toString())!!.title)
    }

    @Test
    fun deviceLinkRoundTripsAndHidesItsKey() {
        val link = DeviceLink(DeviceLink.newId(), "Living room TV", DeviceLink.newKey(), port = 9000, lastAddress = "192.168.1.20")
        assertEquals(link, DeviceLink.fromJson(link.toJson()))
        assertFalse(link.toString().contains(link.key))
        assertEquals(32, DeviceLink.decodeKey(link.key).size)
    }

    @Test
    fun deviceLinkRejectsBadIdsAndKeys() {
        val good = DeviceLink("abcd1234", "TV", DeviceLink.newKey()).toJson()
        assertNull("short key", DeviceLink.fromJson(JSONObject(good.toString()).put("key", "c2hvcnQ")))
        assertNull("not base64", DeviceLink.fromJson(JSONObject(good.toString()).put("key", "!!!")))
        assertNull("bad ID", DeviceLink.fromJson(JSONObject(good.toString()).put("id", "a b")))
        assertEquals("bad port falls back", PlayOnTvProtocol.PORT, DeviceLink.fromJson(JSONObject(good.toString()).put("port", 70000))!!.port)
    }

    @Test
    fun pairingOfferAndAnswer() {
        val phone = DeviceLink("phone0001", "Pixel 9a", DeviceLink.newKey())
        val offered = PairingLink.parseOffer(JSONObject(PairingLink.offer(phone).toString()))
        assertEquals(phone.copy(port = PlayOnTvProtocol.PORT, lastAddress = null), offered)
        assertNull(PairingLink.parseOffer(null))

        val tv = PairingLink.parseAnswer(PairingLink.answer("tv000001", "TCL", 8769), phone.key, "192.168.1.30")
        assertNotNull(tv)
        assertEquals(DeviceLink("tv000001", "TCL", phone.key, 8769, "192.168.1.30"), tv)
        assertNull("junk answer", PairingLink.parseAnswer("{", phone.key, "x"))
    }
}
