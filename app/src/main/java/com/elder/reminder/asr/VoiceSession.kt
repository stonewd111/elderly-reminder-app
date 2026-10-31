package com.elder.reminder.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VoiceState(
    val active: Boolean = false,
    val starting: Boolean = false,
    val partial: String = "",
    val finished: Boolean = false,
    val finalText: String = "",
    val error: String? = null
)

/**
 * 录音 + 识别会话（全局单例，供首页大按钮与语音页共用）。
 */
object VoiceSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var running = false

    fun start(context: Context) {
        if (running) return
        if (_state.value.active && !_state.value.finished) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = VoiceState(error = "没有麦克风权限")
            return
        }

        running = true
        _state.value = VoiceState(starting = true)
        val appContext = context.applicationContext
        job = scope.launch {
            val ready = AsrEngine.awaitReady(appContext)
            if (!ready) {
                running = false
                _state.value = VoiceState(
                    error = "语音识别模型加载失败：" + (AsrEngine.error.value ?: "未知错误")
                )
                return@launch
            }
            val session = AsrEngine.newSession()
            if (session == null) {
                running = false
                _state.value = VoiceState(error = "语音识别未就绪")
                return@launch
            }
            recordLoop(session)
        }
    }

    private suspend fun recordLoop(session: SherpaSession) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = maxOf(minBuf, SAMPLE_RATE)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                bufferSize * 4
            )
        } catch (t: Throwable) {
            null
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            running = false
            _state.value = VoiceState(error = "无法打开麦克风")
            session.release()
            return
        }

        try {
            record.startRecording()
            _state.value = VoiceState(active = true)
            val buffer = FloatArray(SAMPLE_RATE / 10) // 0.1 秒
            while (running) {
                val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) continue
                session.accept(buffer, n)
                val text = session.text()
                if (text != _state.value.partial) {
                    _state.value = _state.value.copy(partial = text)
                }
                if (session.isEndpoint()) break
            }
            session.finish()
            val text = session.text()
            running = false
            _state.value = VoiceState(active = false, finished = true, finalText = text, partial = text)
        } catch (t: Throwable) {
            running = false
            _state.value = VoiceState(error = "识别出错：" + (t.message ?: ""))
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            session.release()
        }
    }

    fun stop() {
        running = false
    }

    fun reset() {
        running = false
        job?.cancel()
        job = null
        _state.value = VoiceState()
    }

    fun markError(message: String) {
        running = false
        _state.value = VoiceState(error = message)
    }

    private const val SAMPLE_RATE = 16000
}
