package com.knightlsy.douyin

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.knightlsy.douyin.data.LogStore
import com.zz.douyin.XposedServiceBridge

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        LogStore.init(this)
        // 绑定 LSPosed 服务。设置页里那些开关必须通过它写远程偏好，
        // 直接写 SharedPreferences 是另一套存储，hook 侧读不到。
        XposedServiceBridge.attach(this)
    }
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
