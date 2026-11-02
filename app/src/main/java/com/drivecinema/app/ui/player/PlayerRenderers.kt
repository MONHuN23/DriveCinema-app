package com.drivecinema.app.ui.player

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * Renderers for the player (Milestone G).
 *
 * Extension renderer mode ON adds the FFmpeg audio renderer (AC-3, E-AC-3, DTS, TrueHD) **after** the device's
 * own `MediaCodecAudioRenderer`. Media3 maps a track to the first renderer with the best support, so the
 * device's decoders and HDMI passthrough still win; FFmpeg only takes tracks nothing else can play.
 * Don't use `EXTENSION_RENDERER_MODE_PREFER`: it would force software decoding even where passthrough works.
 */
object PlayerRenderers {

    fun create(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
}
