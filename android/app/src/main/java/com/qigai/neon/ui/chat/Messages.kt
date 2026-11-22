package com.qigai.neon.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.MessageEntity
import com.qigai.neon.data.plainText
import com.qigai.neon.markdown.MarkdownText
import com.qigai.neon.ui.components.ButtonKind
import com.qigai.neon.ui.components.PillButton
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonMotion
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.coroutines.delay

// ───────────────────────── user ─────────────────────────

@Composable
fun UserBubble(m: MessageEntity) {
    val blocks = remember(m.blocks) { BlocksJson.decode(m.blocks) }
    if (blocks.all { it is ContentBlock.ToolResult }) return // tool results are not user speech
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.End) {
        SelectionContainer {
            Text(
                blocks.plainText(), style = NeonType.body(),
                modifier = Modifier.clip(NeonShapes.userBubble).background(c.userBubble).padding(horizontal = 17.dp, vertical = 12.dp),
            )
        }
    }
}

// ───────────────────────── thinking accordion ─────────────────────────

/** Breathing dot (riff 03: 1200ms). */
@Composable
fun BreathingDot(color: Color = Neon.colors.inkPrimary, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(0.7f, 1.15f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).scale(s).clip(NeonShapes.capsule).background(color.copy(alpha = a)))
}

private val stepRegex = Regex("^\\s*(?:[-*•]|\\d+[.)、])\\s+(.*)$")

private sealed interface ThoughtLine {
    data class Step(val text: String) : ThoughtLine
    data class Para(val text: String) : ThoughtLine
    data class Label(val text: String) : ThoughtLine
}

private fun parseThought(src: String): List<ThoughtLine> {
    val out = mutableListOf<ThoughtLine>()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += ThoughtLine.Para(para.toString().trim()); para.clear() }
    src.lines().forEach { raw ->
        val line = raw.trimEnd()
        val step = stepRegex.find(line)
        when {
            line.isBlank() -> flush()
            step != null -> { flush(); out += ThoughtLine.Step(step.groupValues[1].replace("**", "")) }
            line.trim().let { it.length <= 12 && (it.endsWith("：") || it.endsWith(":")) } -> { flush(); out += ThoughtLine.Label(line.trim().trimEnd('：', ':')) }
            line.trimStart().startsWith("#") -> { flush(); out += ThoughtLine.Label(line.trimStart('#', ' ')) }
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line.trim()) }
        }
    }
    flush()
    return out
}

@Composable
fun ThinkingSection(text: String, durationMs: Long?, live: Boolean, liveStart: Long) {
    val c = Neon.colors
    var expanded by rememberSaveable { mutableStateOf(live) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) { while (live) { now = System.currentTimeMillis(); delay(100) } }
    val secs = ((if (live || durationMs == null) now - liveStart else durationMs) / 100) / 10.0
    val rot by animateFloatAsState(if (expanded) 0f else -90f, NeonMotion.thinking(), label = "chev")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(NeonShapes.item).clickable { expanded = !expanded }.padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                if (live) BreathingDot() else Icon(Lucide.Sparkles, null, Modifier.size(17.dp), tint = c.inkSecondary)
            }
            Spacer(Modifier.width(10.dp))
            val head = buildAnnotatedString {
                append(if (live) "思考中" else "已深度思考")
                if (live || durationMs != null) {
                    append("  ")
                    withStyle(SpanStyle(fontFamily = Neon.fonts.code)) { append("%.1fs".format(secs)) }
                }
            }
            Text(head, style = NeonType.tnum(NeonType.label().copy(color = c.inkSecondary)))
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronUp, null, Modifier.size(16.dp).rotate(rot), tint = c.inkTertiary)
        }
        AnimatedVisibility(
            expanded && text.isNotBlank(),
            enter = expandVertically(NeonMotion.thinking()) + fadeIn(),
            exit = shrinkVertically(NeonMotion.thinking()) + fadeOut(),
        ) {
            Row(Modifier.padding(top = 6.dp, start = 8.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(1.5.dp).fillMaxHeight().background(c.borderHairline))
                Spacer(Modifier.width(14.dp))
                val lines = remember(text) { parseThought(text) }
                val lastStep = lines.indexOfLast { it is ThoughtLine.Step }
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        lines.forEachIndexed { i, l ->
                            when (l) {
                                is ThoughtLine.Label -> Text(l.text, style = NeonType.overline(), modifier = Modifier.padding(top = if (i == 0) 0.dp else 6.dp))
                                is ThoughtLine.Para -> Text(l.text, style = NeonType.secondary())
                                is ThoughtLine.Step -> StepRow(l.text, active = live && i == lastStep)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(text: String, active: Boolean) {
    val c = Neon.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 3.dp).size(16.dp), contentAlignment = Alignment.Center) {
            if (active) {
                Box(Modifier.size(15.dp).clip(NeonShapes.capsule).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(7.dp).clip(NeonShapes.capsule).background(c.inkPrimary))
                }
            } else Icon(Lucide.Check, null, Modifier.size(15.dp), tint = c.inkSecondary)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = NeonType.secondary().copy(color = if (active) c.inkPrimary else c.inkSecondary))
    }
}

// ───────────────────────── tool chips + terminal peek ─────────────────────────

private fun toolSummary(inputJson: String): String {
    val obj = runCatching { com.qigai.neon.provider.Http.json.parseToJsonElement(inputJson) as? kotlinx.serialization.json.JsonObject }.getOrNull()
        ?: return ""
    val v = obj.values.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString() } ?: return ""
    return v.replace('\n', ' ').take(28)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolChips(tools: List<Pair<String, String>>, results: Map<String, ContentBlock.ToolResult>, ids: List<String>) {
    val c = Neon.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEachIndexed { i, (name, summary) ->
            val r = results[ids[i]]
            Row(
                Modifier.heightIn(min = 34.dp).clip(NeonShapes.capsule).background(c.bgSubtle).padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    r == null -> Box(Modifier.size(20.dp).clip(NeonShapes.capsule).border(1.5.dp, c.inkSecondary, NeonShapes.capsule), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Zap, null, Modifier.size(11.dp), tint = c.inkSecondary)
                    }
                    r.isError -> Box(Modifier.size(20.dp).clip(NeonShapes.capsule).background(c.danger), contentAlignment = Alignment.Center) {
                        Icon(Lucide.X, null, Modifier.size(12.dp), tint = Color.White)
                    }
                    else -> Box(Modifier.size(20.dp).clip(NeonShapes.capsule).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Check, null, Modifier.size(12.dp), tint = c.onAccent)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (summary.isBlank()) name else "$name  $summary",
                    style = NeonType.chip().copy(color = c.inkSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Riff 10 "Terminal Peek": a 44dp ink bar that opens into the full output. */
@Composable
fun TerminalPeek(command: String, output: String, isError: Boolean) {
    val c = Neon.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(NeonShapes.terminal).background(c.terminalBg)
            .clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", style = NeonType.code().copy(color = if (isError) c.danger else c.lime))
            Text(command, style = NeonType.code().copy(color = c.terminalInk), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(8.dp, 16.dp).background(c.lime))
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(18.dp).background(c.terminalDim.copy(alpha = 0.4f)))
            Spacer(Modifier.width(12.dp))
            Text(if (open) "收起" else "展开", style = NeonType.caption().copy(color = c.terminalDim))
        }
        AnimatedVisibility(open, enter = expandVertically(NeonMotion.terminal()) + fadeIn(), exit = shrinkVertically(NeonMotion.terminal()) + fadeOut()) {
            SelectionContainer {
                Text(
                    output.trimEnd().take(6000),
                    style = NeonType.code().copy(color = if (isError) c.danger else c.terminalInk.copy(alpha = 0.86f), fontSize = NeonType.mono().fontSize, lineHeight = NeonType.code().lineHeight),
                    modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                    softWrap = false,
                )
            }
        }
    }
}

// ───────────────────────── assistant ─────────────────────────

@Composable
fun AssistantMessage(
    m: MessageEntity,
    isLast: Boolean,
    busy: Boolean,
    toolResults: Map<String, ContentBlock.ToolResult>,
    onRegenerate: () -> Unit,
    showOps: Boolean = true,
    ops: OpsFold? = null,
    onOps: () -> Unit = {},
) {
    val c = Neon.colors
    val blocks = remember(m.blocks) { BlocksJson.decode(m.blocks) }
    val streaming = m.status == "streaming"
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ops != null) OpsFoldRow(ops, onOps)
        if (showOps && streaming && blocks.isEmpty()) ThinkingSection("", null, live = true, liveStart = m.createdAt)

        val toolUses = blocks.mapNotNull {
            when (it) {
                is ContentBlock.ToolUse -> Triple(it.id, it.name, it.inputJson)
                is ContentBlock.ServerToolUse -> Triple(it.id, it.name, it.inputJson)
                else -> null
            }
        }
        blocks.forEachIndexed { i, b ->
            when (b) {
                is ContentBlock.Thinking -> if (showOps) ThinkingSection(
                    b.thinking, b.durationMs,
                    live = streaming && blocks.drop(i + 1).none { it is ContentBlock.Text && it.text.isNotBlank() },
                    liveStart = m.createdAt,
                )
                is ContentBlock.Text -> if (b.text.isNotBlank()) MarkdownText(b.text)
                else -> Unit
            }
        }
        if (showOps && toolUses.isNotEmpty()) {
            ToolChips(toolUses.map { it.second to toolSummary(it.third) }, toolResults, toolUses.map { it.first })
            toolUses.lastOrNull()?.let { (id, name, input) ->
                toolResults[id]?.let { r -> TerminalPeek("$name ${toolSummary(input)}".trim(), r.content, r.isError) }
            }
        }
        if (m.status == "error") ErrorCard(m.error, canRetry = isLast && !busy, onRegenerate)
        if (!streaming && (showOps || isLast || blocks.any { it is ContentBlock.Text && it.text.isNotBlank() })) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val text = blocks.plainText()
                if (text.isNotBlank()) SmallAction(Lucide.Copy, "复制") { clip.setText(AnnotatedString(text)) }
                if (isLast && !busy && m.status != "error") SmallAction(Lucide.RotateCw, "重新生成", onRegenerate)
                Spacer(Modifier.width(6.dp))
                val meta = listOfNotNull(
                    m.model,
                    if (m.status == "stopped") "已停止" else null,
                    m.outputTokens?.let { "$it tokens" },
                ).joinToString("  ")
                Text(meta, style = NeonType.tnum(NeonType.overline()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The folded summary of one turn's thinking and tool calls. */
data class OpsFold(val steps: Int, val live: Boolean, val open: Boolean)

@Composable
private fun OpsFoldRow(f: OpsFold, onToggle: () -> Unit) {
    val c = Neon.colors
    val rot by animateFloatAsState(if (f.open) 0f else -90f, NeonMotion.thinking(), label = "fold")
    Row(
        Modifier.clip(NeonShapes.item).clickable(onClick = onToggle).padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (f.live) BreathingDot() else Icon(Lucide.ListChecks, null, Modifier.size(17.dp), tint = c.inkSecondary)
        }
        Spacer(Modifier.width(10.dp))
        val label = when {
            f.live && f.steps > 0 -> "正在执行第 ${f.steps} 步"
            f.live -> "思考中"
            f.steps > 0 -> "已执行 ${f.steps} 步操作"
            else -> "已深度思考"
        }
        Text(label, style = NeonType.tnum(NeonType.label().copy(color = c.inkSecondary)))
        Spacer(Modifier.width(6.dp))
        Icon(Lucide.ChevronUp, null, Modifier.size(16.dp).rotate(rot), tint = c.inkTertiary)
    }
}

@Composable
private fun ErrorCard(error: String?, canRetry: Boolean, onRetry: () -> Unit) {
    val c = Neon.colors
    Column(
        Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.dangerSoft)
            .border(1.dp, c.danger.copy(alpha = 0.25f), NeonShapes.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleAlert, null, Modifier.size(18.dp), tint = c.danger)
            Spacer(Modifier.width(8.dp))
            Text("请求失败", style = NeonType.label().copy(color = c.danger))
        }
        if (!error.isNullOrBlank()) Text(error, style = NeonType.caption().copy(color = c.inkSecondary))
        if (canRetry) PillButton("重试", kind = ButtonKind.Danger, icon = Lucide.RotateCw, height = 34.dp, onClick = onRetry)
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(NeonShapes.capsule).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(16.dp), tint = Neon.colors.inkTertiary)
    }
}

@Composable
fun ChatColumnWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().widthIn(max = 720.dp)) { content() }
}
