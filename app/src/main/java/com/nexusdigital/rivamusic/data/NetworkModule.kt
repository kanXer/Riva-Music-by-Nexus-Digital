package com.nexusdigital.rivamusic.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

object NetworkModule {
    @Volatile
    private var sharedClient: OkHttpClient? = null

    /**
     * YouTube session cookies (VISITOR_INFO1_LIVE/YSC) — watch page se aati hain
     * aur player calls ke saath isi session ke roop me jaati hain. Inke bina
     * flagged IPs par "Sign in to confirm you're not a bot" aata hai.
     */
    private val cookieStore = mutableMapOf<String, MutableMap<String, Cookie>>()

    val memoryCookieJar: CookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                val host = cookieStore.getOrPut(url.host) { mutableMapOf() }
                cookies.forEach { host[it.name] = it }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(cookieStore) {
                val now = System.currentTimeMillis()
                return cookieStore[url.host]?.values
                    ?.filter { it.expiresAt > now }
                    ?.toList()
                    ?: emptyList()
            }
        }
    }

    /**
     * Release-debug se aane wale youtubei responses yahan append. External app dir
     * (Android/data/com.nexusdigital.rivamusic/files/) adb se cat kiya ja sakta hai.
     */
    @Volatile
    var netDebugFile: File? = null

    fun logNet(tag: String, text: String) {
        try {
            netDebugFile?.let { f ->
                synchronized(this) { f.appendText("[$tag] $text\n") }
            }
        } catch (_: Throwable) {}
    }

    fun getOkHttpClient(context: Context? = null): OkHttpClient {
        return sharedClient ?: synchronized(this) {
            sharedClient ?: buildClient(context).also { sharedClient = it }
        }
    }

    private fun buildClient(context: Context?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(memoryCookieJar)

        if (context != null) {
            val cacheDir = File(context.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, 25 * 1024 * 1024L)) // 25MB HTTP response cache
        }

        return builder.build()
    }
}
