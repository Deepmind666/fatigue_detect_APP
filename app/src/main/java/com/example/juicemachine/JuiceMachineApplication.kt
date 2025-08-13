package com.example.juicemachine

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.example.juicemachine.data.database.AppDatabase
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.repository.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


class JuiceMachineApplication : Application(), ImageLoaderFactory {
    // No need to cancel this scope as it'll be torn down with the process
    val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { RecipeRepository(database.recipeDao()) }
    val hardwareManager by lazy { HardwareManager(this, applicationScope) }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 使用25%的可用内存
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02) // 使用2%的磁盘空间
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(false) // 禁用硬件位图以避免某些设备上的崩溃
            .build()
    }
}