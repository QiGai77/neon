package com.qigai.neon.ui.chat

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
fun ChatScreen(vm: MainViewModel, openSheet: (SheetKind?) -> Unit, onOpenSettings: () -> Unit) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val authorizeDrive = rememberDriveAuthorizer(vm)

    val messages by vm.messages.collectAsState()
    val conv by vm.currentConversation.collectAsState()
    val agent by vm.agentState.collectAsState()
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val models by vm.models.collectAsState()
    val deepThink by vm.deepThink.collectAsState()
    val voice by vm.voice.state.collectAsState()
    val currentId by vm.currentId.collectAsState()

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
        "${provider.label} · $name"
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = if (c.isDark) Color.Black.copy(alpha = 0.62f) else Color(0xFF101113).copy(alpha = 0.26f),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = c.bgDrawer,
                drawerShape = RectangleShape,
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
            deepThink = deepThink, onDeepThink = { vm.setDeepThink(it) },
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
            onPlus = { vm.toast("附件上传将在后续版本提供") },
            onSend = {
                if (provider == null) openSheet(SheetKind.CONNECT_DEEPSEEK)
                else if (vm.send(input)) { input = ""; palette = false }
            },
            onStop = { vm.stop() },
        )
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
    deepThink: Boolean,
    onDeepThink: (Boolean) -> Unit,
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
    hands: Boolean? = null,
    onHands: (Boolean) -> Unit = {},
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().imePadding()) {
        ChatTopBar(title, modelLabel, onMenu, onNewChat, onModel)

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, messages.lastOrNull()?.blocks?.length) {
            if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1, Int.MAX_VALUE / 2)
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
                    itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                        if (m.role == "user") UserBubble(m)
                        else AssistantMessage(m, isLast = i == messages.lastIndex, busy = busy, toolResults = results, onRegenerate = onRegenerate)
                    }
                }
            }
            // content slides under the bar softly instead of being cut
            Box(
                Modifier.fillMaxWidth().height(18.dp)
                    .background(Brush.verticalGradient(listOf(c.bgCanvas, c.bgCanvas.copy(alpha = 0f))))
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = voice.recording || voice.transcribing,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                ListeningOrb(voice)
            }
        }

        Composer(
            text = input, onText = onInput, busy = busy,
            deepThink = deepThink, onDeepThink = onDeepThink,
            paletteOpen = paletteOpen, onPalette = onPalette,
            voice = voice,
            onMic = onMic, onVoiceCancel = onVoiceCancel, onVoiceConfirm = onVoiceConfirm,
            onPlus = onPlus, onSend = onSend, onStop = onStop,
            skills = skills, hands = hands, onHands = onHands,
            onPickSkill = { onInput("/${it.trigger} "); onPalette(false) },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun ChatTopBar(title: String?, modelLabel: String?, onMenu: () -> Unit, onNewChat: () -> Unit, onModel: () -> Unit) {
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Lucide.Text, "菜单", onClick = onMenu)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BlobLogo(22.dp)
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
        IconBtn(Lucide.SquarePen, "新对话", onClick = onNewChat)
    }
}

@Composable
private fun ListeningOrb(v: VoiceState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse = v.levels.takeLast(4).average().toFloat()
        BlobLogo(60.dp, Modifier.padding(4.dp).size((60 + pulse * 10).dp))
        Spacer(Modifier.height(8.dp))
        Text(
            if (v.transcribing) "正在转写…" else "正在听… " + v.engine.ifBlank { "普通话" },
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
            Text("连接一个模型开始对话", style = NeonType.headline().copy(fontFamily = Neon.fonts.regular), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Key 只存在本机 Keystore，不进对话、日志或 Drive", style = NeonType.caption().copy(color = c.inkTertiary), textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            NeonCard(padding = PaddingValues(0.dp)) {
                ConnectRow(Lucide.KeyRound, "DeepSeek", "官方接口 · 深度思考 · 流式", "连接", onDeepSeek)
                Box(Modifier.padding(start = 64.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
                ConnectRow(Lucide.FileJson, "OpenCode Go", "粘贴 Key 或导入 auth.json", "导入", onGo)
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
