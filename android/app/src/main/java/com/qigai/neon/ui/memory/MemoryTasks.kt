package com.qigai.neon.ui.memory

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

// ───────────────────────── S08 · 记忆与任务 ─────────────────────────

data class ReceiptUi(val title: String, val ok: Boolean, val metrics: List<Triple<String, String, String>>)

data class TaskUi(
    val id: String,
    val name: String,
    val schedule: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val receipt: ReceiptUi? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryTasksContent(
    facts: List<String>,
    memorySynced: Boolean,
    tasks: List<TaskUi>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEditMemory: () -> Unit,
    onRemoveFact: (String) -> Unit,
    onAddFact: () -> Unit,
    onToggle: (TaskUi, Boolean) -> Unit,
    onOpenReceipt: (TaskUi) -> Unit,
    onRerun: (TaskUi) -> Unit,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding()) {
        PageTopBar("记忆与任务", onBack) { IconBtn(Lucide.Plus, "新建任务", onClick = onAdd) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "mem") {
                NeonCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconSquircle(Lucide.FileText, size = 40.dp, iconSize = 19.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("memory.md", style = NeonType.code().copy(fontSize = 15.sp))
                                if (memorySynced) {
                                    Spacer(Modifier.width(8.dp))
                                    Row(Modifier.clip(NeonShapes.capsule).background(c.bgSubtle).padding(horizontal = 7.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Lucide.Cloud, null, Modifier.size(11.dp), tint = c.inkSecondary)
                                        Spacer(Modifier.width(3.dp))
                                        Text("已同步", style = NeonType.overline().copy(color = c.inkSecondary))
                                    }
                                }
                            }
                            Text("每次对话前自动载入 · 可编辑或遗忘", style = NeonType.caption())
                        }
                        IconBtn(Lucide.Pencil, "编辑", tint = c.inkSecondary, size = 18.dp, onClick = onEditMemory)
                    }
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        facts.forEach { f ->
                            NeonChip(f, style = ChipStyle.Soft, height = 32.dp, textColor = c.inkPrimary) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Lucide.X, "遗忘", Modifier.size(13.dp).clip(NeonShapes.capsule).clickable { onRemoveFact(f) }, tint = c.inkSecondary)
                            }
                        }
                        NeonChip("添加", style = ChipStyle.Outline, icon = Lucide.Plus, height = 32.dp, onClick = onAddFact)
                    }
                }
            }
            item(key = "taskHead") {
                SectionLabel("定时任务 · WorkManager", Modifier.padding(top = 8.dp), trailing = "${tasks.size} 个")
            }
            if (tasks.isEmpty()) item(key = "noTasks") {
                NeonCard(padding = PaddingValues(22.dp)) { EmptyState("还没有定时任务", "在对话里说「每天早上 8 点给我早报」就能建一个") }
            }
            items(tasks, key = { it.id }) { t -> TaskCard(t, onToggle, onOpenReceipt, onRerun) }
        }
    }
}

@Composable
private fun TaskCard(t: TaskUi, onToggle: (TaskUi, Boolean) -> Unit, onOpen: (TaskUi) -> Unit, onRerun: (TaskUi) -> Unit) {
    val c = Neon.colors
    NeonCard(padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconSquircle(t.icon, size = 40.dp, iconSize = 19.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = NeonType.cardTitle().copy(fontSize = 16.sp, color = if (t.enabled) c.inkPrimary else c.inkSecondary))
                Text(t.schedule, style = NeonType.tnum(NeonType.caption()))
            }
            NeonSwitch(t.enabled, { onToggle(t, it) })
        }
        val r = t.receipt
        if (r != null) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).fillMaxWidth().clip(NeonShapes.tile).background(c.bgTile).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(NeonShapes.capsule).background(if (r.ok) c.accent else c.danger), contentAlignment = Alignment.Center) {
                        Icon(if (r.ok) Lucide.Check else Lucide.X, null, Modifier.size(12.dp), tint = if (r.ok) c.onAccent else Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(r.title, style = NeonType.label())
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    r.metrics.forEach { (label, v, unit) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = NeonType.overline())
                            Spacer(Modifier.height(2.dp))
                            NumberWithUnit(v, unit, NeonType.headline().copy(fontSize = 22.sp), NeonType.caption())
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("在对话中打开", Modifier.weight(1f), kind = ButtonKind.Secondary, height = 38.dp, onClick = { onOpen(t) })
                    PillButton("立即重跑", Modifier.weight(1f), kind = ButtonKind.Secondary, icon = Lucide.RotateCw, height = 38.dp, onClick = { onRerun(t) })
                }
            }
        }
    }
}
