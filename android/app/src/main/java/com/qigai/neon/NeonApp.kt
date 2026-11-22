package com.qigai.neon

import android.app.Application
import androidx.room.Room
import com.qigai.neon.agent.AgentRuntime
import com.qigai.neon.agent.ToolRegistry
import com.qigai.neon.agent.TurnConfig
import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.connectors.LocalStdioMcpBridge
import com.qigai.neon.connectors.McpAuthDiscovery
import com.qigai.neon.connectors.McpClient
import com.qigai.neon.connectors.NativeDeviceTools
import com.qigai.neon.connectors.WebhookConnector
import com.qigai.neon.provider.ChatMessage
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.data.NeonDatabase
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.guard.RiskGate
import com.qigai.neon.hands.HandsOperator
import com.qigai.neon.hands.ShizukuAdbEngine
import com.qigai.neon.oauth.GoogleDriveAuth
import com.qigai.neon.provider.ChatRequest
import com.qigai.neon.provider.DeepSeekClient
import com.qigai.neon.provider.Http
import com.qigai.neon.provider.OpenCodeGoClient
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.provider.StreamEvent
import com.qigai.neon.sandbox.PRootKernel
import com.qigai.neon.sandbox.RootfsManager
import com.qigai.neon.sandbox.ShellExecutor
import com.qigai.neon.scheduled.TaskScheduler
import com.qigai.neon.skills.SkillRepository
import com.qigai.neon.skills.SkillSynthesizer
import com.qigai.neon.sysfiles.DriveSync
import com.qigai.neon.sysfiles.MemoryManager
import com.qigai.neon.voice.VoiceInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class NeonApp : Application() {
    lateinit var container: NeonContainer
        private set

    override fun onCreate() {
        super.onCreate()
        com.qigai.neon.markdown.MathRenderer.init(this)
        CrashLog.install(this)
        container = NeonContainer(this)
    }
}

class NeonContainer(val app: NeonApp) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: NeonDatabase = Room.databaseBuilder(app, NeonDatabase::class.java, "neon.db")
        .addMigrations(NeonDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    val tokens = EncryptedTokenStore(app)
    val settings = SettingsRepository(db.settings(), scope)
    val deepseek = DeepSeekClient({ settings.state.value.deepseekBase }, { tokens.get(EncryptedTokenStore.DEEPSEEK) })
    val go = OpenCodeGoClient({ settings.state.value.goBase }, { tokens.get(EncryptedTokenStore.OPENCODE_GO) })
    val drive = DriveSync(app)

    // capabilities
    val riskGate = RiskGate().also { g -> g.approval = { settings.state.value.approval } }
    val rootfs = RootfsManager(app)
    val proot = PRootKernel(rootfs)
    val shell = ShellExecutor(rootfs, proot, riskGate)
    val localMcp = LocalStdioMcpBridge(shell)
    val mcpDiscovery = McpAuthDiscovery(Http.client)
    val mcp = McpClient(Http.client)
    val mcpOAuth = com.qigai.neon.oauth.McpOAuth(app, Http.client)
    val mcpHub = com.qigai.neon.connectors.McpHub(db.connectorDao(), tokens, mcp, mcpDiscovery, mcpOAuth, localMcp)
    val googleAccount = com.qigai.neon.connectors.GoogleAccount(tokens, mcpOAuth, Http.client)
    val mcpMarket = com.qigai.neon.connectors.McpRegistry(Http.client)
    val device = NativeDeviceTools(app)
    val webhook = WebhookConnector(Http.client, tokens)
    val google = GoogleWorkspaceConnector(
        Http.client,
        token = {
            googleAccount.accessToken()
                ?: if (settings.state.value.workspaceConnected) runCatching { GoogleDriveAuth.silentWorkspaceToken(app) }.getOrNull() else null
        },
        driveToken = {
            googleAccount.accessToken()
                ?: if (settings.state.value.driveConnected) runCatching { GoogleDriveAuth.silentToken(app) }.getOrNull() else null
        },
    )
    val shizuku = ShizukuAdbEngine()
    val hands = HandsOperator(app, shizuku, riskGate, db.handsTraceDao())
    val skills = SkillRepository(rootfs, google, app)
    val skillMarket = com.qigai.neon.skills.SkillMarket(Http.client, skills) { tokens.getConnectorToken("github") }
    val memory = MemoryManager(rootfs, google)
    val tasks = TaskScheduler(app, db.scheduledTaskDao())
    val skillSynth = SkillSynthesizer(complete = { prompt -> completeOnce(prompt) }, skillRepository = skills)

    val vault = com.qigai.neon.vault.Vault(app)
    val vaultForms = com.qigai.neon.vault.VaultFormGate(vault)
    val browser = com.qigai.neon.vault.WebBrowser(app, vault)
    val files = com.qigai.neon.sandbox.SandboxFiles({ rootfs.rootfsDir }, { rootfs.workspaceDir })
    val space = com.qigai.neon.space.SpaceStore(settings)
    val tools = ToolRegistry(
        shellExecutor = shell, handsOperator = hands, googleWorkspace = google, nativeDevice = device,
        webhookConnector = webhook, mcpClient = mcp, localStdioMcp = localMcp,
        connectorDao = db.connectorDao(), mcpHub = mcpHub, tokenStore = tokens, riskGate = riskGate,
        files = files, space = space, memory = memory,
        vault = vault, vaultForms = vaultForms, browser = browser,
    )
    val agent = AgentRuntime(
        db, tools, scope, skills, memory,
        guardPending = { riskGate.pendingRequest.value != null },
        handsEnabled = { settings.state.value.handsEnabled },
        persona = { com.qigai.neon.agent.PersonaPrompt.build(settings.state.value) },
    ) { cid ->
        val s = settings.state.value
        if (s.driveConnected && s.driveAutoSync) DriveSync.enqueue(app, cid)
        scope.launch { runCatching { autoTitle(cid) } }
        scope.launch { runCatching { reflectMemory(cid) } }
    }

    /**
     * After the first finished turn, asks the model for a short title. Only replaces the title the app
     * set from the first message, so a name the user typed is never overwritten.
     */
    /** Lets memory learn from the turn that just ended (mem0-style add, update, delete). */
    private suspend fun reflectMemory(cid: String) {
        if (!settings.state.value.memoryAuto) return
        val msgs = db.messages().list(cid)
        val user = msgs.lastOrNull { m -> m.role == "user" && BlocksJson.decode(m.blocks).any { it is ContentBlock.Text } } ?: return
        val userText = BlocksJson.decode(user.blocks).filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
        val reply = msgs.lastOrNull { it.role == "assistant" }?.let { m ->
            BlocksJson.decode(m.blocks).filterIsInstance<ContentBlock.Text>().joinToString("\n") { it.text }
        }.orEmpty()
        memory.reflect(userText, reply) { p -> completeOnce(p) }
    }

    private suspend fun autoTitle(cid: String) {
        val conv = db.conversations().get(cid) ?: return
        val msgs = db.messages().list(cid)
        val users = msgs.filter { it.role == "user" }.map { BlocksJson.decode(it.blocks) }.filter { b -> b.none { it is ContentBlock.ToolResult } }
        if (users.size != 1) return
        val ask = users.first().filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
        if (conv.title != ask.take(20) && conv.title != "新对话") return
        val reply = msgs.lastOrNull { it.role == "assistant" }?.let { m ->
            BlocksJson.decode(m.blocks).filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }
        }.orEmpty()
        if (ask.isBlank() && reply.isBlank()) return
        val prompt = "给这段对话起一个标题：不超过 12 个汉字，概括用户要做的事，不要标点、引号和前缀，只输出标题。\n\n用户：${ask.take(600)}\n\n助手：${reply.take(600)}"
        val title = completeOnce(prompt).lines().firstOrNull { it.isNotBlank() }.orEmpty()
            .trim().trim('"', '“', '”', '「', '」', '《', '》', '。', '.').removePrefix("标题：").removePrefix("标题:").trim().take(24)
        if (title.isNotBlank() && db.conversations().get(cid)?.title == conv.title) db.conversations().rename(cid, title, conv.updatedAt)
    }
    private val subagentSessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        tools.spawner = spawner@{ parent, tasks ->
            if (parent in subagentSessions) return@spawner "ERROR: 子代理不能再派子代理，自己完成"
            val cfg = defaultTurnConfig() ?: return@spawner "ERROR: 没有可用的模型"
            val results = kotlinx.coroutines.coroutineScope {
                tasks.mapIndexed { i, t ->
                    async {
                        runCatching {
                            agent.runHeadless("子代理 ${i + 1}：" + t.take(16), t, cfg, onCreated = { subagentSessions.add(it) })
                        }.fold({ it.second.ifBlank { "（没有文字结果）" } }, { "失败：" + (it.message ?: "未知错误") })
                    }
                }.awaitAll()
            }
            results.mapIndexed { i, r -> "## 子代理 ${i + 1}\n任务：${tasks[i].take(80)}\n\n${r.take(4000)}" }.joinToString("\n\n")
        }
    }

    private val deepseekAsr: suspend (ByteArray) -> String = { wav -> deepseek.transcribe(wav) }
    val voice = VoiceInput(app, scope) {
        if (tokens.has(EncryptedTokenStore.DEEPSEEK)) deepseekAsr else null
    }

    /** Provider + model the user picked, for work that runs without the chat screen. */
    fun defaultTurnConfig(thinking: Boolean = false): TurnConfig? {
        val s = settings.state.value
        val has = { p: ProviderId -> tokens.has(if (p == ProviderId.DEEPSEEK) EncryptedTokenStore.DEEPSEEK else EncryptedTokenStore.OPENCODE_GO) }
        val p = ProviderId.of(s.provider)?.takeIf(has) ?: ProviderId.entries.firstOrNull(has) ?: return null
        val client = if (p == ProviderId.DEEPSEEK) deepseek else go
        return TurnConfig(client, if (p == ProviderId.DEEPSEEK) s.deepseekModel else s.goModel, null, thinking)
    }

    private suspend fun completeOnce(prompt: String): String {
        val cfg = defaultTurnConfig() ?: error("请先连接一个模型")
        val sb = StringBuilder()
        cfg.provider.stream(
            ChatRequest(model = cfg.model, system = "", messages = listOf(ChatMessage("user", listOf(ContentBlock.Text(prompt)))), thinking = false, sessionId = "skill-synth")
        ).collect { if (it is StreamEvent.TextDelta) sb.append(it.text) }
        return sb.toString()
    }

    init {
        scope.launch { db.messages().markDanglingStopped() }
        scope.launch {
            var was = 0
            agent.states.collect { m ->
                val n = m.values.count { it.busy }
                if (n != was) {
                    if (n == 0) com.qigai.neon.scheduled.HandsForegroundService.keepAlive(app, false)
                    else com.qigai.neon.scheduled.HandsForegroundService.keepAlive(app, true, if (n > 1) "$n 个会话在进行" else "")
                    was = n
                }
            }
        }
        io.launch {
            runCatching { rootfs.ensureInitialized() }
            runCatching { skills.seedBundled() }
            runCatching { skills.reloadFromDisk() }
            runCatching { memory.load() }
        }
    }
}
