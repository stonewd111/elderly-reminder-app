package com.elder.reminder.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.elder.reminder.ui.alarm.AlarmScreen

/**
 * 到点提醒全屏红色界面（纯视觉）。
 * 声音统一由 ForegroundAlarmService 播报（锁屏/后台也能响），
 * 本页不再自行播报声音，避免与前台服务双重声音/竞态。
 * 支持锁屏显示（showWhenLocked + turnScreenOn）以及在其他应用前台时弹出。
 */
class AlarmActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        val screenTitle = intent.getStringExtra(EXTRA_TITLE) ?: "提醒事项"
        val screenMode = intent.getStringExtra(EXTRA_MODE) ?: "VOICE"
        val screenRepeat = intent.getStringExtra(EXTRA_REPEAT) ?: ""
        val screenTime = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val reminderId = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)

        setContentView(
            androidx.compose.ui.platform.ComposeView(this).apply {
                setContent {
                    val level by com.elder.reminder.data.FontPrefs.level.collectAsState()
                    val theme by com.elder.reminder.data.AlarmThemePrefs.theme.collectAsState()
                    // 所有分类都显示完成按钮：文案按分类区分，便于记录"本次已完成"
                    val reminder = com.elder.reminder.data.ReminderRepository.find(reminderId)
                    val takenText = when (reminder?.category) {
                        com.elder.reminder.model.Reminder.Category.MEDICATION -> "已服用"
                        com.elder.reminder.model.Reminder.Category.HEALTH -> "已测量"
                        com.elder.reminder.model.Reminder.Category.WATER -> "已喝水"
                        else -> "已完成"
                    }
                    com.elder.reminder.ui.theme.ElderTheme(scale = com.elder.reminder.data.FontPrefs.scaleOf(level)) {
                        AlarmScreen(
                            title = screenTitle,
                            modeText = if (screenMode == "VOICE") "语音提醒" else "滴滴滴提醒",
                            repeatText = screenRepeat,
                            timeMillis = screenTime,
                            alarmTheme = theme,
                            onStop = { finishAlarm() },
                            onTaken = {
                                markTaken(reminderId)
                                finishAlarm()
                            },
                            takenText = takenText
                        )
                    }
                }
            }
        )
        handler.postDelayed({ finishAlarm() }, ForegroundAlarmService.DURATION_MS)
    }

    /** 点"已服用"：单次提醒永久完成；重复提醒只标记本次完成，保留下一次自动循环。 */
    private fun markTaken(id: Long) {
        if (id < 0) return
        val r = com.elder.reminder.data.ReminderRepository.find(id) ?: return
        if (r.repeat != com.elder.reminder.model.Reminder.Repeat.None) {
            // 重复提醒：下一次闹钟已在响铃时排好，这里只记录"本次已完成"，绝不能取消下一次
            com.elder.reminder.data.ReminderRepository.upsert(
                r.copy(done = true, doneAt = System.currentTimeMillis(), delayed = false, triggered = false)
            )
        } else {
            com.elder.reminder.data.ReminderRepository.upsert(
                r.copy(done = true, doneAt = System.currentTimeMillis(), delayed = false, triggered = false, enabled = false)
            )
            com.elder.reminder.alarm.ReminderScheduler.cancel(this, id)
        }
    }

    private fun finishAlarm() {
        val id = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
        // 只停止本条提醒的播报（同一时刻多个提醒并存时不影响其他）
        runCatching { ForegroundAlarmService.stop(this, id) }
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(id.hashCode())
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_MODE = "alarm_mode"
        const val EXTRA_REPEAT = "alarm_repeat"
        const val EXTRA_TIME = "alarm_time"
    }
}
