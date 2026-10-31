package com.elder.reminder.alarm

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import com.elder.reminder.ui.alarm.AlarmScreen
import java.util.Locale

/**
 * 到点提醒：全屏红色界面 + 声音（语音/滴滴）+ 震动。
 * 支持锁屏显示（showWhenLocked + turnScreenOn）以及在其他应用前台时弹出全屏通知。
 */
class AlarmActivity : Activity(), TextToSpeech.OnInitListener {

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private var mode = "VOICE"
    private var title = "提醒事项"
    private var startedAt = 0L
    private var stopped = false
    private var ttsReady = false

    private val stopRunnable = Runnable { finishAlarm() }

    private val repeatVoice = object : Runnable {
        override fun run() {
            if (stopped) return
            if (System.currentTimeMillis() - startedAt >= DURATION_MS) {
                finishAlarm(); return
            }
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
            if (System.currentTimeMillis() - startedAt >= DURATION_MS) {
                finishAlarm(); return
            }
            runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 600) }
            handler.postDelayed(this, 900)
        }
    }

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

        title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒事项"
        mode = intent.getStringExtra(EXTRA_MODE) ?: "VOICE"
        val repeat = intent.getStringExtra(EXTRA_REPEAT) ?: ""
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, -1L)

        startedAt = System.currentTimeMillis()
        maxAlarmVolume()
        startVibration()

        if (Build.VERSION.SDK_INT >= 33) {
            // 已展示全屏，可移除常驻通知
            runCatching {
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())
            }
        }

        setContentSafely()

        if (mode == "VOICE") {
            tts = TextToSpeech(this, this)
        } else {
            startBeep()
        }
        handler.postDelayed(stopRunnable, DURATION_MS)
    }

    private fun setContentSafely() {
        val activity = this
        val screenTitle = title
        val screenMode = if (mode == "VOICE") "语音提醒" else "滴滴滴提醒"
        val screenRepeat = intent.getStringExtra(EXTRA_REPEAT) ?: ""
        val screenTime = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        setContentView(
            androidx.compose.ui.platform.ComposeView(activity).apply {
                setContent {
                    AlarmScreen(
                        title = screenTitle,
                        modeText = screenMode,
                        repeatText = screenRepeat,
                        timeMillis = screenTime,
                        onStop = { finishAlarm() }
                    )
                }
            }
        )
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
        runCatching {
            tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            handler.post(repeatBeep)
        }.onFailure { finishAlarm() }
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
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 700, 500, 700, 500)
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun finishAlarm() {
        if (stopped) return
        stopped = true
        handler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { tone?.stopTone(); tone?.release() }
        runCatching {
            (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        }
        finish()
    }

    override fun onDestroy() {
        if (!stopped) {
            handler.removeCallbacksAndMessages(null)
            runCatching { tts?.stop(); tts?.shutdown() }
            runCatching { tone?.stopTone(); tone?.release() }
            runCatching { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_MODE = "alarm_mode"
        const val EXTRA_REPEAT = "alarm_repeat"
        const val EXTRA_TIME = "alarm_time"
        const val DURATION_MS = 120_000L
    }
}
