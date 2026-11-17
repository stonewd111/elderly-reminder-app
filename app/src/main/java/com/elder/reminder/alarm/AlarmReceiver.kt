package com.elder.reminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.elder.reminder.AlarmTrace
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
        val isBackup = intent.getBooleanExtra(EXTRA_BACKUP, false)
        // ① 收到广播就立刻记一笔：没有这条就说明闹钟根本没触发
        AlarmTrace.add(
            context,
            if (isBackup) "① 收到兜底闹钟 id=$id" else "① 收到闹钟广播 id=$id"
        )
        val reminder = ReminderRepository.find(id)
        if (reminder == null) {
            AlarmTrace.add(context, "② 中止：仓库里找不到该提醒 id=$id")
            return
        }
        if (!reminder.enabled) {
            AlarmTrace.add(context, "② 中止：提醒已被停用「${reminder.title}」")
            return
        }
        // 兜底闹钟去重：主闹钟已在 10 分钟内正常响过，就不再响第二遍
        if (isBackup && System.currentTimeMillis() - reminder.lastFiredAt < BACKUP_DEDUPE_MS) {
            AlarmTrace.add(context, "② 兜底闹钟：主闹钟已响过，忽略「${reminder.title}」")
            return
        }
        // 迟到观测：重复提醒按规则反推"最近一次该响的时刻"，只记录不改排期
        if (!isBackup && reminder.repeat != Reminder.Repeat.None) {
            val now0 = System.currentTimeMillis()
            val due = ReminderScheduler.lastDueBefore(reminder, now0)
            if (due != null && now0 - due > 2 * 60 * 1000L) {
                val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(java.util.Date(due))
                AlarmTrace.add(
                    context,
                    "① 本次迟到 ${(now0 - due) / 60000} 分钟送达（系统延迟，原定 $hm；下次仍按原定时间排）"
                )
            }
        }

        // 先标记"已触发"（本次响铃中/已响过），待用户处理：
        //  - 点"已服用"/停止 → 完成（markDone）
        //  - 无人工触发、2 分钟自动停止 → 延误（markDelayed）
        // 重复提醒：调度下次（用下次时间副本给调度器，不写回仓库，保留本次状态供显示）；
        //          同时把上一轮的"本次已完成/延误"状态清掉，自动重新进入队列。
        if (reminder.repeat != Reminder.Repeat.None) {
            val now = System.currentTimeMillis()
            val next = ReminderScheduler.nextTrigger(reminder, now + 1000)
            if (next != null) {
                ReminderScheduler.schedule(context, reminder.copy(triggerAt = next))
                ReminderRepository.upsert(
                    reminder.copy(
                        triggered = true, enabled = true,
                        done = false, doneAt = 0L, delayed = false,
                        lastFiredAt = now
                    )
                )
                AlarmTrace.add(context, "② 重复提醒已排下次「${reminder.title}」")
            } else {
                AlarmTrace.add(context, "② 重复提醒算不出下次时间「${reminder.title}」")
            }
        } else {
            ReminderRepository.upsert(
                reminder.copy(triggered = true, enabled = false, lastFiredAt = System.currentTimeMillis())
            )
        }

        // 1) 先启动前台播报服务：保证锁屏/后台一定有声音，并由服务统一发全屏通知+弹页（核心修复）。
        //    通知、fullScreenIntent、红色全屏页均由服务统一管理，避免重复/冲突。
        //    这里必须记录失败原因：前台服务在后台被系统拒绝启动时，之前会被 runCatching 静默吞掉，
        //    表现就是"没声音、也不进延误"。
        val err = runCatching {
            ForegroundAlarmService.start(
                context, id, reminder.title, reminder.mode.name,
                reminder.repeatText(), reminder.triggerAt,
                reminder.soundType, reminder.soundUri
            )
        }.exceptionOrNull()
        AlarmTrace.add(
            context,
            if (err == null) "③ 已调用前台播报服务「${reminder.title}」"
            else "③ 前台播报服务启动失败：${err.javaClass.simpleName} ${err.message}"
        )
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_BACKUP = "reminder_backup"
        const val CHANNEL_ID = "elder_alarm"

        /** 兜底闹钟的去重窗口：主闹钟在这个时间内响过就不再重复响。 */
        private const val BACKUP_DEDUPE_MS = 10 * 60 * 1000L
    }
}
