package com.qigai.neon.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qigai.neon.BuildConfig
import com.qigai.neon.MainViewModel
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.chat.SheetKind
import com.qigai.neon.ui.components.Capsule
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.components.rememberDriveAuthorizer
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: MainViewModel, openSheet: (SheetKind?) -> Unit, onBack: () -> Unit) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val authorizeDrive = rememberDriveAuthorizer(vm)
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = c.inkPrimary) }
            Text("设置", style = NeonType.headline())
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("模型与密钥") {
                Text("Key 存于 Android Keystore 加密存储，只在发请求时注入请求头，不进入提示词、日志或 Drive。", style = NeonType.caption())
                Spacer(Modifier.height(6.dp))
                ProviderId.entries.forEach { p ->
                    val has = vm.keyName(p) in keys
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (has) c.neonLime else c.borderHairline)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.label, style = NeonType.label())
                            Text(if (has) "已配置" else "未配置", style = NeonType.caption())
                        }
                        val kind = if (p == ProviderId.DEEPSEEK) SheetKind.CONNECT_DEEPSEEK else SheetKind.CONNECT_GO
                        if (has) {
                            Capsule("更换", outlined = true) { openSheet(kind) }
                            Spacer(Modifier.width(6.dp))
                            Capsule("移除", outlined = true) { vm.removeKey(p) }
                        } else Capsule("去配置", filled = true) { openSheet(kind) }
                    }
                }
            }
            Section("接口地址") {
                BaseField("DeepSeek Base URL", s.deepseekBase, SettingsRepository.DEFAULT_DEEPSEEK_BASE) { vm.setBase(SettingsRepository.DEEPSEEK_BASE, it) }
                Spacer(Modifier.height(10.dp))
                BaseField("OpenCode Go Base URL", s.goBase, SettingsRepository.DEFAULT_GO_BASE) { vm.setBase(SettingsRepository.GO_BASE, it) }
            }
            Section("对话") {
                ToggleRow("默认开启深度思考", "新会话的深度思考开关初始状态", s.deepThinkDefault) { vm.setDeepThinkDefault(it) }
            }
            Section("外观") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色", ThemeMode.SYSTEM to "跟随系统").forEach { (m, label) ->
                        Capsule(label, filled = s.theme == m, outlined = s.theme != m) { vm.setTheme(m) }
                    }
                }
            }
            Section("Google Drive") {
                if (s.driveConnected) {
                    Text("已授权 · ${s.driveEmail ?: ""}", style = NeonType.label())
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("自动同步对话", "每轮回复结束后写入 Neon/对话/", s.driveAutoSync) { vm.setDriveAutoSync(it) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Capsule("立即同步", outlined = true) { vm.driveSyncNow() }
                        Capsule("断开", outlined = true) { vm.driveDisconnect() }
                        Capsule("在 Google 账号中撤销 ↗", outlined = true) { CustomTabs.open(ctx, "https://myaccount.google.com/connections") }
                    }
                } else {
                    Text("未授权。只申请 drive.file 权限。", style = NeonType.secondary())
                    s.driveError?.let { Text(it, style = NeonType.caption().copy(color = c.danger)) }
                    Spacer(Modifier.height(8.dp))
                    Capsule("去授权", filled = true) { authorizeDrive() }
                }
            }
            Section("关于") {
                Text("Neon ${BuildConfig.VERSION_NAME}", style = NeonType.label())
                Text("build ${BuildConfig.VERSION_CODE}", style = NeonType.tnum(NeonType.caption()))
                Text("对话保存在本机 Room 数据库；未配置的能力只显示空态，不显示示例数据。", style = NeonType.caption())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = NeonType.caption(), modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        NeonCard { content() }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().clip(NeonShapes.item).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NeonType.label())
            Text(sub, style = NeonType.caption())
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.inkPrimary, checkedThumbColor = c.onInk,
                uncheckedTrackColor = c.bgSubtle, uncheckedThumbColor = c.inkSecondary, uncheckedBorderColor = c.borderHairline,
            ),
        )
    }
}

@Composable
private fun BaseField(label: String, value: String, default: String, onSave: (String) -> Unit) {
    val c = Neon.colors
    var t by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = t, onValueChange = { t = it }, singleLine = true, label = { Text(label) },
        textStyle = NeonType.code(), shape = NeonShapes.item, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.inkPrimary, unfocusedBorderColor = c.borderHairline, cursorColor = c.inkPrimary),
    )
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (t.trimEnd('/') != value) Capsule("保存", filled = true) { onSave(t) }
        if (value != default) Capsule("恢复默认", outlined = true) { onSave(""); t = default }
    }
}
