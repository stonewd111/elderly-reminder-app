package com.elder.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                com.elder.reminder.AlarmTrace.add(context, "🔄 系统事件 ${intent.action}，重新排全部闹钟")
                com.elder.reminder.data.ReminderRepository.reload()
                ReminderScheduler.rescheduleAll(context)
            }
        }
    }
}
