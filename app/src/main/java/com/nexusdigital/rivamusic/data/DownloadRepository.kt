package com.nexusdigital.rivamusic.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val http = NetworkModule.getOkHttpClient(appContext).newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val downloadsDir = File(appContext.filesDir, "downloads").apply { mkdirs() }

    private val _downloads = MutableStateFlow(loadDownloads())
    val downloads: StateFlow<List<DownloadedTrack>> = _downloads.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadStatus>> = _progress.asStateFlow()

    fun localPath(trackId: String): String? =
        _downloads.value.firstOrNull { it.track.id == trackId }?.filePath
            ?.takeIf { File(it).exists() }

    fun isDownloaded(trackId: String): Boolean = localPath(trackId) != null

    fun download(track: Track, resolution: StreamResolution, cache: Boolean = false) {
        if (isDownloaded(track.id)) return
        if (jobs[track.id]?.isActive == true) return

        val cachedAt = if (cache) System.currentTimeMillis() else 0L
        val job = scope.launch {
            val temp = File(downloadsDir, "${safeName(track.id)}.part")
            val target = File(downloadsDir, "${safeName(track.id)}.mp4")
            try {
                // Silent cache (30-day auto) UI me progress NAHI dikhta —
                // "Downloading" section sirf manual saves ke liye.
                if (!cache) {
                    _progress.update { it + (track.id to DownloadStatus.Downloading(0f, 0, -1)) }
                }
                temp.delete()
                val request = Request.Builder()
                    .url(resolution.url)
                    .header("User-Agent", resolution.userAgent.ifBlank { DEFAULT_UA })
                    .header("Referer", "https://www.youtube.com/")
                    .header("Origin", "https://www.youtube.com")
                    .build()

                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty response")
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    var bytes = 0L
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                bytes += read
                                val progress = if (total > 0) {
                                    (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                if (!cache) {
                                    _progress.update {
                                        it + (track.id to DownloadStatus.Downloading(progress, bytes, total))
                                    }
                                }
                            }
                        }
                    }
                    if (bytes <= 0L) throw IOException("Empty download")
                }

                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }

                val storedTrack = track.copy(localPath = target.absolutePath)
                val entry = DownloadedTrack(
                    track = storedTrack,
                    filePath = target.absolutePath,
                    sizeBytes = target.length(),
                    downloadedAt = System.currentTimeMillis(),
                    cachedAt = cachedAt,
                )
                _downloads.update { current ->
                    listOf(entry) + current.filterNot { it.track.id == track.id }
                }
                persist()
                _progress.update { it + (track.id to DownloadStatus.Completed) }
            } catch (t: Throwable) {
                temp.delete()
                _progress.update {
                    it + (track.id to DownloadStatus.Failed(t.message ?: "Download failed"))
                }
            } finally {
                jobs.remove(track.id)
            }
        }
        jobs[track.id] = job
    }

    fun delete(trackId: String) {
        val entry = _downloads.value.firstOrNull { it.track.id == trackId } ?: return
        File(entry.filePath).delete()
        _downloads.update { current -> current.filterNot { it.track.id == trackId } }
        _progress.update { it - trackId }
        persist()
    }

    /**
     * Auto-cache from a stream already resolved during playback.
     * Manual (permanent) offline entries are skipped entirely —
     * "agar offline save ho to uska cache naa save ho".
     * Existing cache entries just get their 30-day window refreshed.
     */
    fun autoCache(track: Track, resolution: StreamResolution) {
        val existing = _downloads.value.firstOrNull { it.track.id == track.id }
        if (existing != null) {
            if (existing.isCached) touchCached(track.id)
            return // manual/poor entry left as-is; manual save stays permanent
        }
        if (jobs[track.id]?.isActive == true) return
        download(track, resolution, cache = true)
    }

    /**
     * Resolves and caches a track (e.g. from Recently Played) in the background.
     */
    fun cacheTrackIfNeeded(track: Track, music: MusicRepository) {
        if (isDownloaded(track.id)) return
        if (jobs[track.id]?.isActive == true) return
        scope.launch(Dispatchers.IO) {
            try {
                val resolution = music.resolveAudio(track)
                download(track, resolution, cache = true)
            } catch (e: Exception) {
                android.util.Log.w("RivaCache", "Failed to cache track ${track.title}: ${e.message}")
            }
        }
    }

    fun touchCached(trackId: String) {
        _downloads.update { current ->
            current.map { entry ->
                if (entry.track.id == trackId && entry.isCached) {
                    entry.copy(cachedAt = System.currentTimeMillis())
                } else {
                    entry
                }
            }
        }
        persist()
    }

    /** Drops an entry whose local file no longer exists (stale cache). */
    fun removeBrokenEntry(trackId: String) {
        val entry = _downloads.value.firstOrNull { it.track.id == trackId } ?: return
        if (File(entry.filePath).exists()) return
        _downloads.update { current -> current.filterNot { it.track.id == trackId } }
        _progress.update { it - trackId }
        persist()
    }

    /** Converts a 30-day cache entry into a permanent manual offline save. */
    fun promoteToManual(trackId: String): Boolean {
        val entry = _downloads.value.firstOrNull { it.track.id == trackId } ?: return false
        if (!File(entry.filePath).exists()) return false
        _downloads.update { current ->
            current.map { item ->
                if (item.track.id == trackId) item.copy(cachedAt = 0L) else item
            }
        }
        _progress.update { it - trackId }
        persist()
        return true
    }

    fun isFreshCache(entry: DownloadedTrack, nowMs: Long = System.currentTimeMillis()): Boolean =
        entry.isCached && nowMs - entry.cachedAt <= CACHE_TTL_MS

    fun clearCompletedStatus(trackId: String) {
        _progress.update { current ->
            if (current[trackId] is DownloadStatus.Completed ||
                current[trackId] is DownloadStatus.Failed
            ) {
                current - trackId
            } else {
                current
            }
        }
    }

    private fun loadDownloads(): List<DownloadedTrack> {
        val nowMs = System.currentTimeMillis()
        val raw = prefs.getString(KEY_DOWNLOADS, null) ?: return emptyList()
        val stored = runCatching {
            json.decodeFromString<List<DownloadedTrack>>(raw)
        }.getOrElse { emptyList() }

        val valid = stored.mapNotNull { entry ->
            val currentFile = File(entry.filePath)
            if (!currentFile.exists()) return@mapNotNull null
            entry.copy(
                track = entry.track.copy(localPath = currentFile.absolutePath),
                filePath = currentFile.absolutePath,
                sizeBytes = currentFile.length(),
            )
        }
        if (valid.size != stored.size) {
            val purged = valid.filterNot { entry ->
                val expired = entry.cachedAt > 0L && nowMs - entry.cachedAt > CACHE_TTL_MS
                // deleted (non-cache) entries are also gone; expired cache entries just
                // auto-purge silently to reclaim storage
                expired && File(entry.filePath).delete()
                expired
            }
            if (purged.size != valid.size) {
                prefs.edit().putString(KEY_DOWNLOADS, json.encodeToString(purged)).apply()
            }
            return purged
        }
        return valid
    }

    private fun persist() {
        prefs.edit()
            .putString(KEY_DOWNLOADS, json.encodeToString(_downloads.value))
            .apply()
    }

    private fun safeName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { System.currentTimeMillis().toString() }

    companion object {
        private const val PREFS = "riva_downloads"
        private const val KEY_DOWNLOADS = "items"
        private const val DEFAULT_UA =
            "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
        const val CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000L // 30 days
    }
}
