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
import android.net.Uri
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
import com.elder.reminder.data.SoundPrefs
import java.util.Locale

/**
 * 前台播报服务（核心修复）：
 *  - 保证锁屏/后台也有声音（不依赖 Activity 能否启动）
 *  - 支持同一时刻多个提醒并存：每个提醒有独立通知（独立 fullScreenIntent + 独立通知ID），
 *    停止其中一个不影响其他；全部停止后才真正停止服务。
 *  - 修复 Android 12+ “startForegroundService 后 5 秒内未调用 startForeground”崩溃：
 *    startForeground 第一时间调用；MediaPlayer.prepare()（同步 IO）移到后台线程，不阻塞主线程。
 *  - 按 SoundPrefs 全局音效设置播报：system 闹铃 / male 男声 / female 女声 / custom 自定义MP3，
 *    失败逐级降级（custom→system→TTS→滴滴）。
 */
class ForegroundAlarmService : Service(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var title = "提醒事项"
    private var modeText = "VOICE"
    private var repeatText = ""
    private var startedAt = 0L
    private var stopped = false
    private var ttsReady = false
    private var playingStarted = false
    private val timeMap = linkedMapOf<Long, Long>()

    /** 当前活跃的提醒 id 集合（支持同一时刻多个提醒）。 */
    private val activeAlarms = linkedSetOf<Long>()

    private val stopRunnable = Runnable { stopAll() }

    private val repeatVoice = object : Runnable {
        override fun run() {
            if (stopped) return
            if (System.currentTimeMillis() - startedAt >= DURATION_MS) { stopAll(); return }
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
            if (System.currentTimeMillis() - startedAt >= DURATION_MS) { stopAll(); return }
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
        // 防御：任何路径都确保 startForeground 已调用（Android 12+ 5 秒限制）。
        // 用最简通知先行 startForeground，避免后续耗时操作导致超时崩溃。
        if (!stopped || intent?.action == ACTION_START) {
            runCatching { startForegroundCompat(buildForegroundNotification()) }
        }

        if (intent == null) {
            stopAll()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_STOP -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)
                if (id >= 0) stopOne(id) else stopAll()
            }
            ACTION_START -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)
                title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒事项"
                modeText = intent.getStringExtra(EXTRA_MODE) ?: "VOICE"
                repeatText = intent.getStringExtra(EXTRA_REPEAT) ?: ""
                val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
                // 加入活跃集合；若首次启动则初始化播报
                val first = activeAlarms.isEmpty()
                if (id >= 0) {
                    activeAlarms.add(id)
                    timeMap[id] = time
                }
                if (first) {
                    startedAt = System.currentTimeMillis()
                    stopped = false
                    playingStarted = false
                    maxAlarmVolume()
                    startVibration()
                    // 应用在前台时立即弹红色全屏页；后台交给 fullScreenIntent 兜底
                    tryStartAlarmActivity(intent)
                    // 播放放后台线程，避免同步 prepare() 阻塞主线程导致 5 秒超时崩溃
                    Thread { playByPrefs() }.start()
                    handler.postDelayed(stopRunnable, DURATION_MS)
                } else {
                    // 已有提醒在播，仅为新提醒补发全屏通知 + 尝试弹页
                    tryStartAlarmActivity(intent)
                }
                // 为每个活跃提醒发独立通知（各自 fullScreenIntent），停止一个不影响其他
                refreshNotifications()
            }
        }
        return START_NOT_STICKY
    }

    /** 按 SoundPrefs 全局音效设置播报：system / male / female / custom。失败逐级降级。 */
    private fun playByPrefs() {
        if (stopped) return
        val type = SoundPrefs.effectiveType()
        val ok = when (type) {
            SoundPrefs.TYPE_CUSTOM -> startCustomLoop()
            SoundPrefs.TYPE_MALE -> startResourceLoop(R.raw.alarm_voice_male)
            SoundPrefs.TYPE_FEMALE -> startResourceLoop(R.raw.alarm_voice)
            else -> startPlayerLoop()
        }
        if (ok) return
        // 降级链：custom→系统闹铃→TTS→滴滴
        if (type == SoundPrefs.TYPE_CUSTOM && startPlayerLoop()) return
        handler.post {
            if (stopped) return@post
            tts = TextToSpeech(this, this)
        }
    }

    /** 循环播放内置人声资源。成功返回 true；失败返回 false。 */
    private fun startResourceLoop(resId: Int): Boolean {
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val afd = resources.openRawResourceFd(resId)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                prepare()
                start()
            }
            playingStarted = true
            true
        }.getOrDefault(false)
    }

    /** 循环播放自定义 MP3（content URI，已持久化读权限）。成功返回 true；失败返回 false。 */
    private fun startCustomLoop(): Boolean {
        val path = SoundPrefs.customPath.value
        if (path.isBlank()) return false
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@ForegroundAlarmService, Uri.parse(path))
                isLooping = true
                prepare()
                start()
            }
            playingStarted = true
            true
        }.getOrDefault(false)
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
            playingStarted = true
            true
        }.getOrDefault(false)
    }

    /** 为每个活跃提醒发独立通知（各自 fullScreenIntent）。 */
    private fun refreshNotifications() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        activeAlarms.forEach { id ->
            runCatching { nm.notify(notifId(id), buildAlarmNotification(id)) }
        }
    }

    private fun notifId(id: Long): Int = id.hashCode()

    private fun timeOf(id: Long): Long = timeMap[id] ?: System.currentTimeMillis()

    private fun alarmIntentFor(id: Long): Intent {
        return Intent(this, AlarmActivity::class.java)
            .putExtra(AlarmReceiver.EXTRA_ID, id)
            .putExtra(AlarmActivity.EXTRA_TITLE, title)
            .putExtra(AlarmActivity.EXTRA_MODE, modeText)
            .putExtra(AlarmActivity.EXTRA_REPEAT, repeatText)
            .putExtra(AlarmActivity.EXTRA_TIME, timeOf(id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /** 尝试启动红色全屏页（仅当应用在前台时才可能成功，后台交给 fullScreenIntent）。 */
    private fun tryStartAlarmActivity(src: Intent?) {
        if (stopped) return
        val id = src?.getLongExtra(AlarmReceiver.EXTRA_ID, -1L) ?: -1L
        if (id < 0) return
        val alarmIntent = alarmIntentFor(id)
            .putExtra(AlarmActivity.EXTRA_TITLE, src?.getStringExtra(EXTRA_TITLE) ?: title)
        runCatching { startActivity(alarmIntent) }
    }

    private fun buildAlarmNotification(id: Long): Notification {
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
        val fullScreen = PendingIntent.getActivity(
            this, id.hashCode(), alarmIntentFor(id),
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
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .build()
    }

    /** 前台服务常驻通知（最简，仅用于满足前台服务要求）。 */
    private fun buildForegroundNotification(): Notification {
        val channelId = AlarmReceiver.CHANNEL_ID
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "重要提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到点会全屏弹出并大声提醒"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)
        }
        val fullScreen = PendingIntent.getActivity(
            this, NOTIF_ID, alarmIntentFor(activeAlarms.firstOrNull() ?: -1L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("提醒进行中")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }
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
        }.onFailure { stopAll() }
    }

    /** 停止某个提醒（取消其通知）；若没有其他活跃提醒则停止服务。 */
    private fun stopOne(id: Long) {
        activeAlarms.remove(id)
        timeMap.remove(id)
        runCatching {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(notifId(id))
        }
        if (activeAlarms.isEmpty()) stopAll()
    }

    private fun stopAll() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { tone?.stopTone(); tone?.release() }
        runCatching { player?.stop(); player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
            activeAlarms.forEach { nm.cancel(notifId(it)) }
        }
        activeAlarms.clear()
        stopForegroundCompat()
        stopSelf()
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopAll()
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

        /** 停止某个提醒的播报（全屏页打开后或用户点击停止时调用）。 */
        fun stop(context: Context, id: Long = -1L) {
            val intent = Intent(context, ForegroundAlarmService::class.java).apply {
                action = ACTION_STOP
                putExtra(AlarmReceiver.EXTRA_ID, id)
            }
            context.startService(intent)
        }
    }
}
