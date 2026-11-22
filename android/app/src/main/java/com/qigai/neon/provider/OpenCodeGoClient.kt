package com.qigai.neon.provider

import com.qigai.neon.data.ContentBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

data class GoWindow(val percent: Double, val resetsAt: Long?, val status: String?)
data class GoUsage(val rolling: GoWindow?, val weekly: GoWindow?, val monthly: GoWindow?, val fetchedAt: Long)

/**
 * OpenCode Go — OpenAI-compatible Chat Completions, called directly from the device (no CORS on Android,
 * so no Cloudflare Worker proxy). Base defaults to https://opencode.ai/inference/go
 *   chat:   {base}/openai/v1/chat/completions   (Bearer key, x-opencode-session required)
 *   models: {base}/v1/models                    (public)
 *   usage:  {base}/v1/usage                     (Bearer key; 5h / weekly / monthly windows)
 * Endpoint shapes ported from the Neon web app (src/services/opencode/go.ts) and opencode.ai/docs/go.
 */
class OpenCodeGoClient(
    private val baseUrl: () -> String,
    private val apiKey: () -> String?,
) : ModelProvider {
    override val id = ProviderId.OPENCODE_GO

    private fun key() = apiKey() ?: throw ProviderException("未配置 OpenCode Go Key")

    fun buildBody(req: ChatRequest): JsonObject = buildJsonObject {
        put("model", req.model)
        putJsonArray("messages") {
            if (req.system.isNotBlank()) addJsonObject { put("role", "system"); put("content", req.system) }
            req.messages.forEach { m ->
                val toolResults = m.blocks.filterIsInstance<ContentBlock.ToolResult>()
                toolResults.forEach { tr ->
                    addJsonObject { put("role", "tool"); put("tool_call_id", tr.toolUseId); put("content", tr.content) }
                }
                val text = m.blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
                val calls = m.blocks.filterIsInstance<ContentBlock.ToolUse>()
                if (text.isNotEmpty() || calls.isNotEmpty()) {
                    addJsonObject {
                        put("role", m.role)
                        put("content", text)
                        if (calls.isNotEmpty()) putJsonArray("tool_calls") {
                            calls.forEach { c ->
                                addJsonObject {
                                    put("id", c.id); put("type", "function")
                                    putJsonObject("function") { put("name", c.name); put("arguments", c.inputJson) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (req.tools.isNotEmpty()) putJsonArray("tools") {
            req.tools.forEach { t ->
                addJsonObject {
                    put("type", "function")
                    putJsonObject("function") { put("name", t.name); put("description", t.description); put("parameters", t.inputSchema) }
                }
            }
        }
        val efforts = req.modelInfo?.efforts.orEmpty()
        if (req.thinking && efforts.isNotEmpty()) {
            put("reasoning_effort", nearestEffort(req.effort, efforts))
        } else if (!req.thinking && "none" in efforts) {
            put("reasoning_effort", "none")
        }
        put("stream", true)
        putJsonObject("stream_options") { put("include_usage", true) }
    }

    override fun stream(req: ChatRequest): Flow<StreamEvent> = flow {
        val request = Request.Builder()
            .url("${baseUrl()}/openai/v1/chat/completions")
            .header("Authorization", "Bearer ${key()}")
            .header("x-opencode-session", sessionHeader(req.sessionId))
            .header("Accept", "text/event-stream")
            .post(buildBody(req).toString().toRequestBody(DeepSeekClient.JSON))
            .build()
        var mode = "" // "think" | "text" | "tool"
        var finish: String? = null
        var done = false
        Http.sse(request).collect { frame ->
            if (frame.data.trim() == "[DONE]") { done = true; return@collect }
            val obj = runCatching { Http.json.parseToJsonElement(frame.data).jsonObject }.getOrNull() ?: return@collect
            (obj["error"] as? JsonObject)?.str("message")?.let { throw ProviderException(it) }
            (obj["usage"] as? JsonObject)?.let { u -> emit(StreamEvent.Usage(u.int("prompt_tokens"), u.int("completion_tokens"))) }
            val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@collect
            val delta = choice["delta"] as? JsonObject
            if (delta != null) {
                val reasoning = delta.str("reasoning_content") ?: delta.str("reasoning")
                if (!reasoning.isNullOrEmpty()) {
                    if (mode != "think" && mode != "") emit(StreamEvent.BlockStop)
                    mode = "think"; emit(StreamEvent.ThinkingDelta(reasoning))
                }
                val content = delta.str("content")
                if (!content.isNullOrEmpty()) {
                    if (mode != "text" && mode != "") emit(StreamEvent.BlockStop)
                    mode = "text"; emit(StreamEvent.TextDelta(content))
                }
                (delta["tool_calls"] as? JsonArray)?.forEach { tc ->
                    val t = tc as? JsonObject ?: return@forEach
                    val fn = t["function"] as? JsonObject
                    val id = t.str("id")
                    if (id != null) {
                        if (mode != "") emit(StreamEvent.BlockStop)
                        mode = "tool"
                        emit(StreamEvent.ToolUseStart(id, fn?.str("name") ?: ""))
                    }
                    fn?.str("arguments")?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.ToolInputDelta(it)) }
                }
            }
            choice.str("finish_reason")?.let { finish = it }
        }
        if (finish == null && !done) throw ProviderException("流提前结束（连接中断）")
        if (mode != "") emit(StreamEvent.BlockStop)
        emit(StreamEvent.Stop(when (finish) {
            "length" -> "max_tokens"
            "tool_calls" -> "tool_use"
            "content_filter" -> "refusal"
            else -> "end_turn"
        }))
    }

    override suspend fun listModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        val r = Http.client.newCall(Request.Builder().url("${baseUrl()}/v1/models").get().build()).execute()
        r.use {
            if (!it.isSuccessful) throw Http.errorOf(it)
            val o = Http.json.parseToJsonElement(it.body!!.string()).jsonObject
            (o["data"] as? JsonArray).orEmpty().mapNotNull { e ->
                val m = e as? JsonObject ?: return@mapNotNull null
                val type = m.str("type")
                if (type != null && type != "chat") return@mapNotNull null
                val id = m.str("id") ?: return@mapNotNull null
                val reasoning = ((m["capabilities"] as? JsonObject)?.get("reasoning") as? JsonObject)
                val efforts = (reasoning?.get("effort") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
                ModelInfo(
                    id = id,
                    name = m.str("name") ?: id,
                    reasoning = (reasoning?.get("supported") as? JsonPrimitive)?.content == "true",
                    efforts = efforts,
                )
            }
        }
    }

    /** Real usage windows. Falls back to the legacy /zen/go path if the inference path 404s. */
    suspend fun fetchUsage(keyOverride: String? = null): GoUsage = withContext(Dispatchers.IO) {
        val k = keyOverride ?: key()
        val urls = listOf("${baseUrl()}/v1/usage", "https://opencode.ai/zen/go/v1/usage").distinct()
        var last: ProviderException? = null
        for (u in urls) {
            val r = Http.client.newCall(Request.Builder().url(u).header("Authorization", "Bearer $k").get().build()).execute()
            r.use {
                if (it.code == 404) { last = ProviderException("额度接口不存在 (404)", 404); return@use }
                if (!it.isSuccessful) {
                    if (it.code == 401) throw ProviderException("Key 无效或没有 Go 订阅 (401)", 401)
                    throw Http.errorOf(it)
                }
                return@withContext parseUsage(it.body!!.string())
            }
        }
        throw last ?: ProviderException("额度接口不可用")
    }

    companion object {
        fun sessionHeader(sessionId: String) =
            "ses_neon_" + sessionId.replace(Regex("[^A-Za-z0-9_-]"), "").take(48).ifEmpty { "default" }

        fun parseUsage(raw: String): GoUsage {
            val root = Http.json.parseToJsonElement(raw).jsonObject
            val u = (root["usage"] as? JsonObject) ?: root
            fun win(vararg keys: String): GoWindow? {
                val o = keys.firstNotNullOfOrNull { u[it] as? JsonObject } ?: return null
                val p = ((o["percent"] ?: o["usagePercent"]) as? JsonPrimitive)?.doubleOrNull ?: return null
                var resets: Long? = o.str("resetsAt")?.let { s -> runCatching { Instant.parse(s).toEpochMilli() }.getOrNull() }
                if (resets == null) {
                    (o["resetInSec"] as? JsonPrimitive)?.longOrNull?.let { resets = System.currentTimeMillis() + it * 1000 }
                }
                return GoWindow(p, resets, o.str("status"))
            }
            return GoUsage(
                rolling = win("rolling", "rollingUsage"),
                weekly = win("weekly", "weeklyUsage"),
                monthly = win("monthly", "monthlyUsage"),
                fetchedAt = System.currentTimeMillis(),
            )
        }

        /**
         * Import from a pasted key or OpenCode's auth.json (~/.local/share/opencode/auth.json).
         * Same rules as the web app's parseGoImport.
         */
        fun parseImport(text: String): String? {
            val raw = text.trim()
            if (raw.isEmpty()) return null
            if (raw.startsWith("{")) {
                return runCatching {
                    val o = Http.json.parseToJsonElement(raw).jsonObject
                    for (name in listOf("opencode-go", "opencode_go", "opencode", "go")) {
                        val k = (o[name] as? JsonObject)?.str("key")?.trim()
                        if (!k.isNullOrEmpty()) return@runCatching k
                    }
                    o.str("key")?.trim()
                }.getOrNull()
            }
            return Regex("[A-Za-z0-9_\\-.]{20,}").find(raw)?.value
        }
    }
}

/** Picks the model's own level closest to the one asked for (low < medium < high < max). */
internal fun nearestEffort(want: String, efforts: List<String>): String {
    val scale = listOf("minimal", "low", "medium", "high", "xhigh", "max")
    if (want in efforts) return want
    if (want == "max" && "xhigh" in efforts) return "xhigh"
    val w = scale.indexOf(want).coerceAtLeast(0)
    return efforts.filter { it != "none" }.minByOrNull { kotlin.math.abs(scale.indexOf(it).let { i -> if (i < 0) 3 else i } - w) } ?: efforts.last()
}
