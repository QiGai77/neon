package com.qigai.neon.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass

/** Large page title row used by 速览, 空间 and 我的. */
@Composable
fun PageHeader(title: String, overline: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (overline != null) Text(overline, style = NeonType.caption())
            Text(title, style = NeonType.brand().copy(fontSize = 30.sp, lineHeight = 38.sp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** 40dp glass circle button. */
@Composable
fun GlassCircle(icon: ImageVector, desc: String, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).glass(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(size * 0.46f), tint = Neon.colors.inkPrimary) }
}

/** Filter / choice chip on glass. */
@Composable
fun GlassChip(text: String, active: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = Neon.colors
    val bg by animateColorAsState(if (active) c.chipActive.copy(alpha = if (c.isDark) 0.85f else 0.9f) else Glass.tokens.tile, label = "chip")
    val fg = if (active) c.onChipActive else c.inkSecondary
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(14.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(text, style = NeonType.chip().copy(color = fg))
    }
}

/** Segmented control: a tile track with a raised selected segment. */
@Composable
fun Segmented(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Neon.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Glass.tokens.tile).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) (if (c.isDark) Color.White.copy(alpha = 0.14f) else Color.White) else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = NeonType.chip().copy(color = if (on) c.inkPrimary else c.inkSecondary, fontFamily = if (on) Neon.fonts.medium else Neon.fonts.regular))
            }
        }
    }
}

/** Progress ring, 0..1, accent arc on a tile track. */
@Composable
fun Ring(progress: Float, size: Dp, stroke: Dp = 7.dp, content: @Composable () -> Unit = {}) {
    val c = Neon.colors
    val track = Glass.tokens.tile
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(520), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(c.accent, -90f, 360f * p, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}
