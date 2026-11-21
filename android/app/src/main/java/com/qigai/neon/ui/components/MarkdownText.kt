package com.qigai.neon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalNeonPalette.current
    val clipboard = LocalClipboardManager.current
    val blocks = remember(markdown) { parseBlocks(markdown) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                    Text(
                        text = renderInline(block.text, palette.bgSubtle),
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkPrimary,
                    )
                }
                is MdBlock.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = palette.inkSecondary)
                        Text(
                            text = renderInline(block.text, palette.bgSubtle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkPrimary,
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = renderInline(block.text, palette.bgSubtle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.inkPrimary,
                    )
                }
                is MdBlock.CodeBlock -> {
                    Surface(
                        color = palette.terminalBg,
                        shape = NeonShapes.Control,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    block.lang.ifBlank { "text" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkSecondary,
                                )
                                IconButton(
                                    onClick = { clipboard.setText(AnnotatedString(block.code)) },
                                    modifier = Modifier.padding(0.dp),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制代码", tint = palette.terminalFg)
                                }
                            }
                            Text(
                                text = block.code,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.terminalFg,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseBlocks(raw: String): List<MdBlock> {
    val lines = raw.lines()
    val out = mutableListOf<MdBlock>()
    var i = 0
    val paraBuf = StringBuilder()

    fun flushPara() {
        val text = paraBuf.toString().trim()
        if (text.isNotEmpty()) out += MdBlock.Paragraph(text)
        paraBuf.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val codeBuf = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeBuf.appendLine(lines[i])
                i++
            }
            out += MdBlock.CodeBlock(lang, codeBuf.toString().trimEnd())
            i++
            continue
        }
        if (trimmed.startsWith("#")) {
            flushPara()
            val level = trimmed.takeWhile { it == '#' }.length
            out += MdBlock.Heading(level, trimmed.drop(level).trim())
            i++
            continue
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            flushPara()
            out += MdBlock.Bullet(trimmed.drop(2).trim())
            i++
            continue
        }
        if (trimmed.isEmpty()) {
            flushPara()
        } else {
            if (paraBuf.isNotEmpty()) paraBuf.append('\n')
            paraBuf.append(line)
        }
        i++
    }
    flushPara()
    return out
}

private fun renderInline(text: String, codeBg: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(text.substring(i + 2, end))
                    pop()
                    i = end + 2
                    continue
                }
            }
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg))
                    append(text.substring(i + 1, end))
                    pop()
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
