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
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.fillMaxSize
import com.qigai.neon.R
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType

/** Markdown blocks (GFM subset). LaTeX is rendered with JLaTeXMath; raw source only when it does not parse. */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Math(val tex: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class ListBlock(val ordered: Boolean, val items: List<Pair<Int, String>>, val start: Int) : MdBlock()
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock()
    /** A paragraph that is only a link: shown as a card. */
    data class LinkCard(val url: String, val label: String?) : MdBlock()
    data object Rule : MdBlock()
}

object MdParser {
    private val ulRe = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val olRe = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
    private val hRe = Regex("^(#{1,6})\\s+(.*)$")
    private val linkOnly = Regex("""^\[([^\]]*)]\(([^)\s]+(?:\s+"[^"]*")?)\)[。.]?$""")
    private val bareUrl = Regex("""^<?(https?://\S+)>?$""")
    private val sepRe = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun cells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    fun parse(src: String): List<MdBlock> {
        val lines = src.replace("\r\n", "\n").split("\n")
        val out = mutableListOf<MdBlock>()
        var i = 0
        val para = StringBuilder()
        fun flushPara() {
            if (para.isNotBlank()) {
                val p = para.toString().trim()
                val link = linkOnly.matchEntire(p)
                val bare = bareUrl.matchEntire(p)
                out += when {
                    link != null -> MdBlock.LinkCard(link.groupValues[2].trim().substringBefore(' '), link.groupValues[1].trim().ifEmpty { null })
                    bare != null -> MdBlock.LinkCard(p.trim('<', '>'), null)
                    else -> MdBlock.Paragraph(p)
                }
            }
            para.setLength(0)
        }
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
                t.startsWith("\\[") -> {
                    flushPara()
                    val rest = t.removePrefix("\\[")
                    if (rest.endsWith("\\]")) { out += MdBlock.Math(rest.removeSuffix("\\]").trim()); i++; continue }
                    val sb = StringBuilder(rest)
                    i++
                    while (i < lines.size && !lines[i].trim().endsWith("\\]")) { sb.append('\n').append(lines[i]); i++ }
                    if (i < lines.size) sb.append('\n').append(lines[i].trim().removeSuffix("\\]"))
                    out += MdBlock.Math(sb.toString().trim())
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

/** Inline: **bold**, *italic*, `code`, ~~strike~~, [text](url), <https://…>; $tex$ stays raw here (see [MdText]). */
@Composable
fun mdInline(text: String, base: TextStyle): AnnotatedString = mdInlineRich(text, base, math = false).first

/** Text with inline markdown and rendered inline math. */
@Composable
fun MdText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val (str, tex) = mdInlineRich(text, style, math = true)
    if (tex.isEmpty()) { Text(str, style = style, modifier = modifier); return }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val color = style.color.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: Neon.colors.inkPrimary
    val px = with(density) { style.fontSize.toPx() }
    val inline = remember(tex, px, color) {
        tex.mapIndexedNotNull { k, t ->
            val bmp = MathRenderer.render(t, px, color.toArgb(), block = false) ?: return@mapIndexedNotNull null
            val w = (bmp.width / density.density / density.fontScale).sp
            val h = (bmp.height / density.density / density.fontScale).sp
            "m$k" to androidx.compose.foundation.text.InlineTextContent(
                androidx.compose.ui.text.Placeholder(w, h, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
            ) { androidx.compose.foundation.Image(bmp.asImageBitmap(), t, Modifier.fillMaxSize()) }
        }.toMap()
    }
    if (inline.size < tex.size) {
        // something did not parse: fall back to raw source for the whole line
        Text(mdInline(text, style), style = style, modifier = modifier); return
    }
    Text(str, style = style, modifier = modifier, inlineContent = inline)
}

@Composable
private fun mdInlineRich(text: String, base: TextStyle, math: Boolean): Pair<AnnotatedString, List<String>> {
    val c = Neon.colors
    val codeFont = Neon.fonts.code
    val bold = Neon.fonts.medium
    return remember(text, c, base, math) {
        val tex = mutableListOf<String>()
        fun androidx.compose.ui.text.AnnotatedString.Builder.mathSpan(t: String) {
            if (math) { appendInlineContent("m${tex.size}", t); tex += t }
            else withStyle(SpanStyle(fontFamily = codeFont, color = c.inkSecondary)) { append(t) }
        }
        buildAnnotatedString {
            var i = 0
            val n = text.length
            while (i < n) {
                val ch = text[i]
                when {
                    text.startsWith("\\(", i) && text.indexOf("\\)", i + 2) > i -> {
                        val end = text.indexOf("\\)", i + 2)
                        mathSpan(text.substring(i + 2, end).trim()); i = end + 2
                    }
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
                        if (end > i + 1 && !text.substring(i + 1, end).contains('\n') && !text.substring(i + 1, end).matches(Regex("""[\d.,\s]+"""))) {
                            mathSpan(text.substring(i + 1, end)); i = end + 1
                        } else { append(ch); i++ }
                    }
                    else -> { append(ch); i++ }
                }
            }
        } to tex.toList()
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

/** Renders already-parsed blocks; the bubble chat style splits text from link cards with this. */
@Composable
fun MarkdownBlocks(blocks: List<MdBlock>, modifier: Modifier = Modifier) {
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
            // Clear steps between levels: h1 and h2 get air above and a short accent tick, h4+ reads as a label.
            val (size, ink) = when (b.level) { 1 -> 21.sp to c.inkPrimary; 2 -> 18.5.sp to c.inkPrimary; 3 -> 16.5.sp to c.inkPrimary; else -> 15.sp to c.inkSecondary }
            val st = NeonType.bodyMedium().copy(fontSize = size, lineHeight = size * 1.35f, color = ink)
            if (b.level <= 2) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(size.value.dp * 0.9f).clip(NeonShapes.capsule).background(if (c.isDark) c.lime else c.inkPrimary))
                Spacer(Modifier.width(8.dp))
                MdText(b.text, st)
            } else MdText(b.text, st)
        }
        is MdBlock.Paragraph -> MdText(b.text, body)
        is MdBlock.LinkCard -> com.qigai.neon.ui.chat.LinkOrFileCard(b.url, b.label)
        is MdBlock.Quote -> {
            val callout = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\s*""", RegexOption.IGNORE_CASE).find(b.text)
            if (callout != null) {
                val kind = callout.groupValues[1].uppercase()
                val (label, tint) = when (kind) {
                    "TIP" -> "提示" to c.live
                    "IMPORTANT" -> "重要" to c.inkPrimary
                    "WARNING" -> "注意" to c.warn
                    "CAUTION" -> "警告" to c.danger
                    else -> "说明" to c.inkSecondary
                }
                Column(
                    Modifier.fillMaxWidth().clip(NeonShapes.item).background(tint.copy(alpha = 0.10f))
                        .border(1.dp, tint.copy(alpha = 0.28f), NeonShapes.item).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(label, style = NeonType.label().copy(fontFamily = Neon.fonts.medium, color = if (kind == "NOTE") c.inkSecondary else tint))
                    MdText(b.text.substring(callout.range.last + 1).trimStart(), body)
                }
            } else Row(Modifier.height(IntrinsicSize.Min).clip(NeonShapes.item).background(c.bgSubtle.copy(alpha = 0.5f))) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(c.inkTertiary))
                Spacer(Modifier.width(12.dp))
                MdText(b.text, body.copy(color = c.inkSecondary), )
                Spacer(Modifier.width(12.dp))
            }
        }
        is MdBlock.Rule -> Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(c.borderHairline))
        is MdBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var num = b.start
            b.items.forEach { (depth, text) ->
                Row(Modifier.padding(start = (depth * 18).dp)) {
                    val task = text.startsWith("[ ] ") || text.startsWith("[x] ") || text.startsWith("[X] ")
                    val marker = when {
                        task -> if (text[1] == ' ') "☐" else "☑"
                        b.ordered && depth == 0 -> "${num++}."
                        b.ordered -> "${num++})"
                        else -> when (depth % 3) { 0 -> "•"; 1 -> "◦"; else -> "▪" }
                    }
                    Text(
                        marker,
                        style = NeonType.tnum(body).copy(color = if (b.ordered && !task) c.inkPrimary else c.inkTertiary, fontFamily = if (b.ordered) Neon.fonts.medium else body.fontFamily),
                        modifier = Modifier.widthIn(min = if (b.ordered) 24.dp else 18.dp),
                    )
                    MdText(if (task) text.drop(4) else text, body)
                }
            }
        }
        is MdBlock.Code -> if (b.lang.equals("mermaid", true)) MermaidBlock(b.code) else CodeBlock(b.lang, b.code)
        is MdBlock.Math -> MathBlock(b.tex)
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
                Row(Modifier.background(if (ri == 0) c.bgSubtle else if (ri % 2 == 0) c.bgTile.copy(alpha = 0.5f) else c.bgSurface)) {
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

@Composable
private fun MathBlock(tex: String) {
    val c = Neon.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { (NeonType.body().fontSize * 1.1f).toPx() }
    val ink = c.inkPrimary.toArgb()
    val bmp = remember(tex, px, ink) { MathRenderer.render(tex, px, ink, block = true) }
    if (bmp == null) {
        Column(Modifier.fillMaxWidth().clip(NeonShapes.item).background(c.bgSubtle).padding(12.dp)) {
            SelectionContainer { Text(tex, style = NeonType.code()) }
        }
        return
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        val w = with(density) { bmp.width.toDp() }
        val h = with(density) { bmp.height.toDp() }
        androidx.compose.foundation.Image(bmp.asImageBitmap(), tex, Modifier.size(w, h))
    }
}
