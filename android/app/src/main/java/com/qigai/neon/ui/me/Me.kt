package com.qigai.neon.ui.me

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.MainViewModel
import com.qigai.neon.QuotaState
import com.qigai.neon.data.ChatStyle
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.data.Tone
import com.qigai.neon.ui.chat.SheetKind
import com.qigai.neon.ui.components.NeonOrb
import com.qigai.neon.ui.home.GlassCircle
import com.qigai.neon.ui.home.LocalBottomBarInset
import com.qigai.neon.ui.home.Ring
import com.qigai.neon.ui.home.Segmented
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.ThemeMode
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop

data class MeData(
    val modelLabel: String? = null,
    val quota: com.qigai.neon.provider.GoUsage? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val opsCollapsed: Boolean = false,
    val deepThink: Boolean = false,
    val hands: Boolean = false,
    val version: String = "",
    val agentName: String = "Neon",
    val userCall: String = "",
    val tone: Tone = Tone.CRISP,
    val userName: String = "",
    val userBio: String = "",
    val approvalSummary: String = "重要的才问",
    val chatStyle: ChatStyle = ChatStyle.DOC,
    val memoryCount: Int = 0,
    val vaultCount: Int = 0,
)

class MeActions(
    val onModel: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onOps: (Boolean) -> Unit = {},
    val onDeepThink: (Boolean) -> Unit = {},
    val onHands: (Boolean) -> Unit = {},
    val onChatStyle: (ChatStyle) -> Unit = {},
    val onNavigate: (String) -> Unit = {},
)

/** The line under the name, built from 性格: 叫你 QiGai，说话干脆. */
fun personaLine(call: String, tone: Tone): String =
    (if (call.isBlank()) "" else "叫你 ${call.trim()}，") + "说话" + tone.label

/** 我的: who Neon is, who you are, what it may do, what it costs. */
@Composable
fun MeScreen(vm: MainViewModel, onNavigate: (String) -> Unit, openSheet: (SheetKind?) -> Unit) {
    val s by vm.settings.collectAsState()
    val keys by vm.configured.collectAsState()
    val models by vm.models.collectAsState()
    val quota by vm.quota.collectAsState()
    val facts by vm.c.memory.items.collectAsState()
    val vaultItems by vm.c.vault.items.collectAsState()
    val ctx = LocalContext.current
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }
    val provider = vm.activeProviderId(s, keys)
    val modelLabel = provider?.let { p ->
        val id = vm.modelFor(p, s)
        models.lists[p]?.firstOrNull { it.id == id }?.name ?: id
    }
    MeContent(
        MeData(
            modelLabel, (quota as? QuotaState.Ok)?.usage, s.theme, s.opsCollapsed, s.deepThinkDefault, s.handsEnabled, version,
            agentName = s.agentName, userCall = s.userCall, tone = s.tone, userName = s.userName, userBio = s.userBio,
            approvalSummary = s.approval.summary(), chatStyle = s.chatStyle, memoryCount = facts.size, vaultCount = vaultItems.size,
        ),
        MeActions(
            onModel = { openSheet(if (modelLabel != null) SheetKind.MODEL_PICKER else SheetKind.CONNECT_DEEPSEEK) },
            onTheme = { vm.setTheme(it) },
            onOps = { vm.putSetting(SettingsRepository.OPS_COLLAPSED, if (it) "1" else "0") },
            onDeepThink = { vm.setDeepThinkDefault(it) },
            onHands = { vm.putSetting(SettingsRepository.HANDS, if (it) "1" else "0") },
            onChatStyle = { vm.putSetting(SettingsRepository.CHAT_STYLE, it.key) },
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
        // header
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonOrb(64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.agentName, style = NeonType.brand().copy(fontSize = 26.sp, lineHeight = 32.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(personaLine(d.userCall, d.tone), style = NeonType.label().copy(color = c.inkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlassCircle(Lucide.Pencil, "Neon 的性格", size = 44.dp) { a.onNavigate("persona") }
        }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 关于你
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).glass(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button) { a.onNavigate("about-you") }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Glass.tokens.tile), contentAlignment = Alignment.Center) {
                    val initial = d.userName.trim().firstOrNull()?.uppercaseChar()?.toString()
                    if (initial != null) Text(initial, style = NeonType.bodyMedium().copy(fontSize = 18.sp))
                    else Icon(Lucide.UserRound, null, Modifier.size(20.dp), tint = c.inkPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("关于你", style = NeonType.item().copy(fontSize = 16.sp))
                    Text(
                        d.userBio.replace('\n', ' ').trim().take(20).ifEmpty { "告诉 Neon 你是谁，回答会更合你" },
                        style = NeonType.label().copy(color = c.inkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkSecondary)
            }

            // 动手前 · 操作手机
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HalfCard(Lucide.ShieldCheck, c.warn, "动手前", d.approvalSummary, Modifier.weight(1f)) { a.onNavigate("perms") }
                HalfCard(Lucide.Hand, c.inkPrimary, "操作手机", if (d.hands) "智能调用" else "常关", Modifier.weight(1f)) { a.onNavigate("phone-hands") }
            }

            // 额度
            d.quota?.let { u ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("5 小时" to u.rolling, "本周" to u.weekly, "本月" to u.monthly).forEach { (label, w) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Ring(((w?.percent ?: 0.0) / 100.0).toFloat(), 36.dp, stroke = 5.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(w?.let { "${it.percent.toInt()}%" } ?: "--", style = NeonType.tnum(NeonType.bodyMedium()))
                                Text(label, style = NeonType.caption())
                            }
                        }
                    }
                }
            }

            // 列表
            GlassGroup {
                GroupRow(Lucide.KeyRound, "模型与密钥", onClick = { a.onNavigate("settings") }) { ValueChevron(d.modelLabel ?: "未连接") }
                GroupDivider()
                var themeMenu by remember { mutableStateOf(false) }
                Box {
                    GroupRow(Lucide.Palette, "外观", onClick = { themeMenu = true }) {
                        ValueChevron(when (d.theme) { ThemeMode.SYSTEM -> "跟随系统"; ThemeMode.LIGHT -> "浅色"; ThemeMode.DARK -> "深色" })
                    }
                    DropdownMenu(themeMenu, onDismissRequest = { themeMenu = false }, modifier = Modifier.background(c.bgSurface)) {
                        listOf(ThemeMode.SYSTEM to "跟随系统", ThemeMode.LIGHT to "浅色", ThemeMode.DARK to "深色").forEach { (m, label) ->
                            DropdownMenuItem(
                                text = { Text(label, style = NeonType.item()) },
                                trailingIcon = { if (m == d.theme) Icon(Lucide.Check, null, Modifier.size(16.dp), tint = pickInk()) },
                                onClick = { themeMenu = false; a.onTheme(m) },
                            )
                        }
                    }
                }
                GroupDivider()
                GroupRow(Lucide.MessageSquare, "对话样式") {
                    MiniSegmented(listOf("文档", "气泡"), if (d.chatStyle == ChatStyle.BUBBLE) 1 else 0, onSelect = { a.onChatStyle(if (it == 1) ChatStyle.BUBBLE else ChatStyle.DOC) })
                }
                GroupDivider()
                GroupRow(Lucide.Lock, "安全表单", onClick = { a.onNavigate("vault") }) { ValueChevron(if (d.vaultCount > 0) "${d.vaultCount} 个" else null) }
                GroupDivider()
                GroupRow(Lucide.Brain, "记忆与定时任务", onClick = { a.onNavigate("memory") }) { ValueChevron(if (d.memoryCount > 0) "${d.memoryCount} 条" else null) }
                GroupDivider()
                GroupRow(Lucide.Info, "关于 Neon", onClick = { a.onNavigate("about-neon") }) { ValueChevron(d.version.takeIf { it.isNotBlank() }?.let { "v$it" }) }
            }
        }
    }
}

@Composable
private fun HalfCard(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Neon.colors
    Column(
        modifier.heightIn(min = 118.dp).clip(RoundedCornerShape(24.dp)).glass(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Glass.tokens.tile), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = NeonType.bodyMedium().copy(fontSize = 17.sp))
        Text(sub, style = NeonType.label().copy(color = c.inkSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ───────── 操作手机 ─────────

/** The on/off choice that used to sit on 我的, plus a way into the Hands run view. */
@Composable
fun PhoneHandsRoute(vm: MainViewModel, onBack: () -> Unit, onHands: () -> Unit) {
    val s by vm.settings.collectAsState()
    PhoneHandsContent(s.handsEnabled, onBack, onToggle = { vm.putSetting(SettingsRepository.HANDS, if (it) "1" else "0") }, onHands = onHands)
}

@Composable
fun PhoneHandsContent(on: Boolean, onBack: () -> Unit, onToggle: (Boolean) -> Unit, onHands: () -> Unit) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding()) {
        SubPageBar("操作手机", onBack)
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.Hand, null, Modifier.size(18.dp), tint = c.inkPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text(if (on) "需要时 Neon 会替你点手机上的 App" else "Neon 不会碰手机上的 App", style = NeonType.label(), modifier = Modifier.weight(1f))
                }
                Segmented(listOf("智能调用", "常关"), if (on) 0 else 1, onSelect = { onToggle(it == 0) })
            }
            GlassGroup { GroupRow(Lucide.Smartphone, "最近一次操作", "引擎、每一步和暂停", onClick = onHands) { ValueChevron(null) } }
        }
    }
}

// ───────── 关于 Neon ─────────

@Composable
fun AboutNeonRoute(version: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    fun open(url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    AboutNeonContent(
        version, onBack,
        onGithub = { open("https://github.com/QiGai77/neon") },
        onLicense = { open("https://github.com/QiGai77/neon/blob/main/LICENSE") },
        onUpdate = { open("https://github.com/QiGai77/neon/releases/latest") },
    )
}

@Composable
fun AboutNeonContent(version: String, onBack: () -> Unit, onGithub: () -> Unit = {}, onLicense: () -> Unit = {}, onUpdate: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding()) {
        SubPageBar("关于 Neon", onBack)
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                NeonOrb(72.dp)
                Spacer(Modifier.height(10.dp))
                Text("Neon", style = NeonType.brand())
                Text(if (version.isBlank()) "" else "版本 $version", style = NeonType.caption())
            }
            GlassGroup {
                GroupRow(Lucide.GitBranch, "GitHub", "QiGai77/neon", onClick = onGithub) { ValueChevron(null) }
                GroupDivider()
                GroupRow(Lucide.FileText, "许可证", "MIT", onClick = onLicense) { ValueChevron(null) }
                GroupDivider()
                GroupRow(Lucide.RefreshCw, "检查更新", "打开最新发布页", onClick = onUpdate) { ValueChevron(null) }
            }
        }
    }
}
