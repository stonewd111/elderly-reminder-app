package com.elder.reminder.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class AsrStatus { IDLE, LOADING, READY, ERROR }

/**
 * 内置离线语音识别（sherpa-onnx + 中文流式 zipformer）。
 * 模型随 APK 打包在 assets/model，首次运行复制到内部存储后加载。
 */
object AsrEngine {
    private const val ASSET_DIR = "model"
    private const val MODEL_DIR = "asr"
    private const val SAMPLE_RATE = 16000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recognizer: OnlineRecognizer? = null
    private var loading: Deferred<Boolean>? = null

    private val _status = MutableStateFlow(AsrStatus.IDLE)
    val status: StateFlow<AsrStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun preload(context: Context) {
        if (recognizer != null) return
        val current = loading
        if (current != null && current.isActive) return
        val ctx = context.applicationContext
        loading = scope.async { load(ctx) }
    }

    suspend fun awaitReady(context: Context): Boolean {
        preload(context)
        val job = loading ?: return recognizer != null
        return job.await()
    }

    fun newSession(): SherpaSession? {
        val rec = recognizer ?: return null
        return SherpaSession(rec, SAMPLE_RATE)
    }

    private fun load(ctx: Context): Boolean {
        _status.value = AsrStatus.LOADING
        return try {
            val dir = File(ctx.filesDir, MODEL_DIR).apply { mkdirs() }
            copyAsset(ctx, "$ASSET_DIR/encoder.onnx", File(dir, "encoder.onnx"))
            copyAsset(ctx, "$ASSET_DIR/decoder.onnx", File(dir, "decoder.onnx"))
            copyAsset(ctx, "$ASSET_DIR/joiner.onnx", File(dir, "joiner.onnx"))
            copyAsset(ctx, "$ASSET_DIR/tokens.txt", File(dir, "tokens.txt"))

            val config = OnlineRecognizerConfig()
            config.featConfig.sampleRate = SAMPLE_RATE
            config.featConfig.featureDim = 80
            config.modelConfig.transducer.encoder = File(dir, "encoder.onnx").absolutePath
            config.modelConfig.transducer.decoder = File(dir, "decoder.onnx").absolutePath
            config.modelConfig.transducer.joiner = File(dir, "joiner.onnx").absolutePath
            config.modelConfig.tokens = File(dir, "tokens.txt").absolutePath
            config.modelConfig.numThreads = 2
            config.modelConfig.provider = "cpu"
            config.modelConfig.debug = false
            config.modelConfig.modelType = "zipformer"

            config.enableEndpoint = true
            config.endpointConfig.rule1 = EndpointRule(false, 2.4f, 0.0f)
            config.endpointConfig.rule2 = EndpointRule(true, 0.9f, 0.0f)
            config.endpointConfig.rule3 = EndpointRule(false, 0.0f, 30.0f)
            config.decodingMethod = "greedy_search"
            config.maxActivePaths = 4

            // 模型已复制到 filesDir，用绝对路径（file 模式）加载。
            // 注意：不要用 OnlineRecognizer(assets, config)——asset 模式要求相对路径，
            // 与 filesDir 绝对路径不匹配会导致 native 崩溃。
            // 显式传 null（第一个参数 assetManager）走 newFromFile，用绝对路径。
            recognizer = OnlineRecognizer(null, config)
            _status.value = AsrStatus.READY
            true
        } catch (t: Throwable) {
            _error.value = t.message ?: "语音识别加载失败"
            _status.value = AsrStatus.ERROR
            false
        }
    }

    private fun copyAsset(ctx: Context, assetPath: String, target: File) {
        if (target.exists() && target.length() > 0) return
        val tmp = File(target.parentFile, target.name + ".part")
        ctx.assets.open(assetPath).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }
}

/** 单次录音识别会话。 */
class SherpaSession(
    private val recognizer: OnlineRecognizer,
    private val sampleRate: Int
) {
    private val stream = recognizer.createStream()
    private var finished = false

    fun accept(samples: FloatArray, count: Int) {
        val chunk = if (count == samples.size) samples else samples.copyOf(count)
        stream.acceptWaveform(chunk, sampleRate)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
    }

    fun text(): String = clean(recognizer.getResult(stream).text)

    fun isEndpoint(): Boolean = recognizer.isEndpoint(stream)

    fun finish() {
        if (finished) return
        finished = true
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
    }

    fun release() {
        runCatching { stream.release() }
    }

    private fun clean(raw: String): String =
        raw.replace(" ", "").replace("<unk>", "").trim()
}
