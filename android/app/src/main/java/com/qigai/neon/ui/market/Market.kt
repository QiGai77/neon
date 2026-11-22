package com.qigai.neon.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qigai.neon.MainViewModel
import com.qigai.neon.connectors.ConnectorCatalog
import com.qigai.neon.connectors.ConnectorPreset
import com.qigai.neon.connectors.McpHub
import com.qigai.neon.connectors.PresetAuth
import com.qigai.neon.connectors.PresetGroup
import com.qigai.neon.connectors.RegistryServer
import com.qigai.neon.skills.MarketSkill
import com.qigai.neon.skills.MarketView
import com.qigai.neon.ui.components.ButtonKind
import com.qigai.neon.ui.components.ChipStyle
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.ui.components.EmptyState
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.NeonChip
import com.qigai.neon.ui.components.PillButton
import com.qigai.neon.ui.components.SearchField
import com.qigai.neon.ui.components.SectionLabel
import com.qigai.neon.ui.components.TagChip
import com.qigai.neon.ui.connectors.Spinner
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.settings.PageTopBar
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ───────────────────────── shared pieces ─────────────────────────

/** One row in a market list. [state] drives the trailing control. */
data class MarketRowUi(
    val key: String,
    val title: String,
    val sub: String,
    val tag: String? = null,
    val monogram: String? = null,
    val icon: ImageVector? = null,
    val state: RowState = RowState.Idle,
)

enum class RowState { Idle, Busy, Done, Unavailable }

@Composable
fun MarketTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Neon.colors
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(NeonShapes.capsule).background(c.bgSubtle).padding(3.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxSize().clip(NeonShapes.capsule)
                    .background(if (on) c.bgSurface else c.bgSubtle)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(t, style = NeonType.label().copy(color = if (on) c.inkPrimary else c.inkSecondary))
            }
        }
    }
}

@Composable
fun MarketRow(r: MarketRowUi, actionLabel: String, onOpen: () -> Unit, onAction: () -> Unit) {
    val c = Neon.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(NeonShapes.squircle).background(c.bgSubtle), contentAlignment = Alignment.Center) {
            if (r.monogram != null) Text(r.monogram, style = NeonType.bodyMedium().copy(fontSize = 17.sp))
            else Icon(r.icon ?: Lucide.Plug, null, Modifier.size(19.dp), tint = c.inkPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = NeonType.cardTitle().copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.tag != null) { Spacer(Modifier.width(8.dp)); TagChip(r.tag) }
            }
            if (r.sub.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(r.sub, style = NeonType.caption().copy(fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        when (r.state) {
            RowState.Busy -> Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Spinner(18.dp) }
            RowState.Done -> Icon(Lucide.Check, "已添加", Modifier.size(18.dp), tint = c.inkPrimary)
            RowState.Unavailable -> {}
            RowState.Idle -> PillButton(actionLabel, kind = ButtonKind.Secondary, height = 34.dp, onClick = onAction)
        }
    }
}

@Composable
private fun RowCard(rows: List<MarketRowUi>, actionLabel: String, onOpen: (MarketRowUi) -> Unit, onAction: (MarketRowUi) -> Unit) {
    val c = Neon.colors
    NeonCard(padding = PaddingValues(0.dp)) {
        rows.forEachIndexed { i, r ->
            MarketRow(r, actionLabel, { onOpen(r) }, { onAction(r) })
            if (i < rows.lastIndex) Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
        }
    }
}

/** Search box that waits for the user to stop typing. */
@Composable
private fun rememberDebounced(value: String, ms: Long = 450): String {
    var out by remember { mutableStateOf(value) }
    LaunchedEffect(value) { delay(ms); out = value }
    return out
}

// ───────────────────────── 连接器市场 ─────────────────────────

/** Stateless connector market: built-in presets or the official MCP Registry. */
@Composable
fun ConnectorMarketContent(
    tab: Int,
    onTab: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    presets: List<Pair<String, List<MarketRowUi>>>,
    registry: List<MarketRowUi>,
    registryState: String?,
    onBack: () -> Unit,
    onOpen: (MarketRowUi) -> Unit,
    onConnect: (MarketRowUi) -> Unit,
    onMore: (() -> Unit)?,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        PageTopBar("连接器市场", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tabs") { MarketTabs(listOf("精选", "MCP 市场"), tab, onTab) }
            item(key = "search") { SearchField(query, onQuery, if (tab == 0) "搜索连接器" else "搜索 MCP 服务器") }
            if (tab == 0) {
                presets.forEach { (label, rows) ->
                    if (rows.isEmpty()) return@forEach
                    item(key = "h-$label") { SectionLabel(label, Modifier.padding(top = 4.dp), trailing = "${rows.size}") }
                    item(key = "g-$label") { RowCard(rows, "连接", onOpen, onConnect) }
                }
            } else {
                if (registryState != null && registry.isEmpty()) item(key = "state") {
                    NeonCard(padding = PaddingValues(22.dp)) { EmptyState(registryState) }
                } else {
                    item(key = "reg") { RowCard(registry, "连接", onOpen, onConnect) }
                    if (onMore != null) item(key = "more") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            NeonChip("加载更多", style = ChipStyle.Outline, height = 34.dp, onClick = onMore)
                        }
                    }
                }
            }
        }
    }
}

private fun iconForGroup(g: PresetGroup) = when (g) {
    PresetGroup.LOCAL -> Lucide.SquareTerminal
    else -> null
}

@Composable
fun ConnectorMarketRoute(vm: MainViewModel, onBack: () -> Unit) {
    val c = vm.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by c.db.connectorDao().observeAll().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val busy = remember { mutableStateListOf<String>() }
    var keyFor by remember { mutableStateOf<KeyAsk?>(null) }
    var detail by remember { mutableStateOf<MarketRowUi?>(null) }

    val q = rememberDebounced(query)
    val registry = remember { mutableStateListOf<RegistryServer>() }
    var cursor by remember { mutableStateOf<String?>(null) }
    var regState by remember { mutableStateOf<String?>("加载中") }
    LaunchedEffect(tab, q) {
        if (tab != 1) return@LaunchedEffect
        regState = "加载中"
        c.mcpMarket.search(q).onSuccess { (list, next) ->
            registry.clear(); registry += list; cursor = next
            regState = if (list.isEmpty()) "没有找到" else null
        }.onFailure { regState = it.message ?: "市场暂时打不开" }
    }

    fun stateOf(id: String) = when {
        id in busy -> RowState.Busy
        rows.any { it.id == id && it.status == "connected" } -> RowState.Done
        else -> RowState.Idle
    }

    fun connect(id: String, name: String, run: suspend () -> Result<Int>) {
        if (id in busy) return
        busy += id
        scope.launch {
            run().fold({ vm.toast("$name 已连接，$it 个工具") }, { vm.toast("$name：${it.message ?: "连接失败"}") })
            busy -= id
        }
    }

    fun connectPreset(p: ConnectorPreset, key: String? = null) {
        when {
            p.local -> connect(p.id, p.name) { c.mcpHub.connectLocal(p.id, p.name, p.target) }
            p.auth == PresetAuth.TOKEN && key == null -> keyFor = KeyAsk(p.id, p.name, p.keyHint ?: "令牌", p.keyUrl)
            p.auth == PresetAuth.URL_KEY && key == null -> keyFor = KeyAsk(p.id, p.name, p.keyHint ?: "Key", p.keyUrl)
            p.auth == PresetAuth.URL_KEY -> connect(p.id, p.name) { c.mcpHub.connectRemote(p.id, p.name, p.target.replace("{key}", key!!.trim())) }
            p.auth == PresetAuth.TOKEN -> connect(p.id, p.name) { c.mcpHub.connectRemote(p.id, p.name, p.target, mapOf("Authorization" to "Bearer ${key!!.trim()}")) }
            else -> connect(p.id, p.name) { c.mcpHub.connectRemote(p.id, p.name, p.target) }
        }
    }

    fun regId(s: RegistryServer) = McpHub.safeId(s.name.substringAfterLast('/'))

    fun connectRegistry(s: RegistryServer, key: String? = null) {
        val id = regId(s)
        when {
            s.remoteUrl != null && s.remoteHeader != null && key == null -> keyFor = KeyAsk(id, s.title, s.remoteHeader, null, registry = s)
            s.remoteUrl != null -> connect(id, s.title) {
                val h = if (key != null && s.remoteHeader != null) mapOf(s.remoteHeader to (if (s.remoteHeader.equals("Authorization", true) && !key.startsWith("Bearer")) "Bearer $key" else key)) else emptyMap()
                c.mcpHub.connectRemote(id, s.title, s.remoteUrl, h)
            }
            s.command != null && s.envVars.isNotEmpty() && key == null -> keyFor = KeyAsk(id, s.title, s.envVars.joinToString(" "), null, registry = s)
            s.command != null -> connect(id, s.title) {
                val env = if (key != null && s.envVars.isNotEmpty()) s.envVars.first() + "=" + shellQuote(key.trim()) + " " else ""
                c.mcpHub.connectLocal(id, s.title, env + s.command)
            }
        }
    }

    val ql = query.trim().lowercase()
    val presetRows = PresetGroup.entries.map { g ->
        g.label to ConnectorCatalog.presets.filter { it.group == g }
            .filter { ql.isEmpty() || it.name.lowercase().contains(ql) || it.desc.contains(ql) || it.id.contains(ql) }
            .map { p ->
                MarketRowUi(p.id, p.name, p.desc,
                    tag = when (p.auth) { PresetAuth.OAUTH -> "登录"; PresetAuth.TOKEN -> "令牌"; PresetAuth.URL_KEY -> "Key"; PresetAuth.NONE -> if (p.local) "沙箱" else "免登录" },
                    monogram = if (p.local) null else p.monogram, icon = iconForGroup(p.group), state = stateOf(p.id))
            }
    }
    val regRows = registry.map { s ->
        MarketRowUi(s.name, s.title, s.description,
            tag = when { s.remoteUrl != null -> "远程"; s.command?.startsWith("npx") == true -> "npm"; s.command != null -> "PyPI"; else -> null },
            monogram = s.title.firstOrNull()?.uppercase(), state = if (!s.runnable) RowState.Unavailable else stateOf(regId(s)))
    }

    ConnectorMarketContent(
        tab = tab, onTab = { tab = it }, query = query, onQuery = { query = it },
        presets = presetRows, registry = regRows, registryState = regState,
        onBack = onBack,
        onOpen = { detail = it },
        onConnect = { r ->
            if (tab == 0) ConnectorCatalog.byId(r.key)?.let { connectPreset(it) }
            else registry.firstOrNull { it.name == r.key }?.let { connectRegistry(it) }
        },
        onMore = cursor?.let { cur ->
            {
                scope.launch {
                    c.mcpMarket.search(q, cur).onSuccess { (list, next) ->
                        registry += list.filter { n -> registry.none { it.name == n.name } }; cursor = next
                    }
                }
                Unit
            }
        },
    )

    keyFor?.let { ask ->
        KeyDialog(ask, onDismiss = { keyFor = null }, onOpenUrl = { u -> CustomTabs.open(ctx, u) }) { value ->
            keyFor = null
            if (value.isBlank()) return@KeyDialog
            if (ask.registry != null) connectRegistry(ask.registry, value)
            else ConnectorCatalog.byId(ask.id)?.let { connectPreset(it, value) }
        }
    }
    detail?.let { r ->
        val preset = ConnectorCatalog.byId(r.key)
        val reg = registry.firstOrNull { it.name == r.key }
        InfoDialog(
            title = r.title,
            lines = listOfNotNull(
                r.sub.takeIf { it.isNotBlank() },
                preset?.target ?: reg?.remoteUrl ?: reg?.command,
                reg?.envVars?.takeIf { it.isNotEmpty() }?.let { "需要环境变量 ${it.joinToString(" ")}" },
            ),
            onDismiss = { detail = null },
        )
    }
}

private fun shellQuote(s: String) = "'" + s.replace("'", "'\"'\"'") + "'"

data class KeyAsk(val id: String, val name: String, val label: String, val url: String?, val registry: RegistryServer? = null)

@Composable
fun KeyDialog(ask: KeyAsk, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit, onSave: (String) -> Unit) {
    val c = Neon.colors
    var v by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("连接 ${ask.name}", style = NeonType.headline().copy(fontSize = 18.sp))
            com.qigai.neon.ui.chat.NeonField(v, { v = it }, ask.label, secret = true)
            Text("只保存在本机，加密存储", style = NeonType.caption())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ask.url != null) NeonChip("获取", style = ChipStyle.Outline, icon = Lucide.ArrowUpRight, height = 36.dp, onClick = { onOpenUrl(ask.url) })
                Spacer(Modifier.weight(1f))
                PillButton("取消", kind = ButtonKind.Secondary, height = 40.dp, onClick = onDismiss)
                PillButton("连接", height = 40.dp, enabled = v.isNotBlank(), onClick = { onSave(v) })
            }
        }
    }
}

@Composable
fun InfoDialog(title: String, lines: List<String>, onDismiss: () -> Unit, actions: @Composable (() -> Unit)? = null) {
    val c = Neon.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = NeonType.headline().copy(fontSize = 18.sp))
            lines.forEach { l ->
                if (l.startsWith("http") || l.startsWith("npx") || l.startsWith("uvx")) Text(l, style = NeonType.mono().copy(fontSize = 12.sp))
                else Text(l, style = NeonType.secondary())
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                actions?.invoke()
                PillButton("关闭", height = 40.dp, onClick = onDismiss)
            }
        }
    }
}

// ───────────────────────── 技能市场 ─────────────────────────

@Composable
fun SkillMarketContent(
    tab: Int,
    onTab: (Int) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    rows: List<MarketRowUi>,
    state: String?,
    onBack: () -> Unit,
    onOpen: (MarketRowUi) -> Unit,
    onInstall: (MarketRowUi) -> Unit,
    onMore: (() -> Unit)?,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        PageTopBar("技能市场", onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tabs") { MarketTabs(MarketView.entries.map { it.label }, tab, onTab) }
            item(key = "search") { SearchField(query, onQuery, "在 skills.sh 搜索") }
            if (state != null && rows.isEmpty()) item(key = "state") {
                NeonCard(padding = PaddingValues(22.dp)) { EmptyState(state) }
            } else {
                item(key = "list") { RowCard(rows, "安装", onOpen, onInstall) }
                if (onMore != null) item(key = "more") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        NeonChip("加载更多", style = ChipStyle.Outline, height = 34.dp, onClick = onMore)
                    }
                }
            }
            item(key = "credit") {
                Text("技能目录来自 skills.sh，文件从各自的 GitHub 仓库下载", style = NeonType.caption(), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

private fun formatInstalls(n: Long) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

@Composable
fun SkillMarketRoute(vm: MainViewModel, onBack: () -> Unit) {
    val c = vm.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by c.skills.skills.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    val q = rememberDebounced(query)
    val list = remember { mutableStateListOf<MarketSkill>() }
    var page by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<String?>("加载中") }
    val busy = remember { mutableStateListOf<String>() }

    LaunchedEffect(tab, q) {
        state = "加载中"; page = 0
        val r = if (q.trim().length >= 2) c.skillMarket.search(q) else c.skillMarket.list(MarketView.entries[tab], 0)
        r.onSuccess { l -> list.clear(); list += l; state = if (l.isEmpty()) "没有找到" else null }
            .onFailure { state = it.message ?: "市场暂时打不开" }
    }
    val sources = installed.mapNotNull { it.source }.toSet()
    val rows = list.map { s ->
        MarketRowUi(
            s.key, s.name, "${s.source}  ${formatInstalls(s.installs)} 次安装",
            icon = Lucide.Sparkles,
            state = when {
                s.key in busy -> RowState.Busy
                s.key in sources || installed.any { it.dir == s.skillId } -> RowState.Done
                else -> RowState.Idle
            },
        )
    }
    SkillMarketContent(
        tab = tab, onTab = { tab = it }, query = query, onQuery = { query = it },
        rows = rows, state = state, onBack = onBack,
        onOpen = { r -> CustomTabs.open(ctx, "https://skills.sh/${r.key}") },
        onInstall = { r ->
            val s = list.firstOrNull { it.key == r.key } ?: return@SkillMarketContent
            if (s.key in busy) return@SkillMarketContent
            busy += s.key
            scope.launch {
                c.skillMarket.install(s).fold({ vm.toast("已安装「${it.name}」") }, { vm.toast("${s.name}：${it.message ?: "安装失败"}") })
                busy -= s.key
            }
        },
        onMore = if (q.trim().length >= 2 || state != null) null else ({
            scope.launch {
                c.skillMarket.list(MarketView.entries[tab], page + 1).onSuccess { l -> page++; list += l.filter { n -> list.none { it.key == n.key } } }
            }
            Unit
        }),
    )
}
