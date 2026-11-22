package com.qigai.neon.provider

import com.qigai.neon.BuildConfig
import com.qigai.neon.data.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

enum class ProviderId(val id: String, val label: String) {
    DEEPSEEK("deepseek", "DeepSeek"),
    OPENCODE_GO("opencode-go", "OpenCode Go");

    companion object {
        fun of(id: String?): ProviderId? = entries.firstOrNull { it.id == id }
    }
}

data class ModelInfo(
    val id: String,
    val name: String = id,
    val reasoning: Boolean = true,
    /** reasoning effort values the model accepts (OpenCode Go /models), empty when unknown */
    val efforts: List<String> = emptyList(),
)

data class ChatMessage(val role: String, val blocks: List<ContentBlock>)

/** Tool declaration (Anthropic shape). */
data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject)

data class ChatRequest(
    val model: String,
    val system: String,
    val messages: List<ChatMessage>,
    val thinking: Boolean,
    /** low | medium | high | max when thinking; ignored otherwise. */
    val effort: String = "high",
    val sessionId: String,
    val wantTitle: Boolean = false,
    val tools: List<ToolSpec> = emptyList(),
    val modelInfo: ModelInfo? = null,
)

sealed class StreamEvent {
    data class ThinkingDelta(val text: String) : StreamEvent()
    data class SignatureDelta(val signature: String) : StreamEvent()
    data class TextDelta(val text: String) : StreamEvent()
    data class ToolUseStart(val id: String, val name: String, val server: Boolean = false, val inputJson: String? = null) : StreamEvent()
    data class ToolInputDelta(val partialJson: String) : StreamEvent()
    data object BlockStop : StreamEvent()
    data class Title(val title: String) : StreamEvent()
    data class Usage(val input: Int?, val output: Int?) : StreamEvent()
    data class Stop(val reason: String) : StreamEvent()
}

class ProviderException(message: String, val status: Int? = null) : Exception(message)

interface ModelProvider {
    val id: ProviderId
    fun stream(req: ChatRequest): Flow<StreamEvent>
    suspend fun listModels(): List<ModelInfo>
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "neon-android/${BuildConfig.VERSION_NAME}").build())
        }
        .build()

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    /** Turns a non-2xx response into a readable error; never includes request headers. */
    fun errorOf(r: Response): ProviderException {
        val text = runCatching { r.body?.string().orEmpty() }.getOrDefault("")
        val msg = runCatching {
            val o = json.parseToJsonElement(text).jsonObject
            (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content
                ?: o["message"]?.jsonPrimitive?.content
        }.getOrNull()
        val friendly = when (r.code) {
            401 -> "API Key 无效或已失效 (401)"
            402 -> "账户余额不足 (402)"
            429 -> "请求过于频繁或额度用尽 (429)"
            else -> null
        }
        return ProviderException(listOfNotNull(friendly, msg ?: text.take(300).ifBlank { "HTTP ${r.code}" }).distinct().joinToString(" · "), r.code)
    }

    data class Sse(val event: String?, val data: String)

    /** Executes a streaming request and emits SSE frames. Cancelling the collector cancels the call. */
    fun sse(request: Request): Flow<Sse> = flow {
        val call: Call = client.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        val resp = try {
            call.execute()
        } catch (e: java.io.IOException) {
            if (call.isCanceled()) throw kotlinx.coroutines.CancellationException("cancelled")
            throw ProviderException("网络错误：${e.message ?: e.javaClass.simpleName}")
        }
        resp.use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val ct = r.header("Content-Type").orEmpty()
            val source = r.body?.source() ?: throw ProviderException("空响应")
            if (!ct.contains("event-stream")) {
                val body = source.readUtf8()
                throw ProviderException("响应不是事件流：" + body.take(300))
            }
            var event: String? = null
            val data = StringBuilder()
            while (true) {
                val line = try {
                    source.readUtf8Line()
                } catch (e: java.io.IOException) {
                    if (call.isCanceled()) throw kotlinx.coroutines.CancellationException("cancelled")
                    throw ProviderException("连接中断：${e.message ?: ""}")
                } ?: break
                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) emit(Sse(event, data.toString()))
                        event = null
                        data.setLength(0)
                    }
                    line.startsWith(":") -> Unit
                    line.startsWith("event:") -> event = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).removePrefix(" "))
                    }
                }
            }
            if (data.isNotEmpty()) emit(Sse(event, data.toString()))
        }
    }.flowOn(Dispatchers.IO)
}
