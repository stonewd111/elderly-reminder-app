package com.elder.reminder.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.elder.reminder.AlarmTrace
import com.elder.reminder.MainActivity
import com.elder.reminder.data.ReminderRepository
import com.elder.reminder.model.Reminder
import com.elder.reminder.model.isDoneNow
import com.elder.reminder.model.repeatText

/**
 * 常驻守护服务：一条低调的常驻通知换两件事——
 *
 *  1. 进程被抬成前台优先级，OEM（ColorOS 等）在熄屏/清理后台时不容易把应用冻结、
 *     连带清掉 AlarmManager 里的闹钟（表现为"到点连广播都没有"）。
 *  2. 每 30 秒自查一次：发现"该响却没响"的提醒立刻补响；
 *     每 10 分钟把全部闹钟重新挂一遍，修复被系统清掉的定时项。
 */
class GuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var ticks = 0

    private val tick = object : Runnable {
        override fun run() {
            runCatching { checkMissed() }
            ticks++
            if (ticks % REARM_EVERY_TICKS == 0) {
                runCatching { ReminderScheduler.rearm(this@GuardService) }
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("提醒守护运行中")
            .setContentText("保证到点必响；此通知常驻，请勿关闭")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        if (!started) {
            started = true
            AlarmTrace.add(this, "🛡 守护服务已启动")
            handler.postDelayed(tick, FIRST_DELAY_MS)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 补漏：把"应响时刻已过、却没有任何响铃记录"的提醒找出来。
     * 到点 15 分钟内的立刻补响；更早就只记延误（避免半天后突然响一堆）。
     */
    private fun checkMissed() {
        val now = System.currentTimeMillis()
        ReminderRepository.all().forEach { r ->
            if (!r.enabled || r.delayed || r.isDoneNow(now)) return@forEach
            val due = ReminderScheduler.lastDueBefore(r, now) ?: return@forEach
            if (due <= r.createdAt) return@forEach         // 这条例提醒建立之前的那次，不存在漏报
            if (r.lastFiredAt >= due) return@forEach      // 这次确实响过
            if (due > now - MISSED_GRACE_MS) return@forEach // 刚到点，可能还在派发

            if (now - due > CATCH_UP_MS) {
                // 错过太久：不补响，只标记为延误，让用户看得见。
                // 但只处理"今天该响的那次"——昨天的旧账不翻，否则会把今天还没到点的提醒也挪进延误。
                if (r.repeat != Reminder.Repeat.None && !ReminderScheduler.sameDay(due, now)) return@forEach
                val marked = if (r.repeat == Reminder.Repeat.None) {
                    r.copy(delayed = true, triggered = false, enabled = false)
                } else {
                    r.copy(delayed = true, triggered = false, done = false, doneAt = 0L)
                }
                ReminderRepository.upsert(marked)
                AlarmTrace.add(this, "⚠️ 补记延误（错过较久）「${r.title}」")
                return@forEach
            }

            // 到点 15 分钟内：立刻补响
            AlarmTrace.add(this, "⑦ 守护发现漏报，立即补响「${r.title}」")
            ring(r, now)
        }
    }

    /** 与 AlarmReceiver 的到点处理保持一致，避免状态错乱。 */
    private fun ring(reminder: Reminder, now: Long) {
        if (reminder.repeat != Reminder.Repeat.None) {
            ReminderScheduler.nextTrigger(reminder, now + 1000)?.let { next ->
                ReminderScheduler.schedule(this, reminder.copy(triggerAt = next))
            }
            ReminderRepository.upsert(
                reminder.copy(
                    triggered = true, enabled = true,
                    done = false, doneAt = 0L, delayed = false,
                    lastFiredAt = now
                )
            )
        } else {
            ReminderRepository.upsert(
                reminder.copy(triggered = true, enabled = false, lastFiredAt = now)
            )
        }
        val err = runCatching {
            ForegroundAlarmService.start(
                this, reminder.id, reminder.title, reminder.mode.name,
                reminder.repeatText(), reminder.triggerAt,
                reminder.soundType, reminder.soundUri
            )
        }.exceptionOrNull()
        AlarmTrace.add(
            this,
            if (err == null) "⑧ 补响服务已调用「${reminder.title}」"
            else "⑧ 补响服务启动失败：${err.javaClass.simpleName} ${err.message}"
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "提醒守护", NotificationManager.IMPORTANCE_MIN).apply {
            description = "常驻守护服务，保证提醒准时触发"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        private const val CHANNEL_ID = "elder_guard"
        private const val NOTIF_ID = 0xE1D4
        private const val TICK_MS = 30_000L
        private const val FIRST_DELAY_MS = 10_000L
        private const val REARM_EVERY_TICKS = 20
        /** 到点后 15 分钟内可以补响，超过只记延误 */
        private const val CATCH_UP_MS = 15 * 60 * 1000L
        /** 宽限期：到点 1 分钟内不动，避免与正常派发抢状态 */
        private const val MISSED_GRACE_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                AlarmTrace.add(context, "⚠️ 守护服务启动失败：${it.javaClass.simpleName} ${it.message}")
            }
        }

        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return am.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == GuardService::class.java.name
            }
        }
    }
}
