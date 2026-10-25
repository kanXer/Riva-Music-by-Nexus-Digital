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
 * Search uses the public WEB client. Playback prefers the VISIONOS client —
 * Aug 2026 ke baad ANDROID/IOS/ANDROID_VR clients flagged IPs par PO token
 * maangte hain ("Sign in to confirm you're not a bot"), VISIONOS still exempt.
 * visitorData locally mint hota hai aur X-Goog-Visitor-Id + context me jata hai.
 * VR/IOS/ANDROID ab fallback hain.
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

    /**
     * YouTube Music search (WEB_REMIX) with Metrolist-style filters.
     * Unlike the plain WEB search below this returns proper artist / album
     * metadata and can search Albums, Artists and Playlists too.
     */
    suspend fun searchMusic(
        query: String,
        filter: SearchFilter,
        limit: Int = 30,
    ): List<SearchItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext emptyList()

        val body = buildJsonObject {
            put("context", musicRemixContext())
            put("query", JsonPrimitive(clean))
            filter.params?.let { put("params", JsonPrimitive(it)) }
        }
        val root = json.parseToJsonElement(
            execute(musicRequest("search", body)),
        )

        val out = mutableListOf<SearchItem>()
        val seen = mutableSetOf<String>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["musicResponsiveListItemRenderer"].asObjectOrNull()?.let { renderer ->
                        renderer.toSearchItem(filter)?.let { item ->
                            if (seen.add("${item.type}:${item.id}")) out += item
                        }
                    }
                    element.values.forEach(::visit)
                }
                is JsonArray -> element.forEach(::visit)
                else -> Unit
            }
        }
        visit(root)
        out.take(limit)
    }

    suspend fun searchMusicTracks(query: String, limit: Int = 30): List<Track> =
        searchMusic(query, SearchFilter.SONGS, limit)
            .mapNotNull { it.track }
            .filter { isRealSong(it.title, it.durationSeconds) }
            .distinctBy { it.id }
            .take(limit)

    /**
     * Live search suggestions exactly like Metrolist / YouTube Music:
     * autocomplete queries + recommended songs, artists and playlists.
     */
    suspend fun searchSuggestions(query: String): SearchSuggestions =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext SearchSuggestions()

            val body = buildJsonObject {
                put("context", musicRemixContext())
                put("input", JsonPrimitive(clean))
            }
            val root = json.parseToJsonElement(
                execute(musicRequest("music/get_search_suggestions", body)),
            )

            val queries = mutableListOf<String>()
            val items = mutableListOf<SearchItem>()
            val seenQueries = mutableSetOf<String>()
            val seenItems = mutableSetOf<String>()

            fun visit(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        element["searchSuggestionRenderer"].asObjectOrNull()?.let { renderer ->
                            val suggestion = renderer["suggestion"].asTextRuns()
                            if (!suggestion.isNullOrBlank() && seenQueries.add(suggestion.lowercase())) {
                                queries += suggestion
                            }
                        }
                        element["musicResponsiveListItemRenderer"].asObjectOrNull()?.let { renderer ->
                            renderer.toSearchItem(null)?.let { item ->
                                if (seenItems.add("${item.type}:${item.id}")) items += item
                            }
                        }
                        element.values.forEach(::visit)
                    }
                    is JsonArray -> element.forEach(::visit)
                    else -> Unit
                }
            }
            visit(root)
            SearchSuggestions(
                queries = queries.distinct().take(8),
                items = items.distinctBy { "${it.type}:${it.id}" }.take(6),
            )
        }

    /**
     * Loads all playable tracks from an album / artist / playlist search result.
     */
    suspend fun fetchMusicCollection(
        browseId: String,
        fallbackTitle: String = "YouTube Music",
    ): SearchCollection = withContext(Dispatchers.IO) {
        val clean = browseId.trim()
        if (clean.isEmpty()) throw IOException("Collection id missing")
        val fullId = when {
            clean.startsWith("VL") ||
                clean.startsWith("MP") ||
                clean.startsWith("UC") ||
                clean.startsWith("RD") -> clean
            else -> "VL$clean"
        }

        val body = buildJsonObject {
            put("context", musicRemixContext())
            put("browseId", JsonPrimitive(fullId))
        }
        val root = json.parseToJsonElement(
            execute(musicRequest("browse", body)),
        )

        val tracks = mutableListOf<Track>()
        val seen = mutableSetOf<String>()

        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["musicResponsiveListItemRenderer"].asObjectOrNull()?.let { renderer ->
                        renderer.toBrowseTrack()?.let { track ->
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
            throw IOException("Is collection me koi playable song nahi mila")
        }

        val title = findMusicHeaderTitle(root)?.takeIf { it.isNotBlank() } ?: fallbackTitle
        SearchCollection(
            title = title,
            subtitle = "${tracks.size} tracks",
            tracks = tracks,
        )
    }

    private fun musicRequest(path: String, body: JsonObject): Request =
        Request.Builder()
            .url("$MUSIC_BASE/youtubei/v1/$path?prettyPrint=false")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", WEB_UA)
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .build()

    private fun JsonObject.toSearchItem(filter: SearchFilter?): SearchItem? {
        val flex = this["flexColumns"].asArrayOrNull() ?: return null
        val columns = flex.mapNotNull { column ->
            column.asObjectOrNull()
                ?.get("musicResponsiveListItemFlexColumnRenderer").asObjectOrNull()
                ?.get("text").asTextRuns()
        }
        val title = columns.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val detailColumns = columns.drop(1)
        val details = detailColumns.joinToString(" • ")
        val segments = details.split("•").map { it.trim() }.filter { it.isNotBlank() }

        val thumbnail = thumbnailUrl()
        val videoId = watchVideoId() ?: firstFlexRunVideoId()
        val browseId = this["navigationEndpoint"].asObjectOrNull()
            ?.get("browseEndpoint").asObjectOrNull()
            ?.get("browseId").asStringOrNull()
        val playlistId = menuPlaylistId()

        val textType = when (segments.firstOrNull()?.lowercase()) {
            "song" -> SearchItemType.SONG
            "video" -> SearchItemType.VIDEO
            "album", "single", "ep" -> SearchItemType.ALBUM
            "artist" -> SearchItemType.ARTIST
            "playlist", "podcast" -> SearchItemType.PLAYLIST
            else -> null
        }
        // Search suggestions sometimes omit the "Artist"/"Album" prefix and
        // only expose it through the navigation endpoint. Infer the type so
        // artist shortcuts still show up in the live suggestion list.
        val inferredType = when {
            browseId?.startsWith("UC") == true -> SearchItemType.ARTIST
            browseId?.startsWith("MP") == true -> SearchItemType.ALBUM
            browseId?.startsWith("VL") == true || playlistId != null -> SearchItemType.PLAYLIST
            videoId != null -> SearchItemType.SONG
            else -> null
        }
        val type = when (filter) {
            SearchFilter.SONGS -> if (textType == SearchItemType.VIDEO) SearchItemType.VIDEO else SearchItemType.SONG
            SearchFilter.ALBUMS -> SearchItemType.ALBUM
            SearchFilter.ARTISTS -> SearchItemType.ARTIST
            SearchFilter.PLAYLISTS -> SearchItemType.PLAYLIST
            null -> textType ?: inferredType ?: return null
        }

        val fixedDuration = this["fixedColumns"].asArrayOrNull()
            ?.firstNotNullOfOrNull { col ->
                col.asObjectOrNull()
                    ?.get("musicResponsiveListItemFixedColumnRenderer").asObjectOrNull()
                    ?.get("text").asTextRuns()
                    ?.let { text -> if (DURATION_REGEX.matches(text)) parseDuration(text) else null }
            }
        val detailSegments = if (textType != null) segments.drop(1) else segments
        val duration = fixedDuration ?: detailSegments.firstNotNullOfOrNull { segment ->
            if (DURATION_REGEX.matches(segment)) parseDuration(segment) else null
        } ?: 0

        if (filter == SearchFilter.SONGS && !isRealSong(title, duration)) {
            return null
        }
        val statWords = listOf("plays", "views", "monthly audience", "subscribers", "songs", "episodes")
        val meaningful = detailSegments.filterNot { segment ->
            DURATION_REGEX.matches(segment) ||
                statWords.any { segment.contains(it, ignoreCase = true) }
        }

        val id = when (type) {
            SearchItemType.SONG, SearchItemType.VIDEO -> videoId ?: return null
            SearchItemType.ALBUM, SearchItemType.ARTIST -> browseId ?: return null
            SearchItemType.PLAYLIST -> browseId ?: playlistId?.let { "VL$it" } ?: return null
        }

        val subtitle = when (type) {
            SearchItemType.SONG, SearchItemType.VIDEO -> {
                val artist = meaningful.firstOrNull()?.takeIf { it.length < 90 }
                val album = meaningful.drop(1).firstOrNull()
                    ?.takeIf { it.length < 90 && !it.equals(artist, ignoreCase = true) }
                listOfNotNull(artist, album).joinToString(" • ")
                    .ifBlank { details.ifBlank { "YouTube Music" } }
            }
            SearchItemType.ALBUM -> meaningful.joinToString(" • ")
                .ifBlank { details.ifBlank { "Album" } }
            SearchItemType.ARTIST -> detailSegments.joinToString(" • ")
                .ifBlank { details.ifBlank { "Artist" } }
            SearchItemType.PLAYLIST -> {
                val count = segments.lastOrNull {
                    it.contains("song", ignoreCase = true) ||
                        it.contains("episode", ignoreCase = true)
                }
                listOfNotNull(meaningful.firstOrNull(), count).joinToString(" • ")
                    .ifBlank { details.ifBlank { "Playlist" } }
            }
        }

        val track = if (type == SearchItemType.SONG || type == SearchItemType.VIDEO) {
            val artist = meaningful.firstOrNull()?.takeIf { it.length < 90 }
                ?: detailSegments.firstOrNull()?.takeIf { it.length < 90 }
                ?: "YouTube Music"
            Track(
                id = id,
                title = title,
                artist = artist,
                durationSeconds = duration,
                thumbnailUrl = thumbnail,
            )
        } else {
            null
        }

        return SearchItem(
            id = id,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnail,
            type = type,
            browseId = browseId ?: id,
            durationSeconds = duration,
            track = track,
        )
    }

    private fun JsonObject.toBrowseTrack(): Track? {
        val flex = this["flexColumns"].asArrayOrNull() ?: return null
        val columns = flex.mapNotNull { column ->
            column.asObjectOrNull()
                ?.get("musicResponsiveListItemFlexColumnRenderer").asObjectOrNull()
                ?.get("text").asTextRuns()
        }
        val title = columns.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val segments = columns.drop(1)
            .joinToString(" • ")
            .split("•")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val kind = segments.firstOrNull()?.lowercase()
        if (kind == "episode" || kind == "podcast" || kind == "profile") return null

        val id = watchVideoId() ?: firstFlexRunVideoId()
            ?: this["playlistItemData"].asObjectOrNull()?.get("videoId").asStringOrNull()
            ?: return null
        val fixedDuration = this["fixedColumns"].asArrayOrNull()
            ?.firstNotNullOfOrNull { col ->
                col.asObjectOrNull()
                    ?.get("musicResponsiveListItemFixedColumnRenderer").asObjectOrNull()
                    ?.get("text").asTextRuns()
                    ?.let { text -> if (DURATION_REGEX.matches(text)) parseDuration(text) else null }
            }
        val duration = fixedDuration ?: segments.firstNotNullOfOrNull { segment ->
            if (DURATION_REGEX.matches(segment)) parseDuration(segment) else null
        } ?: 0
        if (!isRealSong(title, duration)) return null
        val statWords = listOf("plays", "views", "monthly audience", "subscribers", "songs", "episodes")
        val meaningful = segments.filterNot { segment ->
            DURATION_REGEX.matches(segment) ||
                statWords.any { segment.contains(it, ignoreCase = true) } ||
                segment.lowercase() in TYPE_WORDS
        }
        val artist = meaningful.firstOrNull()?.takeIf { it.length < 120 } ?: "YouTube Music"

        return Track(
            id = id,
            title = title,
            artist = artist,
            durationSeconds = duration,
            thumbnailUrl = thumbnailUrl(),
        )
    }

    private fun JsonObject.thumbnailUrl(): String? =
        this["thumbnail"].asObjectOrNull()
            ?.get("musicThumbnailRenderer").asObjectOrNull()
            ?.get("thumbnail").asObjectOrNull()
            ?.get("thumbnails").asArrayOrNull()
            ?.lastOrNull().asObjectOrNull()
            ?.get("url").asStringOrNull()
            ?: this["thumbnail"].asObjectOrNull()
                ?.get("thumbnails").asArrayOrNull()
                ?.lastOrNull().asObjectOrNull()
                ?.get("url").asStringOrNull()

    private fun JsonObject.watchVideoId(): String? =
        this["overlay"].asObjectOrNull()
            ?.get("musicItemThumbnailOverlayRenderer").asObjectOrNull()
            ?.get("content").asObjectOrNull()
            ?.get("musicPlayButtonRenderer").asObjectOrNull()
            ?.get("playNavigationEndpoint").asObjectOrNull()
            ?.get("watchEndpoint").asObjectOrNull()
            ?.get("videoId").asStringOrNull()

    private fun JsonObject.firstFlexRunVideoId(): String? {
        val runs = this["flexColumns"].asArrayOrNull()
            ?.firstOrNull().asObjectOrNull()
            ?.get("musicResponsiveListItemFlexColumnRenderer").asObjectOrNull()
            ?.get("text").asObjectOrNull()
            ?.get("runs").asArrayOrNull()
            ?: return null
        for (run in runs) {
            val endpoint = run.asObjectOrNull()
                ?.get("navigationEndpoint").asObjectOrNull()
            val videoId = endpoint
                ?.get("watchEndpoint").asObjectOrNull()
                ?.get("videoId").asStringOrNull()
            if (!videoId.isNullOrBlank()) return videoId
        }
        return null
    }

    private fun JsonObject.menuPlaylistId(): String? =
        this["menu"].asObjectOrNull()
            ?.get("menuRenderer").asObjectOrNull()
            ?.get("items").asArrayOrNull()
            ?.firstNotNullOfOrNull { item ->
                item.asObjectOrNull()
                    ?.get("menuNavigationItemRenderer").asObjectOrNull()
                    ?.get("navigationEndpoint").asObjectOrNull()
                    ?.get("watchPlaylistEndpoint").asObjectOrNull()
                    ?.get("playlistId").asStringOrNull()
            }

    private fun findMusicHeaderTitle(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                for (key in MUSIC_HEADER_KEYS) {
                    val header = element[key].asObjectOrNull() ?: continue
                    val nested = header["header"].asObjectOrNull()
                        ?.get("musicDetailHeaderRenderer").asObjectOrNull()
                    val title = header["title"].asTextRuns()
                        ?: nested?.get("title").asTextRuns()
                    if (!title.isNullOrBlank()) return title
                }
                element.values.forEach { child ->
                    findMusicHeaderTitle(child)?.let { return it }
                }
            }
            is JsonArray -> element.forEach { child ->
                findMusicHeaderTitle(child)?.let { return it }
            }
            else -> Unit
        }
        return null
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

    /**
     * Normal resolve: 4 clients × 2 passes, aur poora fail hone par Musify wala
     * last-resort — free proxy pool se resolve (setting OFF par bhi chalta hai).
     */
    suspend fun resolveAudio(videoId: String): StreamResolution {
        // NOTE: suspend fun, isliye body resolveAudioInternal me hi chalega
        return resolveAudioInternal(videoId, null, allowProxyFallback = true)
    }

    /** Proxy client ke saath resolve — YouTubeProxyManager ka validation path. */
    suspend fun resolveAudio(videoId: String, proxyClient: OkHttpClient): StreamResolution {
        return resolveAudioInternal(videoId, proxyClient, allowProxyFallback = false)
    }

    private suspend fun resolveAudioInternal(
        videoId: String,
        proxyClient: OkHttpClient?,
        allowProxyFallback: Boolean,
    ): StreamResolution = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // Genuine browser session (visitorData + cookies) — iske bina flagged IPs
        // par official music par "Sign in to confirm you're not a bot" aata hai.
        var sessionVd = YouTubeSession.ensurePrimed(videoId)

        for (pass in 0 until 2) {
            val errorsBefore = errors.size
            for (client in playbackClients) {
                try {
                    val resolution = requestPlayer(videoId, client, sessionVd, proxyClient)
                    if (resolution != null) return@withContext resolution
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    errors += "${client.name}: ${t.message ?: t.javaClass.simpleName}"
                }
            }

            val botBlocked = errors.drop(errorsBefore).any { message ->
                message.contains("confirm you", ignoreCase = true) ||
                    message.contains("not a bot", ignoreCase = true)
            }
            if (pass == 0 && botBlocked) {
                // Session purani/ghair-authentic ho gayi — fresh prime karke ek aur round.
                sessionVd = YouTubeSession.refresh(videoId)
                    ?: YouTubeSession.currentVisitorData()
                    ?: sessionVd
                continue
            }
            break
        }

        // Musify ka defence layer #4: direct fail → free proxy pool se try.
        // resolveAudioInternal(videoId, proxyClient) se loop guard ke liye allowProxyFallback=false.
        if (allowProxyFallback && proxyClient == null) {
            val viaProxy = proxyManager.resolveViaProxy(videoId)
            if (viaProxy != null) return@withContext viaProxy
        }

        throw IOException(
            buildString {
                append("YouTube stream resolve nahi hua. ")
                if (errors.isNotEmpty()) append(errors.joinToString(" | "))
            },
        )
    }

    private val proxyManager: YouTubeProxyManager by lazy { YouTubeProxyManager(this, http) }

    private fun requestPlayer(
        videoId: String,
        client: PlaybackClient,
        sessionVd: String?,
        proxyClient: OkHttpClient? = null,
    ): StreamResolution? {
        val vd = if (client.needsVisitorData) sessionVd ?: visitorData() else null
        val context = if (vd != null) withVisitorData(client.context, vd) else client.context
        val body = buildJsonObject {
            put("context", context)
            put("videoId", JsonPrimitive(videoId))
            put("contentCheckOk", JsonPrimitive(true))
            put("racyCheckOk", JsonPrimitive(true))
        }

        val requestBuilder = Request.Builder()
            .url(client.url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("Origin", "https://www.youtube.com")
            .header("X-Youtube-Client-Name", client.numericName)
            .header("X-Youtube-Client-Version", client.version)
        if (vd != null) {
            requestBuilder.header("X-Goog-Visitor-Id", vd)
        }
        val request = requestBuilder.build()

        val rawResponse = if (proxyClient != null) executeWith(proxyClient, request) else newExecute(request)
        NetworkModule.logNet(
            "player",
            "client=${client.name} vd=${vd?.take(12)} resp=${rawResponse.take(700)}",
        )

        val root = json.parseToJsonElement(rawResponse).asObjectOrNull() ?: return null
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

        if (duration <= 0 || duration > 720) return null
        if (!isRealSong(title, duration)) return null
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

    /** execute ka variant jo raw body text deta hai (logging ke liye response text reuse hota hai). */
    private fun newExecute(request: Request): String = execute(request)

    /** Proxy client ke saath raw execute (YouTubeProxyManager validation path). */
    private fun executeWith(client: OkHttpClient, request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}: ${body.take(180)}")
            return body
        }
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
        val numericName: String,
        val version: String,
        val userAgent: String,
        val url: String,
        val context: JsonObject,
        val needsVisitorData: Boolean = false,
    )

    companion object {
        private val DURATION_REGEX = Regex("""^\d{1,2}:\d{2}(?::\d{2})?$""")
        private val TYPE_WORDS = setOf(
            "song", "video", "album", "single", "ep", "artist",
            "playlist", "podcast", "episode", "profile",
        )

        fun isRealSong(title: String, durationSeconds: Int): Boolean {
            if (durationSeconds in 1..25) return false
            if (durationSeconds > 720) return false // > 12 mins is not a standalone song
            val lower = title.lowercase()
            val bannedKeywords = listOf(
                "jukebox", "mashup", "non stop", "nonstop", "non-stop",
                "full album", "full audio songs", "all songs", "audio jukebox",
                "video jukebox", "hours", "hour", "1 hr", "2 hr", "3 hr", "4 hr", "5 hr", "6 hr", "7 hr", "8 hr",
                "mega mix", "megamix", "compilation"
            )
            for (keyword in bannedKeywords) {
                if (lower.contains(keyword)) return false
            }
            return true
        }
        // Carousel shelf headers ("Releases for you", "Videos", ...) are
        // section labels, not the album/artist/playlist title. Keeping them
        // here made collection sheets show the wrong heading.
        private val MUSIC_HEADER_KEYS = listOf(
            "musicImmersiveHeaderRenderer",
            "musicVisualHeaderRenderer",
            "musicDetailHeaderRenderer",
            "musicResponsiveHeaderRenderer",
            "musicEditablePlaylistDetailHeaderRenderer",
        )
        private const val REMIX_VERSION = "1.20260708.01.00"
        private const val YOUTUBE_BASE = "https://www.youtube.com"
        private const val MUSIC_BASE = "https://music.youtube.com"
        private const val WEB_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private const val IOS_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
        private const val WEB_VERSION = "2.20260708.00.00"
        private const val ANDROID_VERSION = "21.26.364"
        private const val ANDROID_UA =
            "com.google.android.youtube/$ANDROID_VERSION (Linux; U; Android 11) gzip"
        private const val VR_VERSION = "1.61.29"
        private const val VR_UA =
            "com.google.android.apps.youtube.vr.oculus/$VR_VERSION " +
                "(Linux; U; Android 7.1.1; eureka-user Build/N2G512) gzip"
        private const val VISIONOS_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/26.0 Safari/605.1.15"
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

        private val vrContext: JsonObject = buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", JsonPrimitive("ANDROID_VR"))
                put("clientVersion", JsonPrimitive(VR_VERSION))
                put("userAgent", JsonPrimitive(VR_UA))
                put("osName", JsonPrimitive("Android"))
                put("osVersion", JsonPrimitive("7.1.1"))
                put("hl", JsonPrimitive("en"))
                put("timeZone", JsonPrimitive("UTC"))
                put("utcOffsetMinutes", JsonPrimitive(0))
            })
        }

        private val visionosContext: JsonObject = buildJsonObject {
            put("client", buildJsonObject {
                put("clientName", JsonPrimitive("VISIONOS"))
                put("clientVersion", JsonPrimitive("1.02"))
                put("deviceMake", JsonPrimitive("Apple"))
                put("deviceModel", JsonPrimitive("RealityDevice17,1"))
                put("platform", JsonPrimitive("MOBILE"))
                put("osName", JsonPrimitive("visionOS"))
                put("osVersion", JsonPrimitive("26.5.23O471"))
                put("userAgent", JsonPrimitive(VISIONOS_UA))
                put("hl", JsonPrimitive("en"))
                put("timeZone", JsonPrimitive("UTC"))
                put("utcOffsetMinutes", JsonPrimitive(0))
            })
        }

        private val playbackClients = listOf(
            // VISIONOS: token-mukat client (Aug 2026 se ANDROID_VR/IOS par PO token
            // chahiye hota hai on flagged IPs, VISIONOS exempt) — mukhya client.
            PlaybackClient(
                name = "VISIONOS",
                numericName = "101",
                version = "1.02",
                userAgent = VISIONOS_UA,
                url = "$YOUTUBE_BASE/youtubei/v1/player?prettyPrint=false",
                context = visionosContext,
                needsVisitorData = true,
            ),
            PlaybackClient(
                name = "ANDROID_VR",
                numericName = "28",
                version = VR_VERSION,
                userAgent = VR_UA,
                url = "$YOUTUBE_BASE/youtubei/v1/player?prettyPrint=false",
                context = vrContext,
                needsVisitorData = true,
            ),
            PlaybackClient(
                name = "IOS",
                numericName = "5",
                version = "21.26.4",
                userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                url = "$YOUTUBE_BASE/youtubei/v1/player?key=$IOS_KEY&prettyPrint=false",
                context = iosContext,
                needsVisitorData = true,
            ),
            PlaybackClient(
                name = "ANDROID",
                numericName = "3",
                version = ANDROID_VERSION,
                userAgent = ANDROID_UA,
                url = "$YOUTUBE_BASE/youtubei/v1/player?prettyPrint=false",
                context = androidContext,
            ),
        )

        /**
         * Anonymous visitorData jo visitor protobuf {id(1): string, ts(5): int32} se
         * locally mint hota hai — har app session me ek. Bot-check layer ke liye
         * client context + X-Goog-Visitor-Id me jata hai.
         */
        @Volatile
        private var cachedVisitorData: String? = null

        fun visitorData(): String {
            cachedVisitorData?.let { return it }
            synchronized(this) {
                cachedVisitorData?.let { return it }
                val allowed = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                val rnd = java.security.SecureRandom()
                val id = StringBuilder(24).also { sb ->
                    repeat(24) { sb.append(allowed[rnd.nextInt(allowed.length)]) }
                }.toString()
                val out = java.io.ByteArrayOutputStream()
                out.write(0x0A)
                out.write(id.toByteArray(Charsets.UTF_8))
                out.write(0x28)
                var ts = (System.currentTimeMillis() / 1000L) and 0xFFFFFFFFL
                while (true) {
                    val byte = (ts and 0x7FL).toInt()
                    ts = ts ushr 7
                    out.write(if (ts != 0L) byte or 0x80 else byte)
                    if (ts == 0L) break
                }
                val vd = java.util.Base64.getEncoder()
                    .withoutPadding()
                    .encodeToString(out.toByteArray())
                cachedVisitorData = vd
                return vd
            }
        }

        /** context.client ke andar visitorData inject karta hai (bina other fields chhue). */
        private fun withVisitorData(context: JsonObject, vd: String): JsonObject = buildJsonObject {
            val clientObj = context["client"].asObjectOrNull()
            if (clientObj != null) {
                // existing client object + visitorData
                put("client", buildJsonObject {
                    clientObj.forEach { (key, value) -> put(key, value) }
                    put("visitorData", JsonPrimitive(vd))
                })
            } else {
                put("client", buildJsonObject {
                    put("visitorData", JsonPrimitive(vd))
                })
            }
        }

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
