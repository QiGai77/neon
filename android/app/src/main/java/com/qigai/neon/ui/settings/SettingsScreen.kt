package com.qigai.neon.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qigai.neon.BuildConfig
import com.qigai.neon.MainViewModel
import com.qigai.neon.data.AppSettings
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.chat.SheetKind
import com.qigai.neon.ui.components.BlobLogo
import com.qigai.neon.ui.components.ChipStyle
import com.qigai.neon.ui.components.IconBtn
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.NeonChip
import com.qigai.neon.ui.components.NeonSwitch
import com.qigai.neon.ui.components.SectionLabel
import com.qigai.neon.ui.components.rememberDriveAuthorizer
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonMotion
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.ThemeMode

@Composable
fun SettingsScreen(vm: MainViewModel, openSheet: (SheetKind?) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val authorizeDrive = rememberDriveAuthorizer(vm)
    SettingsContent(
        s = s,
        hasKey = { p -> vm.keyName(p) in keys },
        onBack = onBack,
        onConnect = { p -> openSheet(if (p == ProviderId.DEEPSEEK) SheetKind.CONNECT_DEEPSEEK else SheetKind.CONNECT_GO) },
        onRemoveKey = { vm.removeKey(it) },
        onBase = { k, v -> vm.setBase(k, v) },
        onDeepThinkDefault = { vm.setDeepThinkDefault(it) },
        onTheme = { vm.setTheme(it) },
        onDriveAutoSync = { vm.setDriveAutoSync(it) },
        onDriveSync = { vm.driveSyncNow() },
        onDriveDisconnect = { vm.driveDisconnect() },
        onDriveRevoke = { CustomTabs.open(ctx, "https://myaccount.google.com/connections") },
        onDriveAuthorize = authorizeDrive,
    )
}

@Composable
fun SettingsContent(
    s: AppSettings,
    hasKey: (ProviderId) -> Boolean,
    onBack: () -> Unit,
    onConnect: (ProviderId) -> Unit,
    onRemoveKey: (ProviderId) -> Unit,
    onBase: (String, String) -> Unit,
    onDeepThinkDefault: (Boolean) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDriveAutoSync: (Boolean) -> Unit,
    onDriveSync: () -> Unit,
    onDriveDisconnect: () -> Unit,
    onDriveRevoke: () -> Unit,
    onDriveAuthorize: () -> Unit,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        PageTopBar("设置", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("模型")
            NeonCard(padding = PaddingValues(vertical = 4.dp)) {
                ProviderId.entries.forEachIndexed { i, p ->
                    val has = hasKey(p)
                    SettingRow(
                        icon = if (p == ProviderId.DEEPSEEK) Lucide.KeyRound else Lucide.FileJson,
                        title = p.label,
                        sub = if (p == ProviderId.DEEPSEEK) s.deepseekModel else s.goModel,
                        status = if (has) "已连接" else null,
                    ) {
                        if (has) {
                            NeonChip("更换", style = ChipStyle.Outline, height = 30.dp) { onConnect(p) }
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(30.dp).clip(NeonShapes.capsule).clickable { onRemoveKey(p) }, contentAlignment = Alignment.Center) {
                                Icon(Lucide.Trash2, "移除", Modifier.size(16.dp), tint = c.inkTertiary)
                            }
                        } else NeonChip("连接", style = ChipStyle.Active, height = 30.dp) { onConnect(p) }
                    }
                    if (i < ProviderId.entries.lastIndex) RowDivider()
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("对话")
            NeonCard(padding = PaddingValues(vertical = 4.dp)) {
                SettingRow(Lucide.Sparkles, "默认开启深度思考", "") {
                    NeonSwitch(s.deepThinkDefault, onDeepThinkDefault)
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("外观")
            NeonCard(padding = PaddingValues(6.dp)) {
                Segmented(
                    options = listOf(ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色", ThemeMode.SYSTEM to "跟随系统"),
                    icons = listOf(Lucide.Sun, Lucide.Moon, Lucide.Monitor),
                    selected = s.theme, onSelect = onTheme,
                )
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("Google Drive")
            NeonCard(padding = PaddingValues(vertical = 4.dp)) {
                if (s.driveConnected) {
                    SettingRow(Lucide.HardDrive, s.driveEmail ?: "已授权", "Neon 文件夹", status = "已连接") {
                        NeonChip("同步", style = ChipStyle.Outline, icon = Lucide.RefreshCw, height = 30.dp, onClick = onDriveSync)
                    }
                    RowDivider()
                    SettingRow(Lucide.Cloud, "自动同步对话", "") {
                        NeonSwitch(s.driveAutoSync, onDriveAutoSync)
                    }
                    RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonChip("断开", style = ChipStyle.Outline, height = 30.dp, onClick = onDriveDisconnect)
                        NeonChip("在 Google 账号中撤销", style = ChipStyle.Outline, icon = Lucide.ArrowUpRight, height = 30.dp, onClick = onDriveRevoke)
                    }
                } else {
                    SettingRow(Lucide.HardDrive, "未授权", s.driveError ?: "") {
                        NeonChip("授权", style = ChipStyle.Active, height = 30.dp, onClick = onDriveAuthorize)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionLabel("接口地址")
            NeonCard(padding = PaddingValues(16.dp)) {
                BaseField("DeepSeek", s.deepseekBase, SettingsRepository.DEFAULT_DEEPSEEK_BASE) { onBase(SettingsRepository.DEEPSEEK_BASE, it) }
                Spacer(Modifier.height(12.dp))
                BaseField("OpenCode Go", s.goBase, SettingsRepository.DEFAULT_GO_BASE) { onBase(SettingsRepository.GO_BASE, it) }
            }

            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BlobLogo(28.dp)
                Spacer(Modifier.height(8.dp))
                Text("Neon ${BuildConfig.VERSION_NAME}", style = NeonType.label())
                Text(
                    "build ${BuildConfig.VERSION_CODE}",
                    style = NeonType.tnum(NeonType.caption().copy(color = c.inkTertiary)), textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PageTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).height(44.dp)) {
        IconBtn(Lucide.ArrowLeft, "返回", Modifier.align(Alignment.CenterStart), onClick = onBack)
        Text(title, style = NeonType.title(), modifier = Modifier.align(Alignment.Center))
        if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action() }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 66.dp).fillMaxWidth().height(1.dp).background(Neon.colors.borderCard))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String,
    status: String? = null,
    trailing: @Composable () -> Unit,
) {
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconSquircle(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = NeonType.item(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (status != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Lucide.Check, null, Modifier.size(13.dp), tint = if (c.isDark) c.live else c.inkSecondary)
                    Spacer(Modifier.width(2.dp))
                    Text(status, style = NeonType.caption().copy(color = if (c.isDark) c.live else c.inkSecondary))
                }
            }
            if (sub.isNotBlank()) Text(sub, style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

@Composable
private fun <T> Segmented(options: List<Pair<T, String>>, icons: List<ImageVector>, selected: T, onSelect: (T) -> Unit) {
    val c = Neon.colors
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp).clip(NeonShapes.item).background(c.bgTile)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * idx, NeonMotion.receipt(), label = "seg")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().padding(3.dp).clip(NeonShapes.small)
                .background(if (c.isDark) c.chipActive else c.bgSurface)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, (v, label) ->
                val on = i == idx
                val fg = if (on) (if (c.isDark) c.onChipActive else c.inkPrimary) else c.inkSecondary
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(NeonShapes.small).clickable { onSelect(v) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(icons[i], null, Modifier.size(15.dp), tint = fg)
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = NeonType.chip().copy(color = fg))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.BaseField(label: String, value: String, default: String, onSave: (String) -> Unit) {
    val c = Neon.colors
    var t by remember(value) { mutableStateOf(value) }
    Text(label, style = NeonType.caption(), modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
    OutlinedTextField(
        value = t, onValueChange = { t = it }, singleLine = true,
        textStyle = NeonType.code(), shape = NeonShapes.item, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.inkPrimary, unfocusedBorderColor = c.borderHairline, cursorColor = c.inkPrimary,
            focusedContainerColor = c.bgTile, unfocusedContainerColor = c.bgTile,
        ),
    )
    if (t.trimEnd('/') != value || value != default) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.trimEnd('/') != value) NeonChip("保存", style = ChipStyle.Active, height = 28.dp) { onSave(t) }
            if (value != default) NeonChip("恢复默认", style = ChipStyle.Outline, height = 28.dp) { onSave(""); t = default }
        }
    }
}
