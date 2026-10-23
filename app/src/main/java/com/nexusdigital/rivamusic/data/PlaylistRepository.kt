package com.nexusdigital.rivamusic.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val coverUrl: String? get() = tracks.firstOrNull()?.thumbnailUrl
}

class PlaylistRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun create(name: String): Playlist {
        val clean = name.trim().ifBlank { "My playlist" }.take(48)
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = clean,
            tracks = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        _playlists.value = listOf(playlist) + _playlists.value
        persist()
        return playlist
    }

    fun createWithTracks(name: String, tracks: List<Track>): Playlist {
        val clean = name.trim().ifBlank { "YouTube Playlist" }.take(64)
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = clean,
            tracks = tracks,
            createdAt = System.currentTimeMillis(),
        )
        _playlists.value = listOf(playlist) + _playlists.value
        persist()
        return playlist
    }

    fun addTrack(playlistId: String, track: Track): Boolean {
        var added = false
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else if (playlist.tracks.any { it.id == track.id }) {
                playlist
            } else {
                added = true
                playlist.copy(tracks = playlist.tracks + track)
            }
        }
        if (added) persist()
        return added
    }

    fun removeTrack(playlistId: String, trackId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(tracks = playlist.tracks.filterNot { it.id == trackId })
            } else {
                playlist
            }
        }
        persist()
    }

    fun delete(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        persist()
    }

    private fun load(): List<Playlist> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Playlist>>(raw) }
            .getOrElse { emptyList() }
    }

    private fun persist() {
        prefs.edit().putString(KEY, json.encodeToString(_playlists.value)).apply()
    }

    companion object {
        private const val PREFS = "riva_playlists"
        private const val KEY = "items"
    }
}
