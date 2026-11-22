package com.qigai.neon.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.MainViewModel
import com.qigai.neon.QuotaState
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.ui.chat.SheetKind
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.NeonOrb
import com.qigai.neon.ui.components.NeonSwitch
import com.qigai.neon.ui.components.SectionLabel
import com.qigai.neon.ui.home.LocalBottomBarInset
import com.qigai.neon.ui.home.Ring
import com.qigai.neon.ui.home.Segmented
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.ThemeMode
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop
import kotlinx.coroutines.launch

data class MeData(
    val modelLabel: String? = null,
    val quota: com.qigai.neon.provider.GoUsage? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val opsCollapsed: Boolean = false,
    val deepThink: Boolean = false,
    val hands: Boolean = false,
    val version: String = "",
)

class MeActions(
    val onModel: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onOps: (Boolean) -> Unit = {},
    val onDeepThink: (Boolean) -> Unit = {},
    val onHands: (Boolean) -> Unit = {},
    val onNavigate: (String) -> Unit = {},
)

/** 我的: who Neon is, what it costs you, and how it behaves. */
@Composable
fun MeScreen(vm: MainViewModel, onNavigate: (String) -> Unit, openSheet: (SheetKind?) -> Unit) {
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val models by vm.models.collectAsState()
    val quota by vm.quota.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    val provider = vm.activeProviderId(s, keys)
    val modelLabel = provider?.let { p ->
        val id = vm.modelFor(p, s)
        models.lists[p]?.firstOrNull { it.id == id }?.name ?: id
    }
    MeContent(
        MeData(modelLabel, (quota as? QuotaState.Ok)?.usage, s.theme, s.opsCollapsed, s.deepThinkDefault, s.handsEnabled, version),
        MeActions(
            onModel = { openSheet(if (modelLabel != null) SheetKind.MODEL_PICKER else SheetKind.CONNECT_DEEPSEEK) },
            onTheme = { vm.setTheme(it) },
            onOps = { on -> scope.launch { vm.c.settings.put(SettingsRepository.OPS_COLLAPSED, if (on) "1" else "0") } },
            onDeepThink = { vm.setDeepThinkDefault(it) },
            onHands = { on -> scope.launch { vm.c.settings.put(SettingsRepository.HANDS, if (on) "1" else "0") } },
            onNavigate = onNavigate,
        ),
    )
}

@Composable
fun MeContent(d: MeData, a: MeActions) {
    val c = Neon.colors
    val bottom = LocalBottomBarInset.current
    Column(
        Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(bottom = bottom + 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonOrb(64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Neon", style = NeonType.brand().copy(fontSize = 26.sp, lineHeight = 32.sp))
                Text("替你跑腿，动手前先问你", style = NeonType.caption())
            }
        }
        Row(Modifier.padding(start = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.qigai.neon.ui.home.GlassChip(d.modelLabel ?: "连接一个模型", d.modelLabel != null, icon = Lucide.Cpu, onClick = a.onModel)
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            d.quota?.let { u ->
                NeonCard(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp, horizontal = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("5 小时" to u.rolling, "本周" to u.weekly, "本月" to u.monthly).forEach { (label, w) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Ring(((w?.percent ?: 0.0) / 100.0).toFloat(), 76.dp) {
                                    Text(w?.let { "${it.percent.toInt()}%" } ?: "--", style = NeonType.tnum(NeonType.bodyMedium()))
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(label, style = NeonType.caption())
                            }
                        }
                    }
                }
            }

            SectionLabel("外观")
            Segmented(
                listOf("跟随系统", "浅色", "深色"),
                when (d.theme) { ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2 },
                onSelect = { a.onTheme(listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)[it]) },
            )

            SectionLabel("对话")
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp))) {
                ToggleRow("思考和命令收成一行", d.opsCollapsed, a.onOps)
            }

            SectionLabel("操作手机")
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Hand, null, Modifier.size(18.dp), tint = c.inkPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(if (d.hands) "需要时 Neon 会替你点手机上的 App" else "Neon 不会碰手机上的 App", style = NeonType.label(), modifier = Modifier.weight(1f))
                }
                Segmented(listOf("智能调用", "常关"), if (d.hands) 0 else 1, onSelect = { a.onHands(it == 0) })
            }

            SectionLabel("更多")
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp))) {
                LinkRow(Lucide.KeyRound, "模型与密钥") { a.onNavigate("settings") }
                HorizontalDivider(color = c.borderCard.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                LinkRow(Lucide.CalendarClock, "记忆与定时任务") { a.onNavigate("memory") }
                HorizontalDivider(color = c.borderCard.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
                LinkRow(Lucide.Settings, "全部设置") { a.onNavigate("settings") }
            }
            Text("Neon ${d.version}", style = NeonType.overline(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ToggleRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!on) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = NeonType.item(), modifier = Modifier.weight(1f))
        NeonSwitch(on, onChange)
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val c = Neon.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.inkPrimary)
        Spacer(Modifier.width(12.dp))
        Text(title, style = NeonType.item(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkTertiary)
    }
}
