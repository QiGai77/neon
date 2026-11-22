package com.qigai.neon.ui.caps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.ui.components.*
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import com.qigai.neon.MainViewModel
import com.qigai.neon.data.ConnectorEntity
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.guard.DeviceCredential
import com.qigai.neon.guard.GuardDecision
import com.qigai.neon.guard.PendingGuardRequest
import com.qigai.neon.hands.HandsEngineMode
import com.qigai.neon.hands.NeonAccessibilityService
import com.qigai.neon.oauth.GoogleDriveAuth
import com.qigai.neon.connectors.GoogleAccount
import com.qigai.neon.sandbox.SandboxStatus
import com.qigai.neon.ui.chat.NeonSheetHost
import com.qigai.neon.ui.connectors.*
import com.qigai.neon.ui.hands.*
import com.qigai.neon.ui.memory.*
import com.qigai.neon.ui.sandbox.*
import com.qigai.neon.ui.skills.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun isSandboxRequest(r: PendingGuardRequest) = r.targetPackage.startsWith("sandbox")

// ───────────────────────── shared input dialog ─────────────────────────

data class Field(val label: String, val initial: String = "", val multiline: Boolean = false)

@Composable
fun InputDialog(title: String, fields: List<Field>, confirm: String, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    val c = Neon.colors
    val values = remember { fields.map { mutableStateOf(it.initial) } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = NeonType.headline().copy(fontSize = 18.sp))
            fields.forEachIndexed { i, f ->
                var v by values[i]
                Column(
                    Modifier.fillMaxWidth().clip(NeonShapes.tile).background(c.bgTile).border(1.dp, c.borderCard, NeonShapes.tile)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(f.label, style = NeonType.overline())
                    BasicTextField(
                        v, { v = it }, singleLine = !f.multiline, textStyle = NeonType.label(), cursorBrush = SolidColor(c.inkPrimary),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).then(if (f.multiline) Modifier.heightIn(min = 120.dp, max = 260.dp) else Modifier),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                PillButton("取消", kind = ButtonKind.Secondary, height = 40.dp, onClick = onDismiss)
                PillButton(confirm, height = 40.dp, onClick = { onConfirm(values.map { it.value.trim() }) })
            }
        }
    }
}

// ───────────────────────── Sentinel (global) ─────────────────────────

/** Shows the Sentinel sheet for any pending high-risk action except shell commands seen on the sandbox screen. */
@Composable
fun SentinelHost(vm: MainViewModel, onSandboxScreen: Boolean, content: @Composable () -> Unit) {
    val gate = vm.c.riskGate
    val pending by gate.pendingRequest.collectAsState()
    val show = pending != null && !(onSandboxScreen && isSandboxRequest(pending!!))
    var last by remember { mutableStateOf(pending) }
    if (pending != null) last = pending
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun authThen(decision: GuardDecision) {
        val r = last ?: return
        val act = ctx as? FragmentActivity
        if (!r.requireBiometric || act == null || !DeviceCredential.canAuthenticate(act)) { gate.resolve(decision); return }
        scope.launch { DeviceCredential.authenticate(act, "确认操作", r.targetLabel).onSuccess { gate.resolve(decision) } }
    }
    NeonSheetHost(open = show, onDismiss = { gate.resolve(GuardDecision.DENY) }, content = content) {
        val r = last
        if (r != null) SentinelSheetContent(
            SentinelUi(
                title = r.title,
                origin = r.stepSummary,
                targetKind = if (isSandboxRequest(r)) "命令" else "操作",
                targetLabel = r.targetLabel,
                targetPackage = r.targetPackage,
                requireBiometric = r.requireBiometric,
            ),
            onDeny = { gate.resolve(GuardDecision.DENY) },
            onLease = { authThen(GuardDecision.ALLOW_LEASE) },
            onAltAuth = { authThen(GuardDecision.ALLOW_ONCE) },
        )
    }
}

// ───────────────────────── 连接器 ─────────────────────────

@Composable
fun rememberWorkspaceAuthorizer(vm: MainViewModel, onDone: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun finish(token: String?) = scope.launch {
        vm.c.settings.put(SettingsRepository.WORKSPACE, "1")
        // Google's own Gmail, Calendar and Drive MCP servers need a preview grant; the built-in tools replace them.
        GoogleAccount.legacyIds.forEach { runCatching { vm.c.mcpHub.disconnect(it) } }
        vm.onDriveAuthorized(token)
        vm.toast("Google 已连接")
        onDone()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val token = runCatching { GoogleDriveAuth.resultFromIntent(ctx, res.data).accessToken }.getOrNull()
            finish(token)
        } else vm.toast("已取消")
    }
    return {
        scope.launch {
            try {
                val r = GoogleDriveAuth.authorizeWorkspace(ctx)
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else finish(r.accessToken)
            } catch (e: Exception) {
                vm.toast(googleError(e))
            }
        }
    }
}

@Composable
fun ConnectorsRoute(vm: MainViewModel, onBack: () -> Unit, onOpenHands: () -> Unit, onMarket: () -> Unit = {}) {
    val c = vm.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsState()
    val rows by c.db.connectorDao().observeAll().collectAsState(initial = emptyList())
    val a11y by NeonAccessibilityService.instance.collectAsState()
    val authorize = rememberWorkspaceAuthorizer(vm)
    var addMcp by remember { mutableStateOf(false) }
    var googleSetup by remember { mutableStateOf(false) }
    val gMail by c.googleAccount.email.collectAsState()
    var webhook by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ConnectorEntity?>(null) }
    val notif = remember(rows) { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) }

    val list = buildList {
        val gAccount = gMail ?: s.driveEmail?.takeIf { s.workspaceConnected && it.isNotBlank() }
        val gOn = gMail != null || s.workspaceConnected
        add(ConnectorUi("google", ConnectorGroup.CLOUD, "Google",
            gAccount ?: "Gmail、日历、Drive",
            if (gOn) ConnectorStatus.Connected("已连接") else ConnectorStatus.Idle, monogram = "G", action = "连接"))
        rows.forEach { e ->
            val group = if (e.category == "local_stdio") ConnectorGroup.LOCAL else ConnectorGroup.CLOUD
            val st = when (e.status) {
                "connected" -> ConnectorStatus.Connected("${e.toolCount} 个工具")
                "connecting" -> ConnectorStatus.Authorizing(e.subtitle)
                "error" -> ConnectorStatus.Failed("失败")
                else -> ConnectorStatus.Idle
            }
            val preset = com.qigai.neon.connectors.ConnectorCatalog.byId(e.id)
            val sub = if (e.status == "error") e.subtitle else preset?.desc ?: e.endpointUrl.orEmpty()
            add(ConnectorUi(e.id, group, e.name, sub, st,
                monogram = if (preset != null && !preset.local) preset.monogram else null,
                icon = if (group == ConnectorGroup.LOCAL) Lucide.SquareTerminal else Lucide.Plug,
                badge = if (e.category == "local_stdio") "沙箱" else null, action = "重试"))
        }
        add(ConnectorUi("hands", ConnectorGroup.LOCAL, "Hands", "操作手机", if (a11y != null) ConnectorStatus.Connected("已开启") else ConnectorStatus.Idle, icon = Lucide.Hand, action = "开启"))
        add(ConnectorUi("phone", ConnectorGroup.LOCAL, "通知", "读取最近的通知", if (notif) ConnectorStatus.Connected("已授权") else ConnectorStatus.Idle, icon = Lucide.Smartphone, action = "授权"))
        val wh = c.tokens.getWebhookUrl()
        add(ConnectorUi("webhook", ConnectorGroup.AUTOMATION, "Webhook", wh.ifBlank { "未设置" }, ConnectorStatus.Idle, icon = Lucide.Workflow, action = if (wh.isBlank()) "设置" else "测试"))
    }

    ConnectorsContent(
        connectors = list,
        onBack = onBack,
        onSearch = onMarket,
        onMarket = onMarket,
        connectedCount = rows.count { it.status == "connected" },
        onOpen = { r ->
            when (r.id) {
                "hands" -> onOpenHands()
                "webhook" -> webhook = true
                "google" -> googleSetup = true
                else -> detail = rows.firstOrNull { it.id == r.id }
            }
        },
        onAction = { r ->
            when (r.id) {
                "google" -> if (gMail != null || s.workspaceConnected) googleSetup = true else authorize()
                "hands" -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "phone" -> ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "webhook" -> if (c.tokens.getWebhookUrl().isBlank()) webhook = true else scope.launch {
                    c.webhook.trigger("""{"event":"neon_ping"}""").fold({ vm.toast("Webhook 正常") }, { vm.toast(it.message ?: "失败") })
                }
                else -> rows.firstOrNull { it.id == r.id }?.let { e -> scope.launch { reconnect(vm, e) } }
            }
        },
        onAddMcp = { addMcp = true },
    )

    if (googleSetup) GoogleSetupDialog(vm, authorize) { googleSetup = false }
    if (addMcp) InputDialog("添加 MCP", listOf(Field("名称"), Field("地址或沙箱命令")), "连接", { addMcp = false }) { (name, target) ->
        addMcp = false
        if (target.isNotBlank()) scope.launch {
            val local = !target.startsWith("http")
            val id = UUID.randomUUID().toString().filter { it.isLetterOrDigit() }.take(8)
            val n = name.ifBlank { if (local) "本地 MCP" else "MCP" }
            (if (local) vm.c.mcpHub.connectLocal(id, n, target) else vm.c.mcpHub.connectRemote(id, n, target.trim()))
                .fold({ vm.toast("$n：$it 个工具") }, { vm.toast("$n 连接失败") })
        }
    }
    detail?.let { e ->
        com.qigai.neon.ui.market.InfoDialog(
            title = e.name,
            lines = listOfNotNull(
                if (e.status == "connected") "${e.toolCount} 个工具" else e.subtitle.takeIf { it.isNotBlank() },
                e.endpointUrl,
            ),
            onDismiss = { detail = null },
            actions = {
                PillButton("断开", kind = ButtonKind.Danger, height = 40.dp, onClick = { scope.launch { c.mcpHub.disconnect(e.id) }; detail = null })
                PillButton("重新连接", kind = ButtonKind.Secondary, height = 40.dp, onClick = { detail = null; scope.launch { reconnect(vm, e) } })
            },
        )
    }
    if (webhook) InputDialog("Webhook", listOf(Field("地址", c.tokens.getWebhookUrl()), Field("签名密钥", c.tokens.getWebhookSecret())), "保存", { webhook = false }) { (url, secret) ->
        webhook = false
        c.tokens.saveWebhookConfig(url, secret)
    }
}

/** Connects again with what is already stored (token, headers, OAuth refresh), signing in again if needed. */
private suspend fun reconnect(vm: MainViewModel, e: ConnectorEntity) {
    val hub = vm.c.mcpHub
    val r = if (e.category == "local_stdio") hub.connectLocal(e.id, e.name, e.endpointUrl.orEmpty())
    else hub.connectRemote(e.id, e.name, e.endpointUrl.orEmpty())
    r.fold({ vm.toast("${e.name}：$it 个工具") }, { vm.toast("${e.name}：${it.message ?: "连接失败"}") })
}

@Composable
private fun GoogleSetupDialog(vm: MainViewModel, oneTap: () -> Unit, onDismiss: () -> Unit) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val g = vm.c.googleAccount
    val mail by g.email.collectAsState()
    val saved = remember { g.client() }
    var id by remember { mutableStateOf(saved?.clientId.orEmpty()) }
    var secret by remember { mutableStateOf(saved?.clientSecret.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val st by vm.settings.collectAsState()
    var own by remember { mutableStateOf(saved != null) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("连接 Google", style = NeonType.headline().copy(fontSize = 18.sp))
            if (mail != null) {
                Text("已登录 $mail。Neon 可以读写 Gmail、日历和 Drive，对话也会备份到 Drive。", style = NeonType.caption())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PillButton("退出登录", kind = ButtonKind.Danger, height = 40.dp, onClick = { g.signOut(); onDismiss() })
                    PillButton("好", height = 40.dp, onClick = onDismiss)
                }
                return@Column
            }
            if (st.workspaceConnected && !own) {
                Text("已连接${st.driveEmail?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}。Neon 可以读写 Gmail、日历和 Drive，对话也会备份到 Drive。", style = NeonType.caption())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PillButton("断开", kind = ButtonKind.Danger, height = 40.dp, onClick = {
                        scope.launch { vm.c.settings.put(SettingsRepository.WORKSPACE, null) }
                        onDismiss()
                    })
                    PillButton("好", height = 40.dp, onClick = onDismiss)
                }
                return@Column
            }
            if (!own) {
                Text("选一个 Google 账号，点允许就行。Neon 会读写 Gmail、日历和 Drive，令牌只存在这台手机上。", style = NeonType.caption())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PillButton("用自己的客户端", kind = ButtonKind.Secondary, height = 40.dp, onClick = { own = true })
                    PillButton("连接 Google", height = 40.dp, onClick = { onDismiss(); oneTap() })
                }
                return@Column
            }
            Text("用你自己的 Google Cloud 桌面应用客户端登录，只给自己用，不用审核。", style = NeonType.caption())
            Step(1, "建一个客户端，类型选桌面应用", "去创建") { com.qigai.neon.oauth.CustomTabs.open(ctx, GoogleAccount.SETUP_URL) }
            Step(2, "把客户端 ID 和密钥粘贴到这里", null) {}
            listOf("客户端 ID" to id, "客户端密钥" to secret).forEachIndexed { i, (label, v) ->
                Column(
                    Modifier.fillMaxWidth().clip(NeonShapes.tile).background(c.bgTile).border(1.dp, c.borderCard, NeonShapes.tile)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(label, style = NeonType.overline())
                    BasicTextField(
                        v, { if (i == 0) id = it.trim() else secret = it.trim() }, singleLine = true,
                        textStyle = NeonType.mono().copy(fontSize = 12.sp, color = c.inkPrimary), cursorBrush = SolidColor(c.inkPrimary),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
            error?.let { Text(it, style = NeonType.caption().copy(color = c.danger)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                PillButton("取消", kind = ButtonKind.Secondary, height = 40.dp, enabled = !busy, onClick = onDismiss)
                PillButton(
                    if (busy) "在浏览器里登录" else "登录 Google", height = 40.dp,
                    enabled = !busy && id.endsWith(".apps.googleusercontent.com"),
                    onClick = {
                        busy = true; error = null
                        g.saveClient(id, secret)
                        scope.launch {
                            g.signIn().fold(
                                onSuccess = { m ->
                                    GoogleAccount.legacyIds.forEach { vm.c.mcpHub.disconnect(it) }
                                    g.accessToken()?.let { vm.onDriveAuthorized(it) }
                                    vm.toast("Google 已登录：$m")
                                    onDismiss()
                                },
                                onFailure = { e ->
                                    busy = false
                                    error = googleError(e)
                                },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String, action: String?, onAction: () -> Unit) {
    val c = Neon.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(c.bgTile), contentAlignment = Alignment.Center) {
            Text("$n", style = NeonType.caption().copy(color = c.inkPrimary))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = NeonType.label(), modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            NeonChip(action, style = ChipStyle.Outline, height = 30.dp, onClick = onAction)
        }
    }
}

private fun googleError(e: Throwable): String {
    val m = e.message.orEmpty()
    val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
    return when {
        code == 10 -> "这个安装包的签名没在 Google 登记，可以先用自己的客户端连接"
        code == 7 -> "网络连不上 Google"
        code == 16 || code == 13 -> "已取消"
        "invalid_client" in m || "401" in m -> "客户端 ID 或密钥不对"
        "access_denied" in m || "拒绝" in m -> "你在 Google 页面取消了授权"
        "redirect_uri" in m -> "客户端类型要选桌面应用"
        else -> m.take(120).ifBlank { "连接失败" }
    }
}

// ───────────────────────── Hands ─────────────────────────

@Composable
fun HandsRoute(vm: MainViewModel, onBack: () -> Unit) {
    val c = vm.c
    val traces by c.db.handsTraceDao().observeLatestRun().collectAsState(initial = emptyList())
    val capsule by c.hands.capsuleState.collectAsState()
    val mode by c.hands.engineMode.collectAsState()
    val stuck by c.hands.stuckWarning.collectAsState()
    val pending by c.riskGate.pendingRequest.collectAsState()
    val a11y by NeonAccessibilityService.instance.collectAsState()
    val guard = pending?.takeIf { !isSandboxRequest(it) }
    Box(Modifier.fillMaxSize()) {
        HandsTaskContent(
            HandsTaskUi(
                title = capsule.stepText.ifBlank { "Hands" },
                step = traces.size.coerceAtLeast(capsule.stepIndex),
                total = capsule.maxSteps,
                blockedCountdown = if (guard != null) -1 else null,
                reasons = guard?.let { g -> listOf(GuardReason("目标", g.targetLabel, true)) + g.reasons.map { GuardReason(it, "") } } ?: emptyList(),
                trace = traces.map { t -> "${t.actionType} ${t.targetLabel}".trim() },
            ),
            onBack = onBack, onMore = { c.hands.stopRun() },
        )
        HandsEngineCard(
            engine = if (mode == HandsEngineMode.SHIZUKU) HandsEngine.SHIZUKU else HandsEngine.ACCESSIBILITY,
            available = buildSet { if (a11y != null) add(HandsEngine.ACCESSIBILITY); if (c.shizuku.hasPermission()) add(HandsEngine.SHIZUKU) },
            loopWatch = if (stuck) "检测到重复操作，已暂停" else "运行正常",
            onEngine = { c.hands.setEngineMode(if (it == HandsEngine.SHIZUKU) HandsEngineMode.SHIZUKU else HandsEngineMode.ACCESSIBILITY) },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }
}

// ───────────────────────── 技能库 ─────────────────────────

private fun iconFor(tags: List<String>): ImageVector {
    val t = tags.joinToString(" ").lowercase()
    return when {
        "hands" in t -> Lucide.Hand
        "gmail" in t || "mail" in t -> Lucide.Mail
        "calendar" in t || "日历" in t -> Lucide.Calendar
        "sandbox" in t || "alpine" in t || "shell" in t -> Lucide.SquareTerminal
        "drive" in t -> Lucide.Cloud
        else -> Lucide.Sparkles
    }
}

fun riskOf(s: String) = when {
    "高" in s || s.contains("high", true) -> Risk.HIGH
    "中" in s || s.contains("medium", true) -> Risk.MEDIUM
    else -> Risk.LOW
}

@Composable
fun SkillsRoute(vm: MainViewModel, onBack: () -> Unit, onMarket: () -> Unit = {}) {
    val c = vm.c
    val scope = rememberCoroutineScope()
    val docs by c.skills.skills.collectAsState()
    val traces by c.db.handsTraceDao().observeLatestRun().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<com.qigai.neon.skills.SkillDocument?>(null) }
    val shown = docs.filter { query.isBlank() || it.name.contains(query, true) || it.command.contains(query, true) || it.description.contains(query, true) }
    SkillsContent(
        skills = shown.map { d -> SkillUi(d.name, d.command.removePrefix("/"), iconFor(d.connectors), riskOf(d.risk), d.connectors.ifEmpty { listOfNotNull(d.source?.substringBeforeLast('/')) }) },
        learned = if (traces.isEmpty()) null else LearnedRunUi(
            "从上次操作生成技能",
            SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(traces.last().createdAt)),
            traces.take(4).map { it.targetLabel.ifBlank { it.actionType }.take(8) },
            "",
        ),
        driveSynced = null, query = query, onQuery = { query = it }, onBack = onBack,
        onOpen = { ui -> viewing = docs.firstOrNull { it.name == ui.name } },
        onDistill = { scope.launch { c.skillSynth.synthesizeFromTraces(traces).fold({ vm.toast("已保存「${it.name}」") }, { vm.toast(it.message ?: "生成失败") }) } },
        onViewTrace = {},
        onAdd = { adding = true },
        onMarket = onMarket,
    )
    if (adding) InputDialog("添加技能", listOf(Field("SKILL.md", multiline = true)), "保存", { adding = false }) { (md) ->
        adding = false
        if (md.isNotBlank()) scope.launch { c.skills.saveSkill("skill_${System.currentTimeMillis()}.SKILL.md", md).fold({ vm.toast("已保存") }, { vm.toast(it.message ?: "格式不对") }) }
    }
    viewing?.let { d ->
        Dialog(onDismissRequest = { viewing = null }) {
            val col = Neon.colors
            Column(Modifier.fillMaxWidth().clip(NeonShapes.card).background(col.bgSurface).padding(20.dp)) {
                Text(d.name, style = NeonType.headline().copy(fontSize = 18.sp))
                Text(d.command, style = NeonType.mono())
                Spacer(Modifier.height(12.dp))
                Text(d.body, style = NeonType.secondary(), modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PillButton("删除", kind = ButtonKind.Danger, height = 40.dp, onClick = { scope.launch { c.skills.deleteSkill(d) }; viewing = null })
                    PillButton("关闭", height = 40.dp, onClick = { viewing = null })
                }
            }
        }
    }
}

// ───────────────────────── 沙箱 ─────────────────────────

@Composable
fun SandboxRoute(vm: MainViewModel, onBack: () -> Unit) {
    val c = vm.c
    val scope = rememberCoroutineScope()
    val status by c.rootfs.status.collectAsState()
    val lines by c.shell.lines.collectAsState()
    val pending by c.riskGate.pendingRequest.collectAsState()
    var input by remember { mutableStateOf("") }
    val history = remember { mutableListOf<String>() }
    var hIdx by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) { if (status !is SandboxStatus.Ready) runCatching { c.rootfs.ensureInitialized() } }
    val ctx = LocalContext.current
    val block = pending?.takeIf { isSandboxRequest(it) }
    var installing by remember { mutableStateOf<com.qigai.neon.sandbox.DevEnv?>(null) }
    var envTick by remember { mutableStateOf(0) }
    val envs = remember(status, envTick, installing) {
        if (status !is SandboxStatus.Ready) emptyList()
        else com.qigai.neon.sandbox.DevEnv.entries.map { e -> DevEnvUi(e.name, e.label, c.rootfs.isInstalled(e), installing == e) }
    }
    fun submit() {
        val cmd = input.trim()
        if (cmd.isEmpty()) return
        history += cmd; hIdx = -1; input = ""
        scope.launch { c.shell.execute(cmd) }
    }
    SandboxContent(
        title = "Alpine 沙箱",
        subtitle = when (val st = status) {
            is SandboxStatus.Ready -> "就绪"
            is SandboxStatus.Extracting -> "解压中"
            is SandboxStatus.MissingBundle -> "缺少系统包"
            is SandboxStatus.Error -> "出错"
            SandboxStatus.Checking -> "检查中"
        },
        facts = when (val st = status) {
            is SandboxStatus.Ready -> listOf(st.arch, "/workspace")
            is SandboxStatus.Extracting -> listOf(st.detail)
            is SandboxStatus.MissingBundle -> listOf(st.reason)
            is SandboxStatus.Error -> listOf(st.message)
            SandboxStatus.Checking -> emptyList()
        },
        lines = lines.takeLast(300).map { l ->
            when {
                l.isCommand -> TermLine(LineKind.PROMPT, l.text.removePrefix("$ ").removePrefix("/workspace $ "))
                l.isError -> TermLine(LineKind.DANGER, l.text)
                else -> TermLine(LineKind.OUT, l.text)
            }
        },
        input = input, onInput = { input = it },
        blocked = block?.let { ShellBlockUi(it.targetLabel, it.reasons.joinToString("；")) },
        onBack = onBack,
        onMore = { c.shell.clearHistory() },
        onKeepBlocked = { c.riskGate.resolve(GuardDecision.DENY) },
        onRunAnyway = {
            val act = ctx as? FragmentActivity
            if (block?.requireBiometric == true && act != null && DeviceCredential.canAuthenticate(act)) {
                scope.launch { DeviceCredential.authenticate(act, "执行命令", block.targetLabel).onSuccess { c.riskGate.resolve(GuardDecision.ALLOW_ONCE) } }
            } else c.riskGate.resolve(GuardDecision.ALLOW_ONCE)
        },
        onKey = { k ->
            when (k) {
                "ESC" -> input = ""
                "TAB" -> input += "\t"
                "↑" -> if (history.isNotEmpty()) { hIdx = if (hIdx < 0) history.lastIndex else (hIdx - 1).coerceAtLeast(0); input = history[hIdx] }
                "↓" -> if (hIdx >= 0) { hIdx++; input = if (hIdx > history.lastIndex) { hIdx = -1; "" } else history[hIdx] }
                "CTRL", "ALT" -> {}
                else -> input += k
            }
        },
        onSubmit = { submit() },
        envs = envs,
        onEnv = { ui ->
            val env = com.qigai.neon.sandbox.DevEnv.valueOf(ui.id)
            if (installing == null) scope.launch {
                installing = env
                val r = c.shell.execute("apk add --no-cache ${env.packages}", timeoutSec = 1200)
                installing = null; envTick++
                vm.toast(if (r.exitCode == 0) "${env.label} 已安装" else "${env.label} 安装失败")
            }
        },
    )
}

// ───────────────────────── 记忆与任务 ─────────────────────────

private val INTERVALS = listOf("每小时" to 60L, "每 2 小时" to 120L, "每天" to 1440L, "每周" to 10080L)

@Composable
fun MemoryTasksRoute(vm: MainViewModel, onBack: () -> Unit) {
    val c = vm.c
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsState()
    val facts by c.memory.items.collectAsState()
    val tasks by c.db.scheduledTaskDao().observeAll().collectAsState(initial = emptyList())
    val receipts by c.db.taskReceiptDao().observeRecent().collectAsState(initial = emptyList())
    var addFact by remember { mutableStateOf(false) }
    var addTask by remember { mutableStateOf(false) }
    val latest = receipts.firstOrNull()
    MemoryTasksContent(
        facts = facts,
        memorySynced = s.driveConnected,
        tasks = tasks.map { t ->
            val r = latest?.takeIf { it.taskId == t.id }
            TaskUi(
                t.id, t.title, t.scheduleLabel, iconFor(listOf(t.skillName, t.prompt)), t.enabled,
                r?.let {
                    ReceiptUi(
                        "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.executedAt))} ${if (it.status == "success") "成功" else "失败"}",
                        it.status == "success",
                        listOf(Triple("耗时", "%.1f".format(it.durationMs / 1000f), "s")),
                    )
                },
            )
        },
        onBack = onBack,
        onAdd = { addTask = true },
        onEditMemory = { addFact = true },
        onRemoveFact = { f -> scope.launch { c.memory.removeMemory(f) } },
        onAddFact = { addFact = true },
        onToggle = { ui, on -> tasks.firstOrNull { it.id == ui.id }?.let { e -> scope.launch { c.tasks.setEnabled(e, on) } } },
        onOpenReceipt = { ui -> receipts.firstOrNull { it.taskId == ui.id }?.sessionId?.let { vm.open(it); onBack() } },
        onRerun = { ui -> c.tasks.runNow(ui.id); vm.toast("已开始") },
    )
    if (addFact) InputDialog("添加记忆", listOf(Field("内容")), "保存", { addFact = false }) { (v) ->
        addFact = false
        if (v.isNotBlank()) scope.launch { c.memory.addMemory(v) }
    }
    if (addTask) InputDialog("新建任务", listOf(Field("名称"), Field("让 Neon 做什么", multiline = true), Field("频率：每小时 每 2 小时 每天 每周", "每天")), "创建", { addTask = false }) { (name, prompt, freq) ->
        addTask = false
        val (label, minutes) = INTERVALS.firstOrNull { it.first == freq } ?: ("每天" to 1440L)
        if (prompt.isNotBlank()) scope.launch { c.tasks.createAndSchedule(name.ifBlank { prompt.take(10) }, "", prompt, label, minutes, false) }
    }
}
