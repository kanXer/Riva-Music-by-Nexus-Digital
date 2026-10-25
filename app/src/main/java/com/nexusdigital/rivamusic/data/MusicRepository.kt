package com.nexusdigital.rivamusic.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(
    private val api: InnerTubeClient = InnerTubeClient(),
) {
    private val streamCache = ConcurrentHashMap<String, StreamResolution>()
    private val lyricsCache = ConcurrentHashMap<String, LyricsResult>()
    private val timedLyricsCache = ConcurrentHashMap<String, List<LyricLine>>()
    private val lrc = LrcLibClient()

    suspend fun search(query: String, limit: Int = 20): List<Track> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        return api.search(clean, limit)
    }

    suspend fun searchMusicTracks(query: String, limit: Int = 30): List<Track> =
        api.searchMusicTracks(query, limit)

    suspend fun searchMusicItems(query: String, filter: SearchFilter, limit: Int = 30): List<SearchItem> =
        api.searchMusic(query, filter, limit)

    suspend fun searchSuggestions(query: String): SearchSuggestions =
        api.searchSuggestions(query)

    suspend fun fetchMusicCollection(browseId: String, title: String): SearchCollection =
        api.fetchMusicCollection(browseId, title)

    suspend fun fetchPlaylist(urlOrId: String): Pair<String, List<Track>> {
        return api.fetchPlaylist(urlOrId)
    }

    suspend fun lyrics(track: Track): LyricsResult {
        lyricsCache[track.id]?.let { return it }
        val fresh = api.fetchLyrics(track.id)
        lyricsCache[track.id] = fresh
        return fresh
    }

    /** Time-synced (LRCLIB LRC). Null = synced nahi mila -> plain fallback. */
    suspend fun timedLyrics(track: Track): List<LyricLine>? {
        timedLyricsCache[track.id]?.let { return it }
        val result = runCatching { lrc.searchSynced(track.artist, track.title, track.durationSeconds) }
        val found = result.getOrNull()?.takeIf { it.size > 2 }
        if (found != null) {
            timedLyricsCache[track.id] = found
        }
        return found
    }

    suspend fun resolveAudio(track: Track): StreamResolution = withContext(Dispatchers.IO) {
        if (track.isDownloaded) {
            return@withContext StreamResolution(
                url = track.localPath.orEmpty(),
                mimeType = "audio/mp4",
                itag = 0,
                userAgent = "",
                headers = emptyMap(),
            )
        }

        val cached = streamCache[track.id]
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return@withContext cached
        }

        val fresh = api.resolveAudio(track.id)
        streamCache[track.id] = fresh
        fresh
    }

    fun cachedStream(trackId: String): StreamResolution? =
        streamCache[trackId]?.takeIf { it.expiresAt > System.currentTimeMillis() }

    fun clearStream(trackId: String) {
        streamCache.remove(trackId)
    }

    fun setRegion(regionCode: String) {
        InnerTubeClient.currentRegion = regionCode
    }
}
