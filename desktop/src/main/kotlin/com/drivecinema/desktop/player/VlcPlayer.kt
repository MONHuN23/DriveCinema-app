package com.drivecinema.desktop.player

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.drivecinema.app.core.logging.AppLog
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer

/** An audio track as offered in the player's track menu. */
data class AudioTrackOption(val id: Int, val label: String)

/** Events the player screen reacts to (saving progress). */
enum class PlayerEvent { PAUSED, FINISHED }

object VlcAvailability {
    @Volatile
    private var found: Boolean? = null

    /** True when libVLC is installed and loadable (Linux: `sudo apt install vlc`). Checked once, see [recheck]. */
    val isAvailable: Boolean
        get() = found ?: recheck()

    /** Looks for libVLC again, e.g. after "Install VLC" finished, so no restart is needed. */
    @Synchronized
    fun recheck(): Boolean {
        val result = try {
            NativeDiscovery().discover()
        } catch (e: Throwable) {
            AppLog.w("VlcPlayer", "libVLC discovery failed: ${e.message}")
            false
        }
        found = result
        return result
    }
}

/**
 * libVLC player whose video frames are drawn by Compose (so controls can sit on top of the video).
 * vlcj rule: never call back into the player from its own event thread — use [MediaPlayer.submit].
 */
class VlcPlayer : AutoCloseable {

    val frame = mutableStateOf<ImageBitmap?>(null)
    val isPlaying = mutableStateOf(false)
    val isBuffering = mutableStateOf(true)
    val hasEnded = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val positionMs = mutableLongStateOf(0L)
    val durationMs = mutableLongStateOf(0L)
    val audioTracks = mutableStateOf<List<AudioTrackOption>>(emptyList())
    val selectedAudioTrack = mutableIntStateOf(-1)
    val volume = mutableIntStateOf(100)
    val isMuted = mutableStateOf(false)

    @Volatile var onEvent: (PlayerEvent) -> Unit = {}

    private val factory = MediaPlayerFactory("--no-video-title-show", "--quiet")
    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    // Two bitmaps used in turn, so Compose never draws one that VLC is writing into.
    private val bitmaps = arrayOf(Bitmap(), Bitmap())
    private var nextBitmap = 0
    private var imageInfo: ImageInfo? = null
    private var pixels = ByteArray(0)
    private var rowBytes = 0

    init {
        player.videoSurface().set(
            CallbackVideoSurface(FormatCallback(), FrameCallback(), true, VideoSurfaceAdapters.getVideoSurfaceAdapter())
        )
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                isPlaying.value = true
                isBuffering.value = false
                hasEnded.value = false
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                isPlaying.value = false
                onEvent(PlayerEvent.PAUSED)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                isPlaying.value = false
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                isPlaying.value = false
                hasEnded.value = true
                onEvent(PlayerEvent.FINISHED)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                isPlaying.value = false
                isBuffering.value = false
                error.value = "VLC could not play this file. Check your connection, or that the file still exists in Drive."
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                positionMs.longValue = newTime
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                durationMs.longValue = newLength
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                isBuffering.value = newCache < 100f
            }

            override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: TrackType, id: Int) {
                if (type == TrackType.AUDIO) mediaPlayer.submit { refreshAudioTracks() }
            }
        })
    }

    /** Opens [url] and starts at [startMs] (VLC seeks before the first frame, no jump). */
    fun open(url: String, startMs: Long) {
        hasEnded.value = false
        error.value = null
        isBuffering.value = true
        val options = buildList {
            // A bigger network buffer rides out slow or uneven connections (e.g. a phone hotspot).
            add(":network-caching=$NETWORK_CACHING_MS")
            if (startMs > 0) add(":start-time=${startMs / 1000.0}")
        }
        player.media().play(url, *options.toTypedArray())
    }

    fun togglePause() {
        if (hasEnded.value) return
        player.controls().pause()
    }

    fun seekTo(ms: Long) {
        val duration = durationMs.longValue
        val target = if (duration > 0) ms.coerceIn(0, duration - 1_000L) else ms.coerceAtLeast(0)
        player.controls().setTime(target)
        positionMs.longValue = target
    }

    fun seekBy(deltaMs: Long) = seekTo(positionMs.longValue + deltaMs)

    fun setVolume(percent: Int) {
        val value = percent.coerceIn(0, 150)
        player.audio().setVolume(value)
        volume.intValue = value
        if (value > 0 && isMuted.value) toggleMute()
    }

    fun toggleMute() {
        val muted = !isMuted.value
        player.audio().setMute(muted)
        isMuted.value = muted
    }

    fun selectAudioTrack(id: Int) {
        player.audio().setTrack(id)
        selectedAudioTrack.intValue = id
    }

    private fun refreshAudioTracks() {
        audioTracks.value = player.audio().trackDescriptions()
            .filter { it.id() >= 0 } // -1 is VLC's "Disable"
            .map { AudioTrackOption(it.id(), it.description()) }
        selectedAudioTrack.intValue = player.audio().track()
    }

    companion object {
        private const val TAG = "VlcPlayer"
        const val NETWORK_CACHING_MS = 5_000

        /**
         * Screen size in real pixels. AWT reports scaled units on HiDPI displays (1920 px at 125 % = 1536), and
         * frames must not be shrunk below what the screen can actually show.
         */
        fun physicalScreenSize(): Pair<Int, Int> {
            val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
            val scale = runCatching {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
            }.getOrDefault(1.0).coerceAtLeast(1.0)
            return VideoBufferSize.physical(screen.width, screen.height, scale)
        }
    }

    override fun close() {
        onEvent = {}
        runCatching { player.controls().stop() }
        runCatching { player.release() }
        runCatching { factory.release() }
        bitmaps.forEach { runCatching { it.close() } }
    }

    private inner class FormatCallback : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val (screenWidth, screenHeight) = physicalScreenSize()
            val size = VideoBufferSize.fit(sourceWidth, sourceHeight, screenWidth, screenHeight)
            AppLog.i(TAG, "Video ${sourceWidth}x$sourceHeight, drawn at ${size.width}x${size.height}")
            val info = ImageInfo(size.width, size.height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
            imageInfo = info
            rowBytes = size.width * 4
            pixels = ByteArray(rowBytes * size.height)
            bitmaps.forEach { it.allocPixels(info) }
            return RV32BufferFormat(size.width, size.height)
        }

        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
    }

    private inner class FrameCallback : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) {}

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<out ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int
        ) {
            val info = imageInfo ?: return
            val buffer = nativeBuffers[0]
            buffer.rewind()
            buffer.get(pixels, 0, minOf(pixels.size, buffer.remaining()))
            val bitmap = bitmaps[nextBitmap]
            nextBitmap = 1 - nextBitmap
            bitmap.installPixels(info, pixels, rowBytes)
            frame.value = bitmap.asComposeImageBitmap()
        }

        override fun unlock(mediaPlayer: MediaPlayer) {}
    }
}
