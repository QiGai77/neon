package com.qigai.neon.ui.chat

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qigai.neon.MainViewModel
import com.qigai.neon.ModelsState
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.provider.ModelInfo
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.components.ButtonKind
import com.qigai.neon.ui.components.ChipStyle
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.components.NeonChip
import com.qigai.neon.ui.components.PillButton
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonMotion
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.coroutines.launch

enum class SheetKind { CONNECT_DEEPSEEK, CONNECT_GO, MODEL_PICKER }

/**
 * Riff 09 "SuperBottomSheet Backdrop": the page behind scales 1 → 0.95 and rounds its corners while the sheet
 * rises on spring(0.85, 380). Drag the sheet down to dismiss.
 */
@Composable
fun NeonSheetHost(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    val c = Neon.colors
    val p = remember { Animatable(0f) }
    LaunchedEffect(open) { p.animateTo(if (open) 1f else 0f, NeonMotion.sheet()) }
    var sheetH by remember { mutableFloatStateOf(1f) }
    var drag by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    BackHandler(open) { onDismiss() }
    LaunchedEffect(open) { if (open) drag = 0f }

    Box(Modifier.fillMaxSize().background(if (c.isDark) Color.Black else Color(0xFF1A1B1E))) {
        val pv = p.value.coerceIn(0f, 1f)
        val follow = (1f - drag / sheetH).coerceIn(0f, 1f) * pv
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val s = 1f - 0.05f * follow
                scaleX = s; scaleY = s
                shape = androidx.compose.foundation.shape.RoundedCornerShape((28 * follow).dp)
                clip = follow > 0.001f
            }
        ) { content() }
        if (p.value > 0.001f) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * follow))
                    .clickable(remember { MutableInteractionSource() }, null) { onDismiss() }
            )
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().widthIn(max = 640.dp)
                    .onSizeChanged { sheetH = it.height.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer { translationY = (1f - p.value) * sheetH + drag }
                    .clip(NeonShapes.sheet).background(c.bgSurface)
                    .border(1.dp, c.borderCard, NeonShapes.sheet)
                    .draggable(
                        rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) },
                        Orientation.Vertical,
                        onDragStopped = { v ->
                            if (drag > sheetH * 0.25f || v > 1800f) onDismiss()
                            else scope.launch {
                                val a = Animatable(drag); a.animateTo(0f, NeonMotion.sheet()) { drag = value }
                            }
                        },
                    )
                    .navigationBarsPadding().imePadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 4.dp).clip(NeonShapes.capsule).background(c.borderHairline))
                }
                sheet()
            }
        }
    }
}

/** Remembers the last kind so the sheet can animate out with its content still in place. */
@Composable
fun NeonSheets(vm: MainViewModel, kind: SheetKind?, open: (SheetKind?) -> Unit, content: @Composable () -> Unit) {
    var last by remember { mutableStateOf(kind) }
    if (kind != null) last = kind
    NeonSheetHost(open = kind != null, onDismiss = { open(null) }, content = content) {
        when (last) {
            SheetKind.CONNECT_DEEPSEEK -> ConnectDeepSeek(vm) { open(null) }
            SheetKind.CONNECT_GO -> ConnectGo(vm) { open(null) }
            SheetKind.MODEL_PICKER -> {
                val s by vm.settings.collectAsState()
                val keys by vm.configured.collectAsState()
                val models by vm.models.collectAsState()
                ModelPickerContent(
                    active = vm.activeProviderId(s, keys),
                    selectedModel = { vm.modelFor(it, s) },
                    hasKey = { vm.keyName(it) in keys },
                    models = models,
                    onPick = { p, m -> vm.selectModel(p, m); open(null) },
                    onConnect = { p -> open(if (p == ProviderId.DEEPSEEK) SheetKind.CONNECT_DEEPSEEK else SheetKind.CONNECT_GO) },
                    onLoad = { vm.loadModels(it) },
                )
            }
            null -> Unit
        }
    }
}

@Composable
fun SheetHeader(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconSquircle(icon, size = 40.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = NeonType.headline())
            Text(sub, style = NeonType.caption())
        }
    }
}

@Composable
private fun NeonField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
    minLines: Int = 1,
) {
    val c = Neon.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(NeonShapes.item).background(c.bgTile)
            .border(1.dp, c.borderCard, NeonShapes.item).padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, style = NeonType.code().copy(color = c.inkTertiary))
        BasicTextField(
            value, onValue, singleLine = minLines == 1, minLines = minLines, maxLines = if (minLines == 1) 1 else 6,
            textStyle = NeonType.code(), cursorBrush = SolidColor(c.inkPrimary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Fineprint(text: String) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(Lucide.Lock, null, Modifier.padding(top = 1.dp).size(13.dp), tint = Neon.colors.inkTertiary)
        Spacer(Modifier.width(6.dp))
        Text(text, style = NeonType.caption().copy(color = Neon.colors.inkTertiary))
    }
}

@Composable
private fun ConnectDeepSeek(vm: MainViewModel, done: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    ConnectDeepSeekContent(
        key = key, onKey = { key = it; err = null }, busy = busy, error = err,
        onGetKey = { CustomTabs.open(ctx, "https://platform.deepseek.com/api_keys") },
        onSave = {
            busy = true
            scope.launch {
                err = vm.saveDeepSeekKey(key)
                busy = false
                if (err == null) { vm.toast("DeepSeek 已连接"); done() }
            }
        },
    )
}

@Composable
fun ConnectDeepSeekContent(key: String, onKey: (String) -> Unit, busy: Boolean, error: String?, onGetKey: () -> Unit, onSave: () -> Unit) {
    SheetHeader(Lucide.KeyRound, "连接 DeepSeek", "api.deepseek.com")
    Spacer(Modifier.height(20.dp))
    NeonField(key, onKey, "sk-…", secret = true)
    error?.let { Text(it, style = NeonType.caption().copy(color = Neon.colors.danger), modifier = Modifier.padding(top = 8.dp)) }
    Fineprint("Key 加密保存在本机")
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("获取 Key", kind = ButtonKind.Secondary, icon = Lucide.ArrowUpRight, height = 44.dp, modifier = Modifier.weight(1f), onClick = onGetKey)
        if (busy) Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Neon.colors.inkPrimary)
        } else PillButton("验证并保存", height = 44.dp, enabled = key.isNotBlank(), modifier = Modifier.weight(1f), onClick = onSave)
    }
}

@Composable
private fun ConnectGo(vm: MainViewModel, done: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            if (text.isBlank()) err = "读取文件失败"
        }
    }
    SheetHeader(Lucide.FileJson, "导入 OpenCode Go", "opencode.ai")
    Spacer(Modifier.height(20.dp))
    NeonField(text, { text = it; err = null }, "粘贴 Key 或 auth.json 内容", minLines = 3)
    err?.let { Text(it, style = NeonType.caption().copy(color = Neon.colors.danger), modifier = Modifier.padding(top = 8.dp)) }
    Fineprint("auth.json 在 ~/.local/share/opencode")
    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonChip("选择文件", style = ChipStyle.Outline, icon = Lucide.Folder, height = 40.dp) {
            picker.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        NeonChip("控制台", style = ChipStyle.Outline, icon = Lucide.ArrowUpRight, height = 40.dp) { CustomTabs.open(ctx, "https://opencode.ai/console") }
        Spacer(Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Neon.colors.inkPrimary)
        else PillButton("保存", height = 44.dp, enabled = text.isNotBlank()) {
            busy = true
            scope.launch {
                err = vm.saveGoKey(text)
                busy = false
                if (err == null) { vm.toast("OpenCode Go 已连接"); done() }
            }
        }
    }
}

@Composable
fun ModelPickerContent(
    active: ProviderId?,
    selectedModel: (ProviderId) -> String,
    hasKey: (ProviderId) -> Boolean,
    models: ModelsState,
    onPick: (ProviderId, String) -> Unit,
    onConnect: (ProviderId) -> Unit,
    onLoad: (ProviderId) -> Unit,
) {
    val c = Neon.colors
    Text("选择模型", style = NeonType.headline(), modifier = Modifier.padding(start = 2.dp))
    Spacer(Modifier.height(10.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        ProviderId.entries.forEach { p ->
            val has = hasKey(p)
            item(key = "h-${p.id}") {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (has) c.live else c.inkTertiary, 6.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(p.label, style = NeonType.caption(), modifier = Modifier.weight(1f))
                    if (!has) NeonChip(if (p == ProviderId.DEEPSEEK) "连接" else "导入", style = ChipStyle.Active, height = 28.dp) { onConnect(p) }
                    else if (p in models.loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = c.inkSecondary)
                }
            }
            val err = models.errors[p]
            val list = models.lists[p].orEmpty()
            if (err != null && list.isEmpty()) item(key = "e-${p.id}") {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("加载失败：$err", style = NeonType.caption().copy(color = c.danger), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    NeonChip("重试", style = ChipStyle.Outline, height = 28.dp) { onLoad(p) }
                }
            }
            if (has && list.isEmpty() && err == null && p !in models.loading) item(key = "l-${p.id}") {
                Row(Modifier.padding(start = 4.dp, bottom = 8.dp)) { NeonChip("加载模型列表", style = ChipStyle.Outline, height = 28.dp) { onLoad(p) } }
            }
            items(list, key = { "${p.id}/${it.id}" }) { m ->
                ModelRow(m, selected = active == p && selectedModel(p) == m.id, dim = !has) {
                    if (!has) onConnect(p) else onPick(p, m.id)
                }
            }
        }
    }
}

@Composable
private fun ModelRow(m: ModelInfo, selected: Boolean, dim: Boolean, onClick: () -> Unit) {
    val c = Neon.colors
    Row(
        Modifier.fillMaxWidth().clip(NeonShapes.tile)
            .background(if (selected) c.bgTile else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, style = NeonType.item().copy(color = if (dim) c.inkSecondary else c.inkPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (m.reasoning) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Lucide.Sparkles, "支持思考", Modifier.size(13.dp), tint = c.inkTertiary)
                }
            }
            Text(m.id, style = NeonType.mono().copy(color = c.inkTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Box(Modifier.size(22.dp).clip(NeonShapes.capsule).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(Lucide.Check, null, Modifier.size(13.dp), tint = c.onAccent)
        }
    }
}

@Suppress("unused")
private fun BoxScope.unused() = Unit
