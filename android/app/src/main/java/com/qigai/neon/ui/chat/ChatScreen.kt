package com.qigai.neon.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qigai.neon.MainViewModel
import com.qigai.neon.R
import com.qigai.neon.agent.AgentState
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.components.BlobLogo
import com.qigai.neon.ui.components.IconBtn
import com.qigai.neon.ui.components.PrimaryButton
import com.qigai.neon.ui.components.SecondaryButton
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.components.rememberDriveAuthorizer
import com.qigai.neon.ui.sider.SiderDrawer
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
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

    val drawerWidth = (LocalConfiguration.current.screenWidthDp * 0.86f).dp
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = c.bgCanvas,
                drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                modifier = Modifier.width(drawerWidth),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
        Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().imePadding()) {
            // ---- top bar ----
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(R.drawable.ic_menu, "菜单") { scope.launch { drawer.open() } }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BlobLogo(26.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(conv?.title ?: "Neon", style = NeonType.title().copy(fontSize = NeonType.headline().fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        Modifier.clip(NeonShapes.capsule).clickable { openSheet(SheetKind.MODEL_PICKER) }.padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(if (provider != null) c.neonLime else c.borderHairline, 7.dp)
                        Spacer(Modifier.width(6.dp))
                        val label = if (provider == null) "未配置模型" else {
                            val id = vm.modelFor(provider, s)
                            val name = models.lists[provider]?.firstOrNull { it.id == id }?.name ?: id
                            "${provider.label} · $name"
                        }
                        Text(label, style = NeonType.label().copy(color = c.inkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.KeyboardArrowDown, "选择模型", Modifier.size(18.dp), tint = c.inkSecondary)
                    }
                }
                IconBtn(R.drawable.ic_compose, "新对话") { vm.newChat(); input = "" }
            }

            // ---- messages ----
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size, messages.lastOrNull()?.blocks?.length) {
                if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1, Int.MAX_VALUE / 2)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    ChatEmpty(
                        configured = provider != null,
                        onDeepSeek = { openSheet(SheetKind.CONNECT_DEEPSEEK) },
                        onGo = { openSheet(SheetKind.CONNECT_GO) },
                    )
                } else {
                    LazyColumn(
                        state = listState, modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                            if (m.role == "user") UserBubble(m)
                            else AssistantMessage(m, isLast = i == messages.lastIndex, busy = busy, onRegenerate = { vm.regenerate() })
                        }
                    }
                }
            }

            // ---- composer ----
            Composer(
                text = input, onText = { input = it }, busy = busy,
                deepThink = deepThink, onDeepThink = { vm.setDeepThink(it) },
                paletteOpen = palette, onPalette = { palette = it },
                voice = voice,
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
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChatEmpty(configured: Boolean, onDeepSeek: () -> Unit, onGo: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BlobLogo(72.dp)
        Spacer(Modifier.height(18.dp))
        if (configured) {
            Text("有什么可以帮你？", style = NeonType.headline())
        } else {
            Text("连接一个模型开始对话", style = NeonType.headline(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Neon 直连 DeepSeek 官方接口或你的 OpenCode Go 订阅，Key 只存在本机加密存储。", style = NeonType.secondary(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("连接 DeepSeek", onClick = onDeepSeek)
                SecondaryButton("导入 OpenCode Go", onClick = onGo)
            }
        }
    }
}

@Suppress("unused")
private val keepProviderImport = ProviderId.DEEPSEEK
