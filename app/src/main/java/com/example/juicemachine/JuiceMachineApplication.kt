package com.example.juicemachine

import android.app.Application
import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import android.net.Uri
import android.graphics.BitmapFactory
import com.example.juicemachine.data.database.AppDatabase
import com.example.juicemachine.data.hardware.HardwareManager
import com.example.juicemachine.data.repository.RecipeRepository
import com.example.juicemachine.data.repository.OrderRepository
import com.example.juicemachine.data.repository.OrderRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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

    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate() {
        super.onCreate()
        // 初始化本地日志
        DebugLogger.init(this)
        // 关闭Toast调试显示，避免频繁弹窗干扰
        DebugLogger.setToastEnabled(false)
        // 关闭文件日志以减少I/O开销（保留崩溃日志）
        DebugLogger.setFileLoggingEnabled(false)
        // 全局未捕获异常处理，落盘到日志文件
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogger.crash("UncaughtException", "线程: ${thread.name}", throwable)
            // 继续交给系统默认处理，避免未知状态持续运行
            try { originalHandler?.uncaughtException(thread, throwable) } catch (_: Exception) {}
        }
        // 清理图片缓存，避免设备残留导致广告页显示差异
        try {
            val loader = Coil.imageLoader(this)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        } catch (_: Exception) {}
    }

    /**
     * 延后：若数据库为空则插入默认配方（在首帧后后台执行，避免冷启动时创建Room）。
     */
    fun ensureDefaultRecipesAsync() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                val existing = try { database.recipeDao().getAllRecipes().first() } catch (_: Exception) { emptyList() }
                if (existing.isEmpty()) {
                    // 仅在数据库为空时插入默认配方（机器A）
                    val defaults = listOf(
                        com.example.juicemachine.data.database.Recipe(
                            name = "柳橙百香", water = 180, juice = 100, price = 9,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 2, imageUri = null, juiceType = "橙汁百香果汁",
                            waterSpeed = 60, juiceSpeed = 60
                        ),
                        com.example.juicemachine.data.database.Recipe(
                            name = "茉莉雪芽", water = 105, juice = 175, price = 8,
                            defaultRemainingWeight = 1000, currentRemainingWeight = 1000,
                            juiceChannel = 1, imageUri = null, juiceType = "牛奶绿茶",
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
                }
                // 名称迁移：检测B版名称但A版图片存在且B版图片不存在时，迁移为A版名称（仅改name，其它字段保留）
                try {
                    val ctx = this@JuiceMachineApplication
                    val aliasMap = mapOf(
                        "霸气青柠" to "柳橙百香",
                        "霸气杨梅" to "茉莉雪芽",
                        "山野栀子" to "鸭屎香柠檬茶"
                    )
                    existing.forEach { r ->
                        val aName = aliasMap[r.name]
                        if (aName != null) {
                            val bKey = when (r.name) {
                                "霸气青柠" -> "ba_qi_qing_ning"
                                "霸气杨梅" -> "ba_qi_yang_mei"
                                else -> "shan_ye_zhi_zi"
                            }
                            val aKey = when (aName) {
                                "柳橙百香" -> "liu_cheng_bai_xiang"
                                "茉莉雪芽" -> "mo_li_xue_ya"
                                else -> "ya_shi_xiang"
                            }
                            val bid = ctx.resources.getIdentifier(bKey, "drawable", ctx.packageName)
                            val aid = ctx.resources.getIdentifier(aKey, "drawable", ctx.packageName)
                            if (bid == 0 && aid != 0) {
                                database.recipeDao().updateRecipeFields(
                                    id = r.id,
                                    name = aName,
                                    water = r.water,
                                    juice = r.juice,
                                    price = r.price,
                                    defaultRemainingWeight = r.defaultRemainingWeight,
                                    currentRemainingWeight = r.currentRemainingWeight,
                                    juiceChannel = r.juiceChannel,
                                    imageUri = r.imageUri,
                                    juiceType = r.juiceType,
                                    hasPulp = r.hasPulp,
                                    pulpTotalCups = r.pulpTotalCups,
                                    pulpDecInterval = r.pulpDecInterval,
                                    pulpDecAmount = r.pulpDecAmount,
                                    waterSpeed = r.waterSpeed,
                                    juiceSpeed = r.juiceSpeed
                                )
                            }
                        }
                    }
                } catch (_: Exception) { /* ignore */ }
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
                // 机器A：仅预热这三张内置广告图
                val builtIn = listOf(
                    "android.resource://$pkg/drawable/ba_qi_qing_ning_ad",
                    "android.resource://$pkg/drawable/ba_qi_yang_mei_ad",
                    "android.resource://$pkg/drawable/shan_ye_zhi_zi_ad"
                )

                fun isReadableUri(uri: String): Boolean {
                    return try {
                        val u = Uri.parse(uri)
                        when (u.scheme) {
                            "android.resource" -> {
                                contentResolver.openInputStream(u)?.use { input ->
                                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeStream(input, null, opts)
                                    opts.outWidth > 0 && opts.outHeight > 0
                                } ?: false
                            }
                            "file" -> {
                                val f = java.io.File(u.path ?: "")
                                if (!f.canRead()) false else {
                                    kotlin.runCatching {
                                        java.io.FileInputStream(f).use { input ->
                                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                            BitmapFactory.decodeStream(input, null, opts)
                                            opts.outWidth > 0 && opts.outHeight > 0
                                        }
                                    }.getOrElse { false }
                                }
                            }
                            "content" -> {
                                contentResolver.openInputStream(u)?.use { input ->
                                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeStream(input, null, opts)
                                    opts.outWidth > 0 && opts.outHeight > 0
                                } ?: false
                            }
                            else -> false
                        }
                    } catch (_: Exception) { false }
                }

                val targets = persisted.filter { it.isNotBlank() && isReadableUri(it) }.ifEmpty { builtIn }
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
