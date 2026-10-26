package com.nexusdigital.rivamusic.data

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * BetterLyrics client (https://betterlyrics.org)
 * Provides syllable-synced and word-synced lyrics in TTML format.
 * Returns sorted [LyricLine] for real-time line highlighting & seeking.
 */
class BetterLyricsClient(
    private val http: OkHttpClient = NetworkModule.getOkHttpClient().newBuilder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchSynced(artist: String, title: String, durationSeconds: Int): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleanArtist = cleanArtistName(artist)
            val cleanTitle = cleanSongTitle(title)

            val attempts = mutableListOf<String>()
            if (durationSeconds > 0) {
                attempts += buildUrl(cleanTitle, cleanArtist, durationSeconds)
            }
            attempts += buildUrl(cleanTitle, cleanArtist, null)

            for (url in attempts) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept", "application/json")
                        .build()

                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val text = response.body?.string().orEmpty()
                        if (text.isBlank()) return@use

                        val root = json.parseToJsonElement(text) as? JsonObject ?: return@use
                        val ttml = (root["ttml"] as? JsonPrimitive)?.contentOrNull ?: return@use
                        val lines = parseTtml(ttml)
                        if (lines.isNotEmpty()) {
                            return@withContext lines
                        }
                    }
                } catch (e: Exception) {
                    // Try next attempt
                }
            }
            null
        }

    private fun buildUrl(song: String, artist: String, duration: Int?): String {
        val s = URLEncoder.encode(song, StandardCharsets.UTF_8.name())
        val a = URLEncoder.encode(artist, StandardCharsets.UTF_8.name())
        return if (duration != null && duration > 0) {
            "https://api.betterlyrics.org/getLyrics?s=$s&a=$a&d=$duration"
        } else {
            "https://api.betterlyrics.org/getLyrics?s=$s&a=$a"
        }
    }

    private fun parseTtml(ttml: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        // Match <p begin="..." ...> text </p>
        val pRegex = Regex("""<p\s+[^>]*begin="([^"]+)"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
        val tagRegex = Regex("""<[^>]+>""")

        for (match in pRegex.findAll(ttml)) {
            val beginStr = match.groupValues[1].trim()
            val rawContent = match.groupValues[2]
            val text = tagRegex.replace(rawContent, "").trim()
            if (text.isBlank()) continue

            val ms = parseTimestampMs(beginStr)
            if (ms >= 0L) {
                out += LyricLine(startMs = ms, text = text)
            }
        }
        return out.sortedBy { it.startMs }
    }

    private fun parseTimestampMs(begin: String): Long {
        // Formats: "00:15.200", "01:23:45.600", or seconds float "15.200" or "15.200s"
        val clean = begin.removeSuffix("s").trim()
        val secFloat = clean.toDoubleOrNull()
        if (secFloat != null) {
            return (secFloat * 1000.0).toLong()
        }

        val parts = clean.split(":")
        return when (parts.size) {
            2 -> {
                val min = parts[0].toLongOrNull() ?: 0L
                val secParts = parts[1].split(".")
                val sec = secParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val fracStr = secParts.getOrNull(1).orEmpty().padEnd(3, '0').take(3)
                val frac = fracStr.toLongOrNull() ?: 0L
                min * 60_000L + sec * 1000L + frac
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: 0L
                val min = parts[1].toLongOrNull() ?: 0L
                val secParts = parts[2].split(".")
                val sec = secParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val fracStr = secParts.getOrNull(1).orEmpty().padEnd(3, '0').take(3)
                val frac = fracStr.toLongOrNull() ?: 0L
                hr * 3600_000L + min * 60_000L + sec * 1000L + frac
            }
            else -> -1L
        }
    }
}
