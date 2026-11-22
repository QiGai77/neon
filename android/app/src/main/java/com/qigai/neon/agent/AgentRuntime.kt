package com.qigai.neon.agent

import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.ConversationEntity
import com.qigai.neon.data.MessageEntity
import com.qigai.neon.data.NeonDatabase
import com.qigai.neon.data.plainText
import com.qigai.neon.provider.ChatMessage
import com.qigai.neon.provider.ChatRequest
import com.qigai.neon.provider.ModelInfo
import com.qigai.neon.provider.ModelProvider
import com.qigai.neon.provider.ProviderException
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.provider.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/** ReAct state machine: Idle → Thinking → (CallingTool → WaitingGuard →) Streaming → Done / Stopped / Failed. */
sealed class AgentState {
    data object Idle : AgentState()
    data class Thinking(val conversationId: String, val startedAt: Long) : AgentState()
    data class CallingTool(val conversationId: String, val toolName: String) : AgentState()
    /** Sentinel is waiting for the user's confirmation. */
    data class WaitingGuard(val conversationId: String, val toolName: String) : AgentState()
    data class Streaming(val conversationId: String) : AgentState()
    data class Done(val conversationId: String, val stopReason: String) : AgentState()
    data class Stopped(val conversationId: String) : AgentState()
    data class Failed(val conversationId: String, val message: String) : AgentState()

    val busy: Boolean get() = this is Thinking || this is CallingTool || this is WaitingGuard || this is Streaming
    val conversation: String?
        get() = when (this) {
            is Thinking -> conversationId; is CallingTool -> conversationId; is WaitingGuard -> conversationId
            is Streaming -> conversationId; is Done -> conversationId; is Stopped -> conversationId; is Failed -> conversationId
            Idle -> null
        }
}

data class TurnConfig(
    val provider: ModelProvider,
    val model: String,
    val modelInfo: ModelInfo?,
    val thinking: Boolean,
    val effort: String = if (thinking) "high" else "none",
)

class AgentRuntime(
    private val db: NeonDatabase,
    private val tools: ToolRegistry,
    private val scope: CoroutineScope,
    private val skills: com.qigai.neon.skills.SkillRepository,
    private val memory: com.qigai.neon.sysfiles.MemoryManager,
    private val guardPending: () -> Boolean,
    private val handsEnabled: () -> Boolean,
    private val persona: () -> String = { "" },
    private val onTurnFinished: (conversationId: String) -> Unit,
) {
    private val _state = MutableStateFlow<AgentState>(AgentState.Idle)
    /** The most recent state change of any conversation. */
    val state: StateFlow<AgentState> = _state.asStateFlow()
    private val _states = MutableStateFlow<Map<String, AgentState>>(emptyMap())
    /** Each conversation's own state; conversations run side by side. */
    val states: StateFlow<Map<String, AgentState>> = _states.asStateFlow()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    private fun set(s: AgentState) {
        if (s.conversation != null && _states.value[s.conversation] == s) return
        s.conversation?.let { id -> _states.update { it + (id to s) } }
        _state.value = s
    }

    fun stateOf(cid: String?): AgentState = cid?.let { _states.value[it] } ?: AgentState.Idle

    companion object {
        const val MAX_STEPS = 12
        const val SYSTEM = "你是 Neon，运行在用户 Android 手机上的助手。用用户的语言回答，使用 Markdown。" +
            "可以调用工具：Alpine Linux 沙箱（/workspace），Gmail，日历，Drive，手机剪贴板和通知，Hands 操作手机，以及已连接的 MCP 连接器。" +
            "沙箱自带 Python 3 和 uv，Node.js 和 pnpm，Git，gcc 和 cmake，以及 ripgrep jq sqlite 等常用工具，缺的软件用 apk add 安装。" +
            "需要时直接调用工具，不要编造结果；高危操作会由 Sentinel 请用户确认。" +
            "数学公式用 LaTeX，行内用 \$...\$，独立公式用 \$\$...\$\$。" +
            "用户上传的文件放在 /workspace/uploads。做出文件交付物（Word、PDF、表格、图片、视频等）时，先在沙箱里生成，再调用 present_file 交给用户；需要给出链接时单独一行写 Markdown 链接，会显示成卡片。" +
            "需要登录网站时，先 vault_list 看有没有安全表单，没有就 vault_request 让用户填，再用 web_open 带上 vault_id 打开；你看不到也不要索要密码和 Cookie。" +
            "用户空间页的卡片可以用 space_cards 管理，遇到车票、航班、快递、出行时可以主动添加生活卡片。"
        fun newId() = UUID.randomUUID().toString()
    }

    /** Creates the user message and runs the loop. Returns the conversation id. */
    suspend fun send(conversationId: String?, text: String, cfg: TurnConfig, attachments: List<ContentBlock.Attachment> = emptyList()): String {
        val now = System.currentTimeMillis()
        val cid = conversationId ?: newId()
        if (db.conversations().get(cid) == null) {
            db.conversations().upsert(
                ConversationEntity(
                    id = cid, title = text.replace(Regex("\\s+"), " ").trim().take(20).ifEmpty { attachments.firstOrNull()?.name?.take(20) ?: "新对话" },
                    createdAt = now, updatedAt = now, provider = cfg.provider.id.id, model = cfg.model,
                )
            )
        }
        db.messages().insert(
            MessageEntity(
                id = newId(), conversationId = cid, role = "user",
                blocks = BlocksJson.encode(attachments + listOfNotNull(text.takeIf { it.isNotBlank() }?.let { ContentBlock.Text(it) })), createdAt = now,
            )
        )
        db.conversations().touch(cid, now)
        start(cid, cfg)
        return cid
    }

    /** Deletes the last assistant reply (and anything after the last user message) and runs again. */
    suspend fun regenerate(conversationId: String, cfg: TurnConfig) {
        val msgs = db.messages().list(conversationId)
        val lastUser = msgs.indexOfLast { it.role == "user" && BlocksJson.decode(it.blocks).none { b -> b is ContentBlock.ToolResult } }
        if (lastUser < 0) return
        msgs.drop(lastUser + 1).forEach { db.messages().delete(it.id) }
        start(conversationId, cfg)
    }

    /** Stops one conversation, or every running one when [cid] is null. */
    fun stop(cid: String? = null) {
        if (cid == null) jobs.values.forEach { it.cancel() } else jobs[cid]?.cancel()
    }

    private fun start(cid: String, cfg: TurnConfig) {
        jobs[cid]?.cancel()
        val j = scope.launch { loop(cid, cfg) }
        jobs[cid] = j
        j.invokeOnCompletion { jobs.remove(cid, j) }
    }

    private suspend fun history(cid: String): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        for (m in db.messages().list(cid)) {
            if (m.status == "error") continue
            val blocks = BlocksJson.decode(m.blocks).map { b ->
                if (b is ContentBlock.Attachment) ContentBlock.Text("[用户上传了文件 ${b.name}，类型 ${b.mime}，大小 ${b.size} 字节，在沙箱 ${b.path}]\n") else b
            }
            // Earlier turns: send text only (+ tool blocks within a tool loop); thinking stays local.
            val keep = blocks.filter { it is ContentBlock.Text || it is ContentBlock.ToolUse || it is ContentBlock.ToolResult }
                .filter { it !is ContentBlock.Text || it.text.isNotBlank() }
            if (keep.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.role == m.role) out[out.size - 1] = last.copy(blocks = last.blocks + keep)
            else out += ChatMessage(m.role, keep)
        }
        while (out.isNotEmpty() && out.first().role != "user") out.removeAt(0)
        return out
    }

    private suspend fun loop(cid: String, cfg: TurnConfig) {
        var step = 0
        while (step < MAX_STEPS) {
            step++
            val result = runStep(cid, cfg, firstTurn = db.messages().list(cid).count { it.role == "assistant" } == 0)
            if (result != "tool_use") return
            // tool_use: dispatch to registry
            val ok = runTools(cid)
            if (!ok) return
        }
        set(AgentState.Done(cid, "max_steps"))
    }

    /** One model request; streams into a Room message. Returns stop reason, or null when ended otherwise. */
    private suspend fun runStep(cid: String, cfg: TurnConfig, firstTurn: Boolean): String? {
        val started = System.currentTimeMillis()
        set(AgentState.Thinking(cid, started))
        val msgId = newId()
        val blocks = mutableListOf<ContentBlock>()
        var base = MessageEntity(
            id = msgId, conversationId = cid, role = "assistant", blocks = "[]", createdAt = started,
            status = "streaming", provider = cfg.provider.id.id, model = cfg.model,
        )
        db.messages().insert(base)
        var closed = true
        var thinkStart = 0L
        var lastFlush = 0L
        var stopReason: String? = null
        var inTok: Int? = null
        var outTok: Int? = null
        var toolInput = StringBuilder()

        suspend fun flush(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastFlush < 120) return
            lastFlush = now
            base = base.copy(blocks = BlocksJson.encode(blocks), inputTokens = inTok, outputTokens = outTok)
            db.messages().upsert(base)
        }

        fun closeThinking() {
            val i = blocks.indexOfLast { it is ContentBlock.Thinking }
            if (i >= 0) {
                val t = blocks[i] as ContentBlock.Thinking
                if (t.durationMs == null && thinkStart > 0) blocks[i] = t.copy(durationMs = System.currentTimeMillis() - thinkStart)
            }
        }

        fun finishToolInput() {
            val i = blocks.indexOfLast { it is ContentBlock.ToolUse }
            if (i >= 0 && toolInput.isNotEmpty()) {
                val t = blocks[i] as ContentBlock.ToolUse
                blocks[i] = t.copy(inputJson = toolInput.toString())
            }
            toolInput = StringBuilder()
        }

        val lastUserText = db.messages().list(cid).lastOrNull { it.role == "user" }
            ?.let { BlocksJson.decode(it.blocks).filterIsInstance<ContentBlock.Text>().joinToString("\n") { b -> b.text } }.orEmpty()
        val system = buildString {
            append(SYSTEM)
            persona().takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
            memory.formatForPrompt(lastUserText).takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
            skills.catalogForPrompt().takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
            skills.matchByPrompt(lastUserText)?.let { sk ->
                append("\n\n【命中技能 ").append(sk.name).append("】")
                if (sk.dir != null) append("技能目录 /workspace/skills/").append(sk.dir).append("\n")
                append(sk.body)
            }
        }
        val req = ChatRequest(
            model = cfg.model, system = system, messages = history(cid).let { h ->
                // history() includes the placeholder? it is empty so filtered out.
                h
            },
            thinking = cfg.thinking, effort = cfg.effort, sessionId = cid,
            wantTitle = firstTurn && cfg.provider.id == ProviderId.DEEPSEEK,
            tools = tools.specs(handsEnabled()), modelInfo = cfg.modelInfo,
        )
        try {
            cfg.provider.stream(req).collect { ev ->
                when (ev) {
                    is StreamEvent.ThinkingDelta -> {
                        val last = blocks.lastOrNull()
                        if (!closed && last is ContentBlock.Thinking) blocks[blocks.size - 1] = last.copy(thinking = last.thinking + ev.text)
                        else { blocks += ContentBlock.Thinking(ev.text); thinkStart = System.currentTimeMillis(); closed = false }
                    }
                    is StreamEvent.SignatureDelta -> {
                        val i = blocks.indexOfLast { it is ContentBlock.Thinking }
                        if (i >= 0) blocks[i] = (blocks[i] as ContentBlock.Thinking).copy(signature = ev.signature)
                    }
                    is StreamEvent.TextDelta -> {
                        if (_states.value[cid] !is AgentState.Streaming) set(AgentState.Streaming(cid))
                        closeThinking()
                        val last = blocks.lastOrNull()
                        if (!closed && last is ContentBlock.Text) blocks[blocks.size - 1] = last.copy(text = last.text + ev.text)
                        else { blocks += ContentBlock.Text(ev.text); closed = false }
                    }
                    is StreamEvent.ToolUseStart -> {
                        closeThinking()
                        blocks += if (ev.server) ContentBlock.ServerToolUse(ev.id, ev.name, ev.inputJson ?: "{}")
                        else ContentBlock.ToolUse(ev.id, ev.name)
                        closed = false
                    }
                    is StreamEvent.ToolInputDelta -> toolInput.append(ev.partialJson)
                    StreamEvent.BlockStop -> { closeThinking(); finishToolInput(); closed = true }
                    is StreamEvent.Title -> db.conversations().rename(cid, ev.title.take(40), System.currentTimeMillis())
                    is StreamEvent.Usage -> { ev.input?.let { inTok = it }; ev.output?.let { outTok = it } }
                    is StreamEvent.Stop -> stopReason = ev.reason
                }
                flush()
            }
            closeThinking(); finishToolInput()
            base = base.copy(status = "done", stopReason = stopReason)
            flush(true)
            db.conversations().touch(cid, System.currentTimeMillis())
            if (stopReason == "tool_use" && blocks.any { it is ContentBlock.ToolUse }) return "tool_use"
            set(AgentState.Done(cid, stopReason ?: "end_turn"))
            onTurnFinished(cid)
            return stopReason
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                closeThinking()
                base = base.copy(status = "stopped", stopReason = "user_stop")
                flush(true)
                if (blocks.isEmpty()) db.messages().delete(msgId)
                set(AgentState.Stopped(cid))
                onTurnFinished(cid)
            }
            throw e
        } catch (e: Exception) {
            closeThinking()
            val msg = (e as? ProviderException)?.message ?: (e.message ?: e.javaClass.simpleName)
            base = base.copy(status = "error", error = msg)
            flush(true)
            set(AgentState.Failed(cid, msg))
            return null
        }
    }

    /** Executes tool_use blocks of the last assistant message through the registry; writes tool_result to Room. */
    private suspend fun runTools(cid: String): Boolean {
        val last = db.messages().list(cid).lastOrNull { it.role == "assistant" } ?: return false
        val uses = BlocksJson.decode(last.blocks).filterIsInstance<ContentBlock.ToolUse>()
        val results = mutableListOf<ContentBlock>()
        for (u in uses) {
            set(AgentState.CallingTool(cid, u.name))
            val watch = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(150)
                    set(if (guardPending()) AgentState.WaitingGuard(cid, u.name) else AgentState.CallingTool(cid, u.name))
                }
            }
            val outcome = try { tools.execute(cid, u.name, u.inputJson) } finally { watch.cancel() }
            results += ContentBlock.ToolResult(u.id, outcome.content, outcome.isError)
        }
        db.messages().insert(
            MessageEntity(
                id = newId(), conversationId = cid, role = "user",
                blocks = BlocksJson.encode(results), createdAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** Runs a prompt to completion without the chat UI (scheduled tasks). Returns conversation id and final text. */
    suspend fun runHeadless(title: String, prompt: String, cfg: TurnConfig): Pair<String, String> {
        val now = System.currentTimeMillis()
        val cid = newId()
        db.conversations().upsert(ConversationEntity(id = cid, title = title.take(40), createdAt = now, updatedAt = now, provider = cfg.provider.id.id, model = cfg.model))
        db.messages().insert(MessageEntity(id = newId(), conversationId = cid, role = "user", blocks = BlocksJson.encode(listOf(ContentBlock.Text(prompt))), createdAt = now))
        loop(cid, cfg)
        val last = db.messages().list(cid).lastOrNull { it.role == "assistant" }
        if (last?.status == "error") throw IllegalStateException(last.error ?: "执行失败")
        return cid to (last?.let { plainTextOf(it) }.orEmpty())
    }

    fun plainTextOf(m: MessageEntity) = BlocksJson.decode(m.blocks).plainText()
}
