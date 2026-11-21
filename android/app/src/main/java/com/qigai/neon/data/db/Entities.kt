package com.qigai.neon.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val driveFileId: String? = null,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["sessionId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "user" | "assistant" | "tool"
    val content: String,
    val thinking: String? = null,
    val thinkingDurationMs: Long = 0L,
    val toolCallsJson: String? = null,
    val terminalSnippet: String? = null,
    val status: String = "done", // "streaming" | "thinking" | "waiting_guard" | "done" | "error"
    val createdAt: Long,
)

@Entity(tableName = "connectors")
data class ConnectorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // "cloud" | "remote_mcp" | "local_stdio" | "native" | "automation"
    val subtitle: String,
    val endpointUrl: String? = null,
    val status: String = "disconnected", // "disconnected" | "connecting" | "connected" | "error"
    val toolCount: Int = 0,
    val toolsJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "hands_traces",
    indices = [Index(value = ["runId", "stepIndex"])],
)
data class HandsTraceEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sessionId: String,
    val stepIndex: Int,
    val engine: String, // "accessibility" | "shizuku"
    val targetPackage: String,
    val actionType: String, // "tap" | "input" | "swipe" | "open_app" | "back" | "tool"
    val targetLabel: String,
    val detail: String,
    val status: String, // "ok" | "blocked" | "failed"
    val createdAt: Long,
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val skillName: String,
    val prompt: String,
    val scheduleLabel: String,
    val intervalMinutes: Long,
    val requiresWifi: Boolean = false,
    val requiresScreenOn: Boolean = false,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_receipts",
    indices = [Index(value = ["taskId", "executedAt"])],
)
data class TaskReceiptEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val sessionId: String?,
    val status: String, // "success" | "failed" | "needs_unlock"
    val summary: String,
    val emailsScanned: Int = 0,
    val eventsSynced: Int = 0,
    val durationMs: Long = 0L,
    val executedAt: Long,
)
