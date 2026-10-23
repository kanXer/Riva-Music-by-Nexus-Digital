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
    private val http: OkHttpClient = NetworkModule.getOkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** [startMs, line] sorted list ya null (synced nahi mila). */
    suspend fun searchSynced(artist: String, title: String, durationSeconds: Int): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleanArtist = artist.replace(Regex("\\(.*?\\)"), " ").trim()
            val cleanTitle = title.substringBefore("-").trim().ifBlank { title }.trim()
            val encArtist = urlEncoded(cleanArtist)
            val encTitle = urlEncoded(cleanTitle)
            val encQuery = urlEncoded("$cleanArtist $cleanTitle")

            // Ordered attempts: strict -> lenient get -> search. 503 (busy) par retry.
            val attempts = listOf(
                "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTitle&duration=$durationSeconds",
                "https://lrclib.net/api/get?artist_name=$encArtist&track_name=$encTitle",
                "https://lrclib.net/api/search?q=$encQuery",
            )

            for (url in attempts) {
                val element = runCatching { requestWithRetry(url) }.getOrNull() ?: continue
                val candidates = when (element) {
                    is JsonArray -> element.toList()
                    is JsonObject -> listOf(element)
                    else -> emptyList()
                }
                for (item in candidates) {
                    val obj = item as? JsonObject ?: continue
                    val synced = (obj["syncedLyrics"] as? JsonPrimitive)?.contentOrNull
                    if (!synced.isNullOrBlank()) {
                        val lines = parseLrc(synced)
                        if (lines.isNotEmpty()) return@withContext lines
                    }
                }
            }
            null
        }

    /** 503 (server busy) par ek retry, chhota backoff ke saath. */
    private suspend fun requestWithRetry(url: String): JsonElement = try {
        request(url)
    } catch (t: IOException) {
        if (t.message?.contains("503") == true) {
            kotlinx.coroutines.delay(1200)
            request(url)
        } else {
            throw t
        }
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
