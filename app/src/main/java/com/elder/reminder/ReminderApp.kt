package com.elder.reminder

import android.app.Application
import com.elder.reminder.alarm.ReminderScheduler
import com.elder.reminder.asr.AsrEngine
import com.elder.reminder.data.ReminderRepository

class ReminderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ReminderRepository.init(this)
        ReminderScheduler.rescheduleAll(this)
        AsrEngine.preload(this)
    }
}
