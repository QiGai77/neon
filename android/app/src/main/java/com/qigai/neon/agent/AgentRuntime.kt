package com.qigai.neon.agent

import com.qigai.neon.data.db.MessageDao
import com.qigai.neon.data.db.MessageEntity
import com.qigai.neon.data.db.SessionDao
import com.qigai.neon.data.db.SessionEntity
import com.qigai.neon.oauth.EncryptedTokenStore
import com.qigai.neon.provider.ChatTurnMessage
import com.qigai.neon.provider.DeepSeekClient
import com.qigai.neon.provider.OpenCodeGoClient
import com.qigai.neon.provider.StreamChunk
import com.qigai.neon.provider.ToolCall
import com.qigai.neon.provider.ToolExecutionChip
import com.qigai.neon.skills.SkillRepository
import com.qigai.neon.sysfiles.ChatDriveSync
import com.qigai.neon.sysfiles.MemoryManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class AgentRuntime(
    private val scope: CoroutineScope,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val tokenStore: EncryptedTokenStore,
    private val deepSeek: DeepSeekClient,
    private val openCodeGo: OpenCodeGoClient,
    private val toolRegistry: ToolRegistry,
    private val skillRepository: SkillRepository,
    private val memoryManager: MemoryManager,
    private val chatDriveSync: ChatDriveSync,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var activeJob: Job? = null

    fun stopCurrentRun() {
        activeJob?.cancel()
        activeJob = null
        _isRunning.value = false
    }

    fun sendUserMessage(
        sessionId: String,
        userText: String,
        enableThinking: Boolean,
        enableHands: Boolean,
    ) {
        if (userText.isBlank() || _isRunning.value) return
        activeJob = scope.launch {
            _isRunning.value = true
            try {
                runConversationTurn(sessionId, userText.trim(), enableThinking, enableHands)
            } catch (_: CancellationException) {
                // Stopped by user
            } finally {
                _isRunning.value = false
            }
        }
    }

    suspend fun runHeadlessPrompt(sessionId: String, prompt: String): String {
        runConversationTurn(sessionId, prompt, enableThinking = false, enableHands = false)
        val msgs = messageDao.listBySession(sessionId)
        return msgs.lastOrNull { it.role == "assistant" }?.content.orEmpty()
    }

    private suspend fun runConversationTurn(
        sessionId: String,
        userText: String,
        enableThinking: Boolean,
        enableHands: Boolean,
    ) {
        val now = System.currentTimeMillis()
        val provider = tokenStore.getActiveProvider()
        val model = tokenStore.getActiveModel()

        val existingSession = sessionDao.getById(sessionId)
        if (existingSession == null) {
            sessionDao.upsert(
                SessionEntity(
                    id = sessionId,
                    title = userText.take(22),
                    providerId = provider,
                    modelId = model,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            sessionDao.upsert(existingSession.copy(updatedAt = now, providerId = provider, modelId = model))
        }

        messageDao.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "user",
                content = userText,
                createdAt = now,
            ),
        )

        val matchedSkill = skillRepository.matchByPrompt(userText)
        val memoryPrompt = memoryManager.formatForPrompt()
        val systemPrompt = buildString {
            appendLine("你是运行在用户 Android 手机原生的个人 AI Agent「Neon」。")
            appendLine("请优先使用可用工具完成真实任务；绝不编造执行结果。")
            if (memoryPrompt.isNotBlank()) {
                appendLine()
                appendLine(memoryPrompt)
            }
            if (matchedSkill != null) {
                appendLine()
                appendLine("【当前激活技能 ${matchedSkill.command} (${matchedSkill.name})】")
                appendLine(matchedSkill.body)
            }
        }

        val allTools = toolRegistry.listAvailableTools().filter { t ->
            enableHands || !t.name.startsWith("hands_")
        }

        val history = messageDao.listBySession(sessionId).map { m ->
            ChatTurnMessage(role = m.role, content = m.content)
        }.toMutableList()

        repeat(8) {
            val assistantId = UUID.randomUUID().toString()
            val startMs = System.currentTimeMillis()
            val thinkingBuf = StringBuilder()
            val textBuf = StringBuilder()
            val pendingTools = mutableListOf<ToolCall>()
            val toolChips = mutableListOf<ToolExecutionChip>()

            suspend fun persistAssistant(status: String) {
                messageDao.upsert(
                    MessageEntity(
                        id = assistantId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = textBuf.toString(),
                        thinking = thinkingBuf.toString().ifBlank { null },
                        thinkingDurationMs = if (thinkingBuf.isNotEmpty()) System.currentTimeMillis() - startMs else 0L,
                        toolCallsJson = if (toolChips.isEmpty()) null else json.encodeToString(toolChips),
                        status = status,
                        createdAt = startMs,
                    ),
                )
            }

            persistAssistant("thinking")

            val stream = if (provider == "deepseek") {
                val key = tokenStore.getDeepSeekKey()
                if (key.isBlank()) {
                    textBuf.append("未配置 DeepSeek API Key，请前往侧边栏 → 设置中填写。")
                    persistAssistant("error")
                    return
                }
                deepSeek.streamChat(key, model, systemPrompt, history, allTools, enableThinking)
            } else {
                val key = tokenStore.getOpenCodeGoKey()
                if (key.isBlank()) {
                    textBuf.append("未配置 OpenCode Go API Key，请前往侧边栏 → 设置中填写或导入 auth.json。")
                    persistAssistant("error")
                    return
                }
                openCodeGo.streamChat(key, model, sessionId, systemPrompt, history, allTools)
            }

            var streamError: String? = null
            stream.collect { chunk ->
                when (chunk) {
                    is StreamChunk.ThinkingDelta -> {
                        thinkingBuf.append(chunk.text)
                        persistAssistant("thinking")
                    }
                    is StreamChunk.TextDelta -> {
                        textBuf.append(chunk.text)
                        persistAssistant("streaming")
                    }
                    is StreamChunk.ToolCallReady -> {
                        pendingTools += chunk.call
                        toolChips += ToolExecutionChip(chunk.call.id, chunk.call.name, "running")
                        persistAssistant("streaming")
                    }
                    is StreamChunk.Error -> {
                        streamError = chunk.message
                    }
                    is StreamChunk.Done -> Unit
                }
            }

            if (streamError != null) {
                textBuf.append("\n\n[请求错误] $streamError")
                persistAssistant("error")
                return
            }

            if (pendingTools.isEmpty()) {
                persistAssistant("done")
                runCatching { chatDriveSync.syncSessionToDrive(sessionId) }
                return
            }

            history += ChatTurnMessage("assistant", textBuf.toString(), toolCalls = pendingTools)

            pendingTools.forEachIndexed { idx, tc ->
                val result = toolRegistry.execute(sessionId, tc.name, tc.inputJson)
                val chipStatus = when {
                    result.startsWith("DENIED:") || result.startsWith("BLOCKED:") -> "blocked"
                    result.startsWith("ERROR:") || result.startsWith("FAILED:") -> "error"
                    else -> "ok"
                }
                toolChips[idx] = toolChips[idx].copy(status = chipStatus)
                persistAssistant("streaming")
                history += ChatTurnMessage("tool", result, toolCallId = tc.id)
            }
            persistAssistant("done")
        }
    }
}
