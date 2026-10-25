package com.nexusdigital.rivamusic.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nexusdigital.rivamusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val isGuest: Boolean = false,
)

class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow<UserProfile?>(loadInitialUser())
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private fun loadInitialUser(): UserProfile? {
        val fbUser = firebaseAuth.currentUser
        if (fbUser != null) {
            val name = fbUser.displayName?.ifBlank { null }
                ?: fbUser.email?.substringBefore("@")
                ?: "Google User"
            return UserProfile(
                uid = fbUser.uid,
                displayName = name,
                email = fbUser.email,
                photoUrl = fbUser.photoUrl?.toString(),
                isGuest = false,
            )
        }

        val isGuest = prefs.getBoolean(KEY_IS_GUEST, false)
        if (isGuest) {
            val guestName = prefs.getString(KEY_GUEST_NAME, "Guest") ?: "Guest"
            val guestId = prefs.getString(KEY_GUEST_ID, "guest_user") ?: "guest_user"
            return UserProfile(
                uid = guestId,
                displayName = guestName,
                email = null,
                photoUrl = null,
                isGuest = true,
            )
        }

        return null
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val serverClientId = runCatching {
            context.getString(R.string.default_web_client_id)
        }.getOrNull() ?: ""

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun signInWithGoogleIdToken(idToken: String, onResult: (Result<UserProfile>) -> Unit) {
        scope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = firebaseAuth.signInWithCredential(credential).await()
                val fbUser = authResult.user ?: throw IllegalStateException("Firebase user is null")

                val name = fbUser.displayName?.ifBlank { null }
                    ?: fbUser.email?.substringBefore("@")
                    ?: "User"
                val profile = UserProfile(
                    uid = fbUser.uid,
                    displayName = name,
                    email = fbUser.email,
                    photoUrl = fbUser.photoUrl?.toString(),
                    isGuest = false,
                )

                prefs.edit()
                    .putBoolean(KEY_IS_GUEST, false)
                    .remove(KEY_GUEST_NAME)
                    .apply()

                _currentUser.value = profile
                onResult(Result.success(profile))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun signInAsGuest(name: String, onResult: (UserProfile) -> Unit) {
        val trimmed = name.trim().ifBlank { "Guest" }
        val guestId = "guest_" + System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_IS_GUEST, true)
            .putString(KEY_GUEST_NAME, trimmed)
            .putString(KEY_GUEST_ID, guestId)
            .apply()

        val profile = UserProfile(
            uid = guestId,
            displayName = trimmed,
            email = null,
            photoUrl = null,
            isGuest = true,
        )
        _currentUser.value = profile
        onResult(profile)
    }

    fun signOut(googleSignInClient: GoogleSignInClient?, onComplete: () -> Unit) {
        scope.launch {
            runCatching { firebaseAuth.signOut() }
            googleSignInClient?.signOut()
            prefs.edit().clear().apply()
            _currentUser.value = null
            onComplete()
        }
    }

    companion object {
        private const val PREFS_NAME = "riva_auth_prefs"
        private const val KEY_IS_GUEST = "is_guest"
        private const val KEY_GUEST_NAME = "guest_name"
        private const val KEY_GUEST_ID = "guest_id"
    }
}
