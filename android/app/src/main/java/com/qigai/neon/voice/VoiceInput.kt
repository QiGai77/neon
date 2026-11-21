package com.qigai.neon.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

data class VoiceState(
    val recording: Boolean = false,
    /** 40 real amplitude bars, 0..1, newest last */
    val levels: List<Float> = List(VoiceInput.BARS) { 0f },
    val startedAt: Long = 0L,
    val transcribing: Boolean = false,
    val engine: String = "",
    val error: String? = null,
)

/**
 * S01b voice input. Bars are always real microphone levels:
 *  - engine "deepseek": AudioRecord PCM16 16 kHz → per-chunk RMS → bars; on confirm the PCM is wrapped as WAV
 *    and sent to DeepSeek ASR (needs a DeepSeek key).
 *  - engine "system": Android SpeechRecognizer (when no DeepSeek key); bars come from its onRmsChanged.
 * Two engines because AudioRecord and SpeechRecognizer can't reliably share the mic on many devices.
 */
class VoiceInput(
    private val context: Context,
    private val scope: CoroutineScope,
    private val asrProvider: () -> (suspend (ByteArray) -> String)?,
) {
    companion object {
        const val BARS = 40
        const val RATE = 16000
        const val MAX_MS = 120_000L
    }

    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var job: Job? = null
    private var record: AudioRecord? = null
    private var pcm = ByteArrayOutputStream()
    private var recognizer: SpeechRecognizer? = null
    private var onText: ((String) -> Unit)? = null
    private var pendingConfirm = false
    private var lastPartial = ""
    private var asr: (suspend (ByteArray) -> String)? = null

    private fun push(level: Float) {
        val s = _state.value
        _state.value = s.copy(levels = (s.levels + level.coerceIn(0f, 1f)).takeLast(BARS))
    }

    fun systemRecognizerAvailable() = SpeechRecognizer.isRecognitionAvailable(context)

    @SuppressLint("MissingPermission")
    fun start(onText: (String) -> Unit) {
        if (_state.value.recording) return
        this.onText = onText
        pendingConfirm = false
        lastPartial = ""
        asr = asrProvider()
        if (asr != null) startAudioRecord()
        else if (systemRecognizerAvailable()) startRecognizer()
        else _state.value = VoiceState(error = "本机没有语音识别，连接 DeepSeek 后可用")
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecord() {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE))
        } catch (e: Exception) {
            _state.value = VoiceState(error = "无法打开麦克风：${e.message}"); return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); _state.value = VoiceState(error = "麦克风初始化失败"); return
        }
        record = rec
        pcm = ByteArrayOutputStream()
        _state.value = VoiceState(recording = true, startedAt = System.currentTimeMillis(), engine = "DeepSeek ASR")
        rec.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val chunk = ShortArray(RATE / 20) // 50 ms
            val bb = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (isActive && _state.value.recording) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                var sum = 0.0
                bb.clear()
                for (i in 0 until n) { val v = chunk[i]; sum += v * v.toDouble(); bb.putShort(v) }
                pcm.write(bb.array(), 0, n * 2)
                val rms = sqrt(sum / n) / 32768.0
                // dBFS (-60..0) → 0..1
                val db = if (rms > 0) 20 * log10(rms) else -90.0
                push(((db + 60) / 60).toFloat())
                if (System.currentTimeMillis() - _state.value.startedAt > MAX_MS) break
            }
        }
    }

    private fun startRecognizer() {
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        _state.value = VoiceState(recording = true, startedAt = System.currentTimeMillis(), engine = "系统语音识别")
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { push((rmsdB + 2f) / 12f) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (lastPartial.isNotBlank()) null else "没有听清，请再说一次"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络错误"
                    else -> "语音识别错误 $error"
                }
                if (lastPartial.isNotBlank()) onText?.invoke(lastPartial)
                finishRecognizer(msg)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().ifBlank { lastPartial }
                if (text.isNotBlank()) onText?.invoke(text)
                finishRecognizer(null)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) lastPartial = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
        r.startListening(intent)
    }

    private fun finishRecognizer(error: String?) {
        recognizer?.destroy(); recognizer = null
        _state.value = VoiceState(error = error)
    }

    /** ✓ — stop and transcribe. */
    fun confirm() {
        val s = _state.value
        if (!s.recording) return
        if (recognizer != null) {
            _state.value = s.copy(recording = false, transcribing = true)
            recognizer?.stopListening() // results arrive in onResults
            return
        }
        val rec = record ?: return
        _state.value = s.copy(recording = false, transcribing = true)
        scope.launch {
            job?.join()
            runCatching { rec.stop() }; rec.release(); record = null
            val wav = wav(pcm.toByteArray())
            val res = runCatching { withContext(Dispatchers.IO) { asr!!.invoke(wav) } }
            res.onSuccess { t -> if (t.isNotBlank()) onText?.invoke(t) }
            _state.value = VoiceState(error = res.exceptionOrNull()?.let { "转写失败：${it.message}" })
        }
    }

    /** ✕ — discard. */
    fun cancel() {
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
        _state.value = _state.value.copy(recording = false)
        val rec = record
        scope.launch {
            job?.join()
            rec?.let { runCatching { it.stop() }; it.release() }
            record = null
            _state.value = VoiceState()
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun wav(data: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(44 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + data.size); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data.size); b.put(data)
        return b.array()
    }
}
