package com.nexusdigital.rivamusic.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexusdigital.rivamusic.BuildConfig
import com.nexusdigital.rivamusic.MainActivity
import com.nexusdigital.rivamusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val PREFS_NAME = "riva_update_checker"
    private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L // Check hourly in background

    const val CHANNEL_ID = "riva_app_updates"
    const val NOTIFICATION_ID = 10099

    // Target repository
    private const val REPO_OWNER = "kanXer"
    private const val REPO_NAME = "Riva-Music-by-Nexus-Digital"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    /**
     * Checks for an available update on GitHub Releases.
     *
     * @param context Application context
     * @param force If true, bypasses the interval check
     * @param notifyIfNew If true, triggers a system push notification when a newer release is found
     * @return UpdateInfo if a newer version is found, or null if up-to-date, interval limited, or error occurs.
     */
    suspend fun checkForUpdate(
        context: Context,
        force: Boolean = false,
        notifyIfNew: Boolean = true
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)

        if (!force && (now - lastCheck < CHECK_INTERVAL_MS)) {
            Log.d(TAG, "Skipping update check; checked ${now - lastCheck}ms ago (interval: ${CHECK_INTERVAL_MS}ms)")
            return@withContext null
        }

        try {
            val client = NetworkModule.getOkHttpClient(context)
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "RivaMusic-App")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned status code ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            // Update last check time on successful check
            prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

            val json = JSONObject(bodyString)
            val rawTag = if (json.has("tag_name")) json.getString("tag_name") else ""
            val cleanTag = rawTag.trimStart('v', 'V').trim()
            val releaseNotes = if (json.has("body")) json.getString("body").trim() else ""
            val htmlUrl = if (json.has("html_url")) json.getString("html_url") else "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

            // Find .apk download url from assets
            var apkDownloadUrl: String? = null
            if (json.has("assets") && !json.isNull("assets")) {
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = if (asset.has("name")) asset.getString("name") else ""
                    val downloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else ""
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        apkDownloadUrl = downloadUrl
                        break
                    }
                }
            }

            val finalDownloadUrl = apkDownloadUrl ?: htmlUrl
            val currentVersion = BuildConfig.VERSION_NAME

            if (isVersionNewer(cleanTag, currentVersion)) {
                Log.i(TAG, "Update available: $cleanTag (current: $currentVersion)")
                val updateInfo = UpdateInfo(
                    latestVersion = cleanTag,
                    currentVersion = currentVersion,
                    releaseNotes = releaseNotes,
                    downloadUrl = finalDownloadUrl,
                    releasePageUrl = htmlUrl
                )

                if (notifyIfNew) {
                    val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_VERSION, "")
                    if (lastNotified != cleanTag) {
                        showUpdateNotification(context, updateInfo)
                        prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, cleanTag).apply()
                    }
                }

                return@withContext updateInfo
            } else {
                Log.d(TAG, "App is up to date: current=$currentVersion, remote=$cleanTag")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Creates Notification Channel and displays a high-priority system status-bar notification.
     */
    fun showUpdateNotification(context: Context, updateInfo: UpdateInfo) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "App Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a new version of Riva Music is released on GitHub"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct download action intent
            val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val downloadPendingIntent = PendingIntent.getActivity(
                context,
                1,
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val summaryNotes = updateInfo.releaseNotes.lineSequence()
                .filter { it.isNotBlank() }
                .take(3)
                .joinToString("\n")
                .ifEmpty { "A newer version of Riva Music is now available." }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("New Version Available: v${updateInfo.latestVersion}")
                .setContentText("Tap to open Riva Music and install the update.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Version v${updateInfo.latestVersion} is out!\n$summaryNotes"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.stat_sys_download,
                    "Download APK",
                    downloadPendingIntent
                )

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission to show notification: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display update notification: ${e.message}")
        }
    }

    /**
     * Compares remote version with current version.
     * Returns true if remote is strictly greater than current.
     * Handles versions like 1.0.2 vs 1.0.10, 1.2 vs 1.2.0, etc.
     */
    fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val remoteParts = remote.split("-", "+")[0].split(".").mapNotNull { it.trim().toIntOrNull() }
        val currentParts = current.split("-", "+")[0].split(".").mapNotNull { it.trim().toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Opens the download or release URL using Intent.ACTION_VIEW
     */
    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open download url: ${e.message}")
        }
    }
}
