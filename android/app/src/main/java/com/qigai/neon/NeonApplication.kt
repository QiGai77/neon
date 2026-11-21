package com.qigai.neon

import android.app.Application
import com.qigai.neon.agent.AgentRuntime
import com.qigai.neon.agent.ToolRegistry
import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.connectors.LocalStdioMcpBridge
import com.qigai.neon.connectors.McpAuthDiscovery
import com.qigai.neon.connectors.McpClient
import com.qigai.neon.connectors.NativeDeviceTools
import com.qigai.neon.connectors.WebhookConnector
import com.qigai.neon.data.db.AppDatabase
import com.qigai.neon.guard.RiskGate
import com.qigai.neon.hands.HandsOperator
import com.qigai.neon.hands.ShizukuAdbEngine
import com.qigai.neon.oauth.CustomTabsOAuth
import com.qigai.neon.oauth.EncryptedTokenStore
import com.qigai.neon.provider.DeepSeekClient
import com.qigai.neon.provider.OpenCodeGoClient
import com.qigai.neon.sandbox.PRootKernel
import com.qigai.neon.sandbox.RootfsManager
import com.qigai.neon.sandbox.ShellExecutor
import com.qigai.neon.scheduled.TaskScheduler
import com.qigai.neon.skills.SkillRepository
import com.qigai.neon.skills.SkillSynthesizer
import com.qigai.neon.sysfiles.ChatDriveSync
import com.qigai.neon.sysfiles.MemoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NeonContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val db = AppDatabase.build(app)
    val tokenStore = EncryptedTokenStore(app)
    val riskGate = RiskGate()

    val oauth = CustomTabsOAuth(app, httpClient, tokenStore)
    val googleWorkspace = GoogleWorkspaceConnector(httpClient, oauth)
    val mcpDiscovery = McpAuthDiscovery(httpClient)
    val mcpClient = McpClient(httpClient)
    val nativeDevice = NativeDeviceTools(app)
    val webhookConnector = WebhookConnector(httpClient, tokenStore)

    val rootfsManager = RootfsManager(app)
    val prootKernel = PRootKernel(rootfsManager)
    val shellExecutor = ShellExecutor(rootfsManager, prootKernel, riskGate)
    val localStdioMcp = LocalStdioMcpBridge(shellExecutor)

    val shizuku = ShizukuAdbEngine()
    val handsOperator = HandsOperator(app, shizuku, riskGate, db.handsTraceDao())

    val deepSeek = DeepSeekClient(httpClient)
    val openCodeGo = OpenCodeGoClient(httpClient)

    val skillRepository = SkillRepository(rootfsManager, googleWorkspace)
    val skillSynthesizer = SkillSynthesizer(tokenStore, deepSeek, openCodeGo, skillRepository)
    val memoryManager = MemoryManager(rootfsManager, googleWorkspace)
    val chatDriveSync = ChatDriveSync(db.sessionDao(), db.messageDao(), rootfsManager, googleWorkspace)
    val taskScheduler = TaskScheduler(app, db.scheduledTaskDao())

    val toolRegistry = ToolRegistry(
        shellExecutor = shellExecutor,
        handsOperator = handsOperator,
        googleWorkspace = googleWorkspace,
        nativeDevice = nativeDevice,
        webhookConnector = webhookConnector,
        mcpClient = mcpClient,
        localStdioMcp = localStdioMcp,
        connectorDao = db.connectorDao(),
        tokenStore = tokenStore,
        riskGate = riskGate,
    )

    val agentRuntime = AgentRuntime(
        scope = appScope,
        sessionDao = db.sessionDao(),
        messageDao = db.messageDao(),
        tokenStore = tokenStore,
        deepSeek = deepSeek,
        openCodeGo = openCodeGo,
        toolRegistry = toolRegistry,
        skillRepository = skillRepository,
        memoryManager = memoryManager,
        chatDriveSync = chatDriveSync,
    )

    init {
        appScope.launch {
            rootfsManager.ensureInitialized()
            skillRepository.reloadFromDisk()
            memoryManager.load()
        }
    }
}

class NeonApplication : Application() {
    lateinit var container: NeonContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = NeonContainer(this)
    }
}
