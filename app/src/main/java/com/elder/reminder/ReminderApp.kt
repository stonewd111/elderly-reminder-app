package com.elder.reminder

import android.app.Application
import com.elder.reminder.alarm.ReminderScheduler
import com.elder.reminder.data.AlarmThemePrefs
import com.elder.reminder.data.CategoryIconPrefs
import com.elder.reminder.data.CommonCategoryPrefs
import com.elder.reminder.data.FontPrefs
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.data.SoundPrefs
import com.elder.reminder.model.Reminder

class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ReminderRepository.init(this)
        SoundPrefs.init(this)
        FontPrefs.init(this)
        AlarmThemePrefs.init(this)
        CommonCategoryPrefs.init(this)
        CategoryIconPrefs.init(this)
        markMissedAlarms()
        ReminderScheduler.rescheduleAll(this)
        // 语音引擎改为懒加载：进入语音页时才加载 25MB 模型，
        // 避免启动阶段在后台线程 native 崩溃（SIGSEGV 会绕过 Java 崩溃捕获，
        // 导致“进不去界面且看不到诊断页”）。见 AsrEngine.awaitReady / VoiceSession.start。
    }

    /**
     * 启动自检：把"响过但没被任何按钮处理"的提醒补记为延误。
     * 覆盖进程被后台划掉、被系统/OEM 强杀、关机期间错过等情况——
     * 这些情况下 stopAll 的延误标记可能来不及执行。
     */
    private fun markMissedAlarms() {
        val now = System.currentTimeMillis()
        ReminderRepository.all().forEach { r ->
            if (r.done || r.delayed) return@forEach
            when {
                // 单次：响过没处理，或早已过点却从未响过（强停/关机期间错过）
                r.repeat == Reminder.Repeat.None &&
                    (r.triggered || (r.enabled && r.triggerAt <= now)) ->
                    ReminderRepository.upsert(
                        r.copy(delayed = true, triggered = false, enabled = false)
                    )
                // 重复：响过没处理 → 本次记延误；enabled 保持不变，后续照常提醒
                r.repeat != Reminder.Repeat.None && r.enabled && r.triggered ->
                    ReminderRepository.upsert(
                        r.copy(delayed = true, triggered = false, done = false, doneAt = 0L)
                    )
            }
        }
    }
}
