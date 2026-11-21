package com.qigai.neon.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val inputJson: String,
)

@Serializable
data class ToolExecutionChip(
    val id: String,
    val label: String,
    val status: String, // "running" | "ok" | "blocked" | "error"
)

data class ChatTurnMessage(
    val role: String, // "user" | "assistant" | "tool"
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

sealed interface StreamChunk {
    data class ThinkingDelta(val text: String) : StreamChunk
    data class TextDelta(val text: String) : StreamChunk
    data class ToolCallReady(val call: ToolCall) : StreamChunk
    data class Done(val stopReason: String) : StreamChunk
    data class Error(val message: String) : StreamChunk
}

data class QuotaWindow(
    val label: String,
    val remainingPercent: Int,
    val resetText: String,
)

data class OpenCodeGoQuota(
    val fiveHour: QuotaWindow,
    val weekly: QuotaWindow,
    val monthly: QuotaWindow,
    val updatedAtMs: Long,
    val error: String? = null,
)
