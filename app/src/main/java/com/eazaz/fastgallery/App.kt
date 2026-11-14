package com.eazaz.fastgallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.eazaz.fastgallery.data.ThumbFetcher
import com.eazaz.fastgallery.data.ThumbKeyer
import com.eazaz.fastgallery.data.ThumbStore
import kotlinx.coroutines.Dispatchers

class App : Application(), SingletonImageLoader.Factory {

    lateinit var thumbs: ThumbStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        thumbs = ThumbStore(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbKeyer())
                add(ThumbFetcher.Factory(thumbs))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            // Limit parallel decodes so fast scrolling doesn't choke the CPU.
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .build()

    companion object {
        lateinit var instance: App
            private set
    }
}
