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

class NeonApp : Application() {
    lateinit var container: NeonContainer
        private set

    override fun onCreate() {
        super.onCreate()
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
    val riskGate = RiskGate()
    val rootfs = RootfsManager(app)
    val proot = PRootKernel(rootfs)
    val shell = ShellExecutor(rootfs, proot, riskGate)
    val localMcp = LocalStdioMcpBridge(shell)
    val mcpDiscovery = McpAuthDiscovery(Http.client)
    val mcp = McpClient(Http.client)
    val mcpOAuth = com.qigai.neon.oauth.McpOAuth(app, Http.client)
    val device = NativeDeviceTools(app)
    val webhook = WebhookConnector(Http.client, tokens)
    val google = GoogleWorkspaceConnector(
        Http.client,
        token = { if (settings.state.value.workspaceConnected) GoogleDriveAuth.silentWorkspaceToken(app) else null },
        driveToken = { if (settings.state.value.driveConnected) runCatching { GoogleDriveAuth.silentToken(app) }.getOrNull() else null },
    )
    val shizuku = ShizukuAdbEngine()
    val hands = HandsOperator(app, shizuku, riskGate, db.handsTraceDao())
    val skills = SkillRepository(rootfs, google)
    val memory = MemoryManager(rootfs, google)
    val tasks = TaskScheduler(app, db.scheduledTaskDao())
    val skillSynth = SkillSynthesizer(complete = { prompt -> completeOnce(prompt) }, skillRepository = skills)

    val tools = ToolRegistry(
        shellExecutor = shell, handsOperator = hands, googleWorkspace = google, nativeDevice = device,
        webhookConnector = webhook, mcpClient = mcp, localStdioMcp = localMcp,
        connectorDao = db.connectorDao(), tokenStore = tokens, riskGate = riskGate,
    )
    val agent = AgentRuntime(
        db, tools, scope, skills, memory,
        guardPending = { riskGate.pendingRequest.value != null },
        handsEnabled = { settings.state.value.handsEnabled },
    ) { cid ->
        val s = settings.state.value
        if (s.driveConnected && s.driveAutoSync) DriveSync.enqueue(app, cid)
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
        io.launch {
            runCatching { rootfs.ensureInitialized() }
            runCatching { skills.reloadFromDisk() }
            runCatching { memory.load() }
        }
    }
}
