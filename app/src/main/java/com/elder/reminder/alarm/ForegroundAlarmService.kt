package com.elder.reminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.elder.reminder.R
import java.util.Locale

/**
 * 前台播报服务（核心修复）：闹钟到点先启动本服务播报声音，
 * 不依赖 AlarmActivity 能否启动。Android 10+ 后台禁止直接 startActivity，
 * 若只靠 Activity 播报声音，锁屏/后台时 Activity 被拦截 → 声音和弹窗同时失效。
 *
 * 本服务负责：
 *  - 播报声音（语音 TTS 循环 / 滴滴 / 系统闹钟铃声循环），锁屏后台也能响
 *  - 常驻高优先级前台通知，带 fullScreenIntent（锁屏亮屏弹全屏页兜底）
 *  - tryStartAlarmActivity()：应用在前台时立即弹红色全屏页
 */
class ForegroundAlarmService : Service(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var mode = "VOICE"
    private var title = "提醒事项"
    private var startedAt = 0L
    private var stopped = false
    private var ttsReady = false

    private val stopRunnable = Runnable { stopAlarm() }

    private val repeatVoice = object : Runnable {
        override fun run() {
            if (stopped) return
            if (System.currentTimeMillis() - startedAt >= DURATION_MS) { stopAlarm(); return }
            if (ttsReady) {
                tts?.speak("到点了，请注意！", TextToSpeech.QUEUE_FLUSH, null, "elder_alarm")
                handler.postDelayed(this, 5000)
            } else {
                handler.postDelayed(this, 5000)
            }
        }
    }

    private val repeatBeep = object : Runnable {
        override fun run() {
            if (stopped) return
            if (System.currentTimeMillis() - startedAt >= DURATION_MS) { stopAlarm(); return }
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 600) }
            handler.postDelayed(this, 900)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopAlarm()
            return START_NOT_STICKY
        }
        this.intent = intent

        title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒事项"
        mode = intent.getStringExtra(EXTRA_MODE) ?: "VOICE"
        startedAt = System.currentTimeMillis()
        stopped = false

        startForegroundCompat(buildNotification())

        maxAlarmVolume()
        startVibration()

        // 应用在前台时立即弹红色全屏页；后台被拦截时由 fullScreenIntent 兜底
        tryStartAlarmActivity()

        if (mode == "VOICE") {
            tts = TextToSpeech(this, this)
        } else {
            startBeep()
        }
        handler.postDelayed(stopRunnable, DURATION_MS)
        return START_NOT_STICKY
    }

    private var intent: Intent? = null
    private val intentId: Long
        get() = intent?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L

    /** 尝试启动红色全屏页（仅当应用在前台时才可能成功，后台交给 fullScreenIntent）。 */
    private fun tryStartAlarmActivity() {
        if (stopped) return
        val id = intentId
        if (id < 0) return
        val alarmIntent = Intent(this, AlarmActivity::class.java)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .putExtra(AlarmActivity.EXTRA_MODE, mode)
            .putExtra(AlarmActivity.EXTRA_REPEAT, intent?.getStringExtra(EXTRA_REPEAT) ?: "")
            .putExtra(AlarmActivity.EXTRA_TIME, intent?.getLongExtra(EXTRA_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        runCatching { startActivity(alarmIntent) }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val channelId = AlarmReceiver.CHANNEL_ID
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        val id = intentId
        val alarmIntent = Intent(this, AlarmActivity::class.java)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .putExtra(AlarmActivity.EXTRA_MODE, mode)
            .putExtra(AlarmActivity.EXTRA_REPEAT, intent?.getStringExtra(EXTRA_REPEAT) ?: "")
            .putExtra(AlarmActivity.EXTRA_TIME, intent?.getLongExtra(EXTRA_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val fullScreen = PendingIntent.getActivity(
            this, id.hashCode(), alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("到点了，请注意！")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // 锁屏亮屏弹全屏页兜底：即使后台启动 Activity 被拦截，全屏通知也能拉起。
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsReady = true
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setSpeechRate(0.9f)
                handler.post(repeatVoice)
            } else {
                startBeep()
            }
        } else {
            startBeep()
        }
    }

    private fun startBeep() {
        if (startPlayerLoop()) return
        runCatching {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            handler.post(repeatBeep)
        }.onFailure { stopAlarm() }
    }

    /** 循环播放系统闹钟铃声，声音响亮、不依赖 TTS/ToneGenerator 硬件。 */
    private fun startPlayerLoop(): Boolean {
        return runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ForegroundAlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
            true
        }.getOrDefault(false)
    }

    private fun maxAlarmVolume() {
        runCatching {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
    }

    private fun startVibration() {
        runCatching {
            val pattern = longArrayOf(0, 700, 500, 700, 500)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopAlarm() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { tone?.stopTone(); tone?.release() }
        runCatching { player?.stop(); player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        runCatching { (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID) }
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.elder.reminder.ALARM_START"
        const val ACTION_STOP = "com.elder.reminder.ALARM_STOP"
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_MODE = "alarm_mode"
        const val EXTRA_REPEAT = "alarm_repeat"
        const val EXTRA_TIME = "alarm_time"
        const val NOTIF_ID = 0xE1D3
        const val DURATION_MS = 120_000L

        /** 启动前台播报服务（保证锁屏/后台也有声音）。 */
        fun start(context: Context, id: Long, title: String, mode: String, repeat: String, time: Long) {
            val intent = Intent(context, ForegroundAlarmService::class.java).apply {
                action = ACTION_START
                putExtra(AlarmReceiver.EXTRA_ID, id)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_REPEAT, repeat)
                putExtra(EXTRA_TIME, time)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** 停止播报（全屏页打开后或用户点击停止时调用，避免声音重复）。 */
        fun stop(context: Context) {
            val intent = Intent(context, ForegroundAlarmService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
