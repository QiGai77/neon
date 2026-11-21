package com.qigai.neon.ui.connectors

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.connectors.NeonNotificationListenerService
import com.qigai.neon.data.db.ConnectorEntity
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(
    container: NeonContainer,
    onBack: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleEmail by container.tokenStore.googleEmail.collectAsState()
    val notifConnected by NeonNotificationListenerService.connected.collectAsState()
    val mcpConnectors by container.db.connectorDao().observeAll().collectAsState(initial = emptyList())

    var statusMsg by remember { mutableStateOf<String?>(null) }
    var showAddMcp by remember { mutableStateOf(false) }
    var mcpName by remember { mutableStateOf("") }
    var mcpUrl by remember { mutableStateOf("") }
    var showAddLocalMcp by remember { mutableStateOf(false) }
    var localMcpName by remember { mutableStateOf("") }
    var localMcpCmd by remember { mutableStateOf("python3 /workspace/mcp_server.py") }
    var webhookUrl by remember { mutableStateOf(container.tokenStore.getWebhookUrl()) }
    var webhookSecret by remember { mutableStateOf(container.tokenStore.getWebhookSecret()) }
    var showWebhookDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = palette.bgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("连接器", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bgCanvas),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "凭据存储在本地 Keystore，各连接器独立授权",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary,
            )

            statusMsg?.let { msg ->
                Surface(color = palette.neonLimeSoft, shape = NeonShapes.Control) {
                    Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }

            // Google Workspace
            ConnectorCard(
                title = "Google Workspace",
                subtitle = googleEmail ?: "Drive、Gmail、Calendar",
                badge = if (googleEmail != null) "已连接" else "未连接",
                badgePositive = googleEmail != null,
                actionLabel = if (googleEmail != null) "重新授权" else "连接",
                onAction = {
                    scope.launch {
                        statusMsg = "正在拉起 Custom Tabs 授权…"
                        container.googleWorkspace.connectAllScopes()
                            .onSuccess { statusMsg = "Google Workspace 已连接并开启自动续期" }
                            .onFailure { statusMsg = "Google 授权失败: ${it.message}" }
                    }
                },
            )

            // Remote & Local MCP List
            mcpConnectors.forEach { conn ->
                ConnectorCard(
                    title = conn.name,
                    subtitle = conn.endpointUrl ?: conn.subtitle,
                    badge = if (conn.status == "connected") "已连接 · ${conn.toolCount} 工具" else conn.status,
                    badgePositive = conn.status == "connected",
                    actionLabel = "刷新工具",
                    onAction = {
                        scope.launch {
                            if (conn.category == "local_stdio") {
                                container.localStdioMcp.listTools(conn.endpointUrl.orEmpty())
                                    .onSuccess { tools ->
                                        val arrJson = Json.encodeToString(
                                            tools.map { t ->
                                                buildJsonObject {
                                                    put("name", t.name)
                                                    put("description", t.description)
                                                    put("inputSchema", t.inputSchema)
                                                }
                                            },
                                        )
                                        container.db.connectorDao().upsert(
                                            conn.copy(status = "connected", toolCount = tools.size, toolsJson = arrJson),
                                        )
                                        statusMsg = "${conn.name} (stdio) 已同步 ${tools.size} 个工具"
                                    }
                                    .onFailure { statusMsg = "本地 stdio MCP 失败: ${it.message}" }
                            } else {
                                val token = container.tokenStore.getConnectorToken(conn.id)
                                container.mcpClient.listTools(conn.endpointUrl.orEmpty(), token)
                                    .onSuccess { tools ->
                                        val arrJson = Json.encodeToString(
                                            tools.map { t ->
                                                buildJsonObject {
                                                    put("name", t.name)
                                                    put("description", t.description)
                                                    put("inputSchema", t.inputSchema)
                                                }
                                            },
                                        )
                                        container.db.connectorDao().upsert(
                                            conn.copy(status = "connected", toolCount = tools.size, toolsJson = arrJson),
                                        )
                                        statusMsg = "${conn.name} 已同步 ${tools.size} 个工具"
                                    }
                                    .onFailure { statusMsg = "MCP 同步失败: ${it.message}" }
                            }
                        }
                    },
                )
            }

            OutlinedButton(
                onClick = { showAddMcp = true },
                modifier = Modifier.fillMaxWidth(),
                shape = NeonShapes.Control,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Text("添加远程 MCP", color = palette.inkPrimary)
            }

            OutlinedButton(
                onClick = { showAddLocalMcp = true },
                modifier = Modifier.fillMaxWidth(),
                shape = NeonShapes.Control,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Text("添加本地 MCP", color = palette.inkPrimary)
            }

            // 手机原生能力
            ConnectorCard(
                title = "手机能力",
                subtitle = "剪贴板、系统分享、通知、DeepLink",
                badge = if (notifConnected) "通知已开启" else "待开启",
                badgePositive = notifConnected,
                actionLabel = "通知权限",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )

            // Webhook
            ConnectorCard(
                title = "Tasker / 飞书 Webhook",
                subtitle = webhookUrl.ifBlank { "未配置 Webhook URL" },
                badge = if (webhookUrl.isNotBlank()) "已配置" else "未配置",
                badgePositive = webhookUrl.isNotBlank(),
                actionLabel = "配置/测试",
                onAction = { showWebhookDialog = true },
            )
        }
    }

    if (showAddMcp) {
        AlertDialog(
            onDismissRequest = { showAddMcp = false },
            title = { Text("添加远程 MCP 服务器") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = mcpName, onValueChange = { mcpName = it }, label = { Text("名称 (如 Notion)") }, singleLine = true)
                    OutlinedTextField(value = mcpUrl, onValueChange = { mcpUrl = it }, label = { Text("MCP Endpoint URL (https://...)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = mcpName.trim().ifBlank { "MCP 服务" }
                    val url = mcpUrl.trim()
                    showAddMcp = false
                    if (url.isNotBlank()) {
                        scope.launch {
                            val id = UUID.randomUUID().toString().take(8)
                            container.db.connectorDao().upsert(
                                ConnectorEntity(id, name, "remote_mcp", "正在探测 OAuth…", url, "connecting", 0),
                            )
                            val meta = container.mcpDiscovery.discover(url)
                            var accessToken = container.tokenStore.getConnectorToken(id)
                            var toolsRes = container.mcpClient.listTools(url, accessToken)

                            if (toolsRes.isFailure && meta != null) {
                                val regClientId = meta.registrationEndpoint?.let { regUrl ->
                                    container.mcpDiscovery.dynamicRegisterClient(regUrl, "com.qigai.neon://oauth/callback")
                                } ?: "neon-android-client"
                                val authRes = container.oauth.runPkceFlow(
                                    authEndpoint = meta.authorizationEndpoint,
                                    tokenEndpoint = meta.tokenEndpoint,
                                    clientId = regClientId,
                                    scopes = meta.scopesSupported.ifEmpty { listOf("mcp:tools") },
                                )
                                authRes.onSuccess { tok ->
                                    container.tokenStore.saveConnectorToken(id, tok.accessToken, tok.refreshToken)
                                    accessToken = tok.accessToken
                                    toolsRes = container.mcpClient.listTools(url, accessToken)
                                }
                            }

                            if (toolsRes.isSuccess) {
                                val tools = toolsRes.getOrThrow()
                                val arrJson = Json.encodeToString(
                                    tools.map { t ->
                                        buildJsonObject {
                                            put("name", t.name)
                                            put("description", t.description)
                                            put("inputSchema", t.inputSchema)
                                        }
                                    },
                                )
                                container.db.connectorDao().upsert(
                                    ConnectorEntity(id, name, "remote_mcp", "已连接", url, "connected", tools.size, arrJson),
                                )
                                statusMsg = "$name 自连成功，发现 ${tools.size} 个工具"
                            } else {
                                val note = if (meta != null) "已发现 OAuth (${meta.authorizationEndpoint})" else toolsRes.exceptionOrNull()?.message.orEmpty()
                                container.db.connectorDao().upsert(
                                    ConnectorEntity(id, name, "remote_mcp", note, url, "error", 0),
                                )
                                statusMsg = "$name 连接状态：$note"
                            }
                        }
                    }
                }) { Text("探测并连接") }
            },
            dismissButton = { TextButton(onClick = { showAddMcp = false }) { Text("取消") } },
        )
    }

    if (showAddLocalMcp) {
        AlertDialog(
            onDismissRequest = { showAddLocalMcp = false },
            title = { Text("添加本地 stdio MCP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = localMcpName, onValueChange = { localMcpName = it }, label = { Text("名称 (如 sqlite-mcp)") }, singleLine = true)
                    OutlinedTextField(value = localMcpCmd, onValueChange = { localMcpCmd = it }, label = { Text("沙箱内启动命令") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val name = localMcpName.trim().ifBlank { "Local MCP" }
                    val cmd = localMcpCmd.trim()
                    showAddLocalMcp = false
                    if (cmd.isNotBlank()) {
                        scope.launch {
                            val id = UUID.randomUUID().toString().take(8)
                            container.db.connectorDao().upsert(
                                ConnectorEntity(id, name, "local_stdio", "正在扫描 stdio 工具…", cmd, "connecting", 0),
                            )
                            container.localStdioMcp.listTools(cmd)
                                .onSuccess { tools ->
                                    val arrJson = Json.encodeToString(
                                        tools.map { t ->
                                            buildJsonObject {
                                                put("name", t.name)
                                                put("description", t.description)
                                                put("inputSchema", t.inputSchema)
                                            }
                                        },
                                    )
                                    container.db.connectorDao().upsert(
                                        ConnectorEntity(id, name, "local_stdio", "Alpine 沙箱内运行", cmd, "connected", tools.size, arrJson),
                                    )
                                    statusMsg = "$name (stdio) 已发现 ${tools.size} 个工具"
                                }
                                .onFailure { err ->
                                    container.db.connectorDao().upsert(
                                        ConnectorEntity(id, name, "local_stdio", err.message.orEmpty(), cmd, "error", 0),
                                    )
                                    statusMsg = "$name 启动失败: ${err.message}"
                                }
                        }
                    }
                }) { Text("扫描并添加") }
            },
            dismissButton = { TextButton(onClick = { showAddLocalMcp = false }) { Text("取消") } },
        )
    }

    if (showWebhookDialog) {
        AlertDialog(
            onDismissRequest = { showWebhookDialog = false },
            title = { Text("Webhook 配置与测试") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = webhookUrl, onValueChange = { webhookUrl = it }, label = { Text("Webhook URL") }, singleLine = true)
                    OutlinedTextField(value = webhookSecret, onValueChange = { webhookSecret = it }, label = { Text("签名密钥 (可选)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    container.tokenStore.saveWebhookConfig(webhookUrl, webhookSecret)
                    showWebhookDialog = false
                    scope.launch {
                        container.webhookConnector.trigger("""{"event":"neon_ping"}""")
                            .onSuccess { statusMsg = "Webhook 测试成功: $it" }
                            .onFailure { statusMsg = "Webhook 测试失败: ${it.message}" }
                    }
                }) { Text("保存并测试") }
            },
            dismissButton = { TextButton(onClick = { showWebhookDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ConnectorCard(
    title: String,
    subtitle: String,
    badge: String,
    badgePositive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    Surface(
        color = palette.bgSurface,
        shape = NeonShapes.Card,
        border = BorderStroke(1.dp, palette.borderHairline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = palette.inkPrimary)
                    Surface(
                        color = if (badgePositive) palette.neonLimeSoft else palette.bgSubtle,
                        shape = NeonShapes.Pill,
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
            }
            Button(
                onClick = onAction,
                shape = NeonShapes.Control,
                colors = ButtonDefaults.buttonColors(containerColor = palette.inkPrimary, contentColor = Color.White),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
