package com.qigai.neon.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.qigai.neon.R
import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.MessageEntity
import com.qigai.neon.data.plainText
import com.qigai.neon.markdown.MarkdownText
import com.qigai.neon.ui.components.Capsule
import com.qigai.neon.ui.components.StatusDot
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.coroutines.delay

@Composable
fun UserBubble(m: MessageEntity) {
    val blocks = remember(m.blocks) { BlocksJson.decode(m.blocks) }
    if (blocks.all { it is ContentBlock.ToolResult }) return // tool results are not user speech
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.End) {
        SelectionContainer {
            Text(
                blocks.plainText(), style = NeonType.body(),
                modifier = Modifier.clip(NeonShapes.userBubble).background(c.bgSubtle).padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun PulseDot() {
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Box(Modifier.alpha(a)) { StatusDot(Neon.colors.neonLime) }
}

@Composable
fun ThinkingCard(text: String, durationMs: Long?, live: Boolean, liveStart: Long) {
    val c = Neon.colors
    var expanded by rememberSaveable { mutableStateOf(live) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) { while (live) { now = System.currentTimeMillis(); delay(100) } }
    val secs = ((if (live || durationMs == null) now - liveStart else (durationMs ?: 0L)) / 100) / 10.0
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "rot")
    Column {
        Row(
            Modifier.clip(NeonShapes.item).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) PulseDot() else Icon(painterResource(R.drawable.ic_sparkle), null, Modifier.size(18.dp), tint = c.inkSecondary)
            Spacer(Modifier.width(8.dp))
            Text(
                if (live) "思考中 · %.1fs".format(secs) else if (durationMs != null) "已深度思考 · %.1fs".format(secs) else "已深度思考",
                style = NeonType.tnum(NeonType.label().copy(color = c.inkSecondary)),
            )
            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp).rotate(rot), tint = c.inkSecondary)
        }
        AnimatedVisibility(expanded && text.isNotBlank()) {
            Row(Modifier.padding(top = 6.dp, start = 8.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(c.borderHairline))
                Spacer(Modifier.width(14.dp))
                SelectionContainer { Text(text.trim(), style = NeonType.secondary()) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantMessage(
    m: MessageEntity,
    isLast: Boolean,
    busy: Boolean,
    onRegenerate: () -> Unit,
) {
    val c = Neon.colors
    val blocks = remember(m.blocks) { BlocksJson.decode(m.blocks) }
    val streaming = m.status == "streaming"
    val clip = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (streaming && blocks.isEmpty()) ThinkingCard("", null, live = true, liveStart = m.createdAt)
        val tools = blocks.filter { it is ContentBlock.ToolUse || it is ContentBlock.ServerToolUse }
        blocks.forEachIndexed { i, b ->
            when (b) {
                is ContentBlock.Thinking -> ThinkingCard(
                    b.thinking, b.durationMs,
                    live = streaming && i == blocks.lastIndex, liveStart = m.createdAt,
                )
                is ContentBlock.Text -> if (b.text.isNotBlank()) MarkdownText(b.text)
                else -> Unit
            }
        }
        if (tools.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tools.forEach { t ->
                val name = when (t) { is ContentBlock.ToolUse -> t.name; is ContentBlock.ServerToolUse -> t.name; else -> "" }
                Capsule(name, icon = painterResource(R.drawable.ic_terminal))
            }
        }
        if (m.status == "error") {
            Column(
                Modifier.fillMaxWidth().clip(NeonShapes.item).background(c.dangerSoft).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("请求失败", style = NeonType.label().copy(color = c.danger, fontFamily = Neon.fonts.medium))
                Text(m.error ?: "", style = NeonType.label().copy(color = c.danger))
                if (isLast && !busy) Capsule("重试", outlined = true) { onRegenerate() }
            }
        }
        if (!streaming) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                val text = blocks.plainText()
                if (text.isNotBlank()) SmallAction(R.drawable.ic_copy, "复制") { clip.setText(AnnotatedString(text)) }
                if (isLast && !busy && m.status != "error") SmallAction(R.drawable.ic_refresh, "重新生成", onRegenerate)
                Spacer(Modifier.width(6.dp))
                val meta = listOfNotNull(
                    m.model,
                    if (m.status == "stopped") "已停止" else null,
                    m.outputTokens?.let { "$it tokens" },
                ).joinToString(" · ")
                Text(meta, style = NeonType.tnum(NeonType.caption()))
            }
        }
    }
}

@Composable
private fun SmallAction(res: Int, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(NeonShapes.capsule).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(res), desc, Modifier.size(16.dp), tint = Neon.colors.inkSecondary)
    }
}

@Composable
fun ChatColumnWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().widthIn(max = 720.dp)) { content() }
}
