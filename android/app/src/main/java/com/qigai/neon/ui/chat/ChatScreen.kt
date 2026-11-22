package com.qigai.neon.ui.chat

import com.qigai.neon.ui.theme.neonBackdrop
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qigai.neon.MainViewModel
import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.MessageEntity
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.components.BlobLogo
import com.qigai.neon.ui.components.ChipStyle
import com.qigai.neon.ui.components.IconBtn
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.NeonChip
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.components.rememberDriveAuthorizer
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.sider.SiderDrawer
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.voice.VoiceState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(vm: MainViewModel, openSheet: (SheetKind?) -> Unit, onOpenSettings: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val authorizeDrive = rememberDriveAuthorizer(vm)
    val hideBar = com.qigai.neon.ui.home.LocalHideBottomBar.current
    LaunchedEffect(drawer.targetValue) { hideBar.value = drawer.targetValue == DrawerValue.Open }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { hideBar.value = false } }

    val messages by vm.messages.collectAsState()
    val handsBlocked by vm.c.tools.handsBlocked.collectAsState()
    var groupSheet by remember { mutableStateOf(false) }
    val conv by vm.currentConversation.collectAsState()
    val agent by vm.agentState.collectAsState()
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val models by vm.models.collectAsState()
    val effort by vm.effort.collectAsState()
    val pending by vm.pending.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.attach(uris) }
    val voice by vm.voice.state.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val skillDocs by vm.c.skills.skills.collectAsState()

    var input by rememberSaveable { mutableStateOf("") }
    var palette by remember { mutableStateOf(false) }
    val busy = agent.busy && (agent.conversation == currentId)
    val provider = vm.activeProviderId(s, keys)

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.voice.start { t -> input = if (input.isBlank()) t else "$input $t" }
        else vm.toast("需要麦克风权限才能语音输入")
    }
    LaunchedEffect(voice.error) { voice.error?.let { vm.toast(it); vm.voice.clearError() } }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(palette && !drawer.isOpen) { palette = false }

    val modelLabel = if (provider == null) null else {
        val id = vm.modelFor(provider, s)
        val name = models.lists[provider]?.firstOrNull { it.id == id }?.name ?: id
        name
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = if (c.isDark) Color.Black.copy(alpha = 0.62f) else Color(0xFF101113).copy(alpha = 0.26f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 30.dp, bottomEnd = 30.dp),
                modifier = Modifier.width(318.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                SiderDrawer(
                    vm,
                    onOpenSettings = { scope.launch { drawer.close() }; onOpenSettings() },
                    onConnectGo = { openSheet(SheetKind.CONNECT_GO) },
                    onAuthorizeDrive = authorizeDrive,
                    onOpenConversation = { vm.open(it); scope.launch { drawer.close() } },
                    onNewChat = { vm.newChat(); scope.launch { drawer.close() } },
                    onNavigate = { r -> scope.launch { drawer.close() }; onNavigate(r) },
                )
            }
        },
    ) {
        ChatContent(
            title = conv?.title,
            modelLabel = modelLabel,
            messages = messages,
            busy = busy,
            input = input, onInput = { input = it; if (it.startsWith("/")) palette = true },
            paletteOpen = palette, onPalette = { palette = it },
            effort = effort, onEffort = { vm.setEffort(it) },
            voice = voice,
            onMenu = { scope.launch { drawer.open() } },
            onNewChat = { vm.newChat(); input = "" },
            onModel = { openSheet(SheetKind.MODEL_PICKER) },
            onConnectDeepSeek = { openSheet(SheetKind.CONNECT_DEEPSEEK) },
            onConnectGo = { openSheet(SheetKind.CONNECT_GO) },
            onRegenerate = { vm.regenerate() },
            onMic = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    vm.voice.start { t -> input = if (input.isBlank()) t else "$input $t" }
                } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onVoiceCancel = { vm.voice.cancel() },
            onVoiceConfirm = { vm.voice.confirm() },
            onPlus = { picker.launch(arrayOf("*/*")) },
            onSend = {
                if (provider == null) openSheet(SheetKind.CONNECT_DEEPSEEK)
                else if (vm.send(input)) { input = ""; palette = false }
            },
            onStop = { vm.stop() },
            skills = skillDocs.map { d -> PaletteSkill(d.name, d.command.removePrefix("/"), com.qigai.neon.ui.icons.Lucide.Sparkles) },
            opsCollapsed = s.opsCollapsed,
            bubble = s.chatStyle == com.qigai.neon.data.ChatStyle.BUBBLE,
            attachments = pending, onRemoveAttachment = { vm.removeAttachment(it) },
            onNewGroup = { groupSheet = true },
            notice = {
        if (handsBlocked) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .background(com.qigai.neon.ui.theme.Glass.tokens.tile).padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Neon 想替你点手机", style = NeonType.item())
                    Text("打开无障碍里的 Neon 就能操作，开好回来再说一遍", style = NeonType.caption())
                }
                com.qigai.neon.ui.components.PillButton("去开启", height = 34.dp) {
                    vm.c.tools.handsBlocked.value = false
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                com.qigai.neon.ui.home.GlassCircle(com.qigai.neon.ui.icons.Lucide.X, "关闭", size = 34.dp) { vm.c.tools.handsBlocked.value = false }
            }
        }
            },
        )
        if (groupSheet) GroupChatSheet(onDismiss = { groupSheet = false }) { members ->
            groupSheet = false; vm.newGroup(members); input = ""
        }
    }
}

/** Stateless chat surface (S01a / S01b). Everything it shows comes in as parameters. */
@Composable
fun ChatContent(
    title: String?,
    modelLabel: String?,
    messages: List<MessageEntity>,
    busy: Boolean,
    input: String,
    onInput: (String) -> Unit,
    paletteOpen: Boolean,
    onPalette: (Boolean) -> Unit,
    effort: String,
    onEffort: (String) -> Unit,
    voice: VoiceState,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onModel: () -> Unit,
    onConnectDeepSeek: () -> Unit,
    onConnectGo: () -> Unit,
    onRegenerate: () -> Unit,
    onMic: () -> Unit,
    onVoiceCancel: () -> Unit,
    onVoiceConfirm: () -> Unit,
    onPlus: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    skills: List<PaletteSkill> = emptyList(),
    opsCollapsed: Boolean = false,
    bubble: Boolean = false,
    attachments: List<PendingAttachment> = emptyList(),
    onRemoveAttachment: (PendingAttachment) -> Unit = {},
    initialThinkOpen: Boolean = false,
    notice: @Composable () -> Unit = {},
    onNewGroup: () -> Unit = {},
) {
    val c = Neon.colors
    val turns = remember(messages) { opTurns(messages) }
    val openTurns = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    LaunchedEffect(opsCollapsed) { openTurns.clear() }
    val shown = remember(messages, turns, opsCollapsed, openTurns.toList()) {
        messages.filterIndexed { i, m ->
            if (m.role == "user") return@filterIndexed !turns.toolResultOnly.contains(m.id)
            if (!opsCollapsed) return@filterIndexed true
            val t = turns.byMessage[m.id] ?: return@filterIndexed true
            t.head == m.id || t.head in openTurns || turns.hasText.contains(m.id) || m.status == "error" || i == messages.lastIndex ||
                m.blocks.contains("\"present_file\"")
        }
    }
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().imePadding()) {
        ChatTopBar(title, modelLabel, onMenu, onNewChat, onModel, thinking = busy, onNewGroup = onNewGroup)

        val listState = rememberLazyListState()
        LaunchedEffect(shown.size, messages.lastOrNull()?.blocks?.length) {
            if (shown.isNotEmpty()) listState.scrollToItem(shown.size - 1, Int.MAX_VALUE / 2)
        }
        val results = remember(messages) {
            messages.asSequence().filter { it.role == "user" }
                .flatMap { BlocksJson.decode(it.blocks).asSequence() }
                .filterIsInstance<ContentBlock.ToolResult>()
                .associateBy { it.toolUseId }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                ChatEmpty(configured = modelLabel != null, onDeepSeek = onConnectDeepSeek, onGo = onConnectGo, onSuggest = onInput)
            } else {
                LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    itemsIndexed(shown, key = { _, m -> m.id }) { i, m ->
                        if (m.role == "user") UserBubble(m)
                        else {
                            val t = turns.byMessage[m.id]
                            val folded = opsCollapsed && t != null && t.head !in openTurns
                            AssistantMessage(
                                m, isLast = m.id == messages.lastOrNull()?.id, busy = busy, toolResults = results, onRegenerate = onRegenerate,
                                showOps = !folded,
                                ops = if (opsCollapsed && t != null && t.head == m.id && (t.steps > 0 || t.thought))
                                    OpsFold(t.steps, live = busy && t.last == messages.lastOrNull()?.id, open = !folded, thinkMs = t.thinkMs, activity = t.activity) else null,
                                onOps = { t?.head?.let { h -> if (h in openTurns) openTurns.remove(h) else openTurns.add(h) } },
                                bubble = bubble,
                            )
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = voice.recording || voice.transcribing,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                ListeningOrb(voice)
            }
        }

        notice()
        Composer(
            text = input, onText = onInput, busy = busy,
            effort = effort, onEffort = onEffort,
            paletteOpen = paletteOpen, onPalette = onPalette,
            voice = voice,
            onMic = onMic, onVoiceCancel = onVoiceCancel, onVoiceConfirm = onVoiceConfirm,
            onPlus = onPlus, onSend = onSend, onStop = onStop,
            skills = skills, attachments = attachments, onRemoveAttachment = onRemoveAttachment, initialThinkOpen = initialThinkOpen,
            onPickSkill = { onInput("/${it.trigger} "); onPalette(false) },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp + com.qigai.neon.ui.home.LocalBottomBarInset.current),
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ChatTopBar(
    title: String?, modelLabel: String?, onMenu: () -> Unit, onNewChat: () -> Unit, onModel: () -> Unit,
    thinking: Boolean = false,
    onNewGroup: () -> Unit = {},
) {
    val c = Neon.colors
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Lucide.Text, "菜单", onClick = onMenu)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.qigai.neon.ui.components.NeonOrb(24.dp, lively = thinking)
                Spacer(Modifier.width(8.dp))
                Text(title ?: "Neon", style = NeonType.title(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.padding(top = 1.dp).clip(NeonShapes.capsule).clickable(onClick = onModel).padding(horizontal = 8.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(if (modelLabel != null) c.live else c.inkTertiary, 6.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    modelLabel ?: "未连接模型",
                    style = NeonType.caption().copy(fontSize = NeonType.chip().fontSize * 0.96f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(3.dp))
                Icon(Lucide.ChevronDown, "选择模型", Modifier.size(13.dp), tint = c.inkTertiary)
            }
        }
        Box {
            Box(
                Modifier.size(44.dp).clip(NeonShapes.capsule)
                    .combinedClickable(onClick = onNewChat, onLongClick = { menu = true }, onLongClickLabel = "选会话或群聊"),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.SquarePen, "新对话，长按选群聊", Modifier.size(22.dp), tint = c.inkPrimary) }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("新会话") }, onClick = { menu = false; onNewChat() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("新群聊") }, onClick = { menu = false; onNewGroup() })
            }
        }
    }
}

private val GROUP_PRESETS = listOf(
    com.qigai.neon.agent.AgentRuntime.GroupMember("产品", "关心用户要什么、值不值得做，先问为什么"),
    com.qigai.neon.agent.AgentRuntime.GroupMember("工程师", "关心怎么实现、成本和风险，给具体做法"),
    com.qigai.neon.agent.AgentRuntime.GroupMember("设计师", "关心体验和细节，给看得见的建议"),
    com.qigai.neon.agent.AgentRuntime.GroupMember("唱反调", "专门找漏洞和反例，说话直接"),
    com.qigai.neon.agent.AgentRuntime.GroupMember("省钱达人", "只关心怎么零成本或最便宜地做到"),
)

/** Pick two to four members for a 群聊; each answers in turn with its own persona. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GroupChatSheet(onDismiss: () -> Unit, onCreate: (List<com.qigai.neon.agent.AgentRuntime.GroupMember>) -> Unit) {
    val picked = remember { androidx.compose.runtime.mutableStateListOf(GROUP_PRESETS[0], GROUP_PRESETS[1], GROUP_PRESETS[3]) }
    val custom = remember { androidx.compose.runtime.mutableStateListOf<com.qigai.neon.agent.AgentRuntime.GroupMember>() }
    var name by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("新群聊", style = NeonType.headline())
            Spacer(Modifier.height(4.dp))
            Text("选两到四个成员，你说一句，他们按顺序各自回一轮", style = NeonType.caption())
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (GROUP_PRESETS + custom).forEach { m ->
                    val on = m in picked
                    com.qigai.neon.ui.home.GlassChip(m.name, on) {
                        if (on) picked.remove(m) else if (picked.size < 4) picked.add(m)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("自己加一个", style = NeonType.overline())
            Spacer(Modifier.height(6.dp))
            NeonField(name, { name = it }, "名字，比如 老板")
            Spacer(Modifier.height(8.dp))
            NeonField(persona, { persona = it }, "性格或立场，比如 只看结果")
            Spacer(Modifier.height(8.dp))
            com.qigai.neon.ui.components.PillButton("加入", kind = com.qigai.neon.ui.components.ButtonKind.Secondary, height = 36.dp, enabled = name.isNotBlank()) {
                val m = com.qigai.neon.agent.AgentRuntime.GroupMember(name.trim(), persona.trim())
                custom.add(m); if (picked.size < 4) picked.add(m); name = ""; persona = ""
            }
            Spacer(Modifier.height(16.dp))
            com.qigai.neon.ui.components.PillButton("开始群聊", modifier = Modifier.fillMaxWidth(), height = 46.dp, enabled = picked.size >= 2) { onCreate(picked.toList()) }
        }
    }
}

/** A user turn's tool work: the first assistant message carries the folded line. */
private data class OpTurn(
    val head: String, val last: String, val steps: Int, val thought: Boolean,
    val thinkMs: Long = 0, val activity: String = "正在思考",
)

/** What a tool call is doing, in plain words, for the one-line status. */
private fun activityOf(name: String, inputJson: String): String {
    val cmd = runCatching {
        (com.qigai.neon.provider.Http.json.parseToJsonElement(inputJson) as? kotlinx.serialization.json.JsonObject)
            ?.get("command")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
    }.getOrNull().orEmpty().trim()
    return when {
        name == "sandbox_exec" -> {
            val c = cmd.lowercase()
            when {
                Regex("""\b(apk add|pip3? install|uv (pip|add|sync)|npm (i|install)|pnpm (i|install|add)|cargo add)\b""").containsMatchIn(c) -> "正在安装依赖"
                Regex("""\bgit (clone|pull|fetch)\b""").containsMatchIn(c) -> "正在拉取代码"
                Regex("""(cat\s*>|tee |sed -i|>>|apply_patch|patch )""").containsMatchIn(c) -> "正在修改代码文件"
                Regex("""\b(make|cmake|gcc|cargo build|npm run build|pnpm build|gradle)\b""").containsMatchIn(c) -> "正在编译"
                Regex("""\b(pytest|npm test|pnpm test|cargo test)\b""").containsMatchIn(c) -> "正在跑测试"
                Regex("""^(cat|head|tail|less|rg|grep|ls|find|tree|wc)\b""").containsMatchIn(c) -> "正在查看文件"
                Regex("""\b(curl|wget)\b""").containsMatchIn(c) -> "正在下载"
                Regex("""\b(python3?|node|uv run)\b""").containsMatchIn(c) -> "正在运行脚本"
                else -> "正在运行命令"
            }
        }
        name.startsWith("gmail_send") -> "正在发邮件"
        name.startsWith("gmail") -> "正在查邮件"
        name.startsWith("calendar") -> "正在看日程"
        name.startsWith("drive") -> "正在处理 Drive 文件"
        name.startsWith("hands") -> "正在操作手机"
        name.startsWith("phone") -> "正在读手机信息"
        name.contains("search", true) -> "正在搜索"
        name.contains("fetch", true) || name.contains("browse", true) -> "正在读网页"
        else -> "正在调用工具"
    }
}

private class OpTurns(
    val byMessage: Map<String, OpTurn>,
    val hasText: Set<String>,
    val toolResultOnly: Set<String>,
)

private fun opTurns(messages: List<MessageEntity>): OpTurns {
    val by = mutableMapOf<String, OpTurn>()
    val text = mutableSetOf<String>()
    val results = mutableSetOf<String>()
    var group = mutableListOf<MessageEntity>()
    fun flush() {
        if (group.isEmpty()) return
        var steps = 0; var thought = false; var thinkMs = 0L
        var activity = "正在思考"
        group.forEach { m ->
            val blocks = BlocksJson.decode(m.blocks)
            steps += blocks.count { it is ContentBlock.ToolUse || it is ContentBlock.ServerToolUse }
            blocks.filterIsInstance<ContentBlock.Thinking>().forEach { thought = true; thinkMs += it.durationMs ?: 0 }
            if (blocks.any { it is ContentBlock.Text && it.text.isNotBlank() }) text += m.id
            activity = when (val b = blocks.lastOrNull()) {
                is ContentBlock.ToolUse -> activityOf(b.name, b.inputJson)
                is ContentBlock.ServerToolUse -> activityOf(b.name, b.inputJson)
                is ContentBlock.Text -> "正在整理回答"
                else -> "正在思考"
            }
        }
        val t = OpTurn(group.first().id, group.last().id, steps, thought, thinkMs, activity)
        group.forEach { by[it.id] = t }
        group = mutableListOf()
    }
    messages.forEach { m ->
        if (m.role == "user") {
            val blocks = BlocksJson.decode(m.blocks)
            if (blocks.isNotEmpty() && blocks.all { it is ContentBlock.ToolResult }) results += m.id else flush()
        } else group += m
    }
    flush()
    return OpTurns(by, text, results)
}

@Composable
private fun ListeningOrb(v: VoiceState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse = v.levels.takeLast(4).average().toFloat()
        BlobLogo(60.dp, Modifier.padding(4.dp).size((60 + pulse * 10).dp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (v.transcribing) "正在转写…" else "正在听",
            style = NeonType.label().copy(color = Neon.colors.inkSecondary),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatEmpty(configured: Boolean, onDeepSeek: () -> Unit, onGo: () -> Unit, onSuggest: (String) -> Unit) {
    val c = Neon.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlobLogo(64.dp)
        Spacer(Modifier.height(20.dp))
        if (configured) {
            Text("有什么可以帮你？", style = NeonType.headline().copy(fontFamily = Neon.fonts.regular, fontSize = NeonType.headline().fontSize * 1.1f))
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("把这段话改得更简洁", "解释一个概念给我听", "帮我列个周末计划").forEach { s ->
                    NeonChip(s, style = ChipStyle.Outline, height = 34.dp) { onSuggest(s) }
                }
            }
        } else {
            Text("连接模型", style = NeonType.headline().copy(fontFamily = Neon.fonts.regular), textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            NeonCard(padding = PaddingValues(0.dp)) {
                ConnectRow(Lucide.KeyRound, "DeepSeek", "官方 API", "连接", onDeepSeek)
                Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
                ConnectRow(Lucide.FileJson, "OpenCode Go", "订阅", "导入", onGo)
            }
        }
    }
}

@Composable
private fun ConnectRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquircle(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NeonType.item())
            Text(sub, style = NeonType.caption())
        }
        NeonChip(action, style = ChipStyle.Active, height = 32.dp, onClick = onClick)
    }
}

@Suppress("unused")
private val keepProviderImport = ProviderId.DEEPSEEK
