package com.nexusdigital.rivamusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.nexusdigital.rivamusic.data.NetworkModule
import java.io.File

class RivaApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        NetworkModule.getOkHttpClient(this)
        NetworkModule.netDebugFile = File(getExternalFilesDir(null), "debug_net.log")
        NetworkModule.logNet("init", "app started")
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { NetworkModule.getOkHttpClient(this) }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80 * 1024 * 1024L)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
