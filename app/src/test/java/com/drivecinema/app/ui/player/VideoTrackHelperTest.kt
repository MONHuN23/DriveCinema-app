package com.drivecinema.app.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task E.4: video the device cannot decode is explained instead of showing a silent black screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoTrackHelperTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val hevc4kHdr = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H265)
        .setWidth(3840)
        .setHeight(2160)
        .setColorInfo(
            ColorInfo.Builder()
                .setColorSpace(C.COLOR_SPACE_BT2020)
                .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                .setColorRange(C.COLOR_RANGE_LIMITED)
                .setLumaBitdepth(10)
                .setChromaBitdepth(10)
                .build()
        )
        .build()

    private val h264Hd = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(1920)
        .setHeight(1080)
        .build()

    private val aac = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setChannelCount(2).build()

    private fun group(type: String, format: Format, support: Int, selected: Boolean = false) =
        Tracks.Group(TrackGroup(type, format), false, intArrayOf(support), booleanArrayOf(selected))

    @Test
    fun describesCodecBitDepthHdrAndResolution() {
        assertEquals("HEVC 10-bit HDR 2160p", VideoTrackHelper.describeFormat(hevc4kHdr))
        assertEquals("H.264 1080p", VideoTrackHelper.describeFormat(h264Hd))
        assertEquals("AV1", VideoTrackHelper.describeFormat(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_AV1).build()))
    }

    @Test
    fun videoBeyondDecoderCapabilitiesProducesMessage() {
        val tracks = Tracks(
            listOf(
                group("video", hevc4kHdr, C.FORMAT_EXCEEDS_CAPABILITIES),
                group("audio", aac, C.FORMAT_HANDLED, selected = true)
            )
        )
        assertEquals(
            "Video format (HEVC 10-bit HDR 2160p) is not supported by this device's decoder.",
            VideoTrackHelper.unsupportedVideoMessage(tracks)
        )
    }

    @Test
    fun playableVideoProducesNoMessage() {
        val tracks = Tracks(listOf(group("video", h264Hd, C.FORMAT_HANDLED, selected = true)))
        assertNull(VideoTrackHelper.unsupportedVideoMessage(tracks))
    }

    @Test
    fun audioOnlyMediaProducesNoMessage() {
        val tracks = Tracks(listOf(group("audio", aac, C.FORMAT_HANDLED, selected = true)))
        assertNull(VideoTrackHelper.unsupportedVideoMessage(tracks))
        assertNull(VideoTrackHelper.unsupportedVideoMessage(Tracks.EMPTY))
    }

    private fun decoderInitError(format: Format?, rendererName: String) = ExoPlaybackException.createForRenderer(
        RuntimeException("decoder init failed"),
        rendererName,
        0,
        format,
        C.FORMAT_HANDLED,
        false,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
    )

    @Test
    fun videoDecoderFailureIsNotTreatedAsAudio() {
        // Regression: every decoder init failure used to be handled as an audio failure, muting audio and
        // restarting playback instead of explaining that the video cannot be decoded.
        val error = decoderInitError(hevc4kHdr, "MediaCodecVideoRenderer")
        assertEquals(DecoderFailure.VIDEO, VideoTrackHelper.classifyDecoderFailure(error))
        assertEquals(
            "Video format (HEVC 10-bit HDR 2160p) is not supported by this device's decoder.",
            VideoTrackHelper.videoDecoderErrorMessage(error)
        )
    }

    @Test
    fun audioDecoderFailureIsStillAudio() {
        val ac3 = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).build()
        assertEquals(DecoderFailure.AUDIO, VideoTrackHelper.classifyDecoderFailure(decoderInitError(ac3, "MediaCodecAudioRenderer")))
    }

    @Test
    fun otherErrorsAreNotDecoderFailures() {
        val network = PlaybackException("net", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertEquals(DecoderFailure.NONE, VideoTrackHelper.classifyDecoderFailure(network))
    }

    @Test
    fun bannerShowsMessageAndDismisses() {
        var dismissed = false
        val message = VideoTrackHelper.unsupportedMessage("HEVC 10-bit HDR 2160p")
        composeTestRule.setContent {
            UnsupportedFormatBanner(message = message, onDismiss = { dismissed = true })
        }
        composeTestRule.onNodeWithText(message, substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Dismiss").performClick()
        assertEquals(true, dismissed)
    }
}
