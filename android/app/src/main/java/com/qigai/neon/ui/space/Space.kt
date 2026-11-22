package com.qigai.neon.ui.space

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import com.qigai.neon.space.SpaceCard
import com.qigai.neon.space.SpaceTypes
import kotlinx.coroutines.launch

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

/** 空间: a page of cards the user (or Neon) arranges. Feature cards open their full page; life cards hold tickets, parcels, trips. */
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
    val cards by vm.c.space.cards.collectAsState(vm.c.space.current())
    val scope = rememberCoroutineScope()
    val store = vm.c.space
    SpaceContent(
        SpaceData(
            skillNames = skills.map { it.name.ifBlank { it.command } },
            connectors = connectors.size, connected = connectors.count { it.status == "connected" },
            taskTitles = tasks.map { it.title }, tasksEnabled = tasks.count { it.enabled },
            memory = memory, sandbox = sandbox,
            handsEnabled = s.handsEnabled, driveConnected = s.driveConnected, driveEmail = s.driveEmail,
        ),
        cards = cards,
        actions = SpaceActions(
            onNavigate = onNavigate,
            onAdd = { type -> scope.launch { store.add(type) } },
            onRemove = { id -> scope.launch { store.remove(id) } },
            onMove = { id, to -> scope.launch { store.move(id, to) } },
            onResize = { card -> scope.launch { store.update(card.id, size = if (card.full) "half" else "full") } },
            onSaveData = { id, data -> scope.launch { store.update(id, data = data) } },
            onReset = { scope.launch { store.reset() } },
        ),
    )
}

class SpaceActions(
    val onNavigate: (String) -> Unit = {},
    val onAdd: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onMove: (String, Int) -> Unit = { _, _ -> },
    val onResize: (SpaceCard) -> Unit = {},
    val onSaveData: (String, Map<String, String>) -> Unit = { _, _ -> },
    val onReset: () -> Unit = {},
)

/** Half cards pair up two to a row; a full card takes the whole row. */
private fun packRows(cards: List<SpaceCard>): List<List<SpaceCard>> {
    val rows = mutableListOf<List<SpaceCard>>()
    var half: SpaceCard? = null
    for (c in cards) {
        if (c.full) { half?.let { rows += listOf(it); half = null }; rows += listOf(c) }
        else if (half == null) half = c
        else { rows += listOf(half!!, c); half = null }
    }
    half?.let { rows += listOf(it) }
    return rows
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpaceContent(
    d: SpaceData,
    cards: List<SpaceCard> = SpaceTypes.defaults,
    actions: SpaceActions = SpaceActions(),
    initialEdit: Boolean = false,
) {
    val c = Neon.colors
    var editing by rememberSaveable { mutableStateOf(initialEdit) }
    var adding by remember { mutableStateOf(false) }
    var filling by remember { mutableStateOf<SpaceCard?>(null) }
    val bottom = LocalBottomBarInset.current
    Column(
        Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(bottom = bottom + 24.dp),
    ) {
        PageHeader("空间") {
            GlassCircle(Lucide.Plus, "添加卡片") { adding = true }
            Spacer(Modifier.width(8.dp))
            GlassCircle(if (editing) Lucide.Check else Lucide.Pencil, if (editing) "完成" else "编辑") { editing = !editing }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cards.isEmpty()) EmptySpace { adding = true }
            packRows(cards).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        val index = cards.indexOf(card)
                        EditFrame(
                            editing, card, Modifier.weight(1f),
                            canLeft = index > 0, canRight = index < cards.lastIndex,
                            onLeft = { actions.onMove(card.id, index - 1) },
                            onRight = { actions.onMove(card.id, index + 1) },
                            onResize = { actions.onResize(card) },
                            onRemove = { actions.onRemove(card.id) },
                        ) {
                            SpaceCardView(card, d, Modifier.fillMaxWidth(), editing) { route ->
                                if (route == "fill") filling = card else actions.onNavigate(route)
                            }
                        }
                    }
                    if (row.size == 1 && !row[0].full) Spacer(Modifier.weight(1f))
                }
            }
            if (editing) Text(
                "恢复默认布局", style = NeonType.label().copy(color = c.inkTertiary),
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable { actions.onReset() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    if (adding) AddCardSheet(cards, onDismiss = { adding = false }) { t -> actions.onAdd(t); adding = false }
    filling?.let { card -> FillCardSheet(card, onDismiss = { filling = null }) { data -> actions.onSaveData(card.id, data); filling = null } }
}

@Composable
private fun EmptySpace(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).clickable(onClick = onAdd).padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NeonOrb(40.dp)
        Spacer(Modifier.height(10.dp))
        Text("空间是空的", style = NeonType.item())
        Text("添加卡片，或者让 Neon 帮你布置", style = NeonType.caption())
    }
}

/** In edit mode a card wobbles a little and grows small controls: move, resize, remove. */
@Composable
private fun EditFrame(
    editing: Boolean, card: SpaceCard, modifier: Modifier,
    canLeft: Boolean, canRight: Boolean,
    onLeft: () -> Unit, onRight: () -> Unit, onResize: () -> Unit, onRemove: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = Neon.colors
    val t = rememberInfiniteTransition(label = "wobble")
    val wob by t.animateFloat(-0.6f, 0.6f, infiniteRepeatable(tween(140 + (card.id.hashCode() and 31)), RepeatMode.Reverse), label = "w")
    Box(modifier.fillMaxHeight().graphicsLayer { rotationZ = if (editing) wob else 0f }) {
        content()
        AnimatedVisibility(editing, Modifier.align(Alignment.TopEnd).padding(8.dp), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Row(
                Modifier.clip(CircleShape).background(if (c.isDark) Color(0xE61C1C20) else Color(0xEBFFFFFF)).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (canLeft) MiniBtn(Lucide.ChevronLeft, "往前", onLeft)
                if (canRight) MiniBtn(Lucide.ChevronRight, "往后", onRight)
                MiniBtn(if (card.full) Lucide.ChevronsDownUp else Lucide.ChevronsUpDown, if (card.full) "变窄" else "变宽", onResize, rotate = 90f)
                MiniBtn(Lucide.X, "移除", onRemove)
            }
        }
    }
}

@Composable
private fun MiniBtn(icon: ImageVector, desc: String, onClick: () -> Unit, rotate: Float = 0f) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(15.dp).rotate(rotate), tint = Neon.colors.inkPrimary)
    }
}

@Composable
private fun SpaceCardView(card: SpaceCard, d: SpaceData, modifier: Modifier, editing: Boolean, onOpen: (String) -> Unit) {
    val c = Neon.colors
    val open: (String) -> Unit = { r -> if (!editing) onOpen(r) }
    when (card.type) {
        "skills" -> Tile("技能", "${d.skillNames.size}", modifier, onClick = { open("skills") }) { SkillDeck(d.skillNames.take(3)) }
        "connectors" -> Tile("连接器", if (d.connected > 0) "${d.connected} 已连" else "${d.connectors}", modifier, onClick = { open("connectors") }) { ConnectorOrbit() }
        "sandbox" -> SandboxTile(d.sandbox, modifier) { open("sandbox") }
        "memory" -> Tile("记忆", "${d.memory.size} 条", modifier, onClick = { open("memory") }) {
            d.memory.take(3).ifEmpty { listOf("聊得越多，Neon 记得越多") }.forEach {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.FileText, null, Modifier.size(13.dp), tint = c.inkTertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(it, style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        "tasks" -> WideTile(
            Lucide.CalendarClock, "定时任务",
            if (d.taskTitles.isEmpty()) "让 Neon 每天或每周替你跑一件事" else d.taskTitles.take(2).joinToString("，"),
            trailing = if (d.taskTitles.isEmpty()) null else "${d.tasksEnabled} 个在跑", modifier = modifier,
        ) { open("memory") }
        "hands" -> WideTile(
            Lucide.Hand, "操作手机", if (d.handsEnabled) "需要时替你点手机上的 App" else "已关闭，在我的里打开",
            trailing = if (d.handsEnabled) "智能调用" else "常关", live = d.handsEnabled, modifier = modifier,
        ) { open("hands") }
        "drive" -> WideTile(
            Lucide.Cloud, "Google Drive",
            if (d.driveConnected) (d.driveEmail ?: "已连接") else "连接后对话和文件自动备份",
            trailing = if (d.driveConnected) "已连" else "登录 Google", live = d.driveConnected, modifier = modifier,
        ) { open(if (d.driveConnected) "settings" else "connectors") }
        "market" -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MarketTile("技能市场", Lucide.Sparkles, Modifier.weight(1f)) { open("skill-market") }
            MarketTile("连接器市场", Lucide.Plug, Modifier.weight(1f)) { open("connector-market") }
        }
        else -> LifeCard(card, modifier, editing, onFill = { open("fill") })
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
private fun WideTile(icon: ImageVector, title: String, sub: String, trailing: String? = null, live: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Neon.colors
    Row(
        modifier.fillMaxWidth().fillMaxHeight().glass(RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
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
