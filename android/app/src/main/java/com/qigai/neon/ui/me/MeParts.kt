package com.qigai.neon.ui.me

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.ui.home.GlassCircle
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass

/** The colour that marks a choice: lime on dark, blue on light (matches the v0.4.5 boards). */
@Composable
internal fun pickInk(): Color = if (Neon.colors.isDark) Neon.colors.lime else Neon.colors.onChipActive

/** Back circle, centred title, optional 完成 on the right. */
@Composable
internal fun SubPageBar(title: String, onBack: () -> Unit, done: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp)) {
        GlassCircle(Lucide.ChevronLeft, "返回", size = 44.dp, onClick = onBack)
        Text(title, style = NeonType.title().copy(fontFamily = Neon.fonts.medium), modifier = Modifier.align(Alignment.Center))
        if (done != null) Text(
            "完成", style = NeonType.title().copy(color = pickInk(), fontFamily = Neon.fonts.medium),
            modifier = Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = done).padding(horizontal = 6.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun GlassGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)), content = content)
}

@Composable
internal fun GroupDivider(start: Dp = 16.dp) {
    Box(Modifier.padding(start = start, end = 16.dp).fillMaxWidth().height(1.dp).background(Neon.colors.borderCard.copy(alpha = 0.7f)))
}

/** Label on the left, an inline single-line field on the right. */
@Composable
internal fun FieldRow(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    hint: String = "",
    labelWidth: Dp = 76.dp,
    clearable: Boolean = false,
) {
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = NeonType.item().copy(color = c.inkSecondary), modifier = Modifier.width(labelWidth))
        PlainField(value, onValue, hint, NeonType.item().copy(fontSize = 16.sp), Modifier.weight(1f), singleLine = true)
        if (clearable && value.isNotEmpty()) {
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button) { onValue("") }, contentAlignment = Alignment.Center) {
                Icon(Lucide.X, "清除", Modifier.size(16.dp), tint = c.inkSecondary)
            }
        }
    }
}

/** A borderless text field with a hint, styled like the surrounding text. */
@Composable
internal fun PlainField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    val c = Neon.colors
    BasicTextField(
        value = value, onValueChange = onValue, modifier = modifier,
        textStyle = style.copy(color = c.inkPrimary), singleLine = singleLine, minLines = minLines,
        cursorBrush = SolidColor(pickInk()),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = style.copy(color = c.inkTertiary), maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                inner()
            }
        },
    )
}

/** Small capsule chip; [on] fills it with the pick colour. */
@Composable
internal fun PickChip(text: String, on: Boolean, icon: ImageVector? = null, height: Dp = 34.dp, onClick: () -> Unit) {
    val c = Neon.colors
    val bg by animateColorAsState(if (on) c.chipActive else Glass.tokens.tile, label = "pick")
    val border = if (on) c.chipActiveBorder else c.borderCard.copy(alpha = if (c.isDark) 1f else 0.6f)
    Row(
        Modifier.height(height).clip(CircleShape).background(bg).border(1.dp, border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = if (on) pickInk() else c.inkPrimary); Spacer(Modifier.width(6.dp)) }
        Text(text, style = NeonType.label().copy(color = if (on) pickInk() else c.inkPrimary), maxLines = 1)
    }
}

/** Tone cell: a rounded tile in a 3-column grid. */
@Composable
internal fun ToneTile(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Neon.colors
    val bg by animateColorAsState(if (on) c.chipActive else Glass.tokens.tile, label = "tone")
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(18.dp)).background(bg)
            .border(1.dp, if (on) c.chipActiveBorder else c.borderCard.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NeonType.item().copy(fontSize = 16.sp, color = if (on) pickInk() else c.inkPrimary), textAlign = TextAlign.Center)
    }
}

/** Two-segment 问我 / 允许 switch used on 动手前 and 对话样式. */
@Composable
internal fun MiniSegmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Neon.colors
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Glass.tokens.tile).padding(3.dp)) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) (if (c.isDark) Color.White.copy(alpha = 0.14f) else Color.White) else Color.Transparent, label = "mseg")
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(11.dp)).background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = NeonType.label().copy(color = if (on) c.inkPrimary else c.inkSecondary, fontFamily = if (on) Neon.fonts.medium else Neon.fonts.regular))
            }
        }
    }
}

/** Icon, title (and optional subtitle), trailing slot. */
@Composable
internal fun GroupRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val c = Neon.colors
    var m = Modifier.fillMaxWidth().heightIn(min = 58.dp)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.inkPrimary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = NeonType.item().copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
internal fun ValueChevron(value: String?) {
    val c = Neon.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!value.isNullOrEmpty()) {
            Text(value, style = NeonType.tnum(NeonType.label().copy(color = c.inkSecondary)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkSecondary)
    }
}

@Composable
internal fun MeSection(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = NeonType.label().copy(color = Neon.colors.inkSecondary), modifier = Modifier.weight(1f))
        trailing()
    }
}
