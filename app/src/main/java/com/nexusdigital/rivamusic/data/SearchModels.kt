package com.nexusdigital.rivamusic.data

/**
 * Search scopes modelled on YouTube Music's own search filters
 * (ported from the Metrolist APK's InnerTube search).
 */
enum class SearchFilter(
    val label: String,
    val params: String?,
) {
    SONGS("Songs", "EgWKAQIIAWoKEAkQBRAKEAMQBA=="),
    ALBUMS("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA=="),
    ARTISTS("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA=="),
    PLAYLISTS("Playlists", "EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ="),
}

enum class SearchItemType {
    SONG,
    VIDEO,
    ALBUM,
    ARTIST,
    PLAYLIST,
}

/**
 * Non-track result (album / artist / playlist) returned by music search.
 * Song results also carry a ready-to-play [track].
 */
data class SearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    val type: SearchItemType,
    val browseId: String? = null,
    val durationSeconds: Int = 0,
    val track: Track? = null,
)

/** Live search suggestions: query completions + song/album/artist shortcuts. */
data class SearchSuggestions(
    val queries: List<String> = emptyList(),
    val items: List<SearchItem> = emptyList(),
)

/** Tracks loaded from an album / artist / playlist search result. */
data class SearchCollection(
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
)
