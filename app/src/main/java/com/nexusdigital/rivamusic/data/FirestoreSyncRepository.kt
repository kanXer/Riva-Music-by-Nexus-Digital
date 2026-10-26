package com.nexusdigital.rivamusic.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class CloudTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val audioUrl: String? = null,
    val savedAt: Long = 0L,
)

class FirestoreSyncRepository {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    suspend fun saveOfflineTrack(uid: String, track: Track, audioUrl: String? = null): Boolean {
        if (uid.isBlank() || uid.startsWith("guest")) return false
        return try {
            val docRef = firestore.collection("users")
                .document(uid)
                .collection("offline_tracks")
                .document(track.id)

            val data = hashMapOf(
                "id" to track.id,
                "title" to track.title,
                "artist" to track.artist,
                "thumbnailUrl" to (track.thumbnailUrl ?: ""),
                "durationSeconds" to track.durationSeconds,
                "audioUrl" to (audioUrl ?: ""),
                "savedAt" to System.currentTimeMillis(),
            )
            docRef.set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeOfflineTrack(uid: String, trackId: String): Boolean {
        if (uid.isBlank() || uid.startsWith("guest")) return false
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("offline_tracks")
                .document(trackId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchOfflineTracks(uid: String): List<CloudTrack> {
        if (uid.isBlank() || uid.startsWith("guest")) return emptyList()
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("offline_tracks")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: return@mapNotNull null
                val artist = doc.getString("artist") ?: "Unknown Artist"
                val thumbnailUrl = doc.getString("thumbnailUrl")?.takeIf { it.isNotBlank() }
                val duration = doc.getLong("durationSeconds")?.toInt() ?: 0
                val audioUrl = doc.getString("audioUrl")?.takeIf { it.isNotBlank() }
                val savedAt = doc.getLong("savedAt") ?: 0L

                CloudTrack(
                    id = id,
                    title = title,
                    artist = artist,
                    thumbnailUrl = thumbnailUrl,
                    durationSeconds = duration,
                    audioUrl = audioUrl,
                    savedAt = savedAt,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveUserPreferences(uid: String, regionCode: String, languages: Set<String>): Boolean {
        if (uid.isBlank() || uid.startsWith("guest")) return false
        return try {
            val docRef = firestore.collection("users").document(uid)
            val data = hashMapOf(
                "region" to regionCode,
                "languages" to languages.toList(),
                "updatedAt" to System.currentTimeMillis(),
            )
            docRef.set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchUserPreferences(uid: String): Pair<String, Set<String>>? {
        if (uid.isBlank() || uid.startsWith("guest")) return null
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (doc.exists()) {
                val region = doc.getString("region") ?: "IN"
                val langsList = (doc.get("languages") as? List<*>)?.mapNotNull { it?.toString() }
                val langs = langsList?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf("Hindi")
                Pair(region, langs)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
