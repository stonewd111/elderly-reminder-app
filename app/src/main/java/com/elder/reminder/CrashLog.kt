package com.elder.reminder

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 捕获未处理崩溃并写入本地文件，供诊断“进不去界面”类问题。
 * 正式版可保留（静默记录，不影响正常使用）。
 */
object CrashLog {
    private const val FILE = "crash.log"

    fun install(context: Context) {
        val ctx = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                val text = "=== $ts ===\n线程: ${thread.name}\n${sw.toString()}"
                File(ctx.filesDir, FILE).writeText(text)
            }
            previous?.uncaughtException(thread, throwable)
                ?: run { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(1) }
        }
    }

    /** 读取最近一次崩溃堆栈，无则返回 null。 */
    fun latest(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        return if (f.exists() && f.length() > 0) f.readText().take(4000) else null
    }

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE).delete()
    }
}
