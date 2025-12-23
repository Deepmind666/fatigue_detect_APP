package com.example.juicemachine.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object DebugLogger {
    private var context: Context? = null
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    // 新增：全局Toast开关（默认关闭，避免频繁弹窗干扰）
    @Volatile private var toastEnabled: Boolean = false
    // 新增：文件日志开关（默认关闭以提升性能）
    @Volatile private var fileLoggingEnabled: Boolean = false
    // 提供轻量开关给热点路径判断是否打印详细日志
    fun isVerboseEnabled(): Boolean = fileLoggingEnabled
    fun setToastEnabled(enabled: Boolean) { toastEnabled = enabled }
    fun setFileLoggingEnabled(enabled: Boolean) { fileLoggingEnabled = enabled }

    fun init(context: Context) {
        // 始终使用 ApplicationContext，避免持有 Activity 导致泄漏
        this.context = context.applicationContext
        // 冷启动避免触发外部存储挂载与目录创建：仅计算路径，不做任何磁盘 I/O
        logFile = File(File(context.filesDir, "debug"), "debug_logs.txt")

        // 写入启动标记（文件不可用则自动忽略）
        logToFile("============ APP 启动 ============")
        logToFile("时间: ${dateFormat.format(Date())}")
        logToFile("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        logToFile("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        logToFile("=====================================")
    }

    fun d(tag: String, message: String, showToast: Boolean = false) {
        Log.d(tag, message)
        logToFile("D/$tag: $message")
        if (showToast && toastEnabled) {
            showToastInternal(message)
        }
    }

    fun i(tag: String, message: String, showToast: Boolean = false) {
        Log.i(tag, message)
        logToFile("I/$tag: $message")
        if (showToast && toastEnabled) {
            showToastInternal(message)
        }
    }

    fun w(tag: String, message: String, showToast: Boolean = false) {
        Log.w(tag, message)
        logToFile("W/$tag: $message")
        if (showToast && toastEnabled) {
            showToastInternal(message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, showToast: Boolean = true) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n异常: ${throwable.javaClass.simpleName}: ${throwable.message}\n堆栈:\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        logToFile("E/$tag: $fullMessage")
        if (showToast && toastEnabled) {
            showToastInternal("错误: $message")
        }
    }

    fun crash(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, "CRASH: $message", throwable)
        val crashInfo = """
            |============ 崩溃日志 ============
            |时间: ${dateFormat.format(Date())}
            |标签: $tag
            |消息: $message
            |异常: ${throwable.javaClass.name}
            |异常消息: ${throwable.message}
            |堆栈跟踪:
            |${Log.getStackTraceString(throwable)}
            |=================================""".trimMargin()
        // 崩溃日志强制写入，即使关闭文件日志
        forceLogToFile(crashInfo)
        // 屏显更详细的异常摘要，帮助现场定位
        val brief = "${throwable.javaClass.simpleName}: ${throwable.message}".take(120)
        showToastInternal("应用崩溃: $message\n$brief", isLong = true)
    }

    private fun logToFile(message: String) {
        if (!fileLoggingEnabled) return
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message\n"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val f = logFile
                if (f != null) {
                    try { f.parentFile?.mkdirs() } catch (_: Exception) {}
                    f.appendText(logEntry)
                }
            } catch (e: Exception) {
                Log.e("DebugLogger", "写入日志文件失败", e)
            }
        }
    }

    private fun forceLogToFile(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message\n"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val f = logFile
                if (f != null) {
                    try { f.parentFile?.mkdirs() } catch (_: Exception) {}
                    f.appendText(logEntry)
                }
            } catch (e: Exception) {
                Log.e("DebugLogger", "写入崩溃日志失败", e)
            }
        }
    }

    private fun showToastInternal(message: String, isLong: Boolean = false) {
        val ctx = context ?: return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val duration = if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                Toast.makeText(ctx, message, duration).show()
            } catch (e: Exception) {
                Log.e("DebugLogger", "显示Toast失败", e)
            }
        }
    }

    fun getLogFile(): File? = logFile

    fun clearLogs() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                logFile?.writeText("")
                logToFile("============ 日志已清空 ============")
            } catch (e: Exception) {
                Log.e("DebugLogger", "清空日志失败", e)
            }
        }
    }
}
