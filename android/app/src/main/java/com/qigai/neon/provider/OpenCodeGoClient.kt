package com.qigai.neon.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * OpenCode Go 原生直连客户端（无浏览器 CORS 限制，无需代理）。
 */
class OpenCodeGoClient(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun streamChat(
        apiKey: String,
        model: String,
        sessionId: String,
        systemPrompt: String,
        messages: List<ChatTurnMessage>,
        tools: List<ToolDefinition>,
    ): Flow<StreamChunk> = callbackFlow {
        val bodyJson = buildJsonObject {
            put("model", model)
            put("stream", true)
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.inputSchema)
                            }
                        }
                    }
                })
            }
            put("messages", buildOpenAiMessages(systemPrompt, messages))
        }

        val req = Request.Builder()
            .url("https://opencode.ai/zen/go/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("x-opencode-session", sessionId)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val toolBuffers = mutableMapOf<Int, Triple<String, String, StringBuilder>>()

        val es = EventSources.createFactory(httpClient).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    toolBuffers.values.forEach { (tid, name, args) ->
                        trySend(StreamChunk.ToolCallReady(ToolCall(tid, name, args.toString().ifBlank { "{}" })))
                    }
                    toolBuffers.clear()
                    trySend(StreamChunk.Done("end_turn"))
                    return
                }
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return
                    val delta = choice["delta"]?.jsonObject ?: return

                    val reasoning = delta["reasoning_content"]?.jsonPrimitive?.content
                        ?: delta["reasoning"]?.jsonPrimitive?.content
                    if (!reasoning.isNullOrEmpty()) {
                        trySend(StreamChunk.ThinkingDelta(reasoning))
                    }

                    val content = delta["content"]?.jsonPrimitive?.content
                    if (!content.isNullOrEmpty()) {
                        trySend(StreamChunk.TextDelta(content))
                    }

                    delta["tool_calls"]?.jsonArray?.forEach { tcEl ->
                        val tc = tcEl.jsonObject
                        val idx = tc["index"]?.jsonPrimitive?.intOrNull ?: 0
                        val fn = tc["function"]?.jsonObject
                        val existing = toolBuffers[idx]
                        val tid = tc["id"]?.jsonPrimitive?.content ?: existing?.first ?: "call_$idx"
                        val name = fn?.get("name")?.jsonPrimitive?.content ?: existing?.second.orEmpty()
                        val sb = existing?.third ?: StringBuilder()
                        sb.append(fn?.get("arguments")?.jsonPrimitive?.content.orEmpty())
                        toolBuffers[idx] = Triple(tid, name, sb)
                    }
                }.onFailure { err ->
                    trySend(StreamChunk.Error(err.message ?: "OpenCode Go 流解析异常"))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errBody = response?.body?.string()
                trySend(StreamChunk.Error(errBody?.ifBlank { null } ?: t?.message ?: "OpenCode Go 连接失败 (${response?.code})"))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        })

        awaitClose { es.cancel() }
    }

    suspend fun fetchQuota(apiKey: String): OpenCodeGoQuota = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext OpenCodeGoQuota(
                fiveHour = QuotaWindow("5 小时", 0, "未配置 Key"),
                weekly = QuotaWindow("本周", 0, "未配置 Key"),
                monthly = QuotaWindow("本月", 0, "未配置 Key"),
                updatedAtMs = System.currentTimeMillis(),
                error = "未配置 OpenCode Go API Key",
            )
        }
        runCatching {
            val req = Request.Builder()
                .url("https://opencode.ai/zen/go/v1/quota")
                .header("Authorization", "Bearer $apiKey")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code}: $raw")
                val obj = json.parseToJsonElement(raw).jsonObject
                fun parseWin(key: String, label: String): QuotaWindow {
                    val w = obj[key]?.jsonObject
                    val pct = w?.get("remaining_percent")?.jsonPrimitive?.intOrNull
                        ?: w?.get("remaining")?.jsonPrimitive?.intOrNull
                        ?: 0
                    val reset = w?.get("reset_at")?.jsonPrimitive?.content ?: "—"
                    return QuotaWindow(label, pct.coerceIn(0, 100), reset)
                }
                OpenCodeGoQuota(
                    fiveHour = parseWin("five_hour", "5 小时"),
                    weekly = parseWin("weekly", "本周"),
                    monthly = parseWin("monthly", "本月"),
                    updatedAtMs = System.currentTimeMillis(),
                )
            }
        }.getOrElse { e ->
            OpenCodeGoQuota(
                fiveHour = QuotaWindow("5 小时", 0, "—"),
                weekly = QuotaWindow("本周", 0, "—"),
                monthly = QuotaWindow("本月", 0, "—"),
                updatedAtMs = System.currentTimeMillis(),
                error = e.message ?: "额度查询失败",
            )
        }
    }

    fun extractKeyFromAuthJson(rawJson: String): String? = runCatching {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        obj["opencode"]?.jsonObject?.get("key")?.jsonPrimitive?.content
            ?: obj["opencode_go"]?.jsonObject?.get("key")?.jsonPrimitive?.content
            ?: obj["api_key"]?.jsonPrimitive?.content
            ?: obj["key"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun buildOpenAiMessages(systemPrompt: String, messages: List<ChatTurnMessage>): JsonArray = buildJsonArray {
        if (systemPrompt.isNotBlank()) {
            addJsonObject {
                put("role", "system")
                put("content", systemPrompt)
            }
        }
        messages.forEach { msg ->
            when (msg.role) {
                "user" -> addJsonObject {
                    put("role", "user")
                    put("content", msg.content)
                }
                "tool" -> addJsonObject {
                    put("role", "tool")
                    put("tool_call_id", msg.toolCallId.orEmpty())
                    put("content", msg.content)
                }
                "assistant" -> addJsonObject {
                    put("role", "assistant")
                    put("content", msg.content)
                    if (msg.toolCalls.isNotEmpty()) {
                        put("tool_calls", buildJsonArray {
                            msg.toolCalls.forEach { tc ->
                                addJsonObject {
                                    put("id", tc.id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", tc.name)
                                        put("arguments", tc.inputJson)
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}
