package com.elder.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText

/**
 * 闹钟到点入口。不再直接 startActivity(AlarmActivity)（Android 10+ 后台禁止，
 * 会被系统拦截导致声音/弹窗同时失效），改为：
 *  1. 先启动 ForegroundAlarmService 前台播报服务 —— 保证锁屏/后台一定有声音
 *  2. 发高优先级通知（带 fullScreenIntent）—— 锁屏亮屏弹全屏页兜底
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val reminder = ReminderRepository.find(id) ?: return
        if (!reminder.enabled) return

        // 先安排下一次，避免重复提醒断档
        if (reminder.repeat != Reminder.Repeat.None) {
            val next = ReminderScheduler.nextTrigger(reminder, System.currentTimeMillis() + 1000)
            if (next != null) {
                val updated = reminder.copy(triggerAt = next, done = false)
                ReminderRepository.upsert(updated)
                ReminderScheduler.schedule(context, updated)
            }
        } else {
            ReminderRepository.upsert(reminder.copy(done = true, enabled = false))
        }

        // 1) 先启动前台播报服务：保证锁屏/后台一定有声音，并由服务统一发全屏通知+弹页（核心修复）。
        //    通知、fullScreenIntent、红色全屏页均由服务统一管理，避免重复/冲突。
        runCatching {
            ForegroundAlarmService.start(
                context, id, reminder.title, reminder.mode.name,
                reminder.repeatText(), reminder.triggerAt
            )
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val CHANNEL_ID = "elder_alarm"
    }
}
