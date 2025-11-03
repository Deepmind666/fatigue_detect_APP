package com.example.juicemachine

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.example.juicemachine.data.database.AppDatabase
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.repository.RecipeRepository
import com.example.juicemachine.data.repository.OrderRepository
import com.example.juicemachine.data.repository.OrderRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.juicemachine.util.DebugLogger


class JuiceMachineApplication : Application(), ImageLoaderFactory {
    // No need to cancel this scope as it'll be torn down with the process
    val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { RecipeRepository(database.recipeDao()) }
    val orderRepository by lazy { OrderRepositoryImpl(database.orderDao()) }
    val hardwareManager by lazy { HardwareManager(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        // 初始化本地日志
        DebugLogger.init(this)
        // 关闭Toast调试显示，避免频繁弹窗干扰
        DebugLogger.setToastEnabled(false)
        // 全局未捕获异常处理，落盘到日志文件
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogger.crash("UncaughtException", "线程: ${thread.name}", throwable)
        }

        // 兜底：若数据库当前没有任何配方，自动插入默认配方，避免“配方管理功能消失”
        applicationScope.launch(Dispatchers.IO) {
            try {
                val count = database.recipeDao().getRecipeCount()
                if (count == 0) {
                    val defaults = listOf(
                        com.example.juicemachine.data.database.Recipe(
                            name = "茉莉雪芽", water = 105, juice = 175, price = 8,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 1, imageUri = null, juiceType = "牛奶绿茶"
                        ),
                        com.example.juicemachine.data.database.Recipe(
                            name = "柳橙百香", water = 180, juice = 100, price = 9,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 2, imageUri = null, juiceType = "橙汁百香果汁"
                        ),
                        com.example.juicemachine.data.database.Recipe(
                            name = "鸭屎香柠檬茶", water = 130, juice = 150, price = 10,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 3, imageUri = null, juiceType = "柠檬汁鸭屎香"
                        )
                    )
                    database.recipeDao().insertAll(defaults)
                    DebugLogger.i("App", "已自动插入默认配方（数据库为空）", showToast = false)
                }
            } catch (e: Exception) {
                DebugLogger.w("App", "默认配方兜底插入失败: ${e.message}", showToast = false)
            }
        }
    }

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