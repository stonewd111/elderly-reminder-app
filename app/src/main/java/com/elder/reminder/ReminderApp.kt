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
import com.elder.reminder.model.isDoneNow

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
        // 注意：漏报自检（markMissedAlarms）不能放在这里。
        // 闹钟到点时，系统会先创建进程、回调 Application.onCreate，随后才派发
        // AlarmReceiver。若在此把"刚过点"的单次提醒判定为延误并置 enabled=false，
        // AlarmReceiver 会因为 !enabled 直接 return，表现为"到点不响、直接进延误队列"。
        // 自检改为仅在用户打开界面时执行（MainActivity.onCreate -> markMissedAlarms）。
        ReminderScheduler.rescheduleAll(this)
        // 语音引擎改为懒加载：进入语音页时才加载 25MB 模型，
        // 避免启动阶段在后台线程 native 崩溃（SIGSEGV 会绕过 Java 崩溃捕获，
        // 导致“进不去界面且看不到诊断页”）。见 AsrEngine.awaitReady / VoiceSession.start。
    }

    /**
     * 启动自检：把"响过但没被任何按钮处理"的提醒补记为延误。
     * 覆盖进程被后台划掉、被系统/OEM 强杀、关机期间错过等情况——
     * 这些情况下 stopAll 的延误标记可能来不及执行。
     *
     * 必须只在用户打开 App 界面时调用（MainActivity.onCreate），
     * 不能放进 Application.onCreate（否则会抢在 AlarmReceiver 之前把刚响的提醒
     * 置为延误+停用，导致到点不响）。
     *
     * 同时保留 [MISSED_GRACE_MS] 宽限期：距原定时间不足 2 分钟（即可能正在
     * 派发或正在响铃）的提醒一律不动，避免与到点流程抢状态。
     */
    internal fun markMissedAlarms() {
        val now = System.currentTimeMillis()
        ReminderRepository.all().forEach { r ->
            if (!r.enabled || r.delayed || r.isDoneNow(now)) return@forEach
            // 按周期规则反推"最近一次本应响的时刻"。重复提醒的 triggerAt 不会前进，不能直接用它判断。
            val due = ReminderScheduler.lastDueBefore(r, now) ?: return@forEach
            if (r.lastFiredAt >= due) return@forEach          // 这次确实响过
            if (due > now - MISSED_GRACE_MS) return@forEach    // 刚到点，可能还在派发
            when {
                // 单次：从未响过或响过没处理 → 记延误并停用
                r.repeat == Reminder.Repeat.None ->
                    ReminderRepository.upsert(
                        r.copy(delayed = true, triggered = false, enabled = false)
                    )
                // 重复：本次漏了 → 记延误；enabled 不变，后续周期照常提醒
                else ->
                    ReminderRepository.upsert(
                        r.copy(delayed = true, triggered = false, done = false, doneAt = 0L)
                    )
            }
        }
    }

    private companion object {
        /** 宽限期，与 ForegroundAlarmService 的响铃时长（2 分钟）一致。 */
        const val MISSED_GRACE_MS = 2 * 60 * 1000L
    }
}
