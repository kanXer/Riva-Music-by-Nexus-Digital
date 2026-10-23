package com.nexusdigital.rivamusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecentRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _recent = MutableStateFlow(load())
    val recent: StateFlow<List<Track>> = _recent.asStateFlow()

    fun add(track: Track) {
        val updated = (listOf(track) + _recent.value.filterNot { it.id == track.id }).take(MAX)
        _recent.value = updated
        prefs.edit().putString(KEY, json.encodeToString(updated)).apply()
    }

    fun clear() {
        _recent.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<Track> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Track>>(raw) }
            .getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS = "riva_recent"
        private const val KEY = "tracks"
        private const val MAX = 20
    }
}
