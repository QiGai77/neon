package com.qigai.neon.ui.space

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qigai.neon.MainViewModel
import com.qigai.neon.sandbox.SandboxStatus
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.components.NeonOrb
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.home.GlassCircle
import com.qigai.neon.ui.home.LocalBottomBarInset
import com.qigai.neon.ui.home.PageHeader
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop

data class SpaceData(
    val skillNames: List<String> = emptyList(),
    val connectors: Int = 0,
    val connected: Int = 0,
    val taskTitles: List<String> = emptyList(),
    val tasksEnabled: Int = 0,
    val memory: List<String> = emptyList(),
    val sandbox: SandboxStatus = SandboxStatus.Checking,
    val handsEnabled: Boolean = false,
    val driveConnected: Boolean = false,
    val driveEmail: String? = null,
)

/** 空间: everything Neon can use, as a bento. Each tile opens its full page. */
@Composable
fun SpaceScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val skills by vm.c.skills.skills.collectAsState()
    val connectorsFlow = remember { vm.c.db.connectorDao().observeAll() }
    val connectors by connectorsFlow.collectAsState(emptyList())
    val tasksFlow = remember { vm.c.db.scheduledTaskDao().observeAll() }
    val tasks by tasksFlow.collectAsState(emptyList())
    val memory by vm.c.memory.items.collectAsState()
    val sandbox by vm.c.rootfs.status.collectAsState()
    val s by vm.settings.collectAsState()
    SpaceContent(
        SpaceData(
            skillNames = skills.map { it.name.ifBlank { it.command } },
            connectors = connectors.size, connected = connectors.count { it.status == "connected" },
            taskTitles = tasks.map { it.title }, tasksEnabled = tasks.count { it.enabled },
            memory = memory, sandbox = sandbox,
            handsEnabled = s.handsEnabled, driveConnected = s.driveConnected, driveEmail = s.driveEmail,
        ),
        onNavigate = onNavigate,
    )
}

@Composable
fun SpaceContent(d: SpaceData, initiallyExpanded: Boolean = false, onNavigate: (String) -> Unit) {
    val c = Neon.colors
    var more by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val bottom = LocalBottomBarInset.current
    Column(
        Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(bottom = bottom + 24.dp),
    ) {
        PageHeader("空间") {
            GlassCircle(Lucide.Store, "市场") { onNavigate("skill-market") }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("技能", "${d.skillNames.size}", Modifier.weight(1f), onClick = { onNavigate("skills") }) {
                    SkillDeck(d.skillNames.take(3))
                }
                Tile("连接器", if (d.connected > 0) "${d.connected} 已连" else "${d.connectors}", Modifier.weight(1f), onClick = { onNavigate("connectors") }) {
                    ConnectorOrbit()
                }
            }
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SandboxTile(d.sandbox, Modifier.weight(1f)) { onNavigate("sandbox") }
                Tile("记忆", "${d.memory.size} 条", Modifier.weight(1f), onClick = { onNavigate("memory") }) {
                    val lines = d.memory.take(3).ifEmpty { listOf("聊得越多，Neon 记得越多") }
                    lines.forEach {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.FileText, null, Modifier.size(13.dp), tint = c.inkTertiary)
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            MoreToggle(more) { more = !more }
            AnimatedVisibility(
                visible = more,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WideTile(
                        Lucide.CalendarClock, "定时任务",
                        if (d.taskTitles.isEmpty()) "让 Neon 每天或每周替你跑一件事" else d.taskTitles.take(2).joinToString("，"),
                        trailing = if (d.taskTitles.isEmpty()) null else "${d.tasksEnabled} 个在跑",
                    ) { onNavigate("memory") }
                    WideTile(
                        Lucide.Hand, "Hands",
                        if (d.handsEnabled) "可以替你点手机上的 App" else "打开后 Neon 能替你操作手机",
                        trailing = if (d.handsEnabled) "已开" else "未开", live = d.handsEnabled,
                    ) { onNavigate("hands") }
                    WideTile(
                        Lucide.Cloud, "Google Drive",
                        if (d.driveConnected) (d.driveEmail ?: "已连接") else "连接后对话和文件自动备份",
                        trailing = if (d.driveConnected) "已连" else "登录 Google", live = d.driveConnected,
                    ) { onNavigate(if (d.driveConnected) "settings" else "connectors") }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarketTile("技能市场", Lucide.Sparkles, Modifier.weight(1f)) { onNavigate("skill-market") }
                        MarketTile("连接器市场", Lucide.Plug, Modifier.weight(1f)) { onNavigate("connector-market") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(
    title: String, meta: String, modifier: Modifier, onClick: () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxHeight().heightIn(min = 156.dp).glass(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NeonType.cardTitle().copy(fontFamily = Neon.fonts.medium), modifier = Modifier.weight(1f))
            Text(meta, style = NeonType.tnum(NeonType.caption()))
        }
        Spacer(Modifier.height(10.dp))
        body()
    }
}

@Composable
private fun SkillDeck(names: List<String>) {
    val c = Neon.colors
    val deck = if (c.isDark) listOf(Color(0xFF1B1B1E), Color(0xFF232327), Color(0xFF2C2C31)) else listOf(Color(0xFFE9E7E0), Color(0xFFF3F2ED), Color.White)
    Box(Modifier.fillMaxWidth().height(86.dp)) {
        for (k in 0..2) {
            val inset = (2 - k) * 8
            Box(
                Modifier.align(Alignment.BottomCenter).padding(start = inset.dp, end = inset.dp, bottom = ((2 - k) * 10).dp)
                    .fillMaxWidth().height(58.dp).clip(RoundedCornerShape(14.dp)).background(deck[k]).padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (k == 2) Text(names.firstOrNull() ?: "还没有技能", style = NeonType.label().copy(fontFamily = Neon.fonts.medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConnectorOrbit() {
    val c = Neon.colors
    val icons = listOf(Lucide.Mail, Lucide.Calendar, Lucide.Globe, Lucide.Server, Lucide.Cloud, Lucide.GitBranch)
    Box(Modifier.fillMaxWidth().height(86.dp), contentAlignment = Alignment.Center) {
        icons.forEachIndexed { i, ic ->
            val a = Math.toRadians(i * 60.0 - 90)
            Box(
                Modifier.padding(start = (kotlin.math.cos(a) * 64 + 64).dp.coerceAtLeast(0.dp), top = (kotlin.math.sin(a) * 34 + 34).dp.coerceAtLeast(0.dp))
                    .align(Alignment.TopStart).size(22.dp).clip(CircleShape).background(Glass.tokens.tile),
                contentAlignment = Alignment.Center,
            ) { Icon(ic, null, Modifier.size(12.dp), tint = c.inkSecondary) }
        }
        NeonOrb(30.dp, blink = false)
    }
}

@Composable
private fun SandboxTile(status: SandboxStatus, modifier: Modifier, onClick: () -> Unit) {
    val c = Neon.colors
    val (line, live) = when (status) {
        is SandboxStatus.Ready -> "alpine  ${status.arch}" to true
        is SandboxStatus.Extracting -> "正在解压  ${status.detail}" to false
        is SandboxStatus.Checking -> "检查中" to false
        is SandboxStatus.MissingBundle -> "缺少运行环境" to false
        is SandboxStatus.Error -> status.message to false
    }
    Column(
        modifier.fillMaxHeight().heightIn(min = 156.dp).clip(RoundedCornerShape(22.dp))
            .background(c.terminalBg.copy(alpha = if (c.isDark) 0.6f else 0.86f)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("沙箱", style = NeonType.cardTitle().copy(color = c.terminalInk, fontFamily = Neon.fonts.medium), modifier = Modifier.weight(1f))
            StatusDot(if (live) c.lime else c.terminalDim)
        }
        Spacer(Modifier.height(12.dp))
        Text(line, style = NeonType.mono().copy(color = c.terminalDim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("~ $ ▍", style = NeonType.mono().copy(color = c.terminalInk))
        Spacer(Modifier.height(4.dp))
        Text("打开终端", style = NeonType.mono().copy(color = c.lime))
    }
}

@Composable
private fun MoreToggle(open: Boolean, onClick: () -> Unit) {
    val c = Neon.colors
    val rot by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = 400f), label = "more")
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(Glass.tokens.tile)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "收起" else "更多", style = NeonType.label().copy(color = c.inkSecondary))
        Spacer(Modifier.width(6.dp))
        Icon(Lucide.ChevronDown, null, Modifier.size(16.dp).rotate(rot), tint = c.inkSecondary)
    }
}

@Composable
private fun WideTile(icon: ImageVector, title: String, sub: String, trailing: String? = null, live: Boolean = false, onClick: () -> Unit) {
    val c = Neon.colors
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquircle(icon, size = 40.dp, iconSize = 19.dp, bg = if (live) c.chipActive else Glass.tokens.tile, tint = if (live) c.onChipActive else c.inkPrimary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NeonType.cardTitle())
            Text(sub, style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = NeonType.caption().copy(color = if (live) c.onChipActive else c.inkSecondary))
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkTertiary)
    }
}

@Composable
private fun MarketTile(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).glass(RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Neon.colors.inkPrimary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = NeonType.label())
    }
}
