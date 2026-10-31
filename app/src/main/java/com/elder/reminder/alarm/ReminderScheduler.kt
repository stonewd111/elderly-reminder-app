package com.elder.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText
import java.util.Calendar

/**
 * 使用 AlarmManager 精确定时。优先 setAlarmClock（最可靠，可穿透 Doze，状态栏显示闹钟图标）。
 */
object ReminderScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        cancel(context, reminder.id)
        if (!reminder.enabled || reminder.done) return
        val fireAt = nextTrigger(reminder, System.currentTimeMillis()) ?: return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmPi = pendingIntent(context, reminder.id)
        val showPi = showIntent(context, reminder)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, showPi), alarmPi)
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmPi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, fireAt, alarmPi)
                }
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmPi)
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, id))
    }

    fun rescheduleAll(context: Context) {
        ReminderRepositoryHolder.repo().filter { it.enabled && !it.done }
            .forEach { schedule(context, it) }
    }

    fun nextTrigger(reminder: Reminder, after: Long): Long? {
        val c = Calendar.getInstance()
        when (val repeat = reminder.repeat) {
            Reminder.Repeat.None -> {
                return if (reminder.triggerAt > after) reminder.triggerAt else null
            }
            Reminder.Repeat.Daily -> {
                // 从原定时间开始，保持时分，跳过已经过去的时间点
                var t = reminder.triggerAt
                if (t > after) return t
                val base = Calendar.getInstance().apply { timeInMillis = reminder.triggerAt }
                val today = Calendar.getInstance().apply {
                    timeInMillis = after
                    set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
                    set(Calendar.MINUTE, base.get(Calendar.MINUTE))
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return if (today.timeInMillis > after) today.timeInMillis
                else today.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            }
            is Reminder.Repeat.Weekly -> {
                val base = Calendar.getInstance().apply { timeInMillis = reminder.triggerAt }
                val targetDays = repeat.days.ifEmpty { setOf(base.get(Calendar.DAY_OF_WEEK).toIsoDay()) }
                for (offset in 0..7) {
                    val cand = Calendar.getInstance().apply {
                        timeInMillis = after
                        add(Calendar.DAY_OF_YEAR, offset)
                        set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, base.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    if (offset == 0 && cand.timeInMillis <= after) continue
                    if (cand.get(Calendar.DAY_OF_WEEK).toIsoDay() in targetDays) return cand.timeInMillis
                }
                return null
            }
            is Reminder.Repeat.Monthly -> {
                val base = Calendar.getInstance().apply { timeInMillis = reminder.triggerAt }
                val startMonth = Calendar.getInstance().apply { timeInMillis = after }
                for (offset in 0..2) {
                    val cand = Calendar.getInstance().apply {
                        timeInMillis = startMonth.timeInMillis
                        add(Calendar.MONTH, offset)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, base.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
                        set(Calendar.DAY_OF_MONTH, repeat.day.coerceAtMost(maxDay))
                    }
                    if (cand.timeInMillis > after) return cand.timeInMillis
                }
                return null
            }
            is Reminder.Repeat.Interval -> {
                val stepMs = repeat.days * 24L * 60L * 60L * 1000L
                var t = reminder.triggerAt
                if (t > after) return t
                while (t <= after) t += stepMs
                return t
            }
        }
        return c.timeInMillis
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmReceiver.EXTRA_ID, reminder.id)
            .putExtra(AlarmActivity.EXTRA_TITLE, reminder.title)
            .putExtra(AlarmActivity.EXTRA_MODE, reminder.mode.name)
            .putExtra(AlarmActivity.EXTRA_REPEAT, reminder.repeatText())
            .putExtra(AlarmActivity.EXTRA_TIME, reminder.triggerAt)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 1000 + reminder.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun Int.toIsoDay(): Int = when (this) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }
}

/** 避免 Scheduler 直接依赖仓库造成的循环，这里用一个轻量桥接。 */
internal object ReminderRepositoryHolder {
    fun repo(): List<Reminder> = com.elder.reminder.data.ReminderRepository.all()
}
