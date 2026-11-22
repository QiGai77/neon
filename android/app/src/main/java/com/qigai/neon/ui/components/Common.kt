package com.qigai.neon.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qigai.neon.R
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass

// ───────────────────────── surfaces ─────────────────────────

/** Liquid glass card, radius 20: translucent fill, bright edge, faint sheen. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = NeonShapes.card,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.fillMaxWidth().glass(shape, fill = color)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/** Soft floating shadow used by the composer and the slash palette. */
fun Modifier.floating(shape: Shape, color: Color, elevation: Dp = 18.dp): Modifier =
    this.shadow(elevation, shape, ambientColor = color.copy(alpha = 0.10f), spotColor = color.copy(alpha = 0.14f))

@Composable
fun BlobLogo(size: Dp, modifier: Modifier = Modifier) {
    NeonOrb(size, modifier)
}

/** Rounded square that holds a line icon (drawer cards, settings rows, tiles). */
@Composable
fun IconSquircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    bg: Color = Neon.colors.bgSubtle,
    tint: Color = Neon.colors.inkPrimary,
) {
    Box(modifier.size(size).clip(NeonShapes.squircle).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(NeonShapes.capsule).background(color))
}

// ───────────────────────── chips ─────────────────────────

enum class ChipStyle { Outline, Soft, Active }

/**
 * 32dp capsule. Outline = hairline edge on canvas; Soft = subtle fill; Active = ink (light) / lime-tinted (dark).
 */
@Composable
fun NeonChip(
    text: String,
    modifier: Modifier = Modifier,
    style: ChipStyle = ChipStyle.Soft,
    icon: ImageVector? = null,
    mono: Boolean = false,
    height: Dp = 32.dp,
    textColor: Color? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Neon.colors
    val bg by animateColorAsState(
        when (style) { ChipStyle.Outline -> Color.Transparent; ChipStyle.Soft -> com.qigai.neon.ui.theme.Glass.tokens.tile; ChipStyle.Active -> c.chipActive },
        label = "chipBg",
    )
    val fg = textColor ?: when (style) { ChipStyle.Active -> c.onChipActive; ChipStyle.Soft -> c.inkSecondary; ChipStyle.Outline -> c.inkSecondary }
    val edge = when (style) { ChipStyle.Outline -> c.borderHairline; ChipStyle.Active -> c.chipActiveBorder; ChipStyle.Soft -> Color.Transparent }
    var m = modifier.heightIn(min = height).clip(NeonShapes.capsule).background(bg)
    if (edge.alpha > 0f) m = m.border(1.dp, edge, NeonShapes.capsule)
    if (onClick != null) m = m.clickable(role = Role.Button, onClick = onClick)
    Row(m.padding(horizontal = if (icon != null) 12.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = (if (mono) NeonType.mono() else NeonType.chip()).copy(color = fg),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}

// ───────────────────────── buttons ─────────────────────────

/** Filled circle: ink in light, lime in dark. Used for send / confirm. */
@Composable
fun AccentCircle(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Neon.colors
    val bg by animateColorAsState(if (enabled) c.accent else c.bgSubtle, label = "acc")
    val fg by animateColorAsState(if (enabled) c.onAccent else c.inkTertiary, label = "accFg")
    Box(
        modifier.size(size).clip(NeonShapes.capsule).background(bg).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(size * 0.52f), tint = fg) }
}

/** Hairline circle: "+" and "×". */
@Composable
fun OutlineCircle(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    tint: Color = Neon.colors.inkSecondary,
    onClick: () -> Unit,
) {
    val c = Neon.colors
    Box(
        modifier.size(size).clip(NeonShapes.capsule).border(1.dp, c.borderHairline, NeonShapes.capsule)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(size * 0.52f), tint = tint) }
}

/** Bare 44dp touch target with a 22dp line icon. */
@Composable
fun IconBtn(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    tint: Color = Neon.colors.inkPrimary,
    size: Dp = 22.dp,
    onClick: () -> Unit,
) {
    Box(modifier.size(44.dp).clip(NeonShapes.capsule).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, Modifier.size(size), tint = tint)
    }
}

enum class ButtonKind { Primary, Secondary, Danger }

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    height: Dp = 40.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Neon.colors
    val (bg, fg, edge) = when (kind) {
        ButtonKind.Primary -> Triple(if (enabled) c.accent else c.bgSubtle, if (enabled) c.onAccent else c.inkTertiary, Color.Transparent)
        ButtonKind.Secondary -> Triple(com.qigai.neon.ui.theme.Glass.tokens.tile, c.inkPrimary, c.borderHairline)
        ButtonKind.Danger -> Triple(c.bgSurface, c.danger, c.danger.copy(alpha = 0.45f))
    }
    var m = modifier.height(height).clip(NeonShapes.capsule).background(bg)
    if (edge.alpha > 0f) m = m.border(1.dp, edge, NeonShapes.capsule)
    Row(
        m.clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(17.dp), tint = fg)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = NeonType.label().copy(color = fg), maxLines = 1)
    }
}

/** 42×24 switch from S08: ink track (light) / lime track (dark), white or ink thumb. */
@Composable
fun NeonSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Neon.colors
    val track by animateColorAsState(if (checked) c.accent else c.borderHairline, label = "track")
    val thumb by animateColorAsState(if (checked) c.onAccent else c.bgSurface, label = "thumb")
    val x by animateDpAsState(if (checked) 20.dp else 2.dp, label = "x")
    Box(
        modifier.size(42.dp, 24.dp).clip(NeonShapes.capsule).background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Switch) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = x, y = 2.dp).size(20.dp).clip(NeonShapes.capsule).background(thumb))
    }
}

// ───────────────────────── text helpers ─────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = NeonType.overline().copy(fontSize = NeonType.caption().fontSize), modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = NeonType.tnum(NeonType.overline()))
    }
}

/** Big number + small unit, e.g. 81%, 23 封, 4.2 s. */
@Composable
fun NumberWithUnit(value: String, unit: String, style: TextStyle, unitStyle: TextStyle, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = NeonType.tnum(style))
        Spacer(Modifier.width(2.dp))
        Text(unit, style = unitStyle, modifier = Modifier.padding(bottom = 1.dp))
    }
}

/** Clean empty state with an optional one-tap action. */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NeonType.item(), textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = NeonType.caption(), textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            PillButton(action, onClick = onAction, height = 36.dp)
        }
    }
}

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
