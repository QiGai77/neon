package com.qigai.neon.provider

import com.qigai.neon.data.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * DeepSeek native, Anthropic-compatible Messages API.
 * POST {base}/anthropic/v1/messages, x-api-key, stream:true.
 * Docs: https://api-docs.deepseek.com/guides/anthropic_api
 */
class DeepSeekClient(
    private val baseUrl: () -> String,
    private val apiKey: () -> String?,
) : ModelProvider {
    override val id = ProviderId.DEEPSEEK

    private fun key() = apiKey() ?: throw ProviderException("未配置 DeepSeek API Key")

    fun buildBody(req: ChatRequest): JsonObject = buildJsonObject {
        put("model", req.model)
        put("max_tokens", 32768)
        if (req.system.isNotBlank()) put("system", req.system)
        put("messages", buildJsonArray {
            req.messages.forEach { m -> add(messageJson(m)) }
        })
        putJsonObject("thinking") { put("type", if (req.thinking) "enabled" else "disabled") }
        if (req.wantTitle) putJsonObject("title") { put("enabled", true) }
        if (req.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                req.tools.forEach { t ->
                    addJsonObject {
                        put("name", t.name)
                        put("description", t.description)
                        put("input_schema", t.inputSchema)
                    }
                }
            })
        }
        put("stream", true)
    }

    private fun messageJson(m: ChatMessage): JsonObject = buildJsonObject {
        put("role", m.role)
        put("content", buildJsonArray {
            m.blocks.forEach { b ->
                when (b) {
                    is ContentBlock.Text -> if (b.text.isNotEmpty()) addJsonObject { put("type", "text"); put("text", b.text) }
                    is ContentBlock.Thinking -> addJsonObject {
                        put("type", "thinking"); put("thinking", b.thinking); put("signature", b.signature ?: "")
                    }
                    is ContentBlock.ToolUse -> addJsonObject {
                        put("type", "tool_use"); put("id", b.id); put("name", b.name)
                        put("input", runCatching { Http.json.parseToJsonElement(b.inputJson) }.getOrDefault(JsonObject(emptyMap())))
                    }
                    is ContentBlock.ToolResult -> addJsonObject {
                        put("type", "tool_result"); put("tool_use_id", b.toolUseId); put("content", b.content)
                        if (b.isError) put("is_error", true)
                    }
                    is ContentBlock.ServerToolUse -> Unit
                }
            }
        })
    }

    override fun stream(req: ChatRequest): Flow<StreamEvent> = flow {
        val body = buildBody(req).toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url("${baseUrl()}/anthropic/v1/messages")
            .header("x-api-key", key())
            .header("anthropic-version", "2023-06-01")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()
        var stopped = false
        Http.sse(request).collect { frame ->
            val payload: JsonElement? = runCatching { Http.json.parseToJsonElement(frame.data) }.getOrNull()
            val obj = payload as? JsonObject
            val err = (obj?.get("error") as? JsonObject)?.get("message")?.jsonPrimitive?.content
            if (err != null) throw ProviderException(err)
            if (frame.event == "error") throw ProviderException(frame.data.take(300))
            if (obj == null) return@collect
            when (obj.str("type")) {
                "message_start" -> {
                    val u = (obj["message"] as? JsonObject)?.get("usage") as? JsonObject
                    if (u != null) emit(StreamEvent.Usage(u.int("input_tokens"), null))
                }
                "content_block_start" -> {
                    val b = obj["content_block"] as? JsonObject ?: return@collect
                    when (b.str("type")) {
                        "text" -> b.str("text")?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.TextDelta(it)) }
                        "thinking" -> b.str("thinking")?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.ThinkingDelta(it)) }
                        "tool_use" -> emit(StreamEvent.ToolUseStart(b.str("id") ?: "", b.str("name") ?: "", false, null))
                        "server_tool_use" -> emit(StreamEvent.ToolUseStart(b.str("id") ?: "", b.str("name") ?: "", true, b["input"]?.toString()))
                    }
                }
                "content_block_delta" -> {
                    val d = obj["delta"] as? JsonObject ?: return@collect
                    when (d.str("type")) {
                        "text_delta" -> d.str("text")?.let { emit(StreamEvent.TextDelta(it)) }
                        "thinking_delta" -> d.str("thinking")?.let { emit(StreamEvent.ThinkingDelta(it)) }
                        "signature_delta" -> d.str("signature")?.let { emit(StreamEvent.SignatureDelta(it)) }
                        "input_json_delta" -> d.str("partial_json")?.let { emit(StreamEvent.ToolInputDelta(it)) }
                    }
                }
                "content_block_stop" -> emit(StreamEvent.BlockStop)
                "message_delta" -> {
                    val d = obj["delta"] as? JsonObject
                    d?.str("title")?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.Title(it)) }
                    val u = obj["usage"] as? JsonObject
                    if (u != null) emit(StreamEvent.Usage(u.int("input_tokens"), u.int("output_tokens")))
                    d?.str("stop_reason")?.let { emit(StreamEvent.Stop(it)); stopped = true }
                }
                "message_stop" -> {
                    if (!stopped) emit(StreamEvent.Stop("end_turn"))
                    stopped = true
                }
            }
        }
        if (!stopped) throw ProviderException("流在 message_stop 之前结束（连接中断）")
    }

    /** GET {base}/models (OpenAI-format list, Bearer). Free; also used to validate a key. */
    override suspend fun listModels(): List<ModelInfo> = listModelsWith(key())

    suspend fun listModelsWith(key: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val r = Http.client.newCall(
            Request.Builder().url("${baseUrl()}/models").header("Authorization", "Bearer $key").get().build()
        ).execute()
        r.use {
            if (!it.isSuccessful) throw Http.errorOf(it)
            val o = Http.json.parseToJsonElement(it.body!!.string()).jsonObject
            (o["data"] as? JsonArray).orEmpty().mapNotNull { e ->
                val m = e as? JsonObject ?: return@mapNotNull null
                val id = m.str("id") ?: return@mapNotNull null
                ModelInfo(id = id, name = id, reasoning = true)
            }
        }
    }

    /** DeepSeek ASR: POST {base}/v1/tools/asr, multipart WAV (mono 16-bit), returns {text}. */
    suspend fun transcribe(wav: ByteArray): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "recording.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val r = Http.client.newCall(
            Request.Builder().url("${baseUrl()}/v1/tools/asr").header("Authorization", "Bearer ${key()}").post(body).build()
        ).execute()
        r.use {
            if (!it.isSuccessful) throw Http.errorOf(it)
            val raw = it.body!!.string()
            runCatching { Http.json.parseToJsonElement(raw).jsonObject.str("text") }.getOrNull()
                ?: throw ProviderException("ASR 返回格式异常：" + raw.take(200))
        }
    }

    companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
