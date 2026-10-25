package com.nexusdigital.rivamusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LikedTrack(
    val track: Track,
    val likedAt: Long = System.currentTimeMillis(),
)

/**
 * Local "Liked songs" library, mirroring Metrolist's Liked songs collection.
 * Persisted as JSON in SharedPreferences so no database dependency is needed.
 */
class LibraryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _liked = MutableStateFlow(load())
    val liked: StateFlow<List<LikedTrack>> = _liked.asStateFlow()

    private val _likedIds = MutableStateFlow(_liked.value.map { it.track.id }.toSet())
    val likedIds: StateFlow<Set<String>> = _likedIds.asStateFlow()

    fun isLiked(trackId: String): Boolean = trackId in _likedIds.value

    /** Returns true when the track is liked after this call. */
    fun toggle(track: Track): Boolean {
        val existing = _liked.value.firstOrNull { it.track.id == track.id }
        return if (existing != null) {
            commit(_liked.value.filterNot { it.track.id == track.id })
            false
        } else {
            commit(listOf(LikedTrack(track, System.currentTimeMillis())) + _liked.value)
            true
        }
    }

    fun like(track: Track) {
        if (isLiked(track.id)) return
        commit(listOf(LikedTrack(track, System.currentTimeMillis())) + _liked.value)
    }

    fun unlike(trackId: String) {
        if (!isLiked(trackId)) return
        commit(_liked.value.filterNot { it.track.id == trackId })
    }

    fun clear() {
        commit(emptyList())
    }

    private fun commit(value: List<LikedTrack>) {
        _liked.value = value
        _likedIds.value = value.map { it.track.id }.toSet()
        prefs.edit().putString(KEY, json.encodeToString(value)).apply()
    }

    private fun load(): List<LikedTrack> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<LikedTrack>>(raw) }
            .getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS = "riva_library"
        private const val KEY = "liked_songs"
    }
}

/**
 * Recent search terms shown above live suggestions, like Metrolist's
 * "Search history" section.
 */
class SearchHistoryRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val updated = (listOf(clean) + _history.value.filterNot { it.equals(clean, ignoreCase = true) })
            .take(MAX)
        _history.value = updated
        persist()
    }

    fun remove(query: String) {
        _history.value = _history.value.filterNot { it == query }
        persist()
    }

    fun clear() {
        _history.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist() {
        prefs.edit().putString(KEY, json.encodeToString(_history.value)).apply()
    }

    private fun load(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }
            .getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS = "riva_search_history"
        private const val KEY = "queries"
        private const val MAX = 12
    }
}
