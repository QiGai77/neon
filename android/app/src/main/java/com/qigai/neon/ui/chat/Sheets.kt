package com.qigai.neon.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qigai.neon.MainViewModel
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.components.Capsule
import com.qigai.neon.ui.components.PrimaryButton
import com.qigai.neon.ui.components.SecondaryButton
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.coroutines.launch

enum class SheetKind { CONNECT_DEEPSEEK, CONNECT_GO, MODEL_PICKER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeonSheets(vm: MainViewModel, kind: SheetKind?, open: (SheetKind?) -> Unit) {
    if (kind == null) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { open(null) },
        sheetState = state,
        shape = NeonShapes.sheet,
        containerColor = Neon.colors.bgSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            when (kind) {
                SheetKind.CONNECT_DEEPSEEK -> ConnectDeepSeek(vm) { open(null) }
                SheetKind.CONNECT_GO -> ConnectGo(vm) { open(null) }
                SheetKind.MODEL_PICKER -> ModelPicker(vm, open)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Neon.colors.inkPrimary,
    unfocusedBorderColor = Neon.colors.borderHairline,
    cursorColor = Neon.colors.inkPrimary,
    focusedTextColor = Neon.colors.inkPrimary,
    unfocusedTextColor = Neon.colors.inkPrimary,
)

@Composable
private fun ConnectDeepSeek(vm: MainViewModel, done: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Text("连接 DeepSeek", style = NeonType.headline())
    Spacer(Modifier.size(6.dp))
    Text("走 DeepSeek 官方 Anthropic 兼容接口（api.deepseek.com/anthropic），支持深度思考与流式输出。Key 只保存在本机加密存储（Android Keystore），不会进入对话、日志或 Drive。", style = NeonType.secondary())
    Spacer(Modifier.size(16.dp))
    OutlinedTextField(
        value = key, onValueChange = { key = it; err = null }, singleLine = true,
        label = { Text("API Key") }, placeholder = { Text("sk-…") },
        visualTransformation = PasswordVisualTransformation(),
        shape = NeonShapes.item, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
    )
    err?.let { Spacer(Modifier.size(8.dp)); Text(it, style = NeonType.label().copy(color = Neon.colors.danger)) }
    Spacer(Modifier.size(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("获取 API Key ↗") { CustomTabs.open(ctx, "https://platform.deepseek.com/api_keys") }
        Spacer(Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Neon.colors.inkPrimary)
        else PrimaryButton("验证并保存", enabled = key.isNotBlank()) {
            busy = true
            scope.launch {
                err = vm.saveDeepSeekKey(key)
                busy = false
                if (err == null) { vm.toast("DeepSeek 已连接"); done() }
            }
        }
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
    Text("导入 OpenCode Go", style = NeonType.headline())
    Spacer(Modifier.size(6.dp))
    Text("粘贴 Go 的 API Key，或导入 OpenCode 的 auth.json（~/.local/share/opencode/auth.json）。手机直连 opencode.ai，无需代理；保存前用额度接口校验（不产生费用）。", style = NeonType.secondary())
    Spacer(Modifier.size(16.dp))
    OutlinedTextField(
        value = text, onValueChange = { text = it; err = null },
        label = { Text("Key 或 auth.json") }, minLines = 2, maxLines = 5,
        shape = NeonShapes.item, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
    )
    err?.let { Spacer(Modifier.size(8.dp)); Text(it, style = NeonType.label().copy(color = Neon.colors.danger)) }
    Spacer(Modifier.size(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("选择 auth.json") { picker.launch(arrayOf("application/json", "text/plain", "*/*")) }
        SecondaryButton("控制台 ↗") { CustomTabs.open(ctx, "https://opencode.ai/console") }
        Spacer(Modifier.weight(1f))
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Neon.colors.inkPrimary)
        else PrimaryButton("保存", enabled = text.isNotBlank()) {
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
private fun ModelPicker(vm: MainViewModel, open: (SheetKind?) -> Unit) {
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val models by vm.models.collectAsState()
    val active = vm.activeProviderId(s, keys)
    Text("选择模型", style = NeonType.headline())
    Spacer(Modifier.size(12.dp))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
        ProviderId.entries.forEach { p ->
            val hasKey = vm.keyName(p) in keys
            item(key = "h-${p.id}") {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (hasKey) Neon.colors.neonLime else Neon.colors.borderHairline)
                    Spacer(Modifier.width(8.dp))
                    Text(p.label, style = NeonType.title(), modifier = Modifier.weight(1f))
                    if (!hasKey) Capsule(if (p == ProviderId.DEEPSEEK) "去配置" else "去导入", filled = true) {
                        open(if (p == ProviderId.DEEPSEEK) SheetKind.CONNECT_DEEPSEEK else SheetKind.CONNECT_GO)
                    } else if (p in models.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.colors.inkSecondary)
                }
            }
            val err = models.errors[p]
            val list = models.lists[p].orEmpty()
            if (err != null && list.isEmpty()) item(key = "e-${p.id}") {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("模型列表加载失败：$err", style = NeonType.caption().copy(color = Neon.colors.danger), modifier = Modifier.weight(1f))
                    Capsule("重试", outlined = true) { vm.loadModels(p) }
                }
            }
            if (hasKey && list.isEmpty() && err == null && p !in models.loading) item(key = "l-${p.id}") {
                Row(Modifier.padding(bottom = 8.dp)) { Capsule("加载模型列表", outlined = true) { vm.loadModels(p) } }
            }
            items(list, key = { "${p.id}/${it.id}" }) { m ->
                val selected = active == p && vm.modelFor(p, s) == m.id
                Row(
                    Modifier.fillMaxWidth().clip(NeonShapes.item)
                        .background(if (selected) Neon.colors.bgSubtle else Neon.colors.bgSurface)
                        .clickable {
                            if (!hasKey) open(if (p == ProviderId.DEEPSEEK) SheetKind.CONNECT_DEEPSEEK else SheetKind.CONNECT_GO)
                            else { vm.selectModel(p, m.id); open(null) }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = NeonType.label())
                        Text(m.id + if (m.reasoning) " · 支持思考" else "", style = NeonType.caption().copy(fontFamily = Neon.fonts.code))
                    }
                    if (selected) Icon(Icons.Default.Check, null, tint = Neon.colors.inkPrimary)
                }
            }
        }
    }
}
