package com.drivecinema.app.ui.player

import androidx.lifecycle.lifecycleScope
import com.drivecinema.app.core.host.HostConnections
import com.drivecinema.app.core.player.NextEpisode
import com.drivecinema.app.core.player.PreviousEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.drivecinema.app.core.auth.GoogleAuthManager
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.getInstance
import com.drivecinema.app.core.logging.DebugPerf
import com.drivecinema.app.core.logging.Perf
import com.drivecinema.app.core.logging.PerfSpan
import com.drivecinema.app.core.player.PlaybackDataSourceFactory
import com.drivecinema.app.core.player.PlaybackProgressTracker
import com.drivecinema.app.core.player.TokenProvider
import com.drivecinema.app.core.security.AuthCredentialStore
import com.drivecinema.app.core.skipintro.AndroidSkipIntro
import com.drivecinema.app.core.skipintro.IntroTimes
import com.drivecinema.app.core.skipintro.SkipIntroEpisodes
import com.drivecinema.app.core.skipintro.SkipIntroKeyAction
import com.drivecinema.app.core.skipintro.SkipIntroKeys
import com.drivecinema.app.core.skipintro.SkipIntroSetting
import com.drivecinema.app.core.skipintro.SkipIntroWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class PlayerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
        const val EXTRA_DRIVE_FILE_ID = "com.drivecinema.app.EXTRA_DRIVE_FILE_ID"
        /** Local SAF document (`content://` only); takes precedence over [EXTRA_DRIVE_FILE_ID]. */
        const val EXTRA_LOCAL_CONTENT_URI = "com.drivecinema.app.EXTRA_LOCAL_CONTENT_URI"
        /** Task I.2: a paired PC's copy: its `dchost://` key (progress) and the HTTPS URL to stream. */
        const val EXTRA_HOST_SOURCE_KEY = "com.drivecinema.app.EXTRA_HOST_SOURCE_KEY"
        const val EXTRA_HOST_URL = "com.drivecinema.app.EXTRA_HOST_URL"
        const val EXTRA_TITLE = "com.drivecinema.app.EXTRA_TITLE"
        const val EXTRA_START_POSITION_MS = "com.drivecinema.app.EXTRA_START_POSITION_MS"
        const val EXTRA_ACCESS_TOKEN = "com.drivecinema.app.EXTRA_ACCESS_TOKEN"
        const val EXTRA_NEXT_EPISODE_ID = "com.drivecinema.app.EXTRA_NEXT_EPISODE_ID"
        const val EXTRA_NEXT_EPISODE_TITLE = "com.drivecinema.app.EXTRA_NEXT_EPISODE_TITLE"
        const val EXTRA_PREVIOUS_EPISODE_ID = "com.drivecinema.app.EXTRA_PREVIOUS_EPISODE_ID"
        const val EXTRA_PREVIOUS_EPISODE_TITLE = "com.drivecinema.app.EXTRA_PREVIOUS_EPISODE_TITLE"
        /** The catalog item being played (film or episode), for the Details button and the episode lookups. */
        const val EXTRA_MEDIA_ITEM_ID = "com.drivecinema.app.EXTRA_MEDIA_ITEM_ID"
        /** Task M.0: the `play.firstFrame` span begun when Play was pressed (its start and trace cookie). */
        const val EXTRA_PERF_START_NANOS = "com.drivecinema.app.EXTRA_PERF_START_NANOS"
        const val EXTRA_PERF_COOKIE = "com.drivecinema.app.EXTRA_PERF_COOKIE"
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
    // State, so the controls follow when Next Episode swaps the video in place (F-29).
    private val titleState = androidx.compose.runtime.mutableStateOf("Streaming")
    private val nextEpisodeTitleState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private var previousEpisodeId: String? = null
    private val previousEpisodeTitleState = androidx.compose.runtime.mutableStateOf<String?>(null)
    /** The catalog item playing now; Details shows only when it is known. Follows Next / Previous in place. */
    private val currentMediaItemId = androidx.compose.runtime.mutableStateOf<String?>(null)
    /** The playing copy's key; a new one gives the player view fresh state. */
    private val mediaKey = androidx.compose.runtime.mutableStateOf("")
    private var playbackResolver: PlaybackResolver? = null
    private var switchingEpisode = false
    /** True between onStop (tracking detached) and the next onStart. */
    private var progressDetached = false
    /** Task L.3: the copy whose technical details were already saved (once per copy, like the desktop). */
    private var techSavedFor: String? = null
    /** Task M.0: ends at the first rendered video frame. */
    private var firstFrameSpan: PerfSpan? = null
    /** Milestone O: the playing copy's intro, once known; hidden with Back for that intro. Reset per copy. */
    private val introTimes = androidx.compose.runtime.mutableStateOf<IntroTimes?>(null)
    private val introDismissed = androidx.compose.runtime.mutableStateOf(false)
    /** Task P.8: the open audio or subtitle panel; the remote's Back closes it before anything else. */
    private val trackPanel = androidx.compose.runtime.mutableStateOf<TrackPanelKind?>(null)
    private var introJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        credentialStore = AuthCredentialStore(this)
        val db = DriveCinemaDatabase.getInstance(this)
        progressTracker = PlaybackProgressTracker(db.playbackProgressDao())

        val source = PlayerUriBuilder.resolveSource(
            driveFileId = intent.getStringExtra(EXTRA_DRIVE_FILE_ID),
            localContentUri = intent.getStringExtra(EXTRA_LOCAL_CONTENT_URI),
            hostSourceKey = intent.getStringExtra(EXTRA_HOST_SOURCE_KEY),
            hostUrl = intent.getStringExtra(EXTRA_HOST_URL)
        )
        // Progress key: Drive file ID for Drive media, content URI for local media.
        val fileId = source?.progressKey ?: ""
        currentFileId = fileId
        titleState.value = intent.getStringExtra(EXTRA_TITLE) ?: "Streaming"
        mediaKey.value = fileId
        val startPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
        nextEpisodeId = intent.getStringExtra(EXTRA_NEXT_EPISODE_ID)
        nextEpisodeTitleState.value = intent.getStringExtra(EXTRA_NEXT_EPISODE_TITLE)
        previousEpisodeId = intent.getStringExtra(EXTRA_PREVIOUS_EPISODE_ID)
        previousEpisodeTitleState.value = intent.getStringExtra(EXTRA_PREVIOUS_EPISODE_TITLE)
        currentMediaItemId.value = intent.getStringExtra(EXTRA_MEDIA_ITEM_ID)
        cachedAccessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN) ?: ""
        if (intent.hasExtra(EXTRA_PERF_START_NANOS)) {
            firstFrameSpan = Perf.resume("play.firstFrame", intent.getLongExtra(EXTRA_PERF_START_NANOS, 0L), intent.getIntExtra(EXTRA_PERF_COOKIE, 0))
                .put("source", source?.javaClass?.simpleName ?: "none")
        }

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

        val dataSourceFactory = PlaybackDataSourceFactory.create(this, tokenProvider, baseClient = DebugPerf.httpClient("player"))
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val renderersFactory = PlayerRenderers.create(this)

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
                    override fun onRenderedFirstFrame() {
                        firstFrameSpan?.end()
                    }

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
                        KeepScreenOn.update(window, isPlaying)
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
            }

        player = exoPlayer
        exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = saveTechOnce(exoPlayer)
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = saveTechOnce(exoPlayer)
        })
        // The player has no other item yet, so tracking can start before the copy is set (F-01 relies on it).
        if (fileId.isNotEmpty()) {
            progressTracker.attach(exoPlayer, fileId)
        }
        if (source != null) {
            lifecycleScope.launch {
                loadMedia(exoPlayer, source, startPositionMs)
                startIntroLookup(exoPlayer, fileId)
            }
        }

        val isTv = com.drivecinema.app.core.device.TvDeviceDetector.isTv(this)
        setContent {
            // Keyed by the playing copy: an episode swapped in by Next Episode starts with fresh player-view state.
            androidx.compose.runtime.key(mediaKey.value) { MinimalPlayerView(
                isTv = isTv,
                player = exoPlayer,
                title = titleState.value,
                nextEpisodeTitle = nextEpisodeTitleState.value,
                controlsVisible = isControlsVisible.value,
                onControlsVisibilityChange = { isControlsVisible.value = it },
                lastInteractionTimeMs = lastInteractionTimeMs.longValue,
                onUserInteraction = { lastInteractionTimeMs.longValue = System.currentTimeMillis() },
                onNextEpisodeClick = { playNextEpisode() },
                previousEpisodeTitle = previousEpisodeTitleState.value,
                onPreviousEpisodeClick = { playPreviousEpisode() },
                onDetailsClick = if (currentMediaItemId.value.isNullOrEmpty()) null else ({ openDetails() }),
                onBackPressed = { finish() },
                skipIntro = introTimes.value,
                skipIntroDismissed = introDismissed.value,
                onSkipIntro = { skipIntro(exoPlayer) },
                onHideSkipIntro = { introDismissed.value = true },
                trackPanel = trackPanel
            ) }
        }
    }

    /**
     * Milestone O: looks up the intro of the copy that just started, without delaying playback. Waits for the
     * player's own length (the services match on it), else uses the length saved for the copy. Nothing is sent
     * when the setting is off, for films, or when the copy changes in the meantime (Next Episode).
     */
    private fun startIntroLookup(exoPlayer: ExoPlayer, key: String) {
        introJob?.cancel()
        introTimes.value = null
        introDismissed.value = false
        if (key.isEmpty()) return
        introJob = lifecycleScope.launch {
            val db = DriveCinemaDatabase.getInstance(this@PlayerActivity)
            val (episode, savedLengthMs) = withContext(Dispatchers.IO) {
                if (!SkipIntroSetting(this@PlayerActivity).isEnabled()) return@withContext null to null
                SkipIntroEpisodes.forSource(db.mediaSourceDao(), db.mediaDao(), key) to
                    db.mediaSourceDao().getSource(key)?.durationMs
            }
            if (episode == null) return@launch
            var lengthMs = exoPlayer.duration
            var waited = 0
            while (lengthMs <= 0 && waited < 20_000) {
                delay(500)
                waited += 500
                lengthMs = exoPlayer.duration
            }
            if (lengthMs <= 0) lengthMs = savedLengthMs ?: return@launch
            val intro = AndroidSkipIntro.lookup(this@PlayerActivity).find(episode.request(lengthMs))
            if (mediaKey.value == key) introTimes.value = intro
        }
    }

    private fun skipIntro(exoPlayer: ExoPlayer) {
        val intro = introTimes.value ?: return
        exoPlayer.seekTo(SkipIntroWindow.seekTarget(intro))
        lastInteractionTimeMs.longValue = System.currentTimeMillis()
    }

    private fun isSkipIntroShowing(): Boolean {
        val intro = introTimes.value ?: return false
        val exoPlayer = player ?: return false
        return exoPlayer.playbackState != androidx.media3.common.Player.STATE_ENDED &&
            SkipIntroWindow.isVisible(intro, exoPlayer.currentPosition, introDismissed.value)
    }

    /**
     * Opens [source] at [startPositionMs] (0 = this copy's own resume point). Used at start and when Next Episode
     * swaps the video in place (F-29).
     */
    private suspend fun loadMedia(exoPlayer: ExoPlayer, source: PlaybackSource, startPositionMs: Long) {
        // Report F-39: the saved resume point is read off the main thread (it used to block it with runBlocking).
        val resumeTarget = if (startPositionMs > 0L) {
            startPositionMs
        } else {
            progressTracker.getResumePosition(source.progressKey)
        }

        // An earlier episode may have had its audio switched off after a decoder failure, or its own track choices.
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, false)
            .clearOverrides()
            .build()
        exoPlayer.setMediaItem(MediaItem.fromUri(source.uri))

        if (resumeTarget > 0L) {
            exoPlayer.seekTo(resumeTarget)
            // Registered before the progress tracker, so a resume point beyond this copy's end is
            // corrected (start over) before any "finished" state can be recorded.
            exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != androidx.media3.common.Player.STATE_READY &&
                        playbackState != androidx.media3.common.Player.STATE_ENDED
                    ) return
                    exoPlayer.removeListener(this)
                    val copyDuration = exoPlayer.duration
                    if (copyDuration != androidx.media3.common.C.TIME_UNSET &&
                        PlaybackTimeUtils.shouldRestartFromBeginning(resumeTarget, copyDuration)
                    ) {
                        android.util.Log.i(TAG, "Resume point is past the end of this copy; starting from the beginning")
                        exoPlayer.seekTo(0L)
                    }
                }
            })
        }
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /**
     * Task L.3: once the copy is ready, saves its resolution, codecs, frame rate, HDR and length on its
     * `media_sources` row (keyed by the progress key, which is the copy's source key), for the details screens.
     */
    private fun saveTechOnce(exoPlayer: ExoPlayer) {
        val key = currentFileId
        if (key.isEmpty() || techSavedFor == key || exoPlayer.playbackState != androidx.media3.common.Player.STATE_READY) return
        val tech = PlayedTech.from(exoPlayer.currentTracks, exoPlayer.duration) ?: return
        techSavedFor = key
        val dao = DriveCinemaDatabase.getInstance(this).mediaSourceDao()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                dao.updatePlayedTech(
                    key, tech.widthPx, tech.heightPx, tech.durationMs, tech.videoCodec,
                    tech.audioCodec, tech.audioChannels, tech.frameRate, tech.isHdr
                )
            }.onFailure { android.util.Log.w(TAG, "Couldn't save the copy's details", it) }
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

    /** Report F-29: Next episode. Moving on counts as having watched this one, even during its ending song (J.1). */
    private fun playNextEpisode() {
        val nextId = nextEpisodeId
        if (nextId.isNullOrEmpty()) return
        switchEpisode(nextId, nextEpisodeTitleState.value, markCurrentWatched = true)
    }

    /** Previous episode; the episode being left keeps its resume point. */
    private fun playPreviousEpisode() {
        val previousId = previousEpisodeId
        if (previousId.isNullOrEmpty()) return
        switchEpisode(previousId, previousEpisodeTitleState.value, markCurrentWatched = false)
    }

    /**
     * Report F-29: plays [targetId] in this player (best copy and resume point, as MainActivity picks them), so the
     * home screen never shows in between. A slow PC connection or no available copy is handed back to MainActivity,
     * which shows its dialog or message as before.
     */
    private fun switchEpisode(targetId: String, targetTitle: String?, markCurrentWatched: Boolean) {
        if (switchingEpisode) return
        val exoPlayer = player ?: return
        switchingEpisode = true
        if (markCurrentWatched && currentFileId.isNotEmpty()) progressTracker.markWatched(exoPlayer, currentFileId)
        lifecycleScope.launch {
            val db = DriveCinemaDatabase.getInstance(this@PlayerActivity)
            val (resolved, following, preceding) = withContext(Dispatchers.IO) {
                Triple(
                    resolver(db).resolve(targetId),
                    NextEpisode.find(db.mediaDao(), targetId),
                    PreviousEpisode.find(db.mediaDao(), targetId)
                )
            }
            val playable = resolved.playableInPlace()
            if (playable == null || isFinishing) {
                handEpisodeBack(targetId, targetTitle)
                return@launch
            }
            if (currentFileId.isNotEmpty()) progressTracker.detach(exoPlayer, currentFileId)
            val source = playable.source
            currentFileId = source.progressKey
            mediaKey.value = source.progressKey
            trackPanel.value = null // the new episode has its own tracks
            currentMediaItemId.value = targetId
            titleState.value = targetTitle ?: titleState.value
            nextEpisodeId = following?.first
            nextEpisodeTitleState.value = following?.second
            previousEpisodeId = preceding?.first
            previousEpisodeTitleState.value = preceding?.second
            isControlsVisible.value = true
            lastInteractionTimeMs.longValue = System.currentTimeMillis()
            loadMedia(exoPlayer, source, playable.resumePositionMs)
            progressTracker.attach(exoPlayer, source.progressKey)
            startIntroLookup(exoPlayer, source.progressKey)
            switchingEpisode = false
        }
    }

    /** The old way: MainActivity resolves the episode again and shows the slow-connection dialog or a message. */
    private fun handEpisodeBack(episodeId: String, episodeTitle: String?) {
        setResult(RESULT_OK, PlayerExit.PlayEpisode(episodeId, episodeTitle).toIntent())
        finish()
    }

    /** Details: MainActivity opens the film's page, or the series' page for an episode. onStop saves the progress. */
    private fun openDetails() {
        val itemId = currentMediaItemId.value
        if (itemId.isNullOrEmpty()) return
        setResult(RESULT_OK, PlayerExit.OpenDetails(itemId).toIntent())
        finish()
    }

    /** Same resolver as MainActivity (local, then a paired PC, then Drive). */
    private fun resolver(db: DriveCinemaDatabase): PlaybackResolver = playbackResolver ?: run {
        val hosts = HostConnections.get(this)
        PlaybackResolver(
            db.mediaSourceDao(),
            hostUrl = { key -> hosts.stream(key, recheck = true)?.first },
            checkHost = { itemId, key -> hosts.checkConnection(itemId, key) }
        ).also { playbackResolver = it }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Gamepad A/B behave like D-pad centre / Back (for both key down and key up, so clicks still work).
        val keyEvent = RemoteKeyMapper.normalize(event)
        android.util.Log.i(TAG, "dispatchKeyEvent: action=${keyEvent.action}, keyCode=${keyEvent.keyCode}, controlsVisible=${isControlsVisible.value}")
        if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
            val keyCode = keyEvent.keyCode

            // Task P.8: an open track panel takes OK and the D-pad; Back closes it (focus returns to its button).
            if (trackPanel.value != null) {
                lastInteractionTimeMs.longValue = System.currentTimeMillis()
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    trackPanel.value = null
                    return true
                }
                if (mediaActionFor(keyCode) == null) {
                    return super.dispatchKeyEvent(keyEvent)
                }
            }

            // 0. Milestone O: with the controls hidden, OK skips the intro and Back hides the button.
            val skipAction = SkipIntroKeys.action(
                isOk = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                    keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                isBack = keyCode == android.view.KeyEvent.KEYCODE_BACK,
                controlsVisible = isControlsVisible.value,
                buttonVisible = isSkipIntroShowing()
            )
            when (skipAction) {
                SkipIntroKeyAction.SKIP -> {
                    player?.let { skipIntro(it) }
                    return true
                }
                SkipIntroKeyAction.HIDE -> {
                    introDismissed.value = true
                    return true
                }
                null -> Unit
            }

            // 1. Dedicated Hardware Media Keys: Play/Pause, Seek, Stop, Next
            val mediaAction = mediaActionFor(keyCode)
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
                    PlayerKeyAction.PreviousEpisode -> playPreviousEpisode()
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

    private fun mediaActionFor(keyCode: Int): PlayerKeyAction? = RemoteKeyMapper.mediaAction(
        keyCode,
        hasNextEpisode = !nextEpisodeId.isNullOrEmpty(),
        hasPreviousEpisode = !previousEpisodeId.isNullOrEmpty()
    )

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
