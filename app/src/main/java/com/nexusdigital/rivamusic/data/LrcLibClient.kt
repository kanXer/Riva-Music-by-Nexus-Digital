package com.nexusdigital.rivamusic.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * LRCLIB (https://lrclib.net) — public synced-lyrics database.
 * REALTIME (time-synced) lyrics ke liye: LRC format "[mm:ss.xx] line".
 * YouTube Music plain shelf se better realtime experience deti hai;
 * available nahi ho to MusicRepository plain lyrics fallback use karta hai.
 */
class LrcLibClient(
    private val http: OkHttpClient = NetworkModule.getOkHttpClient().newBuilder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** [startMs, line] sorted list ya null (synced nahi mila). */
    suspend fun searchSynced(artist: String, title: String, durationSeconds: Int): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleanArtist = cleanArtistName(artist)
            val cleanTitle = cleanSongTitle(title)
            val encArtist = urlEncoded(cleanArtist)
            val encTitle = urlEncoded(cleanTitle)
            val encQuery = urlEncoded("$cleanArtist $cleanTitle")

            // 1. Strict GET with duration match
            if (durationSeconds > 0) {
                val strictUrl = "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTitle&duration=$durationSeconds"
                val strictEl = runCatching { request(strictUrl) }.getOrNull()
                if (strictEl is JsonObject) {
                    val synced = (strictEl["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
                    if (!synced.isNullOrBlank()) {
                        val lines = parseLrc(synced)
                        if (lines.isNotEmpty()) return@withContext lines
                    }
                }
            }

            // 2. Lenient GET verified by duration
            val lenientUrl = "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTitle"
            val lenientEl = runCatching { request(lenientUrl) }.getOrNull()
            if (lenientEl is JsonObject) {
                val candidateDur = (lenientEl["duration"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
                val durDiff = if (durationSeconds > 0) kotlin.math.abs(candidateDur - durationSeconds) else 0.0
                if (durDiff <= 6.0) {
                    val synced = (lenientEl["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
                    if (!synced.isNullOrBlank()) {
                        val lines = parseLrc(synced)
                        if (lines.isNotEmpty()) return@withContext lines
                    }
                }
            }

            // 3. Search: sort candidates by closest duration to avoid live/remix mismatches
            val searchUrl = "https://lrclib.net/api/search?q=$encQuery"
            val searchEl = runCatching { request(searchUrl) }.getOrNull()
            if (searchEl is JsonArray) {
                val candidates = searchEl.mapNotNull { it as? JsonObject }
                    .filter { obj ->
                        val synced = (obj["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
                        !synced.isNullOrBlank()
                    }

                val bestCandidate = if (durationSeconds > 0) {
                    candidates.minByOrNull { obj ->
                        val dur = (obj["duration"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
                        kotlin.math.abs(dur - durationSeconds)
                    }
                } else {
                    candidates.firstOrNull()
                }

                if (bestCandidate != null) {
                    val candDur = (bestCandidate["duration"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0
                    val durDiff = if (durationSeconds > 0) kotlin.math.abs(candDur - durationSeconds) else 0.0
                    if (durDiff <= 8.0 || durationSeconds <= 0) {
                        val synced = (bestCandidate["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
                        if (!synced.isNullOrBlank()) {
                            val lines = parseLrc(synced)
                            if (lines.isNotEmpty()) return@withContext lines
                        }
                    }
                }
            }
            null
        }

    private fun request(url: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "RivaMusic/1.0 (Android)")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("LRCLIB HTTP ${response.code}")
            val text = response.body?.string() ?: throw IOException("Empty LRCLIB response")
            return json.parseToJsonElement(text)
        }
    }

    private fun parseLrc(raw: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]")
        raw.lineSequence().forEach { line ->
            var rest = line.trim()
            val stamps = mutableListOf<Long>()
            while (true) {
                val match = stamp.matchAt(rest, 0) ?: break
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val fracMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                stamps.add(minutes * 60_000 + seconds * 1_000 + fracMs)
                rest = rest.substring(match.value.length).trimStart()
            }
            val text = rest.trim()
            if (text.isEmpty()) return@forEach
            for (startMs in stamps) out.add(LyricLine(startMs = startMs, text = text))
        }
        return out.sortedBy { it.startMs }
    }

    private fun urlEncoded(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    companion object {
        private fun defaultHttpClient(): OkHttpClient = NetworkModule.getOkHttpClient()
    }
}
