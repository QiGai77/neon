package com.qigai.neon.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qigai.neon.R
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType

@Composable
fun NeonCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = Neon.colors
    Column(
        modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).border(1.dp, c.borderHairline, NeonShapes.card).padding(padding),
        content = content,
    )
}

@Composable
fun BlobLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.neon_blob), contentDescription = "Neon", modifier = modifier.size(size))
}

/** Capsule chip. filled = ink fill (active), otherwise subtle/outline. */
@Composable
fun Capsule(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    outlined: Boolean = false,
    icon: Painter? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Neon.colors
    val bg = if (filled) c.inkPrimary else if (outlined) Color.Transparent else c.bgSubtle
    val fg = if (filled) c.onInk else c.inkPrimary
    var m = modifier.clip(NeonShapes.capsule).background(bg)
    if (outlined) m = m.border(1.dp, c.borderHairline, NeonShapes.capsule)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = NeonType.label().copy(color = fg))
        trailing?.invoke(this)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(NeonShapes.capsule).background(color))
}

/** Clean empty state with a one-tap action ("去配置/去授权"). */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NeonType.bodyMedium(), textAlign = TextAlign.Center)
        if (subtitle != null) Text(subtitle, style = NeonType.secondary(), textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            PrimaryButton(action, onClick = onAction)
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Neon.colors
    Box(
        modifier.clip(NeonShapes.item).background(if (enabled) c.inkPrimary else c.borderHairline)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = NeonType.label().copy(color = if (enabled) c.onInk else c.inkSecondary, fontFamily = Neon.fonts.medium)) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val c = Neon.colors
    Box(
        modifier.clip(NeonShapes.item).border(BorderStroke(1.dp, if (danger) c.danger else c.borderHairline), NeonShapes.item)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = NeonType.label().copy(color = if (danger) c.danger else c.inkPrimary)) }
}

@Composable
fun IconBtn(res: Int, desc: String, modifier: Modifier = Modifier, tint: Color = Neon.colors.inkPrimary, size: Dp = 24.dp, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clip(NeonShapes.capsule).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(painterResource(res), desc, Modifier.size(size), tint = tint)
    }
}

fun formatClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
