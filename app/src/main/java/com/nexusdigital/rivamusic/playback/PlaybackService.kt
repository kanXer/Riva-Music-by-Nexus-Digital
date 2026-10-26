package com.nexusdigital.rivamusic.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.nexusdigital.rivamusic.MainActivity
import com.nexusdigital.rivamusic.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val closeCommand = SessionCommand(ACTION_CLOSE, Bundle.EMPTY)
    private val closeButton = CommandButton.Builder()
        .setDisplayName("Close")
        .setSessionCommand(closeCommand)
        .setIconResId(R.drawable.ic_notif_close)
        .setCustomIconResId(R.drawable.ic_notif_close)
        .build()

    private val outputSpeakerCommand = SessionCommand(ACTION_OUTPUT_SPEAKER, Bundle.EMPTY)
    private val outputSpeakerButton = CommandButton.Builder()
        .setDisplayName("Output")
        .setSessionCommand(outputSpeakerCommand)
        .setIconResId(R.drawable.ic_notif_speaker)
        .setCustomIconResId(R.drawable.ic_notif_speaker)
        .build()

    private val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)
    private val nextButton = CommandButton.Builder()
        .setDisplayName("Next")
        .setSessionCommand(nextCommand)
        .setIconResId(R.drawable.ic_notif_next)
        .setCustomIconResId(R.drawable.ic_notif_next)
        .build()

    private val prevCommand = SessionCommand(ACTION_PREV, Bundle.EMPTY)
    private val prevButton = CommandButton.Builder()
        .setDisplayName("Previous")
        .setSessionCommand(prevCommand)
        .setIconResId(R.drawable.ic_notif_prev)
        .setCustomIconResId(R.drawable.ic_notif_prev)
        .build()

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = SessionCommands.Builder()
                .add(closeCommand)
                .add(outputSpeakerCommand)
                .add(nextCommand)
                .add(prevCommand)
                .build()

            val playerCommands = Player.Commands.Builder()
                .addAll(session.player.availableCommands)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_BACK)
                .add(Player.COMMAND_SEEK_FORWARD)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(ImmutableList.of(closeButton, outputSpeakerButton))
                .build()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int {
            when (playerCommand) {
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                    playbackEventListener?.invoke(PlaybackEvent.SkipNext)
                    return SessionResult.RESULT_SUCCESS
                }
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                    playbackEventListener?.invoke(PlaybackEvent.SkipPrevious)
                    return SessionResult.RESULT_SUCCESS
                }
            }
            return super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_CLOSE -> {
                    closePlayback()
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS),
                    )
                }
                ACTION_OUTPUT_SPEAKER -> {
                    openMediaOutputSwitcher()
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS),
                    )
                }
                ACTION_NEXT -> {
                    playbackEventListener?.invoke(PlaybackEvent.SkipNext)
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS),
                    )
                }
                ACTION_PREV -> {
                    playbackEventListener?.invoke(PlaybackEvent.SkipPrevious)
                    return com.google.common.util.concurrent.Futures.immediateFuture(
                        SessionResult(SessionResult.RESULT_SUCCESS),
                    )
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to USER_AGENT,
                    "Referer" to "https://www.youtube.com/",
                    "Origin" to "https://www.youtube.com",
                ),
            )

        // DefaultDataSource delegates http/https to the factory above and also
        // supports file:// (offline downloads) and content:// schemes.
        val upstreamDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // LRU Media Cache: previously streamed chunks/songs are cached locally.
        // Replay and backward seeking are instantaneous (0ms network delay).
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getMediaCache(this))
            .setUpstreamDataSourceFactory(upstreamDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Ultra-fast playback startup: begins playback with 1s buffer
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12_000, // minBufferMs
                45_000, // maxBufferMs
                1_000,  // bufferForPlaybackMs (1s instead of 2.5s)
                2_000,  // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val forwardingPlayer = RivaForwardingPlayer(player)

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .setCustomLayout(ImmutableList.of(closeButton, outputSpeakerButton))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun openMediaOutputSwitcher() {
        openMediaOutputSwitcher(this)
    }

    private fun closePlayback() {
        playbackEventListener?.invoke(PlaybackEvent.Stop)
        mediaSession?.let { session ->
            val player = session.player
            player.stop()
            player.clearMediaItems()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val USER_AGENT =
            "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
        const val ACTION_CLOSE = "com.nexusdigital.rivamusic.ACTION_CLOSE"
        const val ACTION_OUTPUT_SPEAKER = "com.nexusdigital.rivamusic.ACTION_OUTPUT_SPEAKER"
        const val ACTION_NEXT = "com.nexusdigital.rivamusic.ACTION_NEXT"
        const val ACTION_PREV = "com.nexusdigital.rivamusic.ACTION_PREV"

        fun openMediaOutputSwitcher(context: Context) {
            try {
                val panelIntent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                }
                context.startActivity(panelIntent)
            } catch (e: Exception) {
                try {
                    val soundIntent = Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(soundIntent)
                } catch (ignored: Exception) {
                    try {
                        val btIntent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(btIntent)
                    } catch (ignored2: Exception) {}
                }
            }
        }

        var playbackEventListener: ((PlaybackEvent) -> Unit)? = null

        @Volatile
        private var mediaCache: SimpleCache? = null

        @Synchronized
        fun getMediaCache(context: Context): SimpleCache {
            return mediaCache ?: run {
                val cacheDir = File(context.applicationContext.cacheDir, "exoplayer_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024L) // 150MB media cache
                val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
                SimpleCache(cacheDir, evictor, databaseProvider).also { mediaCache = it }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private class RivaForwardingPlayer(player: Player) : androidx.media3.common.ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun seekTo(positionMs: Long) {
        super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekToNext() {
        PlaybackService.playbackEventListener?.invoke(PlaybackEvent.SkipNext)
    }

    override fun seekToNextMediaItem() {
        PlaybackService.playbackEventListener?.invoke(PlaybackEvent.SkipNext)
    }

    override fun seekToPrevious() {
        PlaybackService.playbackEventListener?.invoke(PlaybackEvent.SkipPrevious)
    }

    override fun seekToPreviousMediaItem() {
        PlaybackService.playbackEventListener?.invoke(PlaybackEvent.SkipPrevious)
    }
}
