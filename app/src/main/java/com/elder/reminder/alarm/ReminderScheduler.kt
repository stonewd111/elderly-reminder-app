package com.elder.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elder.reminder.AlarmTrace
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 使用 AlarmManager 精确定时。优先 setAlarmClock（最可靠，可穿透 Doze，状态栏显示闹钟图标）。
 */
object ReminderScheduler {

    private val TIME_FMT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

    fun schedule(context: Context, reminder: Reminder, trace: Boolean = true) {
        fun log(msg: String) {
            if (trace) AlarmTrace.add(context, msg)
        }
        cancel(context, reminder.id)
        // 重复提醒的 done 只代表"本次已完成"，不影响下一次排期；单次提醒完成即不再排
        if (!reminder.enabled || (reminder.done && reminder.repeat == Reminder.Repeat.None)) return
        val fireAt = nextTrigger(reminder, System.currentTimeMillis())
        if (fireAt == null) {
            log("⚠️ 算不出下次时间，未排期「${reminder.title}」")
            return
        }

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmPi = pendingIntent(context, reminder.id)
        val showPi = showIntent(context, reminder)
        val at = TIME_FMT.format(java.util.Date(fireAt))
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, showPi), alarmPi)
            log("⏰ 已挂闹钟「${reminder.title}」→ $at（setAlarmClock）")
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmPi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, fireAt, alarmPi)
                }
                log("⏰ 已挂闹钟「${reminder.title}」→ $at（降级 setExact，原因 ${e.javaClass.simpleName}）")
            } catch (e2: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, alarmPi)
                log("⚠️ 已挂闹钟「${reminder.title}」→ $at（降级为非精确，可能延后，原因 ${e2.javaClass.simpleName}）")
            }
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, id))
    }

    fun rescheduleAll(context: Context) {
        // 只跳过被人工暂停（enabled=false）的提醒；重复提醒即使本次已完成也要继续排下一次
        ReminderRepositoryHolder.repo().filter { it.enabled }
            .forEach { schedule(context, it) }
    }

    /**
     * 守护式重排：进程还活着时定期把所有闹钟重新挂一遍，
     * 修复被 OEM 清理/冻结时清掉的 AlarmManager 项。
     * 跳过马上要响的（[minAheadMs] 内不动），避免把正在派发的广播取消掉。
     */
    fun rearm(context: Context, minAheadMs: Long = 5 * 60 * 1000L) {
        val now = System.currentTimeMillis()
        ReminderRepositoryHolder.repo().forEach { r ->
            val next = nextTrigger(r, now) ?: return@forEach
            if (next - now < minAheadMs) return@forEach
            schedule(context, r, trace = false)
        }
    }

    /**
     * 最近一次"本应已经响过"的时刻（严格 <= now）。没有则返回 null。
     * 重复提醒的 triggerAt 只在创建时定过，之后不会前进，所以不能直接拿 triggerAt 判断漏报，
     * 必须按周期规则把最近一次到点时刻反推出来。
     */
    fun lastDueBefore(reminder: Reminder, now: Long, lookBackDays: Int = 40): Long? {
        var cursor = now - lookBackDays * 24L * 60L * 60L * 1000L
        var last: Long? = null
        var i = 0
        while (i++ < 400) {
            val next = nextTrigger(reminder, cursor) ?: return last
            if (next > now) return last
            last = next
            cursor = next
        }
        return last
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
