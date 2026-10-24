package com.nexusdigital.rivamusic.data

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Musify ke ProxyManager ka Kotlin port — self-managed free HTTP proxy pool.
 *
 * Direct IP bot-flagged ho jaye ("Sign in to confirm you're not a bot") aur
 * sab InnerTube clients fail ho jayein, to ye layer 5 public proxy-list
 * sources se fresh proxies nikaal kar *unse* stream resolve karti hai.
 * Musify ke routing ke jaisa hi rules:
 *  - 5 sources: proxyscrape v4, geonode, openproxylist, spys.me, proxifly
 *  - har proxy end-to-end validate hota hai (real player call + stream URL)
 *  - fail hue proxy blocklist me (TTL 30 min), working set prefer hota hai
 *  - list har 60 min me refresh, 120 min purani discard
 *  - ek resolve try ke liye max 5 random proxies
 */
class YouTubeProxyManager internal constructor(
    private val innerTube: InnerTubeClient,
    private val baseHttp: OkHttpClient,
) {
    private data class Candidate(
        val address: String,
        val host: String,
        val port: Int,
        val source: String,
    )

    private val countryProxies = mutableMapOf<String, MutableList<Candidate>>()
    private val allProxies = LinkedHashMap<String, Candidate>()
    private val workingProxies = HashSet<String>()
    private val blockedProxies = HashMap<String, Long>() // address -> blockedAtMs

    @Volatile
    private var lastFetchedMs = 0L

    @Volatile
    private var lastCleanupMs = System.currentTimeMillis()

    private val lock = Any()
    private val fetchStart = HashMap<Long, Long>() // single-flight fetch guard

    // ---- Public entry point ---------------------------------------------------

    /**
     * Proxy ke through stream resolve — last resort. Proxy setting OFF hone par
     * bhi chalta hai (Musify `allowWhenDisabled` ka pattern).
     * Null = koi proxy kaam ka nahi mila.
     */
    suspend fun resolveViaProxy(videoId: String): StreamResolution? =
        withContext(Dispatchers.IO) {
            if (!refreshIfStale()) return@withContext null
            cleanupIfNeeded()
            pruneExpiredBlocked()

            var resolution: StreamResolution? = null
            var attempt = 0
            while (attempt < MAX_ATTEMPTS) {
                attempt++
                val candidate = nextCandidate() ?: break
                val tried = runCatching { tryCandidate(candidate, videoId) }
                val success = tried.getOrNull()
                if (success != null) {
                    resolution = success
                    break
                }
                NetworkModule.logNet(
                    "proxy",
                    "attempt $attempt ${candidate.address} fail " +
                        "(${tried.exceptionOrNull()?.message ?: "no stream"})",
                )
                block(candidate)
            }
            resolution
        }

    private suspend fun tryCandidate(candidate: Candidate, videoId: String): StreamResolution {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(candidate.host, candidate.port)))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(NetworkModule.memoryCookieJar)
            .build()

        return try {
            innerTube
                .resolveAudio(videoId, client)
                .also {
                    synchronized(lock) { workingProxies += candidate.address }
                    NetworkModule.logNet("proxy", "OK ${candidate.address} (${candidate.source})")
                }
        } finally {
            try { client.connectionPool.evictAll() } catch (_: Throwable) {}
            try { client.dispatcher.executorService.shutdown() } catch (_: Throwable) {}
        }
    }

    // ---- Candidate selection ----------------------------------------------------

    private fun nextCandidate(): Candidate? = synchronized(lock) {
        if (allProxies.isEmpty()) return null

        // 1) Working set se (pehle se proven) — blocked hata kar
        val working = workingProxies.filter { !isBlocked(it) && allProxies.containsKey(it) }
        if (working.isNotEmpty()) return allProxies[working.random()]

        // 2) Naya random candidate — random country bucket, nahi to sab me se
        val countries = countryProxies.entries
            .filter { (_, list) -> list.any { !isBlocked(it.address) } }
            .map { it.key }
        val pool: List<Candidate> = if (countries.isNotEmpty()) {
            (countryProxies[countries.random()] ?: emptyList()).filter { !isBlocked(it.address) }
        } else {
            allProxies.values.filter { !isBlocked(it.address) }
        }
        pool.randomOrNull()
    }

    private fun isBlocked(address: String): Boolean {
        val blockedAt = blockedProxies[address] ?: return false
        if (System.currentTimeMillis() - blockedAt > BLOCK_TTL_MS) {
            blockedProxies.remove(address)
            return false
        }
        return true
    }

    private fun pruneExpiredBlocked() = synchronized(lock) {
        val now = System.currentTimeMillis()
        blockedProxies.entries.removeAll { now - it.value > BLOCK_TTL_MS }
    }

    private fun block(candidate: Candidate) = synchronized(lock) {
        blockedProxies[candidate.address] = System.currentTimeMillis()
        workingProxies.remove(candidate.address)
        if (blockedProxies.size > MAX_BLOCKED) {
            // Sabse purane drop, 75% capacity rakho (Musify ka prune)
            val keep = blockedProxies.entries
                .sortedByDescending { it.value }
                .take((MAX_BLOCKED * 0.75).toInt())
            blockedProxies.clear()
            keep.forEach { blockedProxies[it.key] = it.value }
        }
        countryProxies.values.forEach { list -> list.removeAll { it.address == candidate.address } }
        countryProxies.entries.removeAll { it.value.isEmpty() }
    }

    // ---- Stale refetch + cleanup ----------------------------------------------

    /** True = pool ready. Ek waqt par sirf ek fetch chalta hai (60s window lock). */
    private fun refreshIfStale(): Boolean = synchronized(lock) {
        val fresh = lastFetchedMs != 0L &&
            System.currentTimeMillis() - lastFetchedMs < REFRESH_INTERVAL_MS &&
            allProxies.isNotEmpty()
        if (fresh) return true

        val inFlight = fetchStart.entries.firstOrNull { System.currentTimeMillis() - it.value < 120_000 }
        if (inFlight != null) return allProxies.isNotEmpty()

        val tag = System.nanoTime()
        fetchStart[tag] = System.currentTimeMillis()
        try {
            fetchProxies()
            return allProxies.isNotEmpty()
        } finally {
            fetchStart.remove(tag)
        }
    }

    private fun cleanupIfNeeded() = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now - lastCleanupMs < CLEANUP_INTERVAL_MS) return

        if (now - lastFetchedMs >= CLEANUP_INTERVAL_MS) {
            // 120 min purani list + resources discard (Musify ka full reset)
            synchronized(lock) {
                countryProxies.clear()
                allProxies.clear()
                workingProxies.clear()
                blockedProxies.clear()
            }
            lastFetchedMs = 0L
        }
        lastCleanupMs = now
    }

    // ---- Proxy list fetch (5 sources) --------------------------------------------

    private fun fetchProxies() {
        countryProxies.clear()
        allProxies.clear()

        var sourcesOk = 0
        if (runCatching { fetchProxyScrape() }.isSuccess) sourcesOk++
        if (runCatching { fetchGeonode() }.isSuccess) sourcesOk++
        if (runCatching { fetchOpenProxyList() }.isSuccess) sourcesOk++
        if (runCatching { fetchSpysMe() }.isSuccess) sourcesOk++
        if (runCatching { fetchProxifly() }.isSuccess) sourcesOk++

        if (allProxies.isNotEmpty()) lastFetchedMs = System.currentTimeMillis()
        NetworkModule.logNet(
            "proxy",
            "fetch done total=${allProxies.size} sourcesOk=$sourcesOk",
        )
    }

    private fun fetchText(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "Chrome/149.0.0.0 Safari/537.36",
            )
            .build()
        baseHttp.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            resp.body?.string()
        }
    }.getOrNull()

    private fun addCandidate(address: String, country: String, source: String) = synchronized(lock) lit@{
        val host = address.substringBefore(':').trim()
        val port = address.substringAfter(':', "").trim().toIntOrNull() ?: return@lit Unit
        if (host.isEmpty() || port !in 1..65535) return@lit Unit
        val full = "$host:$port"
        if (isBlocked(full) || allProxies.containsKey(full)) return@lit Unit

        val candidate = Candidate(full, host, port, source)
        allProxies[full] = candidate
        countryProxies.getOrPut(if (country.isBlank()) "XX" else country) { mutableListOf() }
            .add(candidate)
    }

    // ---- Sources ---------------------------------------------------------------

    // Source 1: ProxyScrape v4 — { proxies: [{ip, port, alive, ip_data:{countryCode}}] }
    private fun fetchProxyScrape() {
        val json = fetchTextJson(SCRAPE_URL) ?: return
        val proxies = json["proxies"].asArr() ?: return
        for (item in proxies) {
            val obj = item.asObj() ?: continue
            if (obj["alive"]?.prim()?.booleanOrNull != true) continue
            val ip = obj["ip"]?.str() ?: continue
            val port = obj["port"]?.int() ?: continue
            val country = obj["ip_data"]?.asObj()?.get("countryCode")?.str() ?: ""
            addCandidate("$ip:$port", country, "proxyscrape")
        }
    }

    // Source 2: Geonode — { data: [{ip, port, country}] }
    private fun fetchGeonode() {
        val json = fetchTextJson(GEONODE_URL) ?: return
        val data = json["data"].asArr() ?: return
        for (item in data) {
            val obj = item.asObj() ?: continue
            val ip = obj["ip"]?.str() ?: continue
            val port = obj["port"]?.int() ?: continue
            val country = obj["country"]?.str() ?: ""
            addCandidate("$ip:$port", country, "geonode")
        }
    }

    // Source 3: roosterkid/openproxylist HTTPS.txt
    // Line: "GLenum safe|HTTP 1.2.3.4:8080 123ms US ISP..." — Musify ka regex
    private fun fetchOpenProxyList() {
        val text = fetchText(OPENPROXY_URL) ?: return
        val regex = Regex(
            """(.)\s(?<ip>\d+\.\d+\.\d+\.\d+):(?<port>\d+)\s(?:(?<responsetime>\d+)(?:ms))\s(?<country>[A-Z]{2})\s(?<isp>.+)$""",
        )
        text.lineSequence().forEach { line ->
            val match = regex.find(line.trim()) ?: return@forEach
            val ip = match.groupValues[2]
            val port = match.groupValues[3]
            addCandidate("$ip:$port", match.groupValues[4], "openproxylist")
        }
    }

    // Source 4: spys.me/proxy.txt — "IP:PORT CC-Anon(-SSL) ..." — Musify ka regex
    private fun fetchSpysMe() {
        val text = fetchText(SPYS_URL) ?: return
        val regex = Regex(
            """(?<ip>\d+\.\d+\.\d+\.\d+):(?<port>\d+)\s(?<country>[A-Z]{2})-(?<anon>[HNA!]{1,2})(?:\s|-)(?<ssl>[\sS!]*)""",
        )
        text.lineSequence()
            .filter { it.isNotBlank() && !it.trim().startsWith(";") }
            .forEach { line ->
                val match = regex.find(line) ?: return@forEach
                addCandidate(
                    "${match.groupValues[1]}:${match.groupValues[2]}",
                    match.groupValues[3],
                    "spys.me",
                )
            }
    }

    // Source 5: proxifly — har line "http://host:port"
    private fun fetchProxifly() {
        val text = fetchText(PROXIFLY_URL) ?: return
        text.lineSequence().forEach { raw ->
            val uri = raw.trim()
            if (!uri.startsWith("http://")) return@forEach
            val tail = uri.removePrefix("http://")
            if (tail.isEmpty()) return@forEach
            val host = tail.substringBefore(':')
            val port = tail.substringAfter(':', "").substringBefore('/').toIntOrNull()
                ?: return@forEach
            if (host.isNotEmpty()) addCandidate("$host:$port", "", "proxifly")
        }
    }

    private fun fetchTextJson(url: String): JsonObject? {
        val text = fetchText(url) ?: return null
        return runCatching {
            Json { ignoreUnknownKeys = true; isLenient = true }
                .parseToJsonElement(text)
        }.getOrNull().asObj()
    }

    // ---- Local JSON helpers (InnerTubeClient wale private hain) ----------------

    private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asArr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.prim(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.contentOrNull
        ?.replace("\"", "")?.toIntOrNull()

    companion object {
        private const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L   // 60 min
        private const val CLEANUP_INTERVAL_MS = 120 * 60 * 1000L  // 120 min
        private const val BLOCK_TTL_MS = 30 * 60 * 1000L          // 30 min
        private const val MAX_BLOCKED = 200
        private const val MAX_ATTEMPTS = 5

        private const val SCRAPE_URL =
            "https://api.proxyscrape.com/v4/free-proxy-list/get?request=display_proxies" +
                "&proxy_format=protocolipport&format=json&protocol=http&ssl=yes"
        private const val GEONODE_URL =
            "https://proxylist.geonode.com/api/proxy-list?limit=50&page=1" +
                "&sort_by=lastChecked&sort_type=desc&protocols=http%2Chttps"
        private const val OPENPROXY_URL =
            "https://raw.githubusercontent.com/roosterkid/openproxylist/main/HTTPS.txt"
        private const val SPYS_URL = "https://spys.me/proxy.txt"
        private const val PROXIFLY_URL =
            "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/http/data.txt"
    }
}
