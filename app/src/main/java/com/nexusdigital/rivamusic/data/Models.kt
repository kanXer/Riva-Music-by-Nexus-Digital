package com.nexusdigital.rivamusic.data

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int = 0,
    val thumbnailUrl: String? = null,
    val localPath: String? = null,
) {
    val durationMs: Long get() = durationSeconds * 1000L
    val isDownloaded: Boolean get() = !localPath.isNullOrBlank()
}

@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
)

@Serializable
data class DownloadedTrack(
    val track: Track,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long,
    /**
     * Non-zero => auto-cached because user played this track (kept for CACHE_TTL).
     * Zero => user saved it manually (permanent until deleted).
     */
    val cachedAt: Long = 0L,
) {
    val isCached: Boolean get() = cachedAt > 0L
}

sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(val progress: Float, val bytes: Long, val totalBytes: Long) : DownloadStatus
    data object Completed : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

data class StreamResolution(
    val url: String,
    val mimeType: String,
    val itag: Int,
    val userAgent: String,
    val headers: Map<String, String>,
    val expiresAt: Long = System.currentTimeMillis() + 3 * 60 * 60 * 1000L,
)

enum class PlaybackPhase {
    IDLE,
    RESOLVING,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR,
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

/** Ek synced lyrics line (LRCLIB LRC se) — startMs = kab se dikhna shuru. */
data class LyricLine(
    val startMs: Long,
    val text: String,
)
