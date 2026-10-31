package com.elder.reminder

import android.app.Application
import com.elder.reminder.alarm.ReminderScheduler
import com.elder.reminder.data.FontPrefs
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.data.SoundPrefs

class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ReminderRepository.init(this)
        SoundPrefs.init(this)
        FontPrefs.init(this)
        ReminderScheduler.rescheduleAll(this)
        // 语音引擎改为懒加载：进入语音页时才加载 25MB 模型，
        // 避免启动阶段在后台线程 native 崩溃（SIGSEGV 会绕过 Java 崩溃捕获，
        // 导致“进不去界面且看不到诊断页”）。见 AsrEngine.awaitReady / VoiceSession.start。
    }
}
