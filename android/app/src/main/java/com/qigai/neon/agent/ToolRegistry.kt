package com.qigai.neon.agent

import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.connectors.LocalStdioMcpBridge
import com.qigai.neon.connectors.McpClient
import com.qigai.neon.connectors.NativeDeviceTools
import com.qigai.neon.connectors.WebhookConnector
import com.qigai.neon.data.ConnectorDao
import com.qigai.neon.guard.GuardDecision
import com.qigai.neon.guard.PendingGuardRequest
import com.qigai.neon.guard.RiskGate
import com.qigai.neon.guard.RiskLevel
import com.qigai.neon.guard.RiskPolicy
import com.qigai.neon.hands.HandsOperator
import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.provider.ToolSpec
import com.qigai.neon.sandbox.ShellExecutor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ToolRegistry(
    private val shellExecutor: ShellExecutor,
    private val handsOperator: HandsOperator,
    private val googleWorkspace: GoogleWorkspaceConnector,
    private val nativeDevice: NativeDeviceTools,
    private val webhookConnector: WebhookConnector,
    private val mcpClient: McpClient,
    private val localStdioMcp: LocalStdioMcpBridge,
    private val connectorDao: ConnectorDao,
    private val tokenStore: EncryptedTokenStore,
    private val riskGate: RiskGate,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun objSchema(vararg props: Pair<String, String>, required: List<String> = emptyList()): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                props.forEach { (k, desc) ->
                    putJsonObject(k) {
                        put("type", "string")
                        put("description", desc)
                    }
                }
            }
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }

    /** [hands] false hides the phone-control tools for this turn. */
    suspend fun specs(hands: Boolean = true): List<ToolSpec> {
        val base = mutableListOf<ToolSpec>(
            ToolSpec("sandbox_exec", "在离线 Alpine Linux 沙箱 (/workspace) 中执行命令或 Python 脚本", objSchema("command" to "要执行的 bash/sh 命令", required = listOf("command"))),
            ToolSpec("hands_dump_screen", "获取当前手机屏幕的无障碍控件树（含编号与坐标）", objSchema()),
            ToolSpec("hands_screenshot", "截取当前屏幕并叠加 Set-of-Mark 编号框", objSchema()),
            ToolSpec("hands_tap", "点击屏幕上的指定编号控件", objSchema("node_index" to "控件编号整数", required = listOf("node_index"))),
            ToolSpec("hands_swipe", "在屏幕上执行滑动手势", objSchema("x1" to "起点X", "y1" to "起点Y", "x2" to "终点X", "y2" to "终点Y", required = listOf("x1", "y1", "x2", "y2"))),
            ToolSpec("hands_input", "向指定编号的输入框填入文本", objSchema("node_index" to "输入框编号", "text" to "文本内容", required = listOf("node_index", "text"))),
            ToolSpec("hands_list_apps", "列出手机上所有可启动的应用名称与包名", objSchema()),
            ToolSpec("hands_open_app", "按应用中文名或包名启动手机应用", objSchema("package_name" to "应用名或 Android 包名", required = listOf("package_name"))),
            ToolSpec("hands_back", "执行系统返回键", objSchema()),
            ToolSpec("gmail_list_messages", "查询 Gmail 邮件列表", objSchema("query" to "搜索关键词")),
            ToolSpec("gmail_read_message", "读取指定 Gmail 邮件的完整内容", objSchema("message_id" to "邮件 ID", required = listOf("message_id"))),
            ToolSpec("gmail_send_message", "发送 Gmail 邮件（需 Sentinel 指纹确认）", objSchema("to" to "收件人邮箱", "subject" to "邮件主题", "body" to "正文内容", required = listOf("to", "subject", "body"))),
            ToolSpec("calendar_list_events", "列出 Google Calendar 近期日程", objSchema()),
            ToolSpec("calendar_create_event", "在 Google Calendar 创建日程", objSchema("summary" to "日程标题", "start_iso" to "开始时间 ISO8601", "end_iso" to "结束时间 ISO8601", "description" to "备注", required = listOf("summary", "start_iso", "end_iso"))),
            ToolSpec("drive_write_file", "向 Google Drive /Neon/附件 写入文本或 CSV 文件", objSchema("filename" to "文件名", "content" to "文件内容", required = listOf("filename", "content"))),
            ToolSpec("phone_read_clipboard", "读取手机剪贴板文本", objSchema()),
            ToolSpec("phone_list_notifications", "读取最近捕获的手机通知", objSchema()),
            ToolSpec("phone_open_deeplink", "通过 DeepLink 或 URL 跳转应用", objSchema("url" to "目标链接", required = listOf("url"))),
            ToolSpec("webhook_trigger", "触发已配置的 Webhook 自动化端点", objSchema("payload" to "JSON 负载", required = listOf("payload"))),
        )

        connectorDao.listAll()
            .filter { (it.category == "remote_mcp" || it.category == "local_stdio") && it.status == "connected" }
            .forEach { conn ->
                runCatching {
                    val prefix = if (conn.category == "local_stdio") "localmcp" else "mcp"
                    val arr = json.parseToJsonElement(conn.toolsJson).jsonArray
                    arr.forEach { el ->
                        val obj = el.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                        val desc = obj["description"]?.jsonPrimitive?.content.orEmpty()
                        val schema = obj["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
                        base += ToolSpec("${prefix}_${conn.id}_$name", desc, schema)
                    }
                }
            }
        return if (hands) base else base.filterNot { it.name.startsWith("hands_") }
    }

    suspend fun execute(sessionId: String, toolName: String, inputJson: String): ToolOutcome {
        val out = runCatching { executeRaw(sessionId, toolName, inputJson) }.getOrElse { "ERROR: ${it.message ?: it.javaClass.simpleName}" }
        return ToolOutcome(out.take(MAX_RESULT), isError = out.startsWith("ERROR") || out.startsWith("DENIED"))
    }

    private suspend fun executeRaw(sessionId: String, toolName: String, inputJson: String): String {
        val args = runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        val assessment = RiskPolicy.assessToolCall(
            toolName = toolName,
            shellCommand = args["command"]?.jsonPrimitive?.content.orEmpty(),
        )
        if (assessment.level == RiskLevel.CONFIRM && toolName != "sandbox_exec" && !toolName.startsWith("hands_")) {
            val decision = riskGate.requestApproval(
                request = PendingGuardRequest(
                    title = "Sentinel 外部操作确认",
                    targetLabel = "$toolName(${inputJson.take(80)})",
                    targetPackage = "neon.connector",
                    reasons = assessment.reasons,
                    requireBiometric = assessment.requireBiometric,
                    stepSummary = "调用外部连接器工具",
                ),
                scopeKey = "tool:$toolName",
            )
            if (decision == GuardDecision.DENY) return "DENIED: 用户拒绝执行 $toolName"
        }

        return when (toolName) {
            "sandbox_exec" -> {
                val cmd = args["command"]?.jsonPrimitive?.content.orEmpty()
                val res = shellExecutor.execute(cmd)
                "exit=${res.exitCode}\n${res.output}"
            }
            "hands_dump_screen" -> handsOperator.dumpScreen()
            "hands_screenshot" -> handsOperator.captureSetOfMarkScreenshot()
            "hands_tap" -> {
                val idx = args["node_index"]?.jsonPrimitive?.intOrNull
                    ?: args["node_index"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return "ERROR: 缺少 node_index"
                handsOperator.tapNode(sessionId, idx)
            }
            "hands_swipe" -> {
                val x1 = args["x1"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 500f
                val y1 = args["y1"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1400f
                val x2 = args["x2"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 500f
                val y2 = args["y2"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 500f
                handsOperator.swipe(sessionId, x1, y1, x2, y2)
            }
            "hands_input" -> {
                val idx = args["node_index"]?.jsonPrimitive?.intOrNull
                    ?: args["node_index"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return "ERROR: 缺少 node_index"
                val text = args["text"]?.jsonPrimitive?.content.orEmpty()
                handsOperator.inputText(sessionId, idx, text)
            }
            "hands_list_apps" -> handsOperator.listInstalledApps()
            "hands_open_app" -> handsOperator.openApp(sessionId, args["package_name"]?.jsonPrimitive?.content.orEmpty())
            "hands_back" -> handsOperator.globalBack(sessionId)
            "gmail_list_messages" -> googleWorkspace.listGmailMessages(args["query"]?.jsonPrimitive?.content.orEmpty())
            "gmail_read_message" -> googleWorkspace.readGmailMessage(args["message_id"]?.jsonPrimitive?.content.orEmpty())
            "gmail_send_message" -> googleWorkspace.sendGmailMessage(
                args["to"]?.jsonPrimitive?.content.orEmpty(),
                args["subject"]?.jsonPrimitive?.content.orEmpty(),
                args["body"]?.jsonPrimitive?.content.orEmpty(),
            )
            "calendar_list_events" -> googleWorkspace.listCalendarEvents()
            "calendar_create_event" -> googleWorkspace.createCalendarEvent(
                args["summary"]?.jsonPrimitive?.content.orEmpty(),
                args["start_iso"]?.jsonPrimitive?.content.orEmpty(),
                args["end_iso"]?.jsonPrimitive?.content.orEmpty(),
                args["description"]?.jsonPrimitive?.content.orEmpty(),
            )
            "drive_write_file" -> {
                val name = args["filename"]?.jsonPrimitive?.content ?: "output.txt"
                val content = args["content"]?.jsonPrimitive?.content.orEmpty()
                googleWorkspace.uploadOrUpdateTextFile("附件", name, content).fold(
                    onSuccess = { "OK: 已写入 Google Drive /Neon/附件/$name (id=$it)" },
                    onFailure = { "ERROR: ${it.message}" },
                )
            }
            "phone_read_clipboard" -> nativeDevice.readClipboard()
            "phone_list_notifications" -> nativeDevice.listRecentNotifications()
            "phone_open_deeplink" -> {
                val ok = nativeDevice.openDeepLink(args["url"]?.jsonPrimitive?.content.orEmpty())
                if (ok) "OK: 已跳转" else "ERROR: 无法打开链接"
            }
            "webhook_trigger" -> {
                webhookConnector.trigger(args["payload"]?.jsonPrimitive?.content ?: "{}").fold(
                    onSuccess = { "OK: $it" },
                    onFailure = { "ERROR: ${it.message}" },
                )
            }
            else -> {
                if (toolName.startsWith("localmcp_")) {
                    val rest = toolName.removePrefix("localmcp_")
                    val connId = rest.substringBefore("_")
                    val remoteTool = rest.substringAfter("_")
                    val conn = connectorDao.getById(connId) ?: return "ERROR: 未找到本地 stdio MCP $connId"
                    localStdioMcp.callTool(conn.endpointUrl.orEmpty(), remoteTool, args).fold(
                        onSuccess = { it },
                        onFailure = { "ERROR: ${it.message}" },
                    )
                } else if (toolName.startsWith("mcp_")) {
                    val rest = toolName.removePrefix("mcp_")
                    val connId = rest.substringBefore("_")
                    val remoteTool = rest.substringAfter("_")
                    val conn = connectorDao.getById(connId) ?: return "ERROR: 未找到 MCP 连接器 $connId"
                    val token = tokenStore.getConnectorToken(connId)
                    mcpClient.callTool(conn.endpointUrl.orEmpty(), token, remoteTool, args).fold(
                        onSuccess = { it },
                        onFailure = { "ERROR: ${it.message}" },
                    )
                } else {
                    "ERROR: 未知工具 $toolName"
                }
            }
        }
    }

    companion object { const val MAX_RESULT = 24_000 }
}

/** Result of a real tool execution. */
data class ToolOutcome(val content: String, val isError: Boolean = false)
