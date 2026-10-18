package com.drivecinema.app.ui.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.drivecinema.app.core.auth.GoogleAuthManager
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.getInstance
import com.drivecinema.app.core.player.PlaybackDataSourceFactory
import com.drivecinema.app.core.player.PlaybackProgressTracker
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.app.core.security.AuthCredentialStore
import kotlinx.coroutines.runBlocking

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_DRIVE_FILE_ID = "com.drivecinema.app.EXTRA_DRIVE_FILE_ID"
        /** Local SAF document (`content://` only); takes precedence over [EXTRA_DRIVE_FILE_ID]. */
        const val EXTRA_LOCAL_CONTENT_URI = "com.drivecinema.app.EXTRA_LOCAL_CONTENT_URI"
        const val EXTRA_TITLE = "com.drivecinema.app.EXTRA_TITLE"
        const val EXTRA_START_POSITION_MS = "com.drivecinema.app.EXTRA_START_POSITION_MS"
        const val EXTRA_ACCESS_TOKEN = "com.drivecinema.app.EXTRA_ACCESS_TOKEN"
        const val EXTRA_NEXT_EPISODE_ID = "com.drivecinema.app.EXTRA_NEXT_EPISODE_ID"
        const val EXTRA_NEXT_EPISODE_TITLE = "com.drivecinema.app.EXTRA_NEXT_EPISODE_TITLE"
    }

    private var player: ExoPlayer? = null
    private lateinit var credentialStore: AuthCredentialStore
    private lateinit var progressTracker: PlaybackProgressTracker
    private var currentFileId: String = ""
    private val authManager = GoogleAuthManager()
    // Written by the token refresh callback, read on ExoPlayer's loader thread (report F-11).
    @Volatile
    private var cachedAccessToken: String = ""
    private val isControlsVisible = androidx.compose.runtime.mutableStateOf(true)
    private val lastInteractionTimeMs = androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    private var nextEpisodeId: String? = null
    private var nextEpisodeTitle: String? = null
    /** True between onStop (tracking detached) and the next onStart. */
    private var progressDetached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialStore = AuthCredentialStore(this)
        val db = DriveCinemaDatabase.getInstance(this)
        progressTracker = PlaybackProgressTracker(db.playbackProgressDao())

        val source = PlayerUriBuilder.resolveSource(
            driveFileId = intent.getStringExtra(EXTRA_DRIVE_FILE_ID),
            localContentUri = intent.getStringExtra(EXTRA_LOCAL_CONTENT_URI)
        )
        // Progress key: Drive file ID for Drive media, content URI for local media.
        val fileId = source?.progressKey ?: ""
        currentFileId = fileId
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Streaming"
        val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        nextEpisodeId = intent.getStringExtra(EXTRA_NEXT_EPISODE_ID)
        nextEpisodeTitle = intent.getStringExtra(EXTRA_NEXT_EPISODE_TITLE)
        cachedAccessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""

        val tokenProvider = object : TokenProvider {
            override fun getAccessToken(): String {
                if (cachedAccessToken.isNotEmpty()) return cachedAccessToken
                return forceRefresh()
            }

            @Synchronized
            override fun forceRefresh(): String {
                val creds = credentialStore.getCredentials()
                if (creds != null && creds.refreshToken.isNotEmpty()) {
                    var token = ""
                    val latch = java.util.concurrent.CountDownLatch(1)
                    authManager.refreshAccessToken(
                        this@PlayerActivity,
                        com.drivecinema.app.MainActivity.GOOGLE_CLIENT_ID,
                        creds.refreshToken
                    ) { result ->
                        result.fold(
                            onSuccess = { refreshedToken ->
                                token = refreshedToken
                                cachedAccessToken = refreshedToken
                                android.util.Log.i(TAG, "Access token refreshed successfully")
                            },
                            onFailure = { error ->
                                android.util.Log.e(TAG, "Token refresh failed: ${error.message}", error)
                            }
                        )
                        latch.countDown()
                    }
                    try {
                        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    } catch (e: InterruptedException) {
                        android.util.Log.e(TAG, "Token refresh interrupted", e)
                    }
                    if (token.isNotEmpty()) {
                        return token
                    }
                }
                return cachedAccessToken
            }
        }

        val dataSourceFactory = PlaybackDataSourceFactory.create(this, tokenProvider)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(false)
                    .setExceedAudioConstraintsIfNecessary(false)
                    .build()
            )
        }

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateName = when (playbackState) {
                            androidx.media3.common.Player.STATE_IDLE -> "STATE_IDLE"
                            androidx.media3.common.Player.STATE_BUFFERING -> "STATE_BUFFERING"
                            androidx.media3.common.Player.STATE_READY -> "STATE_READY"
                            androidx.media3.common.Player.STATE_ENDED -> "STATE_ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }
                        android.util.Log.i(TAG, "Playback state changed: $stateName, playWhenReady=$playWhenReady")
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        android.util.Log.i(TAG, "Playback isPlaying: $isPlaying")
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        android.util.Log.i(TAG, "=== onTracksChanged: ${tracks.groups.size} groups ===")
                        for ((groupIndex, groupInfo) in tracks.groups.withIndex()) {
                            val typeName = when (groupInfo.type) {
                                androidx.media3.common.C.TRACK_TYPE_AUDIO -> "AUDIO"
                                androidx.media3.common.C.TRACK_TYPE_VIDEO -> "VIDEO"
                                androidx.media3.common.C.TRACK_TYPE_TEXT -> "TEXT"
                                else -> "OTHER(${groupInfo.type})"
                            }
                            android.util.Log.i(TAG, "Group $groupIndex [$typeName]: length=${groupInfo.length}")
                            val group = groupInfo.mediaTrackGroup
                            for (i in 0 until group.length) {
                                val format = group.getFormat(i)
                                val isSupported = groupInfo.isTrackSupported(i)
                                val isSelected = groupInfo.isTrackSelected(i)
                                val supportLevel = groupInfo.getTrackSupport(i)
                                val supportName = when (supportLevel) {
                                    androidx.media3.common.C.FORMAT_HANDLED -> "FORMAT_HANDLED"
                                    androidx.media3.common.C.FORMAT_EXCEEDS_CAPABILITIES -> "FORMAT_EXCEEDS_CAPABILITIES"
                                    androidx.media3.common.C.FORMAT_UNSUPPORTED_SUBTYPE -> "FORMAT_UNSUPPORTED_SUBTYPE"
                                    androidx.media3.common.C.FORMAT_UNSUPPORTED_TYPE -> "FORMAT_UNSUPPORTED_TYPE"
                                    androidx.media3.common.C.FORMAT_UNSUPPORTED_DRM -> "FORMAT_UNSUPPORTED_DRM"
                                    else -> "UNKNOWN($supportLevel)"
                                }
                                android.util.Log.i(
                                    TAG,
                                    "  Track $i: id=${format.id}, label=${format.label}, mimeType=${format.sampleMimeType}, " +
                                    "codecs=${format.codecs}, lang=${format.language}, channels=${format.channelCount}, " +
                                    "sampleRate=${format.sampleRate}, selected=$isSelected, supported=$isSupported ($supportName)"
                                )
                            }
                        }

                        when (val result = AudioTrackHelper.resolveAudioTracks(this@apply, tracks)) {
                            is AudioSelectionResult.SupportedTrackSelected -> {
                                android.util.Log.i(TAG, "Audio track resolved: supported track selected -> ${result.track.label}")
                            }
                            is AudioSelectionResult.NoSupportedTrack -> {
                                android.util.Log.w(TAG, "Audio track resolved: NO supported tracks found on this device! Audio rendering disabled to prevent crash.")
                            }
                            is AudioSelectionResult.NoAudioTracks -> {
                                android.util.Log.i(TAG, "No audio tracks present in media.")
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e(TAG, "Playback error [errorCode=${error.errorCode}, name=${error.errorCodeName}]: ${error.message}", error)
                        // A video decoder failure is shown by MinimalPlayerView; only audio failures are recovered here.
                        if (VideoTrackHelper.classifyDecoderFailure(error) == DecoderFailure.AUDIO) {
                            android.util.Log.w(TAG, "Audio decoder failed in PlayerActivity; disabling audio and recovering video playback", error)
                            AudioTrackHelper.disableAudio(this@apply)
                            prepare()
                            play()
                            return
                        }

                        var cause: Throwable? = error.cause
                        var depth = 1
                        while (cause != null && depth <= 10) {
                            when (cause) {
                                is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> {
                                    android.util.Log.e(TAG, "  -> HTTP ${cause.responseCode} ${cause.responseMessage}, headerFields=${cause.headerFields}")
                                }
                                is androidx.media3.datasource.HttpDataSource.HttpDataSourceException -> {
                                    android.util.Log.e(TAG, "  -> HttpDataSourceException (type=${cause.type}): ${cause.message}")
                                }
                                else -> {
                                    android.util.Log.e(TAG, "  -> [${cause.javaClass.simpleName}]: ${cause.message}")
                                }
                            }
                            cause = cause.cause
                            depth++
                        }
                    }
                })

                if (source != null) {
                    val mediaItem = MediaItem.fromUri(source.uri)
                    setMediaItem(mediaItem)

                    val resumeTarget = if (startPositionMs > 0L) {
                        startPositionMs
                    } else {
                        runBlocking { progressTracker.getResumePosition(fileId) }
                    }

                    if (resumeTarget > 0L) {
                        seekTo(resumeTarget)
                        // Registered before the progress tracker, so a resume point beyond this copy's end is
                        // corrected (start over) before any "finished" state can be recorded.
                        addListener(object : androidx.media3.common.Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState != androidx.media3.common.Player.STATE_READY &&
                                    playbackState != androidx.media3.common.Player.STATE_ENDED
                                ) return
                                removeListener(this)
                                val copyDuration = duration
                                if (copyDuration != androidx.media3.common.C.TIME_UNSET &&
                                    PlaybackTimeUtils.shouldRestartFromBeginning(resumeTarget, copyDuration)
                                ) {
                                    android.util.Log.i(TAG, "Resume point is past the end of this copy; starting from the beginning")
                                    seekTo(0L)
                                }
                            }
                        })
                    }
                    prepare()
                    playWhenReady = true
                }
            }

        player = exoPlayer
        if (fileId.isNotEmpty()) {
            progressTracker.attach(exoPlayer, fileId)
        }

        setContent {
            MinimalPlayerView(
                player = exoPlayer,
                title = title,
                nextEpisodeTitle = nextEpisodeTitle,
                controlsVisible = isControlsVisible.value,
                onControlsVisibilityChange = { isControlsVisible.value = it },
                lastInteractionTimeMs = lastInteractionTimeMs.longValue,
                onUserInteraction = { lastInteractionTimeMs.longValue = System.currentTimeMillis() },
                onNextEpisodeClick = { playNextEpisode() },
                onBackPressed = { finish() }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Back from the background (screen lock, Home): onStop detached progress tracking, so attach it again.
        val current = player
        if (progressDetached && current != null && currentFileId.isNotEmpty()) {
            progressTracker.attach(current, currentFileId)
            progressDetached = false
        }
    }

    override fun onStop() {
        super.onStop()
        player?.let {
            if (currentFileId.isNotEmpty()) {
                progressTracker.detach(it, currentFileId)
                progressDetached = true
            }
            it.pause()
        }
    }

    /** Hands the next episode back to MainActivity, which picks its best copy (local or Drive) and resume point. */
    private fun playNextEpisode() {
        val nextId = nextEpisodeId
        if (nextId.isNullOrEmpty()) return
        // Moving on counts as having watched this one, even during its ending song (Milestone J.1).
        player?.let { if (currentFileId.isNotEmpty()) progressTracker.markWatched(it, currentFileId) }
        setResult(RESULT_OK, android.content.Intent().apply {
            putExtra(EXTRA_NEXT_EPISODE_ID, nextId)
            putExtra(EXTRA_NEXT_EPISODE_TITLE, nextEpisodeTitle)
        })
        finish()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Gamepad A/B behave like D-pad centre / Back (for both key down and key up, so clicks still work).
        val keyEvent = RemoteKeyMapper.normalize(event)
        android.util.Log.i(TAG, "dispatchKeyEvent: action=${keyEvent.action}, keyCode=${keyEvent.keyCode}, controlsVisible=${isControlsVisible.value}")
        if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = keyEvent.keyCode

            // 1. Dedicated Hardware Media Keys: Play/Pause, Seek, Stop, Next
            val mediaAction = RemoteKeyMapper.mediaAction(keyCode, hasNextEpisode = !nextEpisodeId.isNullOrEmpty())
            if (mediaAction != null) {
                when (mediaAction) {
                    PlayerKeyAction.TogglePlayPause -> player?.let { if (it.isPlaying) it.pause() else it.play() }
                    PlayerKeyAction.Play -> player?.play()
                    PlayerKeyAction.Pause -> player?.pause()
                    is PlayerKeyAction.SeekBy -> player?.let { p ->
                        val duration = p.duration.coerceAtLeast(0L)
                        p.seekTo((p.currentPosition + mediaAction.deltaMs).coerceIn(0L, duration))
                    }
                    PlayerKeyAction.Exit -> finish()
                    PlayerKeyAction.NextEpisode -> playNextEpisode()
                }
                lastInteractionTimeMs.longValue = System.currentTimeMillis()
                return true
            }

            // 2. Hardware BACK Key Handling: if controls are visible, hide them first; otherwise exit
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (isControlsVisible.value) {
                    isControlsVisible.value = false
                    return true
                }
                return super.dispatchKeyEvent(keyEvent)
            }

            // 3. Navigation Keys (DPAD, Enter, Select, Space, gamepad A)
            if (RemoteKeyMapper.isNavigationKey(keyCode)) {
                if (!isControlsVisible.value) {
                    isControlsVisible.value = true
                    lastInteractionTimeMs.longValue = System.currentTimeMillis()
                    return true
                } else {
                    lastInteractionTimeMs.longValue = System.currentTimeMillis()
                }
            }
        }

        return super.dispatchKeyEvent(keyEvent)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
