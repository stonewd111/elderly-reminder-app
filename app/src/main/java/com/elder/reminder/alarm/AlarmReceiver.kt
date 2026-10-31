package com.elder.reminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.elder.reminder.R
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.repeatText

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

        val channelId = CHANNEL_ID
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "重要提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到点会全屏弹出并大声提醒"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val alarmIntent = Intent(context, AlarmActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(AlarmActivity.EXTRA_TITLE, reminder.title)
            .putExtra(AlarmActivity.EXTRA_MODE, reminder.mode.name)
            .putExtra(AlarmActivity.EXTRA_REPEAT, reminder.repeatText())
            .putExtra(AlarmActivity.EXTRA_TIME, reminder.triggerAt)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        val fullScreen = PendingIntent.getActivity(
            context, id.hashCode(), alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("到点了，请注意！")
            .setContentText(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(null)
            .setVibrate(longArrayOf(0, 600, 400, 600))
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .build()
        nm.notify(id.hashCode(), notification)

        // 通知已经带全屏 Intent；再尝试直接拉起，兼容部分厂商。
        try {
            context.startActivity(alarmIntent)
        } catch (_: Exception) {
            // 被系统拦截时，全屏通知仍是兜底
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val CHANNEL_ID = "elder_alarm"
    }
}
