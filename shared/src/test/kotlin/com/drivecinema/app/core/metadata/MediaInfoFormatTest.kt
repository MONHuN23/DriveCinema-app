package com.drivecinema.app.core.metadata

import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Task L.3: the same codec words and info line on every platform; unknown parts left out, never guessed. */
class MediaInfoFormatTest {

    @Test
    fun codecNamesFromVlcFourccAndMedia3Mime() {
        assertEquals("HEVC", MediaInfoFormat.videoCodecName("hevc"))
        assertEquals("HEVC", MediaInfoFormat.videoCodecName("video/hevc"))
        assertEquals("H.264", MediaInfoFormat.videoCodecName("h264"))
        assertEquals("E-AC-3", MediaInfoFormat.audioCodecName("eac3"))
        assertEquals("AC-3", MediaInfoFormat.audioCodecName("a52 ")) // VLC pads FourCCs to four characters
        assertEquals("DTS", MediaInfoFormat.audioCodecName("dts "))
        assertEquals("E-AC-3 Atmos", MediaInfoFormat.audioCodecName("audio/eac3-joc"))
        assertEquals("XYZW", MediaInfoFormat.videoCodecName("xyzw")) // unknown: as reported
        assertNull(MediaInfoFormat.audioCodecName("  "))
    }

    @Test
    fun resolutionAllowsForCroppedFilms() {
        assertEquals("2160p", MediaInfoFormat.resolutionLabel(3840, 1600))
        assertEquals("1080p", MediaInfoFormat.resolutionLabel(1920, 800))
        assertEquals("720p", MediaInfoFormat.resolutionLabel(1280, 534))
        assertEquals("480p", MediaInfoFormat.resolutionLabel(720, 480))
        assertNull(MediaInfoFormat.resolutionLabel(null, 1080))
    }

    @Test
    fun containerSizeLengthAndChannels() {
        assertEquals("MKV", MediaInfoFormat.containerOf("Dune.Part.Two.2024.2160p.mkv"))
        assertEquals("MP4", MediaInfoFormat.containerOf("file:///C:/Films/Alien%20(1979).MP4"))
        assertNull(MediaInfoFormat.containerOf("1AbCdriveFileId"))
        assertEquals("8.4 GB", MediaInfoFormat.sizeLabel(9_019_431_321))
        assertEquals("700 MB", MediaInfoFormat.sizeLabel(734_003_200))
        assertEquals("2 h 12 min", MediaInfoFormat.durationLabel(7_920_000))
        assertEquals("48 min", MediaInfoFormat.durationLabel(2_880_000))
        assertEquals("5.1", MediaInfoFormat.channelsLabel(6))
        assertNull(MediaInfoFormat.channelsLabel(0))
    }

    @Test
    fun infoLineShowsOnlyWhatIsKnown() {
        val full = MediaSourceEntity(
            sourceKey = "drive-id", mediaItemId = "m", kind = SourceKind.DRIVE, sizeBytes = 9_019_431_321,
            widthPx = 3840, heightPx = 2160, durationMs = 7_920_000, container = "MKV",
            videoCodec = "HEVC", audioCodec = "E-AC-3", audioChannels = 6, isHdr = true
        )
        assertEquals("2 h 12 min · 2160p HDR · HEVC · E-AC-3 5.1 · MKV · 8.4 GB · Drive", MediaInfoFormat.infoLine(full))

        // A local copy never played: container from its path, size from the scan.
        val local = MediaSourceEntity("file:///films/Alien.avi", "m", SourceKind.LOCAL, sizeBytes = 734_003_200)
        assertEquals("AVI · 700 MB · This device", MediaInfoFormat.infoLine(local))
        assertEquals("AVI · 700 MB", MediaInfoFormat.infoLine(local, withSource = false))

        assertNull(MediaInfoFormat.infoLine(MediaSourceEntity("drive-id", "m", SourceKind.DRIVE)))
        assertEquals("2 h 12 min · 2160p", MediaInfoFormat.shortLine(full))
    }

    @Test
    fun copiesInPlayOrderAndTheBestKnownCopy() {
        val drive = MediaSourceEntity("drive-id", "m", SourceKind.DRIVE, durationMs = 2_880_000, heightPx = 1080, widthPx = 1920)
        val pc = MediaSourceEntity("dchost://pc/1", "m", SourceKind.HOST)
        val local = MediaSourceEntity("content://local/1", "m", SourceKind.LOCAL, sizeBytes = 1)
        assertEquals(listOf(local, pc, drive), MediaInfoFormat.sortCopies(listOf(drive, pc, local)))
        // The Drive copy knows the most, so the episode row uses it even though it plays last.
        assertEquals(drive, MediaInfoFormat.bestKnown(listOf(drive, pc, local)))
        // A tie goes to play order.
        assertEquals(local, MediaInfoFormat.bestKnown(listOf(pc, local)))
        assertNull(MediaInfoFormat.bestKnown(emptyList()))
        assertEquals("Dolby Vision", MediaInfoFormat.videoCodecName("video/dolby-vision"))
    }
}
