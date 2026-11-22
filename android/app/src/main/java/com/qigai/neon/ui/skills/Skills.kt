package com.qigai.neon.ui.skills

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

// ───────────────────────── S06 · 技能库 ─────────────────────────

data class SkillUi(
    val name: String,
    val trigger: String,
    val icon: ImageVector,
    val risk: Risk,
    val tags: List<String>,
)

/** A finished run the agent proposes to keep as a SKILL.md ("Learn-from-Run"). */
data class LearnedRunUi(
    val title: String,
    val source: String,
    val steps: List<String>,
    val note: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillsContent(
    skills: List<SkillUi>,
    learned: LearnedRunUi?,
    driveSynced: Int?,
    query: String,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (SkillUi) -> Unit,
    onDistill: () -> Unit,
    onViewTrace: () -> Unit,
    onAdd: () -> Unit = {},
    onMarket: (() -> Unit)? = null,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding()) {
        PageTopBar("技能库", onBack) { IconBtn(Lucide.Plus, "添加技能", onClick = onAdd) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") { SearchField(query, onQuery, "搜索技能") }
            if (onMarket != null) item(key = "market") {
                Row(
                    Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).border(1.dp, c.borderCard, NeonShapes.card)
                        .clickable(onClick = onMarket).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(NeonShapes.squircle).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Store, null, Modifier.size(19.dp), tint = c.onAccent)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("技能市场", style = NeonType.cardTitle().copy(fontSize = 16.sp))
                        Spacer(Modifier.height(3.dp))
                        Text("从 skills.sh 安装热门技能", style = NeonType.caption().copy(fontSize = 13.sp))
                    }
                    Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkTertiary)
                }
            }
            if (learned != null) item(key = "learned") { LearnedCard(learned, onDistill, onViewTrace) }
            item(key = "head") {
                Row(Modifier.padding(top = 4.dp)) {
                    SectionLabel("全部 ${skills.size}", Modifier.weight(1f))
                }
            }
            if (skills.isEmpty()) item(key = "empty") {
                NeonCard(padding = PaddingValues(22.dp)) {
                    EmptyState("还没有技能")
                }
            } else item(key = "list") {
                NeonCard(padding = PaddingValues(0.dp)) {
                    skills.forEachIndexed { i, s ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(14.dp), verticalAlignment = Alignment.Top) {
                            IconSquircle(s.icon, size = 40.dp, iconSize = 19.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.name, style = NeonType.cardTitle().copy(fontSize = 16.sp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("/${s.trigger}", style = NeonType.mono().copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                                    RiskLabel(s.risk)
                                }
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    s.tags.forEach { TagChip(it) }
                                }
                            }
                        }
                        if (i < skills.lastIndex) Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LearnedCard(l: LearnedRunUi, onDistill: () -> Unit, onViewTrace: () -> Unit) {
    val c = Neon.colors
    val edge = if (c.isDark) c.chipActiveBorder else c.inkPrimary
    Column(Modifier.fillMaxWidth().clip(NeonShapes.card).background(c.bgSurface).border(1.5.dp, edge, NeonShapes.card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(NeonShapes.squircle).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Lucide.WandSparkles, null, Modifier.size(19.dp), tint = c.onAccent)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(l.title, style = NeonType.cardTitle().copy(fontSize = 16.sp))
                Text(l.source, style = NeonType.caption())
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            l.steps.forEachIndexed { i, s ->
                TagChip("${i + 1} $s")
                if (i < l.steps.lastIndex) Icon(Lucide.ArrowRight, null, Modifier.padding(top = 6.dp).size(12.dp), tint = c.inkTertiary)
            }
        }
        if (l.note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(l.note, style = NeonType.caption().copy(fontSize = 13.sp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("沉淀为技能", icon = Lucide.Sparkles, height = 40.dp, onClick = onDistill)
            PillButton("查看轨迹", kind = ButtonKind.Secondary, height = 40.dp, onClick = onViewTrace)
        }
    }
}
