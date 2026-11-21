package com.qigai.neon.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.R
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType

/** Markdown blocks (GFM subset). LaTeX is shown as raw source in a math box. */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Math(val tex: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class ListBlock(val ordered: Boolean, val items: List<Pair<Int, String>>, val start: Int) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    data object Rule : MdBlock()
}

object MdParser {
    private val ulRe = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val olRe = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val hRe = Regex("^(#{1,6})\\s+(.*)$")
    private val sepRe = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun cells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    fun parse(src: String): List<MdBlock> {
        val lines = src.replace("\r\n", "\n").split("\n")
        val out = mutableListOf<MdBlock>()
        var i = 0
        val para = StringBuilder()
        fun flushPara() { if (para.isNotBlank()) out += MdBlock.Paragraph(para.toString().trim()); para.setLength(0) }
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") || t.startsWith("~~~") -> {
                    flushPara()
                    val fence = t.take(3)
                    val lang = t.drop(3).trim()
                    val sb = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) { sb.append(lines[i]).append('\n'); i++ }
                    out += MdBlock.Code(lang, sb.toString().trimEnd('\n'))
                    i++
                    continue
                }
                t.startsWith("$$") -> {
                    flushPara()
                    val rest = t.removePrefix("$$")
                    if (rest.endsWith("$$") && rest.length >= 2) { out += MdBlock.Math(rest.removeSuffix("$$").trim()); i++; continue }
                    val sb = StringBuilder(rest)
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("$$")) { sb.append('\n').append(lines[i]); i++ }
                    if (i < lines.size) sb.append('\n').append(lines[i].trim().removeSuffix("$$"))
                    out += MdBlock.Math(sb.toString().trim())
                    i++
                    continue
                }
                t.isEmpty() -> flushPara()
                hRe.matches(t) -> { flushPara(); val m = hRe.find(t)!!; out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trimEnd('#', ' ')) }
                t.matches(Regex("^([-*_])\\s*(\\1\\s*){2,}$")) -> { flushPara(); out += MdBlock.Rule }
                t.startsWith(">") -> {
                    flushPara()
                    val sb = StringBuilder()
                    while (i < lines.size && lines[i].trim().startsWith(">")) { sb.append(lines[i].trim().removePrefix(">").trim()).append('\n'); i++ }
                    out += MdBlock.Quote(sb.toString().trim())
                    continue
                }
                t.contains("|") && i + 1 < lines.size && sepRe.matches(lines[i + 1]) -> {
                    flushPara()
                    val header = cells(t)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].contains("|") && lines[i].isNotBlank()) { rows += cells(lines[i]); i++ }
                    out += MdBlock.Table(header, rows)
                    continue
                }
                ulRe.matches(line) || olRe.matches(line) -> {
                    flushPara()
                    val ordered = olRe.matches(line) && !ulRe.matches(line)
                    val items = mutableListOf<Pair<Int, String>>()
                    var start = 1
                    var first = true
                    while (i < lines.size) {
                        val l = lines[i]
                        val u = ulRe.find(l)
                        val o = olRe.find(l)
                        if (u != null) items += (u.groupValues[1].length / 2) to u.groupValues[2]
                        else if (o != null) { if (first) start = o.groupValues[2].toIntOrNull() ?: 1; items += (o.groupValues[1].length / 2) to o.groupValues[3] }
                        else if (l.isNotBlank() && l.startsWith("  ") && items.isNotEmpty()) {
                            val last = items.removeAt(items.size - 1); items += last.first to (last.second + " " + l.trim())
                        } else break
                        first = false
                        i++
                    }
                    out += MdBlock.ListBlock(ordered, items, start)
                    continue
                }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
            }
            i++
        }
        flushPara()
        return out
    }
}

/** Inline: **bold**, *italic*, `code`, ~~strike~~, [text](url), <https://…>, $tex$ (raw). */
@Composable
fun mdInline(text: String, base: TextStyle): AnnotatedString {
    val c = Neon.colors
    val codeFont = Neon.fonts.code
    val bold = Neon.fonts.medium
    return remember(text, c, base) {
        buildAnnotatedString {
            var i = 0
            val n = text.length
            while (i < n) {
                val ch = text[i]
                when {
                    ch == '\\' && i + 1 < n -> { append(text[i + 1]); i += 2 }
                    ch == '`' -> {
                        val end = text.indexOf('`', i + 1)
                        if (end > i) {
                            withStyle(SpanStyle(fontFamily = codeFont, background = c.bgSubtle, fontSize = base.fontSize * 0.9f)) { append(" " + text.substring(i + 1, end) + " ") }
                            i = end + 1
                        } else { append(ch); i++ }
                    }
                    text.startsWith("**", i) || text.startsWith("__", i) -> {
                        val mark = text.substring(i, i + 2)
                        val end = text.indexOf(mark, i + 2)
                        if (end > i + 2) {
                            withStyle(SpanStyle(fontFamily = bold)) { append(inlinePlain(text.substring(i + 2, end))) }
                            i = end + 2
                        } else { append(mark); i += 2 }
                    }
                    text.startsWith("~~", i) -> {
                        val end = text.indexOf("~~", i + 2)
                        if (end > i + 2) { withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text.substring(i + 2, end)) }; i = end + 2 }
                        else { append("~~"); i += 2 }
                    }
                    (ch == '*' || ch == '_') && i + 1 < n && text[i + 1] != ' ' -> {
                        val end = text.indexOf(ch, i + 1)
                        val wordy = ch == '_' && i > 0 && text[i - 1].isLetterOrDigit()
                        if (end > i + 1 && !wordy) { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }; i = end + 1 }
                        else { append(ch); i++ }
                    }
                    ch == '[' -> {
                        val close = text.indexOf("](", i)
                        val end = if (close > 0) text.indexOf(')', close) else -1
                        if (close > i && end > close) {
                            val label = text.substring(i + 1, close)
                            val url = text.substring(close + 2, end).trim().substringBefore(' ')
                            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))) { append(label) }
                            i = end + 1
                        } else { append(ch); i++ }
                    }
                    ch == '<' && text.startsWith("<http", i) -> {
                        val end = text.indexOf('>', i)
                        if (end > i) {
                            val url = text.substring(i + 1, end)
                            withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)))) { append(url) }
                            i = end + 1
                        } else { append(ch); i++ }
                    }
                    ch == '$' && i + 1 < n && text[i + 1] != ' ' && text[i + 1] != '$' -> {
                        val end = text.indexOf('$', i + 1)
                        if (end > i + 1 && !text.substring(i + 1, end).contains('\n')) {
                            withStyle(SpanStyle(fontFamily = codeFont, color = c.inkSecondary)) { append(text.substring(i + 1, end)) }
                            i = end + 1
                        } else { append(ch); i++ }
                    }
                    else -> { append(ch); i++ }
                }
            }
        }
    }
}

private fun inlinePlain(s: String) = s.replace("`", "")

@Composable
fun MarkdownText(src: String, modifier: Modifier = Modifier) {
    val blocks = remember(src) { MdParser.parse(src) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { MdBlockView(it) }
    }
}

@Composable
private fun MdBlockView(b: MdBlock) {
    val c = Neon.colors
    val body = NeonType.body()
    when (b) {
        is MdBlock.Heading -> {
            val size = when (b.level) { 1 -> 22.sp; 2 -> 20.sp; 3 -> 18.sp; else -> 16.sp }
            val st = NeonType.bodyMedium().copy(fontSize = size, lineHeight = size * 1.4f)
            Text(mdInline(b.text, st), style = st)
        }
        is MdBlock.Paragraph -> Text(mdInline(b.text, body), style = body)
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.borderHairline))
            Spacer(Modifier.width(10.dp))
            val st = body.copy(color = c.inkSecondary)
            Text(mdInline(b.text, st), style = st)
        }
        is MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderHairline))
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            var num = b.start
            b.items.forEach { (depth, text) ->
                Row(Modifier.padding(start = (depth * 16).dp)) {
                    val marker = if (b.ordered) "${num++}." else "•"
                    Text(marker, style = NeonType.tnum(body).copy(color = c.inkSecondary), modifier = Modifier.widthIn(min = 22.dp))
                    val task = text.startsWith("[ ] ") || text.startsWith("[x] ") || text.startsWith("[X] ")
                    val shown = if (task) (if (text[1] == ' ') "☐ " else "☑ ") + text.drop(4) else text
                    Text(mdInline(shown, body), style = body)
                }
            }
        }
        is MdBlock.Code -> CodeBlock(b.lang, b.code)
        is MdBlock.Math -> Column(
            Modifier.fillMaxWidth().clip(NeonShapes.item).background(c.bgSubtle).padding(12.dp)
        ) {
            Text("LaTeX", style = NeonType.caption())
            Spacer(Modifier.height(4.dp))
            SelectionContainer { Text(b.tex, style = NeonType.code()) }
        }
        is MdBlock.Table -> MdTable(b)
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val c = Neon.colors
    val clip = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().clip(NeonShapes.item).border(1.dp, c.borderHairline, NeonShapes.item).background(c.bgSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().background(c.bgSubtle).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lang.ifEmpty { "code" }, style = NeonType.caption().copy(fontFamily = Neon.fonts.code), modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(NeonShapes.capsule).clickable { clip.setText(AnnotatedString(code)) }.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_copy), "复制代码", Modifier.size(14.dp), tint = c.inkSecondary)
                Spacer(Modifier.width(4.dp))
                Text("复制", style = NeonType.caption())
            }
        }
        SelectionContainer {
            Text(
                code, style = NeonType.code(),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                softWrap = false,
            )
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table) {
    val c = Neon.colors
    val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
    val st = NeonType.label()
    Box(Modifier.fillMaxWidth().clip(NeonShapes.item).border(1.dp, c.borderHairline, NeonShapes.item).horizontalScroll(rememberScrollState())) {
        Column {
            listOf(t.header).plus(t.rows).forEachIndexed { ri, row ->
                Row(Modifier.background(if (ri == 0) c.bgSubtle else c.bgSurface)) {
                    for (ci in 0 until cols) {
                        val cell = row.getOrElse(ci) { "" }
                        val s = if (ri == 0) st.copy(fontFamily = Neon.fonts.medium) else st
                        Text(mdInline(cell, s), style = s, modifier = Modifier.widthIn(min = 72.dp, max = 240.dp).padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
                if (ri < t.rows.size) Box(Modifier.height(1.dp).background(c.borderHairline).fillMaxWidth())
            }
        }
    }
}
