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
    val highResThumbnailUrl: String? get() = upgradeThumbnailQuality(thumbnailUrl)
}

fun upgradeThumbnailQuality(url: String?): String? {
    if (url.isNullOrBlank()) return null
    var clean = url
    // Google User Content (YouTube Music high-res album art: 800x800 crisp quality)
    if (clean.contains("googleusercontent.com") || clean.contains("ggpht.com")) {
        clean = clean.replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w800-h800-l90-rj")
            .replace(Regex("=s\\d+[^&?]*"), "=s800-l90-rj")
    }
    // YouTube video thumbnails: upgrade to standard definition or high resolution
    if (clean.contains("i.ytimg.com") || clean.contains("img.youtube.com")) {
        clean = clean.replace("/hqdefault.jpg", "/sddefault.jpg")
            .replace("/mqdefault.jpg", "/sddefault.jpg")
            .replace("/default.jpg", "/sddefault.jpg")
            .replace(Regex("=w\\d+-h\\d+[^&?]*"), "=w800-h800")
            .replace(Regex("=s\\d+[^&?]*"), "=s800")
    }
    return clean
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
    val expiresAt: Long = System.currentTimeMillis() + 30 * 60 * 1000L,
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

val RECORD_LABEL_PATTERNS = listOf(
    "t-series", "tseries", "zee music", "sony music", "sonymusic",
    "tips official", "tips music", "yrf", "saregama", "speed records",
    "geet mp3", "white hill", "desi melodies", "aditya music",
    "lahari music", "times music", "eros now", "svf", "bongo",
    "speed punjabi", "single track studios", "jass records",
    "vevo", "official channel", "records", "music company"
)

fun isRecordLabel(name: String): Boolean {
    val low = name.lowercase().trim()
    return RECORD_LABEL_PATTERNS.any { low.contains(it) }
}

/**
 * Cleans YouTube track titles to remove noise like "Official Video", "| Aashiqui 2",
 * "ft. ...", parentheses, so lyrics providers (LRCLIB / BetterLyrics) find exact matches fast.
 */
fun cleanSongTitle(title: String): String {
    var clean = title
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("\\[.*?\\]"), " ")
        .split('|')[0]
        .split(Regex("(?i)\\b(feat\\.|feat|ft\\.|ft)\\b"))[0]
        .replace(Regex("(?i)\\b(official|lyrics?|video|audio|full song|hd|4k)\\b.*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return clean.ifBlank { title.trim() }
}

/**
 * Cleans YouTube artist strings by removing "Topic", "VEVO", parentheses, extra featured artists.
 */
fun cleanArtistName(artist: String): String {
    var clean = artist
        .replace(Regex("\\(.*?\\)"), " ")
        .replace(Regex("\\[.*?\\]"), " ")
        .split(',')[0]
        .split('&')[0]
        .split(Regex("(?i)\\b(feat\\.|feat|ft\\.|ft)\\b"))[0]
        .replace(Regex("(?i)\\b(topic|vevo|official)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    return clean.ifBlank { artist.trim() }
}

/**
 * Generates ranked candidate queries for LRCLIB search.
 * Handles record labels (e.g. T-Series, Zee Music) by falling back to title,
 * and handles "Movie - Song" / "Song - Movie" patterns.
 */
fun getLyricSearchQueries(artist: String, title: String): List<String> {
    val ca = cleanArtistName(artist)
    val ct = cleanSongTitle(title)
    val isLabel = isRecordLabel(artist) || isRecordLabel(ca)

    val hyphenParts = if (ct.contains('-') || ct.contains('–') || ct.contains('—')) {
        ct.split('-', '–', '—').map { it.trim() }.filter { it.isNotBlank() }
    } else {
        emptyList()
    }

    val queries = mutableListOf<String>()
    if (!isLabel && ca.isNotBlank()) {
        queries += "$ca $ct"
        for (part in hyphenParts) {
            queries += "$ca $part"
        }
    }

    queries += ct
    for (part in hyphenParts) {
        queries += part
    }

    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (q in queries) {
        val norm = q.replace(Regex("\\s+"), " ").trim()
        if (norm.isNotBlank() && seen.add(norm.lowercase())) {
            out += norm
        }
    }
    return out
}
