package com.qigai.neon.ui.connectors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.ui.components.*
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.settings.PageTopBar
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType

// ───────────────────────── S03 · 连接器 ─────────────────────────

enum class ConnectorGroup(val label: String) { CLOUD("云端"), LOCAL("本地与设备"), AUTOMATION("自动化") }

sealed interface ConnectorStatus {
    data class Connected(val text: String) : ConnectorStatus
    /** OAuth / discovery in flight: dashed card with a progress line. */
    data class Authorizing(val step: String) : ConnectorStatus
    data class Failed(val reason: String) : ConnectorStatus
    data object Idle : ConnectorStatus
}

data class ConnectorUi(
    val id: String,
    val group: ConnectorGroup,
    val name: String,
    val sub: String,
    val status: ConnectorStatus,
    /** letter monogram ("G", "N") or null to use [icon] */
    val monogram: String? = null,
    val icon: ImageVector? = null,
    val badge: String? = null,
    /** trailing button for idle / automation rows ("自连测试", "连接") */
    val action: String? = null,
)

@Composable
fun ConnectorsContent(
    connectors: List<ConnectorUi>,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (ConnectorUi) -> Unit,
    onAction: (ConnectorUi) -> Unit,
    onAddMcp: () -> Unit,
    onMarket: (() -> Unit)? = null,
    connectedCount: Int = 0,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        PageTopBar("连接器", onBack) { IconBtn(Lucide.Store, "连接器市场", onClick = onSearch) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onMarket != null) item(key = "market") {
                Row(
                    Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).border(1.dp, c.borderCard, NeonShapes.card)
                        .clickable(onClick = onMarket).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(NeonShapes.squircle).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Store, null, Modifier.size(19.dp), tint = c.onAccent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("连接器市场", style = NeonType.cardTitle().copy(fontSize = 16.sp))
                        Spacer(Modifier.height(3.dp))
                        Text(if (connectedCount > 0) "已连接 $connectedCount 个" else "精选服务和 MCP 官方市场", style = NeonType.caption().copy(fontSize = 13.sp))
                    }
                    Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkTertiary)
                }
            }
            ConnectorGroup.entries.forEach { g ->
                val rows = connectors.filter { it.group == g }
                if (rows.isEmpty()) return@forEach
                item(key = "h-${g.name}") { SectionLabel(g.label, Modifier.padding(top = 6.dp)) }
                // pending ones stand alone (dashed); settled ones share a card
                val pending = rows.filter { it.status is ConnectorStatus.Authorizing }
                val settled = rows - pending.toSet()
                if (settled.isNotEmpty()) item(key = "g-${g.name}") {
                    NeonCard(padding = PaddingValues(0.dp)) {
                        settled.forEachIndexed { i, r ->
                            ConnectorRow(r, onOpen, onAction)
                            if (i < settled.lastIndex) Box(Modifier.padding(start = 70.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
                        }
                    }
                }
                pending.forEach { r -> item(key = "p-${r.id}") { PendingConnector(r, onOpen) } }
            }
            item(key = "add") {
                Row(
                    Modifier.padding(top = 6.dp).fillMaxWidth().height(52.dp).clip(NeonShapes.card)
                        .dashedBorder(c.borderHairline, 20.dp).clickable(onClick = onAddMcp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.Plus, null, Modifier.size(17.dp), tint = c.inkSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("添加 MCP 服务器 URL", style = NeonType.label().copy(color = c.inkSecondary))
                }
            }
        }
    }
}

@Composable
private fun ConnectorBadge(r: ConnectorUi) {
    val c = Neon.colors
    Box(Modifier.size(40.dp).clip(NeonShapes.squircle).background(c.bgSubtle), contentAlignment = Alignment.Center) {
        if (r.monogram != null) Text(r.monogram, style = NeonType.bodyMedium().copy(fontSize = 17.sp))
        else Icon(r.icon ?: Lucide.Plug, null, Modifier.size(19.dp), tint = c.inkPrimary)
    }
}

@Composable
private fun ConnectorRow(r: ConnectorUi, onOpen: (ConnectorUi) -> Unit, onAction: (ConnectorUi) -> Unit) {
    val c = Neon.colors
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectorBadge(r)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = NeonType.cardTitle().copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (r.badge != null) { Spacer(Modifier.width(8.dp)); TagChip(r.badge) }
                }
                Spacer(Modifier.width(8.dp))
                when (val s = r.status) {
                    is ConnectorStatus.Connected -> CheckStatus(s.text)
                    is ConnectorStatus.Failed -> Text(s.reason, style = NeonType.chip().copy(color = c.danger), maxLines = 1)
                    else -> {}
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(r.sub, style = NeonType.caption().copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (r.action != null && r.status !is ConnectorStatus.Connected) {
            Spacer(Modifier.width(10.dp))
            PillButton(r.action, kind = ButtonKind.Secondary, height = 38.dp, onClick = { onAction(r) })
        }
    }
}

@Composable
private fun PendingConnector(r: ConnectorUi, onOpen: (ConnectorUi) -> Unit) {
    val c = Neon.colors
    val step = (r.status as ConnectorStatus.Authorizing).step
    Column(Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).dashedBorder(c.inkTertiary, 20.dp, 1.2.dp).clickable { onOpen(r) }) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 15.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            ConnectorBadge(r)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, style = NeonType.cardTitle().copy(fontSize = 16.sp))
                    if (r.badge != null) { Spacer(Modifier.width(8.dp)); TagChip(r.badge) }
                }
                Spacer(Modifier.height(3.dp))
                Text(r.sub, style = NeonType.caption().copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
        Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spinner(16.dp)
            Spacer(Modifier.width(10.dp))
            Text(step, style = NeonType.caption().copy(fontSize = 13.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            PillButton("授权中…", height = 36.dp, onClick = { onOpen(r) })
        }
    }
}

/** Quiet arc spinner; frozen at a nice angle in snapshots. */
@Composable
fun Spinner(size: Dp, modifier: Modifier = Modifier) {
    val c = Neon.colors
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "a")
    Canvas(modifier.size(size)) {
        val w = 2.dp.toPx()
        drawCircle(c.borderHairline, style = Stroke(w))
        drawArc(c.inkPrimary, a - 90f, 100f, false, style = Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}
