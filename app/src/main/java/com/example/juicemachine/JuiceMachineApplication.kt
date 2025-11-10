package com.example.juicemachine

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.request.ImageRequest
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
    }

    /**
     * 延后：若数据库为空则插入默认配方（在首帧后后台执行，避免冷启动时创建Room）。
     */
    fun ensureDefaultRecipesAsync() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val count = database.recipeDao().getRecipeCount()
                if (count == 0) {
                    val defaults = listOf(
                        com.example.juicemachine.data.database.Recipe(
                            name = "茉莉雪芽", water = 105, juice = 175, price = 8,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 1, imageUri = null, juiceType = "牛奶绿茶",
                            waterSpeed = 60, juiceSpeed = 60
                        ),
                        com.example.juicemachine.data.database.Recipe(
                            name = "柳橙百香", water = 180, juice = 100, price = 9,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 2, imageUri = null, juiceType = "橙汁百香果汁",
                            waterSpeed = 60, juiceSpeed = 60
                        ),
                        com.example.juicemachine.data.database.Recipe(
                            name = "鸭屎香柠檬茶", water = 130, juice = 150, price = 10,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 3, imageUri = null, juiceType = "柠檬汁鸭屎香",
                            waterSpeed = 60, juiceSpeed = 60
                        )
                    )
                    database.recipeDao().insertAll(defaults)
                    DebugLogger.i("App", "已自动插入默认配方（数据库为空）", showToast = false)
                }
                // 保障：若旧库中存在果汁速度=0的记录，统一修正为60
                try {
                    database.recipeDao().updateJuiceSpeedDefaultIfZero(60)
                } catch (_: Exception) { /* ignore */ }
            } catch (e: Exception) {
                DebugLogger.w("App", "默认配方兜底插入失败: ${e.message}", showToast = false)
            }
        }
    }

    /**
     * 延后：预热广告图（持久化最多3张，否则内置资源），在首帧后后台执行。
     */
    fun preheatAdsAsync() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val prefs = getSharedPreferences("ads_prefs", Context.MODE_PRIVATE)
                val json = prefs.getString("ads_image_uris", null)
                val persisted: List<String> = try {
                    if (json.isNullOrBlank()) emptyList() else org.json.JSONArray(json).let { arr ->
                        buildList(minOf(arr.length(), 3)) {
                            for (i in 0 until minOf(arr.length(), 3)) {
                                when (val e = arr.get(i)) {
                                    is org.json.JSONObject -> e.optString("uri").takeIf { it.isNotBlank() }?.let { add(it) }
                                    is String -> add(e)
                                }
                            }
                        }
                    }
                } catch (_: Exception) { emptyList() }

                val pkg = packageName
                val builtIn = listOf(
                    "android.resource://$pkg/${com.example.juicemachine.R.drawable.ad_ya_shi_xiang_1}",
                    "android.resource://$pkg/${com.example.juicemachine.R.drawable.ad_liu_cheng_bai_xinag_1}",
                    "android.resource://$pkg/${com.example.juicemachine.R.drawable.ad_mo_li_xue_ya_1}"
                )
                val targets = if (persisted.isNotEmpty()) persisted else builtIn
                val loader = newImageLoader()
                targets.forEach { uri ->
                    loader.enqueue(
                        ImageRequest.Builder(this@JuiceMachineApplication)
                            .data(uri)
                            .build()
                    )
                }
                DebugLogger.i("App", "预热广告图数量: ${targets.size}", showToast = false)
            } catch (e: Exception) {
                DebugLogger.w("App", "预热首屏广告图失败: ${e.message}", showToast = false)
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
                    .maxSizePercent(0.05) // 提升到5%，容纳多张内置广告图
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(false) // 禁用硬件位图以避免某些设备上的崩溃
            .build()
    }
}