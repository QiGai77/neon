package com.qigai.neon.provider

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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

class DeepSeekClient(
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun streamChat(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<ChatTurnMessage>,
        tools: List<ToolDefinition>,
        enableThinking: Boolean,
    ): Flow<StreamChunk> = callbackFlow {
        val bodyJson = buildJsonObject {
            put("model", model)
            put("max_tokens", 8192)
            put("stream", true)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            if (enableThinking) {
                putJsonObject("thinking") {
                    put("type", "enabled")
                    put("budget_tokens", 4096)
                }
            }
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.inputSchema)
                        }
                    }
                })
            }
            put("messages", buildMessagesArray(messages))
        }

        val req = Request.Builder()
            .url("https://api.deepseek.com/anthropic/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        var currentToolId: String? = null
        var currentToolName: String? = null
        val toolInputBuf = StringBuilder()

        val es = EventSources.createFactory(httpClient).newEventSource(req, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") return
                runCatching {
                    val obj = json.parseToJsonElement(data).jsonObject
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "content_block_start" -> {
                            val cb = obj["content_block"]?.jsonObject
                            if (cb?.get("type")?.jsonPrimitive?.content == "tool_use") {
                                currentToolId = cb["id"]?.jsonPrimitive?.content
                                currentToolName = cb["name"]?.jsonPrimitive?.content
                                toolInputBuf.clear()
                            }
                        }
                        "content_block_delta" -> {
                            val delta = obj["delta"]?.jsonObject ?: return
                            when (delta["type"]?.jsonPrimitive?.content) {
                                "thinking_delta" -> {
                                    val t = delta["thinking"]?.jsonPrimitive?.content.orEmpty()
                                    if (t.isNotEmpty()) trySend(StreamChunk.ThinkingDelta(t))
                                }
                                "text_delta" -> {
                                    val t = delta["text"]?.jsonPrimitive?.content.orEmpty()
                                    if (t.isNotEmpty()) trySend(StreamChunk.TextDelta(t))
                                }
                                "input_json_delta" -> {
                                    toolInputBuf.append(delta["partial_json"]?.jsonPrimitive?.content.orEmpty())
                                }
                            }
                        }
                        "content_block_stop" -> {
                            val tid = currentToolId
                            val tname = currentToolName
                            if (tid != null && tname != null) {
                                trySend(
                                    StreamChunk.ToolCallReady(
                                        ToolCall(tid, tname, toolInputBuf.toString().ifBlank { "{}" }),
                                    ),
                                )
                                currentToolId = null
                                currentToolName = null
                                toolInputBuf.clear()
                            }
                        }
                        "message_delta" -> {
                            val reason = obj["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.content ?: "end_turn"
                            trySend(StreamChunk.Done(reason))
                        }
                        "error" -> {
                            val msg = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: data
                            trySend(StreamChunk.Error(msg))
                        }
                    }
                }.onFailure { err ->
                    trySend(StreamChunk.Error(err.message ?: "SSE 解析错误"))
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val body = response?.body?.string()
                trySend(StreamChunk.Error(body?.ifBlank { null } ?: t?.message ?: "网络请求失败 (${response?.code})"))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        })

        awaitClose { es.cancel() }
    }

    private fun buildMessagesArray(messages: List<ChatTurnMessage>): JsonArray = buildJsonArray {
        messages.forEach { msg ->
            when (msg.role) {
                "user" -> addJsonObject {
                    put("role", "user")
                    put("content", msg.content)
                }
                "tool" -> addJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        addJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", msg.toolCallId.orEmpty())
                            put("content", msg.content)
                        }
                    })
                }
                "assistant" -> addJsonObject {
                    put("role", "assistant")
                    if (msg.toolCalls.isEmpty()) {
                        put("content", msg.content)
                    } else {
                        put("content", buildJsonArray {
                            if (msg.content.isNotBlank()) {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", msg.content)
                                }
                            }
                            msg.toolCalls.forEach { tc ->
                                addJsonObject {
                                    put("type", "tool_use")
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    val inputObj = runCatching { json.parseToJsonElement(tc.inputJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }
                                    put("input", inputObj)
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}
