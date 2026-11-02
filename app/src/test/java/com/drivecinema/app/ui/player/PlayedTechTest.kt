package com.drivecinema.app.ui.player

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Task L.3 (phone/TV): what Media3 reports about a copy, in the same words as the desktop. */
class PlayedTechTest {

    private fun video(mime: String, color: ColorInfo? = null) = Format.Builder()
        .setSampleMimeType(mime).setWidth(3840).setHeight(1600).setFrameRate(23.976f).setColorInfo(color).build()

    private val eac3 = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).setChannelCount(6).build()

    @Test
    fun hdr10HevcWithSurroundAudio() {
        val hdr10 = ColorInfo.Builder()
            .setColorSpace(C.COLOR_SPACE_BT2020).setColorRange(C.COLOR_RANGE_LIMITED).setColorTransfer(C.COLOR_TRANSFER_ST2084)
            .build()
        val tech = PlayedTech.from(video(MimeTypes.VIDEO_H265, hdr10), eac3, durationMs = 9_300_000L)!!

        assertEquals(PlayedTech(3840, 1600, 9_300_000L, "HEVC", "E-AC-3", 6, 23.976f, isHdr = true), tech)
    }

    @Test
    fun sdrIsFalseAndAMissingColourInfoIsUnknown() {
        val sdr = ColorInfo.Builder().setColorTransfer(C.COLOR_TRANSFER_SDR).build()
        assertEquals(false, PlayedTech.from(video(MimeTypes.VIDEO_H264, sdr), null, null)!!.isHdr)
        assertNull(PlayedTech.from(video(MimeTypes.VIDEO_H264), null, null)!!.isHdr)
        assertEquals(true, PlayedTech.from(video(MimeTypes.VIDEO_DOLBY_VISION), null, null)!!.isHdr)
    }

    @Test
    fun unknownValuesStayNull() {
        val tech = PlayedTech.from(null, eac3, durationMs = C.TIME_UNSET)!!
        assertNull(tech.widthPx)
        assertNull(tech.videoCodec)
        assertNull(tech.frameRate)
        assertNull(tech.isHdr)
        assertNull("C.TIME_UNSET is not a length", tech.durationMs)
        assertEquals("E-AC-3", tech.audioCodec)

        assertNull("nothing to save without tracks", PlayedTech.from(null, null, 1000L))
    }
}
