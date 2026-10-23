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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Small InnerTube client modelled after the requests used by Musify.
 *
 * Search uses the public WEB client. Playback intentionally prefers the
 * ANDROID client because, at the time of writing, it is the client that still
 * returns a directly playable muxed MP4 URL (itag 18) without a PO token.
 */
class InnerTubeClient(
    private val http: OkHttpClient = NetworkModule.getOkHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun search(query: String, limit: Int = 20): List<Track> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("context", webContext())
            put("query", JsonPrimitive(query))
        }

        val request = Request.Builder()
            .url("$YOUTUBE_BASE/youtubei/v1/search?key=$WEB_KEY&prettyPrint=false")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_UA)
            .header("Origin", "https://www.youtube.com")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", WEB_VERSION)
            .build()

        val responseText = execute(request)
        val root = json.parseToJsonElement(responseText)
        val tracks = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["videoRenderer"]?.asObjectOrNull()?.let { renderer ->
                        renderer.toTrack()?.let { track ->
                            if (seen.add(track.id)) tracks += track
                        }
                    }
                    element.values.forEach(::visit)
                }
                is JsonArray -> element.forEach(::visit)
                else -> Unit
            }
        }

        visit(root)
        tracks.take(limit)
    }

    suspend fun fetchPlaylist(playlistIdOrUrl: String): Pair<String, List<Track>> = withContext(Dispatchers.IO) {
        val pid = extractPlaylistId(playlistIdOrUrl)
        val browseId = if (pid.startsWith("VL")) pid else "VL$pid"

        val body = buildJsonObject {
            put("context", webContext())
            put("browseId", JsonPrimitive(browseId))
        }

        val request = Request.Builder()
            .url("$YOUTUBE_BASE/youtubei/v1/browse?key=$WEB_KEY&prettyPrint=false")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_UA)
            .header("Origin", "https://www.youtube.com")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", WEB_VERSION)
            .build()

        val responseText = execute(request)
        val root = json.parseToJsonElement(responseText)

        var title: String? = null
        fun findTitle(e: JsonElement) {
            if (title != null) return
            when (e) {
                is JsonObject -> {
                    val header = e["playlistHeaderRenderer"]?.asObjectOrNull()
                        ?: e["pageHeaderRenderer"]?.asObjectOrNull()
                    if (header != null) {
                        val t = header["title"]?.asTextRuns()
                            ?: header["pageTitle"]?.asTextRuns()
                            ?: header["title"]?.asSimpleText()
                        if (!t.isNullOrBlank()) {
                            title = t
                            return
                        }
                    }
                    e.values.forEach(::findTitle)
                }
                is JsonArray -> e.forEach(::findTitle)
                else -> Unit
            }
        }
        findTitle(root)

        val tracks = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["playlistVideoRenderer"]?.asObjectOrNull()?.let { renderer ->
                        renderer.toPlaylistVideoTrack()?.let { track ->
                            if (seen.add(track.id)) tracks += track
                        }
                    }
                    element["musicResponsiveListItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                        renderer.toMusicTrack()?.let { track ->
                            if (seen.add(track.id)) tracks += track
                        }
                    }
                    element["videoRenderer"]?.asObjectOrNull()?.let { renderer ->
                        renderer.toTrack()?.let { track ->
                            if (seen.add(track.id)) tracks += track
                        }
                    }
                    element.values.forEach(::visit)
                }
                is JsonArray -> element.forEach(::visit)
                else -> Unit
            }
        }
        visit(root)

        if (tracks.isEmpty()) {
            throw IOException("Playlist me koi gaane nahi mile ya link galat hai")
        }

        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: "YouTube Playlist"
        resolvedTitle to tracks
    }

    /**
     * Lyrics preview from same song page (WEB_REMIX = YT Music client):
     * /next se LYRICS tab browses id (MPLYt...), /browse se text lines.
     */
    suspend fun fetchLyrics(videoId: String): LyricsResult = withContext(Dispatchers.IO) {
        val nextBody = buildJsonObject {
            put("context", musicRemixContext())
            put("videoId", JsonPrimitive(videoId))
        }
        val nextText = execute(
            Request.Builder()
                .url("$MUSIC_BASE/youtubei/v1/next?prettyPrint=false")
                .post(nextBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("User-Agent", WEB_UA)
                .header("Origin", "https://music.youtube.com")
                .build(),
        )
        val nextRoot = json.parseToJsonElement(nextText)

        val browseId = findLyricsBrowseId(nextRoot)
            ?: throw IOException("Lyrics available nahi hai")

        val browseBody = buildJsonObject {
            put("context", musicRemixContext())
            put("browseId", JsonPrimitive(browseId))
        }
        val browseText = execute(
            Request.Builder()
                .url("$MUSIC_BASE/youtubei/v1/browse?prettyPrint=false")
                .post(browseBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("User-Agent", WEB_UA)
                .header("Origin", "https://music.youtube.com")
                .build(),
        )
        val lines = collectLyricsText(json.parseToJsonElement(browseText))
        if (lines.isEmpty()) throw IOException("Lyrics nahi mile")
        LyricsResult(lines = lines)
    }

    private fun findLyricsBrowseId(element: JsonElement): String? {
        when (element) {
            is JsonPrimitive -> {
                // lyrics browse ids always start with MPLYt
                val text = element.contentOrNull
                if (text != null && text.startsWith("MPLYt")) return text
            }
            is JsonObject -> element.values.forEach { child ->
                findLyricsBrowseId(child)?.let { return it }
            }
            is JsonArray -> element.forEach { child ->
                findLyricsBrowseId(child)?.let { return it }
            }
        }
        return null
    }

    private fun collectLyricsText(element: JsonElement): List<String> {
        val shelf = firstDescriptionShelf(element) ?: return emptyList()
        val runs = firstDescriptionDesc(shelf)?.let { runTexts(it) } ?: return emptyList()
        return runs.joinToString("")
            .split("\n")
            .map { it.trim() }
            .let { pieces -> pieces.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() } }
    }

    // musicDescriptionShelfRenderer.description runs texts.
    private fun runTexts(obj: JsonObject): List<String> {
        val out = mutableListOf<String>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    e["text"]?.asStringOrNull()?.let { out += it }
                    e.values.forEach(::walk)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(obj)
        return out
    }

    private fun firstDescriptionShelf(element: JsonElement): JsonObject? {
        when (element) {
            is JsonObject -> {
                val shelf = element["musicDescriptionShelfRenderer"].asObjectOrNull()
                if (shelf != null) return shelf
                element.values.forEach { child ->
                    firstDescriptionShelf(child)?.let { return it }
                }
            }
            is JsonArray -> element.forEach { child ->
                firstDescriptionShelf(child)?.let { return it }
            }
            else -> Unit
        }
        return null
    }

    private fun firstDescriptionDesc(shelf: JsonObject): JsonObject? = shelf["description"].asObjectOrNull()

    suspend fun resolveAudio(videoId: String): StreamResolution = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        for (client in playbackClients) {
            try {
                val resolution = requestPlayer(videoId, client)
                if (resolution != null) return@withContext resolution
            } catch (t: Throwable) {
                errors += "${client.name}: ${t.message ?: t.javaClass.simpleName}"
            }
        }

        throw IOException(
            buildString {
                append("YouTube stream resolve nahi hua. ")
                if (errors.isNotEmpty()) append(errors.joinToString(" | "))
            },
        )
    }

    private fun requestPlayer(videoId: String, client: PlaybackClient): StreamResolution? {
        val body = buildJsonObject {
            put("context", client.context)
            put("videoId", JsonPrimitive(videoId))
            put("contentCheckOk", JsonPrimitive(true))
            put("racyCheckOk", JsonPrimitive(true))
        }

        val request = Request.Builder()
            .url(client.url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("X-Youtube-Client-Name", client.name)
            .header("X-Youtube-Client-Version", client.version)
            .build()

        val root = json.parseToJsonElement(execute(request)).asObjectOrNull() ?: return null
        val status = root["playabilityStatus"].asObjectOrNull()
        val playability = status?.get("status")?.asStringOrNull()
        if (playability != null && playability != "OK") {
            val reason = status["reason"].asStringOrNull() ?: playability
            throw IOException(reason)
        }

        val streaming = root["streamingData"].asObjectOrNull() ?: return null
        val formats = buildList {
            streaming["adaptiveFormats"].asArrayOrNull()?.forEach { add(it.asObjectOrNull()) }
            streaming["formats"].asArrayOrNull()?.forEach { add(it.asObjectOrNull()) }
        }.filterNotNull()

        val audioOnly = formats
            .filter { it.isAudioOnly() && it["url"].asStringOrNull() != null }
            .maxByOrNull { it["bitrate"].asLongOrNull() ?: 0L }

        val muxedAudio = formats
            .filter { it.hasAudio() && it["url"].asStringOrNull() != null }
            .maxByOrNull { it["bitrate"].asLongOrNull() ?: 0L }

        val chosen = audioOnly ?: muxedAudio ?: return null

        val url = chosen["url"].asStringOrNull() ?: return null
        return StreamResolution(
            url = url,
            mimeType = chosen["mimeType"].asStringOrNull() ?: "audio/mp4",
            itag = chosen["itag"].asIntOrNull() ?: 0,
            userAgent = client.userAgent,
            headers = mapOf(
                "User-Agent" to client.userAgent,
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com",
            ),
        )
    }

    private fun JsonObject.isAudioOnly(): Boolean =
        this["mimeType"].asStringOrNull()?.startsWith("audio/") == true

    private fun JsonObject.hasAudio(): Boolean {
        val mime = this["mimeType"].asStringOrNull() ?: return false
        return mime.startsWith("audio/") || mime.contains("mp4a") || mime.contains("opus")
    }

    private fun JsonObject.toTrack(): Track? {
        val id = this["videoId"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val title = this["title"].asTextRuns()?.takeIf { it.isNotBlank() } ?: return null
        val artist = this["ownerText"].asTextRuns()
            ?: this["longBylineText"].asTextRuns()
            ?: "YouTube"
        val duration = parseDuration(this["lengthText"].asSimpleText())
        val thumb = this["thumbnail"]
            .asObjectOrNull()
            ?.get("thumbnails")
            .asArrayOrNull()
            ?.lastOrNull()
            .asObjectOrNull()
            ?.get("url")
            .asStringOrNull()

        if (duration <= 0) return null
        if (this["badges"].toString().contains("LIVE", ignoreCase = true)) return null

        return Track(
            id = id,
            title = title,
            artist = artist,
            durationSeconds = duration,
            thumbnailUrl = thumb,
        )
    }

    private fun extractPlaylistId(rawInput: String): String {
        val clean = rawInput.trim()
        val match = Regex("""(?:list=|\/playlist\?list=)([a-zA-Z0-9_-]+)""").find(clean)
        if (match != null) {
            return match.groupValues[1]
        }
        if (clean.contains("list=")) {
            val part = clean.substringAfter("list=").substringBefore("&").substringBefore("#").trim()
            if (part.isNotBlank()) return part
        }
        return clean
    }

    private fun JsonObject.toPlaylistVideoTrack(): Track? {
        val id = this["videoId"].asStringOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val title = this["title"].asTextRuns()?.takeIf { it.isNotBlank() } ?: return null
        val artist = this["shortBylineText"].asTextRuns()
            ?: this["ownerText"].asTextRuns()
            ?: this["longBylineText"].asTextRuns()
            ?: "YouTube"
        val duration = this["lengthSeconds"].asStringOrNull()?.toIntOrNull()
            ?: parseDuration(this["lengthText"].asSimpleText())
        val thumb = this["thumbnail"]
            .asObjectOrNull()
            ?.get("thumbnails")
            .asArrayOrNull()
            ?.lastOrNull()
            .asObjectOrNull()
            ?.get("url")
            .asStringOrNull()

        return Track(
            id = id,
            title = title,
            artist = artist,
            durationSeconds = duration.coerceAtLeast(0),
            thumbnailUrl = thumb,
        )
    }

    private fun JsonObject.toMusicTrack(): Track? {
        val id = this["playlistItemData"].asObjectOrNull()?.get("videoId").asStringOrNull()
            ?: this["videoId"].asStringOrNull()
            ?: return null
        val flex = this["flexColumns"].asArrayOrNull() ?: return null
        val col0 = flex.getOrNull(0)?.asObjectOrNull()
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.asObjectOrNull()
            ?.get("text")
        val title = col0.asTextRuns() ?: return null
        val col1 = flex.getOrNull(1)?.asObjectOrNull()
            ?.get("musicResponsiveListItemFlexColumnRenderer")?.asObjectOrNull()
            ?.get("text")
        val artist = col1.asTextRuns() ?: "YouTube Music"
        val thumb = this["thumbnail"]
            .asObjectOrNull()
            ?.get("musicThumbnailRenderer")
            ?.asObjectOrNull()
            ?.get("thumbnail")
            ?.asObjectOrNull()
            ?.get("thumbnails")
            .asArrayOrNull()
            ?.lastOrNull()
            .asObjectOrNull()
            ?.get("url")
            .asStringOrNull()

        return Track(
            id = id,
            title = title,
            artist = artist,
            durationSeconds = 0,
            thumbnailUrl = thumb,
        )
    }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(180)}")
            }
            return body
        }
    }

    private fun parseDuration(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val parts = text.trim().split(":").mapNotNull { it.toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }

    private data class PlaybackClient(
        val name: String,
        val version: String,
        val userAgent: String,
        val url: String,
        val context: JsonObject,
    )

    companion object {
        private const val REMIX_VERSION = "1.20260708.01.00"
        private const val YOUTUBE_BASE = "https://www.youtube.com"
        private const val MUSIC_BASE = "https://music.youtube.com"
        private const val WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val IOS_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
        private const val WEB_VERSION = "2.20260708.00.00"
        private const val ANDROID_VERSION = "21.26.364"
        private const val ANDROID_UA =
            "com.google.android.youtube/$ANDROID_VERSION (Linux; U; Android 11) gzip"
        private const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        var currentRegion: String = "IN"

        private fun webContext(gl: String = currentRegion): JsonObject = buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", JsonPrimitive("WEB"))
                put("clientVersion", JsonPrimitive(WEB_VERSION))
                put("browserName", JsonPrimitive("Chrome"))
                put("browserVersion", JsonPrimitive("149.0.7827.232"))
                put("hl", JsonPrimitive("en"))
                put("gl", JsonPrimitive(if (gl == "WW") "US" else gl))
            })
        }

        /** YT Music jaisa context (WEB_REMIX) — sirf lyrics/feed ke liye. */
        private fun musicRemixContext(gl: String = currentRegion): JsonObject = buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", JsonPrimitive("WEB_REMIX"))
                put("clientVersion", JsonPrimitive(REMIX_VERSION))
                put("hl", JsonPrimitive("en"))
                put("gl", JsonPrimitive(if (gl == "WW") "US" else gl))
            })
        }

        private val androidContext: JsonObject = buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", JsonPrimitive("ANDROID"))
                put("clientVersion", JsonPrimitive(ANDROID_VERSION))
                put("androidSdkVersion", JsonPrimitive(30))
                put("userAgent", JsonPrimitive(ANDROID_UA))
                put("hl", JsonPrimitive("en"))
                put("timeZone", JsonPrimitive("UTC"))
                put("utcOffsetMinutes", JsonPrimitive(0))
                put("osName", JsonPrimitive("Android"))
                put("osVersion", JsonPrimitive("11"))
            })
        }

        private val iosContext: JsonObject = buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", JsonPrimitive("IOS"))
                put("clientVersion", JsonPrimitive("21.26.4"))
                put("deviceMake", JsonPrimitive("Apple"))
                put("deviceModel", JsonPrimitive("iPhone16,2"))
                put("userAgent", JsonPrimitive(
                    "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                ))
                put("hl", JsonPrimitive("en"))
                put("platform", JsonPrimitive("MOBILE"))
                put("osName", JsonPrimitive("IOS"))
                put("osVersion", JsonPrimitive("18.3.2.22D82"))
                put("timeZone", JsonPrimitive("UTC"))
                put("gl", JsonPrimitive("US"))
                put("utcOffsetMinutes", JsonPrimitive(0))
            })
        }

        private val playbackClients = listOf(
            PlaybackClient(
                name = "ANDROID",
                version = ANDROID_VERSION,
                userAgent = ANDROID_UA,
                url = "$YOUTUBE_BASE/youtubei/v1/player?prettyPrint=false",
                context = androidContext,
            ),
            PlaybackClient(
                name = "IOS",
                version = "21.26.4",
                userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                url = "$YOUTUBE_BASE/youtubei/v1/player?key=$IOS_KEY&prettyPrint=false",
                context = iosContext,
            ),
        )

        private fun defaultHttpClient(): OkHttpClient = NetworkModule.getOkHttpClient()
    }
}

/**
 * Lyrics fetch result (plain lines — YouTube Music description shelf).
 */
data class LyricsResult(val lines: List<String>, val provider: String? = null)

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray
private fun JsonElement?.asStringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.intOrNull

private fun JsonElement?.asLongOrNull(): Long? =
    (this as? JsonPrimitive)?.longOrNull

private fun JsonElement?.asSimpleText(): String? =
    asObjectOrNull()?.get("simpleText").asStringOrNull()

private fun JsonElement?.asTextRuns(): String? {
    val obj = asObjectOrNull() ?: return asStringOrNull()
    obj["simpleText"].asStringOrNull()?.let { return it }
    val runs = obj["runs"].asArrayOrNull() ?: return null
    return runs.joinToString("") { run ->
        run.asObjectOrNull()?.get("text").asStringOrNull().orEmpty()
    }.takeIf { it.isNotBlank() }
}

private fun buildJsonObject(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
    kotlinx.serialization.json.buildJsonObject(block)
