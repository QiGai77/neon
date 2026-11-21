package com.qigai.neon.ui.components

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
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import androidx.compose.ui.draw.drawBehind

/** Dashed rounded outline (pending connector, "add" slots). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp, dash: Dp = 5.dp, gap: Dp = 4.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

/** "✓ 已连接 · 9 工具" style trailing status. */
@Composable
fun CheckStatus(text: String, modifier: Modifier = Modifier, color: Color = Neon.colors.inkPrimary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Check, null, Modifier.size(15.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(text, style = NeonType.chip().copy(color = color), maxLines = 1)
    }
}

/** 24dp soft tag ("Gmail", "Alpine", "远程 MCP"). */
@Composable
fun TagChip(text: String, modifier: Modifier = Modifier, mono: Boolean = false, active: Boolean = false) {
    val c = Neon.colors
    Box(
        modifier.height(24.dp).clip(NeonShapes.capsule).background(if (active) c.chipActive else c.bgSubtle)
            .then(if (active && c.chipActiveBorder.alpha > 0f) Modifier.border(1.dp, c.chipActiveBorder, NeonShapes.capsule) else Modifier)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 1,
            style = (if (mono) NeonType.mono() else NeonType.caption()).copy(color = if (active) c.onChipActive else c.inkSecondary),
        )
    }
}

enum class Risk(val label: String) { LOW("低风险"), MEDIUM("中风险"), HIGH("高风险") }

@Composable
fun RiskLabel(risk: Risk, modifier: Modifier = Modifier) {
    val c = Neon.colors
    val color = when (risk) { Risk.LOW -> c.inkTertiary; Risk.MEDIUM -> c.warn; Risk.HIGH -> c.danger }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(color, 6.dp)
        Spacer(Modifier.width(5.dp))
        Text(risk.label, style = NeonType.caption().copy(color = if (risk == Risk.LOW) c.inkTertiary else color))
    }
}

/** 44dp search field on the tile colour. */
@Composable
fun SearchField(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val c = Neon.colors
    Row(
        modifier.fillMaxWidth().height(44.dp).clip(NeonShapes.capsule).background(c.bgSurface).border(1.dp, c.borderCard, NeonShapes.capsule)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Search, null, Modifier.size(17.dp), tint = c.inkTertiary)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, style = NeonType.label().copy(color = c.inkTertiary))
            BasicTextField(value, onValue, singleLine = true, textStyle = NeonType.label(), cursorBrush = SolidColor(c.inkPrimary), modifier = Modifier.fillMaxWidth())
        }
    }
}
