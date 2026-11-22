package com.qigai.neon.ui.hands

import com.qigai.neon.ui.theme.neonBackdrop
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
import com.qigai.neon.ui.components.*
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.settings.PageTopBar
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType

// ───────────────────────── S04 · Hands 实时操作 ─────────────────────────

/** Floating capsule shown over other apps while Hands drives the phone. */
@Composable
fun HandsCapsule(
    step: Int,
    total: Int,
    action: String,
    paused: Boolean,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Neon.colors
    val ink = Color(0xFF141415)
    Column(
        modifier.width(300.dp).floating(RoundedCornerShape(26.dp), Color.Black, 24.dp)
            .clip(RoundedCornerShape(26.dp)).background(ink).padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (paused) Color(0xFFF59A3A) else c.lime, 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "$step/$total  $action", style = NeonType.label().copy(color = Color(0xFFF2F1EC)),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(28.dp).clip(NeonShapes.capsule).clickable(onClick = onCollapse), contentAlignment = Alignment.Center) {
                Icon(Lucide.ChevronUp, "收起", Modifier.size(16.dp), tint = Color(0xFF8A8A8E))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1.25f).height(42.dp).clip(NeonShapes.capsule).background(Color(0xFF2A2A2C)).clickable(onClick = onPause),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (paused) Lucide.Play else Lucide.Pause, null, Modifier.size(15.dp), tint = Color(0xFFF2F1EC))
                Spacer(Modifier.width(6.dp))
                Text(if (paused) "继续执行" else "暂停并接管", style = NeonType.label().copy(color = Color(0xFFF2F1EC)))
            }
            Row(
                Modifier.weight(1f).height(42.dp).clip(NeonShapes.capsule).background(Color(0xFFFF5A52)).clickable(onClick = onStop),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                Spacer(Modifier.width(7.dp))
                Text("急停", style = NeonType.label().copy(color = Color.White))
            }
        }
    }
}

/** Set-of-Mark target: the node Hands is about to tap. */
@Composable
fun SomMarker(index: Int, modifier: Modifier = Modifier) {
    val c = Neon.colors
    Box(modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF101113).copy(alpha = 0.18f), radius = size.minDimension / 2)
            drawCircle(Color(0xFF101113), radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.6.dp.toPx()))
            drawCircle(c.lime, radius = 7.dp.toPx())
            drawCircle(Color(0xFF101113), radius = 7.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        Text("$index", style = NeonType.mono().copy(fontSize = 9.sp, color = Color(0xFF101113)), modifier = Modifier.offset(y = (-15).dp))
    }
}

enum class HandsEngine(val title: String, val sub: String) {
    ACCESSIBILITY("无障碍", ""),
    SHIZUKU("Shizuku", ""),
}

/** Bottom card under the live screen: engine switch + loop watchdog. */
@Composable
fun HandsEngineCard(
    engine: HandsEngine,
    available: Set<HandsEngine>,
    loopWatch: String,
    onEngine: (HandsEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Neon.colors
    Column(
        modifier.fillMaxWidth().floating(NeonShapes.sheet, c.shadow, 20.dp).clip(NeonShapes.sheet).background(c.bgSurface)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Hand, null, Modifier.size(18.dp), tint = c.inkPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Hands 引擎", style = NeonType.cardTitle().copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(NeonShapes.tile).background(c.bgSubtle).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HandsEngine.entries.forEach { e ->
                val on = e == engine
                val ok = e in available
                Row(
                    Modifier.weight(1f).clip(NeonShapes.item).background(if (on) c.bgSurface else Color.Transparent)
                        .then(if (on) Modifier.border(1.dp, c.borderCard, NeonShapes.item) else Modifier)
                        .clickable(enabled = ok) { onEngine(e) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = NeonType.label().copy(color = if (on) c.inkPrimary else c.inkSecondary), maxLines = 1)
                        if (!ok) Text("未授权", style = NeonType.overline())
                    }
                    if (on) StatusDot(c.accent, 8.dp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(c.live, 6.dp)
            Spacer(Modifier.width(8.dp))
            Text(loopWatch, style = NeonType.caption().copy(fontSize = 13.sp))
        }
    }
}

// ───────────────────────── S05 · Hands 任务 + Sentinel ─────────────────────────

data class GuardReason(val label: String, val value: String, val danger: Boolean = false)

data class HandsTaskUi(
    val title: String,
    val step: Int,
    val total: Int,
    /** non-null while Sentinel holds the task; seconds left before auto-deny */
    val blockedCountdown: Int?,
    val reasons: List<GuardReason>,
    val trace: List<String>,
)

@Composable
fun HandsTaskContent(task: HandsTaskUi, onBack: () -> Unit, onMore: () -> Unit) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp).height(48.dp)) {
            IconBtn(Lucide.ArrowLeft, "返回", Modifier.align(Alignment.CenterStart), onClick = onBack)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hands 任务", style = NeonType.title())
                Text("${task.title}  ${task.step}/${task.total}", style = NeonType.overline())
            }
            IconBtn(Lucide.EllipsisVertical, "更多", Modifier.align(Alignment.CenterEnd), tint = c.inkSecondary, onClick = onMore)
        }
        Column(Modifier.padding(horizontal = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (task.blockedCountdown != null) {
                Row(
                    Modifier.fillMaxWidth().clip(NeonShapes.tile).background(c.dangerSoft).border(1.dp, c.danger.copy(alpha = 0.35f), NeonShapes.tile)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Lucide.CircleAlert, null, Modifier.size(17.dp), tint = c.danger)
                    Spacer(Modifier.width(8.dp))
                    Text("等待确认", style = NeonType.label().copy(color = c.danger), modifier = Modifier.weight(1f))
                    if (task.blockedCountdown >= 0) Text("00:%02d".format(task.blockedCountdown), style = NeonType.tnum(NeonType.code()).copy(color = c.danger))
                }
            }
            NeonCard(padding = PaddingValues(16.dp)) {
                Text("触发原因", style = NeonType.cardTitle())
                Spacer(Modifier.height(10.dp))
                task.reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.label, style = NeonType.label().copy(color = c.inkSecondary), modifier = Modifier.weight(1f))
                        if (r.danger) Box(Modifier.clip(NeonShapes.capsule).background(c.dangerSoft).padding(horizontal = 9.dp, vertical = 3.dp)) {
                            Text(r.value, style = NeonType.caption().copy(color = c.danger))
                        } else Text(r.value, style = NeonType.label())
                    }
                }
            }
            if (task.trace.isNotEmpty()) {
                SectionLabel("执行轨迹")
                NeonCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    task.trace.forEachIndexed { i, t ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val done = i < task.step - 1
                            if (done) Icon(Lucide.Check, null, Modifier.size(15.dp), tint = c.inkSecondary)
                            else StatusDot(if (i == task.step - 1) c.danger else c.borderHairline, 8.dp, Modifier.padding(horizontal = 3.5.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(t, style = NeonType.label().copy(color = if (done) c.inkSecondary else c.inkPrimary))
                        }
                    }
                }
            }
        }
    }
}

data class SentinelUi(
    val title: String,
    val origin: String,
    val targetKind: String,
    val targetLabel: String,
    val targetPackage: String,
    val requireBiometric: Boolean,
)

/** Sheet body for the high-risk confirmation (goes inside NeonSheetHost). */
@Composable
fun SentinelSheetContent(s: SentinelUi, onDeny: () -> Unit, onLease: () -> Unit, onAltAuth: () -> Unit) {
    val c = Neon.colors
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ShieldAlert, null, Modifier.size(20.dp), tint = c.danger)
            Spacer(Modifier.width(8.dp))
            Text(s.title, style = NeonType.headline().copy(fontSize = 18.sp))
        }
        Spacer(Modifier.height(4.dp))
        Text(s.origin, style = NeonType.caption().copy(fontSize = 13.sp), modifier = Modifier.padding(start = 28.dp))
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(NeonShapes.tile).background(c.bgTile).border(1.dp, c.borderCard, NeonShapes.tile).padding(14.dp)) {
            Row {
                Text("目标控件", style = NeonType.overline(), modifier = Modifier.weight(1f))
                Text(s.targetKind, style = NeonType.overline())
            }
            Spacer(Modifier.height(6.dp))
            Text(s.targetLabel, style = NeonType.bodyMedium().copy(fontSize = 17.sp))
            Text(s.targetPackage, style = NeonType.mono())
        }
        if (s.requireBiometric) {
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(NeonShapes.capsule).border(1.dp, c.borderHairline, NeonShapes.capsule), contentAlignment = Alignment.Center) {
                    Icon(Lucide.Fingerprint, null, Modifier.size(32.dp), tint = c.inkPrimary)
                }
                Spacer(Modifier.height(10.dp))
                Text("验证指纹", style = NeonType.label())
                Row(Modifier.clip(NeonShapes.capsule).clickable(onClick = onAltAuth).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.ScanFace, null, Modifier.size(13.dp), tint = c.inkTertiary)
                    Spacer(Modifier.width(5.dp))
                    Text("人脸识别", style = NeonType.caption().copy(color = c.inkTertiary))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("拒绝", Modifier.weight(1f), kind = ButtonKind.Danger, icon = Lucide.X, height = 46.dp, onClick = onDeny)
            PillButton("本次会话允许", Modifier.weight(1f), icon = Lucide.Lock, height = 46.dp, onClick = onLease)
        }
    }
}
