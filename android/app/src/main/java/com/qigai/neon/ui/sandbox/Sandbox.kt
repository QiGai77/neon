package com.qigai.neon.ui.sandbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

// ───────────────────────── S07 · Alpine 沙箱 ─────────────────────────

enum class LineKind { PROMPT, OUT, OK, DIM, DANGER, STATUS }

/** One rendered terminal line. STATUS lines are "● name  state  transport". */
data class TermLine(val kind: LineKind, val text: String, val state: String = "", val transport: String = "", val live: Boolean = true)

data class ShellBlockUi(val command: String, val explanation: String)

@Composable
fun SandboxContent(
    title: String,
    subtitle: String,
    facts: List<String>,
    lines: List<TermLine>,
    input: String,
    onInput: (String) -> Unit,
    blocked: ShellBlockUi?,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onKeepBlocked: () -> Unit,
    onRunAnyway: () -> Unit,
    onKey: (String) -> Unit,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).height(48.dp)) {
            IconBtn(Lucide.ArrowLeft, "返回", Modifier.align(Alignment.CenterStart), onClick = onBack)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = NeonType.title())
                Text(subtitle, style = NeonType.overline())
            }
            IconBtn(Lucide.EllipsisVertical, "更多", Modifier.align(Alignment.CenterEnd), tint = c.inkSecondary, onClick = onMore)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            facts.forEachIndexed { i, f -> TagChip(f, active = i == 0) }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Terminal(lines, input, onInput, Modifier.weight(1f))
            AnimatedVisibility(blocked != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                if (blocked != null) ShellGuardCard(blocked, onKeepBlocked, onRunAnyway)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("ESC", "TAB", "CTRL", "ALT", "/", "-", "↑", "↓").forEach { k ->
                Box(
                    Modifier.weight(1f).height(36.dp).clip(NeonShapes.small).background(c.bgSurface).border(1.dp, c.borderCard, NeonShapes.small)
                        .clickable { onKey(k) },
                    contentAlignment = Alignment.Center,
                ) { Text(k, style = NeonType.mono().copy(color = c.inkPrimary, fontSize = 11.sp)) }
            }
        }
    }
}

@Composable
private fun Terminal(lines: List<TermLine>, input: String, onInput: (String) -> Unit, modifier: Modifier) {
    val c = Neon.colors
    val ink = c.terminalInk
    val dim = c.terminalDim
    val mono = NeonType.mono().copy(fontSize = 12.sp, lineHeight = 19.sp)
    Column(modifier.fillMaxWidth().clip(NeonShapes.terminal).background(c.terminalBg).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c.lime, 7.dp)
            Spacer(Modifier.width(8.dp))
            Text("neon@alpine · bash", style = mono.copy(color = ink), modifier = Modifier.weight(1f))
            Text("PRoot", style = mono.copy(color = dim))
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState(), reverseScrolling = true)) {
            lines.forEach { l ->
                when (l.kind) {
                    LineKind.PROMPT -> Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = c.lime)) { append("/workspace $ ") }
                        withStyle(SpanStyle(color = ink)) { append(l.text) }
                    }, style = mono)
                    LineKind.STATUS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● ", style = mono.copy(color = if (l.live) c.lime else dim))
                        Text(l.text, style = mono.copy(color = ink), modifier = Modifier.width(118.dp), maxLines = 1)
                        Text(l.state, style = mono.copy(color = if (l.live) c.lime else dim), modifier = Modifier.width(92.dp), maxLines = 1)
                        Text(l.transport, style = mono.copy(color = dim), maxLines = 1)
                    }
                    else -> Text(
                        l.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = mono.copy(color = when (l.kind) { LineKind.OK -> ink; LineKind.DANGER -> Color(0xFFFF6B6B); LineKind.DIM -> dim; else -> Color(0xFFBDBCB7) }),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("/workspace $ ", style = mono.copy(color = c.lime))
                Box(Modifier.weight(1f)) {
                    BasicTextField(input, onInput, singleLine = true, textStyle = mono.copy(color = ink), cursorBrush = SolidColor(c.lime), modifier = Modifier.fillMaxWidth())
                    if (input.isEmpty()) Box(Modifier.size(8.dp, 15.dp).background(c.lime))
                }
            }
        }
    }
}

@Composable
private fun ShellGuardCard(b: ShellBlockUi, onKeep: () -> Unit, onRun: () -> Unit) {
    val c = Neon.colors
    Column(
        Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).border(1.dp, c.danger.copy(alpha = 0.45f), NeonShapes.card).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Shield, null, Modifier.size(18.dp), tint = c.danger)
            Spacer(Modifier.width(8.dp))
            Text("ShellGuard 已拦截高危命令", style = NeonType.cardTitle().copy(fontSize = 16.sp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(NeonShapes.small).background(c.terminalBg).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(b.command, style = NeonType.code().copy(color = Color(0xFFFF6B6B)))
        }
        Spacer(Modifier.height(8.dp))
        Text(b.explanation, style = NeonType.caption().copy(fontSize = 13.sp, lineHeight = 19.sp))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("保持拦截", Modifier.weight(1f), height = 42.dp, onClick = onKeep)
            PillButton("我已了解，执行", Modifier.weight(1f), kind = ButtonKind.Danger, height = 42.dp, onClick = onRun)
        }
    }
}
