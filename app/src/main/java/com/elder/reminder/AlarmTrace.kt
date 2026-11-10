package com.elder.reminder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟触发链路记录（轻量文本日志）。
 *
 * 用途：区分「闹钟没送达（广播没来）」和「送达了但播报没起来」这两类问题。
 * 排查时的读法：
 *  - 到点后完全没有①「收到闹钟广播」→ 闹钟没触发（被系统清掉/冻结/未挂上）
 *  - 有①、没有③「播报服务已启动」→ 前台服务被系统拒绝启动（看②后面的报错）
 *  - 有③、没有④「播放成功」→ 播报失败（铃声/音频焦点问题）
 *  - ①②③④都有 → 铃声正常响过，属于现场没听到
 *
 * 只在本地写入，不上传，不影响正常提醒。
 */
object AlarmTrace {

    private const val FILE = "alarm_trace.log"
    private const val MAX_LINES = 600

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    @Synchronized
    fun add(context: Context?, event: String) {
        val ctx = context?.applicationContext ?: return
        runCatching {
            val f = File(ctx.filesDir, FILE)
            val line = "${fmt.format(Date())}  $event"
            val kept = (readLines(f) + line).takeLast(MAX_LINES)
            f.writeText(kept.joinToString("\n"))
        }
    }

    /** 读取最近 [count] 条，按时间正序（新的在最后）。 */
    @Synchronized
    fun latest(context: Context, count: Int = 20): String {
        val f = File(context.applicationContext.filesDir, FILE)
        return runCatching {
            readLines(f).takeLast(count).joinToString("\n")
        }.getOrDefault("")
    }

    @Synchronized
    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }

    private fun readLines(f: File): List<String> {
        if (!f.exists() || f.length() == 0L) return emptyList()
        return runCatching { f.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
    }
}
