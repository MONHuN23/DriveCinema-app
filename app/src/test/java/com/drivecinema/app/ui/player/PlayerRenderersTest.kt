package com.drivecinema.app.ui.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerRenderersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun renderersOf(factory: DefaultRenderersFactory): List<Renderer> =
        factory.createRenderers(
            Handler(Looper.getMainLooper()),
            object : VideoRendererEventListener {},
            object : AudioRendererEventListener {},
            { _ -> },
            { _ -> }
        ).toList()

    @Test
    fun ffmpegAudioRendererIsAddedAfterTheDeviceDecoder() {
        val renderers = renderersOf(PlayerRenderers.create(context))

        val mediaCodec = renderers.indexOfFirst { it is MediaCodecAudioRenderer }
        val ffmpeg = renderers.indexOfFirst { it is FfmpegAudioRenderer }
        assertTrue("MediaCodecAudioRenderer must be present", mediaCodec >= 0)
        assertTrue("FfmpegAudioRenderer must be present", ffmpeg >= 0)
        // The device decoder (and HDMI passthrough) must come first; FFmpeg is only the fallback.
        assertTrue("FFmpeg must come after the device decoder", mediaCodec < ffmpeg)
    }

    @Test
    fun withoutExtensionModeThereIsNoFfmpegRenderer() {
        val renderers = renderersOf(
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        )

        assertEquals(0, renderers.count { it is FfmpegAudioRenderer })
    }
}
