package com.drivecinema.app.core.devicesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Milestone Q: the per-device sync file (design §3). */
class SyncFileCodecTest {

    private val file = SyncFile(
        deviceId = "3f9c0a1b2c3d4e5f",
        deviceName = "Google Pixel 9a",
        platform = "android-phone",
        updatedAt = 1_761_740_000_000L,
        entries = mapOf(
            "drive:1AbCdEf" to SyncEntry(1_260_000L, 1_440_000L, false, 1_761_739_000_000L),
            "tmdb:tv:1399:s1e3" to SyncEntry(3_300_000L, 3_360_000L, true, 1_761_700_000_000L)
        )
    )

    @Test
    fun roundTripKeepsEveryField() {
        assertEquals(file, SyncFileCodec.decode(SyncFileCodec.encode(file)))
    }

    @Test
    fun theSameStateAlwaysGivesTheSameText() {
        val reversed = file.copy(entries = file.entries.entries.reversed().associate { it.key to it.value })
        assertEquals(SyncFileCodec.encode(file), SyncFileCodec.encode(reversed))
    }

    @Test
    fun theFileHoldsNoTitlesOnlyIdsAndNumbers() {
        val text = SyncFileCodec.encode(file)
        assertTrue(text.contains("drive:1AbCdEf"))
        assertFalse(text.contains("title", ignoreCase = true))
    }

    @Test
    fun aFileFromANewerAppOrABrokenFileIsSkipped() {
        assertNull(SyncFileCodec.decode(SyncFileCodec.encode(file).replace("\"format\":1", "\"format\":2")))
        assertNull(SyncFileCodec.decode("not json"))
        assertNull(SyncFileCodec.decode("{}"))
        assertNull("no device ID", SyncFileCodec.decode("""{"format":1,"entries":{}}"""))
    }

    @Test
    fun brokenEntriesAreSkippedAndTheRestIsRead() {
        val decoded = SyncFileCodec.decode(
            """{"format":1,"deviceId":"d1","entries":{
                "drive:ok":{"p":5,"d":10,"c":false,"t":100},
                "drive:noTime":{"p":5,"d":10,"c":false},
                "drive:zeroTime":{"p":5,"d":10,"c":false,"t":0},
                "drive:noPosition":{"d":10,"t":100},
                "drive:notAnObject":7,
                "drive:strings":{"p":"20","d":"30","c":true,"t":"200"},
                "drive:negative":{"p":-5,"d":-1,"t":300}
            }}"""
        )!!
        assertEquals(setOf("drive:ok", "drive:strings", "drive:negative"), decoded.entries.keys)
        assertEquals(SyncEntry(20, 30, true, 200), decoded.entries["drive:strings"])
        assertEquals(SyncEntry(0, 0, false, 300), decoded.entries["drive:negative"])
    }

    @Test
    fun fileNamesCarryTheDeviceId() {
        assertEquals("progress-abc.json", SyncFile.fileName("abc"))
        assertEquals("abc", SyncFile.deviceIdOf("progress-abc.json"))
        assertNull(SyncFile.deviceIdOf("progress-.json"))
        assertNull(SyncFile.deviceIdOf("settings.json"))
    }

    @Test
    fun deviceIdsAreSixteenHexCharacters() {
        val id = DeviceSyncIdentity.newDeviceId()
        assertTrue(id, Regex("[0-9a-f]{16}").matches(id))
    }
}
