package com.nexusdigital.rivamusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Browser jaisa YouTube session.
 *
 * Watch page (HTML) pehle kholte hain taaki YouTube hume asli anonymous visitor
 * samjhe: response se genuine `visitorData` + cookies (VISITOR_INFO1_LIVE, YSC)
 * milte hain. Inhe `/youtubei/v1/player` call me bhejne se flagged IPs par bhi
 * official music/compilation videos resolve ho jaate hain — locally minted
 * visitorData se "Sign in to confirm you're not a bot" aata rehta hai.
 *
 * Session ~30 min ke liye cache hoti hai; bot-check aane par refresh hoti hai.
 */
object YouTubeSession {
    private const val SESSION_TTL_MS = 30 * 60 * 1000L
    private const val WATCH_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

    private val visitorDataRegex = Regex("\"INNERTUBE_CONTEXT_VISITOR_DATA\":\"([^\"]+)\"")
    private val fallbackVisitorRegex = Regex("\"visitorData\":\"([^\"]+)\"")

    private val mutex = Mutex()

    @Volatile
    private var visitorData: String? = null

    @Volatile
    private var primedAt: Long = 0L

    /** Cached genuine visitorData, agar session abhi fresh hai. */
    fun currentVisitorData(): String? {
        val vd = visitorData ?: return null
        if (System.currentTimeMillis() - primedAt > SESSION_TTL_MS) return null
        return vd
    }

    /** Session ready karta hai (ek hi watch page request, cached). */
    suspend fun ensurePrimed(videoId: String): String? {
        currentVisitorData()?.let { return it }
        return mutex.withLock {
            currentVisitorData() ?: fetch(videoId)
        }
    }

    /** Bot-check ke baad naya session banata hai. */
    suspend fun refresh(videoId: String): String? = mutex.withLock { fetch(videoId) }

    private suspend fun fetch(videoId: String): String? = withContext(Dispatchers.IO) {
        val html = try {
            val request = Request.Builder()
                .url("https://www.youtube.com/watch?v=$videoId&hl=en")
                .header("User-Agent", WATCH_UA)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .get()
                .build()
            NetworkModule.getOkHttpClient().newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                NetworkModule.logNet(
                    "session",
                    "prime status=${response.code} bytes=${body.length}",
                )
                body
            }
        } catch (t: Throwable) {
            NetworkModule.logNet("session", "prime error ${t.message}")
            ""
        }

        val value = visitorDataRegex.find(html)?.groupValues?.get(1)
            ?: fallbackVisitorRegex.find(html)?.groupValues?.get(1)
        if (!value.isNullOrBlank()) {
            visitorData = value
            primedAt = System.currentTimeMillis()
        }
        NetworkModule.logNet("session", "visitorData=${value?.take(14)}")
        value
    }
}
