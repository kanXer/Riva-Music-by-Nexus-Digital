package com.nexusdigital.rivamusic.data

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

object NetworkModule {
    @Volatile
    private var sharedClient: OkHttpClient? = null

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

        if (context != null) {
            val cacheDir = File(context.cacheDir, "http_cache")
            builder.cache(Cache(cacheDir, 25 * 1024 * 1024L)) // 25MB HTTP response cache
        }

        return builder.build()
    }
}
