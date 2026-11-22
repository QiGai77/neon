package com.qigai.neon.agent

import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.connectors.LocalStdioMcpBridge
import com.qigai.neon.connectors.McpClient
import com.qigai.neon.connectors.NativeDeviceTools
import com.qigai.neon.connectors.WebhookConnector
import com.qigai.neon.data.ConnectorDao
import com.qigai.neon.guard.ApprovalPolicy
import com.qigai.neon.vault.meta
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
    private val mcpHub: com.qigai.neon.connectors.McpHub,
    private val tokenStore: EncryptedTokenStore,
    private val riskGate: RiskGate,
    private val files: com.qigai.neon.sandbox.SandboxFiles? = null,
    private val space: com.qigai.neon.space.SpaceStore? = null,
    private val vault: com.qigai.neon.vault.Vault? = null,
    private val vaultForms: com.qigai.neon.vault.VaultFormGate? = null,
    private val browser: com.qigai.neon.vault.WebBrowser? = null,
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
            ToolSpec("sandbox_exec", "在 Alpine Linux 沙箱 (/workspace) 中执行 bash 命令。可用 python3 uv node npm pnpm git gcc make cmake rg jq sqlite3，可 apk add 安装其他软件", objSchema("command" to "要执行的 bash 命令", "timeout_sec" to "超时秒数，默认 120，安装依赖或编译时可设到 900", required = listOf("command"))),
            ToolSpec("hands_dump_screen", "获取当前手机屏幕的无障碍控件树（含编号与坐标）", objSchema()),
            ToolSpec("hands_screenshot", "截取当前屏幕并叠加 Set-of-Mark 编号框", objSchema()),
            ToolSpec("hands_tap", "点击屏幕上的指定编号控件", objSchema("node_index" to "控件编号整数", required = listOf("node_index"))),
            ToolSpec("hands_swipe", "在屏幕上执行滑动手势", objSchema("x1" to "起点X", "y1" to "起点Y", "x2" to "终点X", "y2" to "终点Y", required = listOf("x1", "y1", "x2", "y2"))),
            ToolSpec("hands_input", "向指定编号的输入框填入文本", objSchema("node_index" to "输入框编号", "text" to "文本内容", required = listOf("node_index", "text"))),
            ToolSpec("hands_list_apps", "列出手机上所有可启动的应用名称与包名", objSchema()),
            ToolSpec("hands_open_app", "按应用中文名或包名启动手机应用", objSchema("package_name" to "应用名或 Android 包名", required = listOf("package_name"))),
            ToolSpec("hands_back", "执行系统返回键", objSchema()),
            ToolSpec("gmail_list_messages", "搜索 Gmail，返回每封的发件人、主题、时间和摘要。query 用 Gmail 搜索语法，如 is:unread、from:xx、newer_than:7d，留空是最新邮件", objSchema("query" to "Gmail 搜索语句")),
            ToolSpec("gmail_read_message", "读取指定 Gmail 邮件的完整内容", objSchema("message_id" to "邮件 ID", required = listOf("message_id"))),
            ToolSpec("gmail_send_message", "发送 Gmail 邮件（需 Sentinel 指纹确认）", objSchema("to" to "收件人邮箱", "subject" to "邮件主题", "body" to "正文内容", required = listOf("to", "subject", "body"))),
            ToolSpec("calendar_list_events", "列出 Google Calendar 近期日程", objSchema()),
            ToolSpec("calendar_create_event", "在 Google Calendar 创建日程", objSchema("summary" to "日程标题", "start_iso" to "开始时间 ISO8601", "end_iso" to "结束时间 ISO8601", "description" to "备注", required = listOf("summary", "start_iso", "end_iso"))),
            ToolSpec("drive_search", "按文件名或全文搜索 Google Drive，留空列出最近修改的文件", objSchema("query" to "关键词")),
            ToolSpec("drive_read_file", "读取 Google Drive 文件的文本内容，文档和幻灯片导出为文本，表格导出为 CSV", objSchema("file_id" to "文件 ID", required = listOf("file_id"))),
            ToolSpec("drive_write_file", "向 Google Drive /Neon/附件 写入文本或 CSV 文件", objSchema("filename" to "文件名", "content" to "文件内容", required = listOf("filename", "content"))),
            ToolSpec("present_file", "把沙箱里做好的文件交给用户，对话里会显示成可打开、可分享的文件卡片。做完 Word、PDF、表格、图片、视频等交付物后调用", objSchema("path" to "文件在沙箱里的绝对路径，比如 /workspace/out/报告.docx", "title" to "卡片上显示的标题，可省略", required = listOf("path"))),
            ToolSpec("space_cards", "管理用户空间页的卡片：list 列出，add 添加，update 修改，remove 删除，move 调整位置。生活卡片类型 train 高铁票、flight 航班、parcel 快递、nav 导航、note 自定义；功能卡片类型 files skills connectors sandbox memory tasks hands drive。看到车票、航班、快递短信或用户要求时主动添加",
                buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", "string"); put("enum", kotlinx.serialization.json.JsonArray(listOf("list", "add", "update", "remove", "move").map { kotlinx.serialization.json.JsonPrimitive(it) })) }
                        putJsonObject("id") { put("type", "string"); put("description", "卡片 id，update remove move 时必填") }
                        putJsonObject("type") { put("type", "string"); put("description", "add 时的卡片类型") }
                        putJsonObject("size") { put("type", "string"); put("description", "half 半宽，full 整宽") }
                        putJsonObject("position") { put("type", "integer"); put("description", "move 的目标位置，从 0 开始") }
                        putJsonObject("data") {
                            put("type", "object")
                            put("description", "卡片内容。train: number from to depart arrive date seat gate；flight: number airline from to depart arrive date gate seat；parcel: carrier number status eta；nav: title address eta；note: title body emoji。时间写 HH:mm，日期写 YYYY-MM-DD")
                        }
                    }
                    putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("action")) }
                }),
            ToolSpec("phone_read_clipboard", "读取手机剪贴板文本", objSchema()),
            ToolSpec("phone_list_notifications", "读取最近捕获的手机通知", objSchema()),
            ToolSpec("phone_open_deeplink", "通过 DeepLink 或 URL 跳转应用", objSchema("url" to "目标链接", required = listOf("url"))),
            ToolSpec("vault_list", "列出本机保存的安全表单（网站登录账号或 Cookie）。只返回编号、网站和打码的账号，看不到密码和 Cookie 的值", objSchema("site" to "可选，只看某个网站")),
            ToolSpec("vault_request", "弹出安全表单让用户填写某网站的登录信息，加密存在手机上，只返回表单编号。kind=login 填账号密码，kind=cookie 粘贴 Cookie。需要登录而 vault_list 里没有时调用", objSchema("site" to "网站域名，如 github.com", "kind" to "login 或 cookie", "reason" to "一句话说明为什么要登录", "existing_id" to "可选，要更新的表单编号", required = listOf("site", "kind", "reason"))),
            ToolSpec("web_open", "用内置浏览器打开网页并返回页面文字和带编号的可操作元素。传 vault_id 时先注入该安全表单的 Cookie，或在登录页自动填好账号密码（值不经过你）", objSchema("url" to "网址", "vault_id" to "可选，安全表单编号", required = listOf("url"))),
            ToolSpec("web_read", "重新读取内置浏览器当前页面", objSchema()),
            ToolSpec("web_click", "点击内置浏览器页面上指定编号的元素", objSchema("index" to "元素编号", required = listOf("index"))),
            ToolSpec("web_type", "向内置浏览器页面上指定编号的输入框填入文字。不要用它填密码，密码用 web_fill", objSchema("index" to "输入框编号", "text" to "文字", "submit" to "true 时填完提交表单", required = listOf("index", "text"))),
            ToolSpec("web_fill", "用安全表单在当前页面填好账号和密码", objSchema("vault_id" to "安全表单编号", required = listOf("vault_id"))),
            ToolSpec("web_save_login", "把内置浏览器里当前网站的登录状态（Cookie）存进安全表单，下次 web_open 带上编号就能直接登录", objSchema("label" to "可选，名字", "existing_id" to "可选，更新已有表单")),
            ToolSpec("webhook_trigger", "触发已配置的 Webhook 自动化端点", objSchema("payload" to "JSON 负载", required = listOf("payload"))),
        )

        connectorDao.listAll()
            .filter { (it.category == "remote_mcp" || it.category == "local_stdio") && it.status == "connected" }
            .forEach { conn ->
                runCatching {
                    val arr = json.parseToJsonElement(conn.toolsJson).jsonArray
                    arr.forEach { el ->
                        val obj = el.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                        val desc = obj["description"]?.jsonPrimitive?.content.orEmpty()
                        val schema = obj["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
                        val alias = mcpAlias(conn.id, name)
                        mcpAliases[alias] = conn.id to name
                        base += ToolSpec(alias, "[${conn.name}] ${desc.take(900)}", schema)
                    }
                }
            }
        val noWeb = if (browser == null) base.filterNot { it.name.startsWith("web_") || it.name.startsWith("vault_") } else base
        return if (hands) noWeb else noWeb.filterNot { it.name.startsWith("hands_") }
    }

    suspend fun execute(sessionId: String, toolName: String, inputJson: String): ToolOutcome {
        val out = runCatching { executeRaw(sessionId, toolName, inputJson) }.getOrElse { "ERROR: ${it.message ?: it.javaClass.simpleName}" }
        val clipped = if (out.length <= MAX_RESULT) out
            else out.take(6_000) + "\n…中间省略 ${out.length - MAX_RESULT} 字…\n" + out.takeLast(MAX_RESULT - 6_000)
        return ToolOutcome(clipped, isError = out.startsWith("ERROR") || out.startsWith("DENIED"))
    }

    /** Hands calls that only look, plus hands_tap, which HandsOperator gates itself with the button label. */
    private val handsReads = setOf("hands_tap", "hands_dump_screen", "hands_screenshot", "hands_list_apps")

    private suspend fun executeRaw(sessionId: String, toolName: String, inputJson: String): String {
        val args = runCatching { json.parseToJsonElement(inputJson).jsonObject }.getOrElse { JsonObject(emptyMap()) }
        val assessment = RiskPolicy.assessToolCall(
            toolName = toolName,
            shellCommand = args["command"]?.jsonPrimitive?.content.orEmpty(),
        )
        if (toolName != "sandbox_exec" && toolName !in handsReads) {
            val clickLabel = if (toolName == "web_click") args["index"]?.jsonPrimitive?.content?.toIntOrNull()?.let { browser?.element(it)?.label }.orEmpty() else ""
            val type = ApprovalPolicy.typeOf(toolName, if (toolName.startsWith("hands_")) inputJson else clickLabel)
            val base = if (clickLabel.isNotEmpty() && com.qigai.neon.guard.TapWords.findMatches(clickLabel).isNotEmpty()) RiskLevel.CONFIRM else assessment.level
            val decision = riskGate.gate(
                type = type, base = base, write = ApprovalPolicy.isWrite(toolName),
                request = PendingGuardRequest(
                    title = "Sentinel 外部操作确认",
                    targetLabel = if (clickLabel.isNotEmpty()) "点击「$clickLabel」" else "$toolName(${inputJson.take(80)})",
                    targetPackage = "neon.connector",
                    reasons = assessment.reasons,
                    requireBiometric = assessment.requireBiometric,
                    stepSummary = if (toolName.startsWith("hands_")) "操作手机" else "调用外部连接器工具",
                ),
                scopeKey = "tool:$toolName",
            )
            if (decision == GuardDecision.DENY) return "DENIED: 用户拒绝执行 $toolName"
        }

        return when (toolName) {
            "sandbox_exec" -> {
                val cmd = args["command"]?.jsonPrimitive?.content.orEmpty()
                val timeout = (args["timeout_sec"]?.jsonPrimitive?.content?.toLongOrNull() ?: 120L).coerceIn(10L, 1800L)
                val res = shellExecutor.execute(cmd, timeout)
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
            "drive_search" -> googleWorkspace.searchDrive(args["query"]?.jsonPrimitive?.content.orEmpty())
            "drive_read_file" -> googleWorkspace.readDriveFile(args["file_id"]?.jsonPrimitive?.content.orEmpty())
            "drive_write_file" -> {
                val name = args["filename"]?.jsonPrimitive?.content ?: "output.txt"
                val content = args["content"]?.jsonPrimitive?.content.orEmpty()
                googleWorkspace.uploadOrUpdateTextFile("附件", name, content).fold(
                    onSuccess = { "OK: 已写入 Google Drive /Neon/附件/$name (id=$it)" },
                    onFailure = { "ERROR: ${it.message}" },
                )
            }
            "present_file" -> {
                val path = args["path"]?.jsonPrimitive?.content.orEmpty()
                val f = files?.host(path)
                if (f == null || !f.exists()) "ERROR: 沙箱里没有 $path"
                else "OK: 已交给用户 ${f.name}，${f.length()} 字节"
            }
            "space_cards" -> space?.handleTool(args) ?: "ERROR: 空间不可用"
            "phone_read_clipboard" -> nativeDevice.readClipboard()
            "phone_list_notifications" -> nativeDevice.listRecentNotifications()
            "vault_list" -> {
                val v = vault ?: return "ERROR: 安全表单不可用"
                val site = args["site"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                val list = if (site != null) v.forSite(site).map { it.meta() } else v.items.value
                if (list.isEmpty()) "还没有保存的安全表单" + (site?.let { "（$it）" } ?: "")
                else list.joinToString("\n") { m ->
                    "${m.id}  ${m.site}  ${if (m.kind == "login") "账号 ${m.user}" else "Cookie ${m.cookieCount} 项"}${if (m.label.isNotBlank() && m.label != m.site) "  ${m.label}" else ""}"
                }
            }
            "vault_request" -> {
                val g = vaultForms ?: return "ERROR: 安全表单不可用"
                val site = args["site"]?.jsonPrimitive?.content.orEmpty()
                if (site.isBlank()) return "ERROR: 缺少 site"
                val kind = if (args["kind"]?.jsonPrimitive?.content == "cookie") "cookie" else "login"
                val item = g.request(com.qigai.neon.vault.VaultFormRequest(
                    site = com.qigai.neon.vault.siteOf(site), kind = kind,
                    reason = args["reason"]?.jsonPrimitive?.content.orEmpty(),
                    existingId = args["existing_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                ))
                if (item == null) "DENIED: 用户没有填写安全表单" else "OK: 已保存安全表单 ${item.id}（${item.site}），用 web_open 时传 vault_id=${item.id}"
            }
            "web_open" -> {
                val b = browser ?: return "ERROR: 浏览器不可用"
                b.open(args["url"]?.jsonPrimitive?.content ?: return "ERROR: 缺少 url", args["vault_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
            }
            "web_read" -> browser?.snapshot() ?: "ERROR: 浏览器不可用"
            "web_click" -> {
                val i = args["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "ERROR: 缺少 index"
                browser?.click(i) ?: "ERROR: 浏览器不可用"
            }
            "web_type" -> {
                val b = browser ?: return "ERROR: 浏览器不可用"
                val i = args["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: return "ERROR: 缺少 index"
                if (b.element(i)?.password == true) return "ERROR: 密码框请用 web_fill 和安全表单填写"
                val r = b.type(i, args["text"]?.jsonPrimitive?.content.orEmpty(), args["submit"]?.jsonPrimitive?.content == "true")
                if (r == "OK" && args["submit"]?.jsonPrimitive?.content == "true") { kotlinx.coroutines.delay(1500); b.snapshot() } else r
            }
            "web_fill" -> {
                val b = browser ?: return "ERROR: 浏览器不可用"
                val item = vault?.get(args["vault_id"]?.jsonPrimitive?.content.orEmpty()) ?: return "ERROR: 没有这个安全表单"
                if (item.kind != "login") return "ERROR: 这个安全表单只有 Cookie，用 web_open 带上 vault_id"
                if (b.fillLogin(item)) "OK: 已填好账号和密码，点登录按钮提交" else "ERROR: 当前页面没有对应 ${item.site} 的登录框"
            }
            "web_save_login" -> {
                val b = browser ?: return "ERROR: 浏览器不可用"
                val item = b.saveLogin(args["existing_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }, args["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
                if (item == null) "ERROR: 当前页面没有可保存的登录状态" else "OK: 登录状态已存为安全表单 ${item.id}（${item.site}）"
            }
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
                val target = mcpAliases[toolName] ?: if (toolName.startsWith("mcp_") || toolName.startsWith("localmcp_")) {
                    val rest = toolName.substringAfter("_")
                    rest.substringBefore("_") to rest.substringAfter("_")
                } else null
                if (target == null) "ERROR: 未知工具 $toolName"
                else mcpHub.call(target.first, target.second, args).fold(
                    onSuccess = { it },
                    onFailure = { "ERROR: ${it.message}" },
                )
            }
        }
    }

    private val mcpAliases = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    /** Function names must match ^[a-zA-Z0-9_-]{1,64}$ for every provider. */
    private fun mcpAlias(connId: String, tool: String): String {
        val clean = tool.map { if (it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-') it else '_' }.joinToString("")
        val full = "mcp_${connId}_$clean"
        return if (full.length <= 64) full else full.take(55) + "_" + Integer.toHexString(full.hashCode()).take(8)
    }

    companion object { const val MAX_RESULT = 24_000 }
}

/** Result of a real tool execution. */
data class ToolOutcome(val content: String, val isError: Boolean = false)
