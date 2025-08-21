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
    fun setToastEnabled(enabled: Boolean) { toastEnabled = enabled }

    fun init(context: Context) {
        this.context = context
        // 使用应用专用目录，不需要权限
        val logDir = File(context.getExternalFilesDir(null), "debug")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "debug_logs.txt")

        // 写入启动标记
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
        logToFile(crashInfo)
        // 崩溃信息仍显示给用户
        showToastInternal("应用崩溃: $message", isLong = true)
    }

    private fun logToFile(message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $message\n"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                logFile?.appendText(logEntry)
            } catch (e: Exception) {
                Log.e("DebugLogger", "写入日志文件失败", e)
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