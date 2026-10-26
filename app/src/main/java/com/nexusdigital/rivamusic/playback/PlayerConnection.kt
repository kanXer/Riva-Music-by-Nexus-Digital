package com.nexusdigital.rivamusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nexusdigital.rivamusic.data.PlaybackPhase
import com.nexusdigital.rivamusic.data.Track
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val hasMedia: Boolean = false,
)

sealed interface PlaybackEvent {
    data object Ended : PlaybackEvent
    data object SkipNext : PlaybackEvent
    data object SkipPrevious : PlaybackEvent
    data object Stop : PlaybackEvent
    data class Error(val message: String) : PlaybackEvent
}

class PlayerConnection(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
    ).buildAsync()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var pendingPlay: Pair<Uri, Track>? = null
    private var pendingSeekTarget: Long? = null
    private var lastSeekTimestamp: Long = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                pendingSeekTarget = null
            }
            controller?.let(::sync)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                if (controller?.currentMediaItem != null) {
                    _events.tryEmit(PlaybackEvent.Ended)
                }
            }
            controller?.let(::sync)
        }

        override fun onPlayerError(error: PlaybackException) {
            _events.tryEmit(PlaybackEvent.Error(error.message ?: "Playback failed"))
            controller?.let(::sync)
        }
    }

    init {
        PlaybackService.playbackEventListener = { event ->
            _events.tryEmit(event)
        }
        controllerFuture.addListener(
            {
                runCatching {
                    controllerFuture.get().also { c ->
                        controller = c
                        c.addListener(listener)
                        _state.update { it.copy(connected = true) }
                        sync(c)
                        pendingPlay?.let { (uri, track) ->
                            pendingPlay = null
                            applyMedia(c, uri, track)
                        }
                        startTicker()
                    }
                }.onFailure {
                    _events.tryEmit(PlaybackEvent.Error("Player connect failed: ${it.message}"))
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun play(uri: Uri, track: Track) {
        val c = controller
        if (c == null) {
            pendingPlay = uri to track
            return
        }
        applyMedia(c, uri, track)
    }

    fun play(track: Track, url: String) {
        val uri = if (track.isDownloaded && track.localPath != null) {
            Uri.fromFile(File(track.localPath))
        } else {
            url.toUri()
        }
        play(uri, track)
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.currentMediaItem == null) return
        when {
            c.isPlaying -> c.pause()
            c.playbackState == Player.STATE_ENDED -> {
                c.seekTo(0)
                c.play()
            }
            else -> c.play()
        }
        sync(c)
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        pendingSeekTarget = target
        lastSeekTimestamp = System.currentTimeMillis()
        _state.update { it.copy(positionMs = target) }
        controller?.seekTo(target)
    }

    fun replay() {
        val c = controller ?: return
        c.seekTo(0)
        c.play()
        sync(c)
    }

    fun release() {
        PlaybackService.playbackEventListener = null
        ticker?.cancel()
        controller?.removeListener(listener)
        if (controllerFuture.isDone) {
            runCatching { controllerFuture.get().release() }
        }
        controller = null
        scope.cancel()
    }

    private fun applyMedia(c: MediaController, uri: Uri, track: Track) {
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(track.thumbnailUrl?.toUri())
                    .build(),
            )
            .build()

        c.setMediaItem(item)
        c.prepare()
        c.play()
        sync(c)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                controller?.let(::sync)
                val isPlaying = controller?.isPlaying == true
                delay(if (isPlaying) 100L else 500L)
            }
        }
    }

    private fun sync(c: Player) {
        runCatching {
            val item = c.currentMediaItem
            val metadata = item?.mediaMetadata
            val duration = c.duration.takeIf { it > 0L } ?: 0L
            val phase = when {
                c.playerError != null -> PlaybackPhase.ERROR
                c.playbackState == Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
                c.playbackState == Player.STATE_ENDED -> PlaybackPhase.ENDED
                c.isPlaying -> PlaybackPhase.PLAYING
                c.playbackState == Player.STATE_READY -> PlaybackPhase.PAUSED
                else -> PlaybackPhase.IDLE
            }

            val currentPos = c.currentPosition.coerceAtLeast(0L)
            val effectivePos = if (pendingSeekTarget != null) {
                if (System.currentTimeMillis() - lastSeekTimestamp < 1500L && kotlin.math.abs(currentPos - pendingSeekTarget!!) > 2000L) {
                    pendingSeekTarget!!
                } else {
                    pendingSeekTarget = null
                    currentPos
                }
            } else {
                currentPos
            }

            _state.value = PlayerUiState(
                connected = true,
                isPlaying = c.isPlaying,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                phase = phase,
                mediaId = item?.mediaId,
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                artworkUrl = metadata?.artworkUri?.toString(),
                durationMs = duration,
                positionMs = effectivePos,
                bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
                hasMedia = item != null,
            )
        }
    }
}
