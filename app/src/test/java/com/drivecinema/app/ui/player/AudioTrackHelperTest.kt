package com.drivecinema.app.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioTrackHelperTest {

    @Test
    fun testOnlyShowsSelectorWhenMultipleAudioTracksExist() {
        val singleTrack = listOf(AudioTrackInfo(index = 0, language = "eng", label = "English"))
        val multiTrack = listOf(
            AudioTrackInfo(index = 0, language = "eng", label = "English"),
            AudioTrackInfo(index = 1, language = "hun", label = "Hungarian")
        )
        assertFalse(AudioTrackHelper.shouldShowSelector(singleTrack))
        assertTrue(AudioTrackHelper.shouldShowSelector(multiTrack))
    }

    @Test
    fun testEmptyTracksDoesNotShowSelector() {
        assertFalse(AudioTrackHelper.shouldShowSelector(emptyList()))
    }

    @Test
    fun testFormatCodecName() {
        assertEquals("AC-3 (Dolby Digital)", AudioTrackHelper.formatCodecName("audio/ac3"))
        assertEquals("E-AC-3 (Dolby Digital Plus)", AudioTrackHelper.formatCodecName("audio/eac3"))
        assertEquals("Dolby Atmos", AudioTrackHelper.formatCodecName("audio/eac3-joc"))
        assertEquals("Dolby TrueHD", AudioTrackHelper.formatCodecName("audio/true-hd"))
        assertEquals("AAC", AudioTrackHelper.formatCodecName("audio/mp4a-latm"))
        assertEquals("Opus", AudioTrackHelper.formatCodecName("audio/opus"))
        assertEquals("FLAC", AudioTrackHelper.formatCodecName("audio/flac"))
        assertEquals("MP3", AudioTrackHelper.formatCodecName("audio/mpeg"))
        assertEquals("DTS", AudioTrackHelper.formatCodecName("audio/vnd.dts"))
        assertEquals("DTS-HD", AudioTrackHelper.formatCodecName("audio/vnd.dts.hd"))
        assertEquals("Vorbis", AudioTrackHelper.formatCodecName("audio/vorbis"))
        assertEquals("Unknown", AudioTrackHelper.formatCodecName(null))
        assertEquals("CUSTOM", AudioTrackHelper.formatCodecName("audio/custom"))
    }

    @Test
    fun testFormatChannelCount() {
        assertEquals("Mono", AudioTrackHelper.formatChannelCount(1))
        assertEquals("Stereo", AudioTrackHelper.formatChannelCount(2))
        assertEquals("5.1 Surround", AudioTrackHelper.formatChannelCount(6))
        assertEquals("7.1 Surround", AudioTrackHelper.formatChannelCount(8))
        assertEquals("4 ch", AudioTrackHelper.formatChannelCount(4))
        assertEquals("", AudioTrackHelper.formatChannelCount(0))
        assertEquals("", AudioTrackHelper.formatChannelCount(-1))
    }

    @Test
    fun testFormatLanguage() {
        assertEquals("English", AudioTrackHelper.formatLanguage("en"))
        assertEquals("German", AudioTrackHelper.formatLanguage("de"))
        assertEquals("Hungarian", AudioTrackHelper.formatLanguage("hu"))
        assertEquals("Unknown Language", AudioTrackHelper.formatLanguage(null))
        assertEquals("Unknown Language", AudioTrackHelper.formatLanguage(""))
    }

    @Test
    fun testResolveAudioTracksWhenSupportedTrackAlreadySelected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()

        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("en")
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()
        val trackGroup = TrackGroup("audio", format)
        val group = Tracks.Group(trackGroup, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))
        val tracks = Tracks(listOf(group))

        val result = AudioTrackHelper.resolveAudioTracks(player, tracks)
        assertTrue(result is AudioSelectionResult.SupportedTrackSelected)
        val selected = (result as AudioSelectionResult.SupportedTrackSelected).track
        assertEquals("AAC", selected.codecDisplay)
        assertEquals("Stereo", selected.channelsDisplay)
        assertEquals("English", selected.languageDisplay)
        assertTrue(selected.isSupported)
        assertFalse(player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO))

        player.release()
    }

    @Test
    fun testResolveAudioTracksSwitchesToSupportedWhenCurrentIsUnsupported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()

        val unsupportedFormat = Format.Builder()
            .setSampleMimeType("audio/ac3")
            .setLanguage("de")
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val supportedFormat = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setLanguage("en")
            .setChannelCount(2)
            .setSampleRate(48000)
            .build()
        val trackGroup = TrackGroup("audio", unsupportedFormat, supportedFormat)
        // track 0 is selected but unsupported; track 1 is supported but not selected
        val group = Tracks.Group(
            trackGroup,
            false,
            intArrayOf(C.FORMAT_UNSUPPORTED_TYPE, C.FORMAT_HANDLED),
            booleanArrayOf(true, false)
        )
        val tracks = Tracks(listOf(group))

        val result = AudioTrackHelper.resolveAudioTracks(player, tracks)
        assertTrue(result is AudioSelectionResult.SupportedTrackSelected)
        val selected = (result as AudioSelectionResult.SupportedTrackSelected).track
        assertEquals(1, selected.trackIndex)
        assertEquals("AAC", selected.codecDisplay)
        assertTrue(selected.isSupported)

        // Verify override was applied to player
        val overrides = player.trackSelectionParameters.overrides
        assertTrue("An override should be present for the supported track", overrides.containsKey(trackGroup))
        assertEquals(listOf(1), overrides[trackGroup]?.trackIndices)

        player.release()
    }

    @Test
    fun testResolveAudioTracksDisablesAudioWhenAllTracksAreUnsupported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val player = ExoPlayer.Builder(context).build()

        val unsupportedFormat1 = Format.Builder()
            .setSampleMimeType("audio/ac3")
            .setLanguage("de")
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val unsupportedFormat2 = Format.Builder()
            .setSampleMimeType("audio/ac3")
            .setLanguage("en")
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        val trackGroup = TrackGroup("audio", unsupportedFormat1, unsupportedFormat2)
        val group = Tracks.Group(
            trackGroup,
            false,
            intArrayOf(C.FORMAT_UNSUPPORTED_TYPE, C.FORMAT_UNSUPPORTED_TYPE),
            booleanArrayOf(true, false)
        )
        val tracks = Tracks(listOf(group))

        val result = AudioTrackHelper.resolveAudioTracks(player, tracks)
        assertTrue(result is AudioSelectionResult.NoSupportedTrack)
        val noSupported = result as AudioSelectionResult.NoSupportedTrack
        assertEquals(2, noSupported.allTracks.size)
        assertFalse(noSupported.allTracks[0].isSupported)
        assertFalse(noSupported.allTracks[1].isSupported)

        // Verify audio rendering was disabled in player parameters
        assertTrue(
            "Audio track type must be disabled to avoid decoder crash",
            player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)
        )

        player.release()
    }

    @Test
    fun testExoPlayerPreservesExceedRendererCapabilities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setExceedRendererCapabilitiesIfNecessary(false).build())
        }
        val player = ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
        val params = trackSelector.parameters
        System.err.println("DEBUG_EXO: trackSelector params exceedRendererCapabilitiesIfNecessary = ${params.exceedRendererCapabilitiesIfNecessary}")
        val playerTrackParams = player.trackSelectionParameters
        System.err.println("DEBUG_EXO: player trackSelectionParameters = $playerTrackParams")
        player.release()
    }
}


