package com.qigai.neon.ui.space

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.space.SpaceCard
import com.qigai.neon.space.SpaceTypes
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass

private val Shape = RoundedCornerShape(22.dp)
private val RailBlue = Color(0xFF2F6BFF)
private val SkyTeal = Color(0xFF14A3C7)
private val ParcelOrange = Color(0xFFF08A24)
private val NavGreen = Color(0xFF22B573)

fun typeIcon(type: String): ImageVector = when (type) {
    "train" -> Lucide.TrainFront
    "flight" -> Lucide.Plane
    "parcel" -> Lucide.Truck
    "nav" -> Lucide.Navigation
    "note" -> Lucide.Pencil
    "skills" -> Lucide.Sparkles
    "connectors" -> Lucide.Plug
    "sandbox" -> Lucide.SquareTerminal
    "memory" -> Lucide.Brain
    "tasks" -> Lucide.CalendarClock
    "hands" -> Lucide.Hand
    "drive" -> Lucide.Cloud
    "market" -> Lucide.Store
    else -> Lucide.LayoutGrid
}

@Composable
fun LifeCard(card: SpaceCard, modifier: Modifier, editing: Boolean, onFill: () -> Unit) {
    val d = card.data
    val hasData = d.values.any { it.isNotBlank() }
    if (!hasData) { EmptyLife(card, modifier, onFill); return }
    when (card.type) {
        "train" -> TicketCard(modifier, RailBlue, Lucide.TrainFront, d["number"].orEmpty(), d["date"], d["from"], d["to"], d["depart"], d["arrive"],
            listOfNotNull(d["seat"]?.let { "座位" to it }, d["gate"]?.let { "检票口" to it }), onFill)
        "flight" -> TicketCard(modifier, SkyTeal, Lucide.Plane, listOfNotNull(d["airline"], d["number"]).joinToString(" "), d["date"], d["from"], d["to"], d["depart"], d["arrive"],
            listOfNotNull(d["gate"]?.let { "登机口" to it }, d["seat"]?.let { "座位" to it }), onFill)
        "parcel" -> ParcelCard(d, modifier, onFill)
        "nav" -> NavCard(d, modifier, editing, onFill)
        else -> NoteCard(d, modifier, onFill)
    }
}

@Composable
private fun EmptyLife(card: SpaceCard, modifier: Modifier, onFill: () -> Unit) {
    val c = Neon.colors
    val t = SpaceTypes.of(card.type)
    Column(
        modifier.fillMaxHeight().heightIn(min = 120.dp).glass(Shape).clickable(role = Role.Button, onClick = onFill).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSquircle(typeIcon(card.type), size = 34.dp, iconSize = 17.dp, bg = Glass.tokens.tile, tint = c.inkPrimary)
            Spacer(Modifier.width(10.dp))
            Text(t?.title ?: card.type, style = NeonType.cardTitle())
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(10.dp))
        Text("点一下填写，或把短信、截图发给 Neon", style = NeonType.caption(), maxLines = 2)
    }
}

/** Train and flight: route with big times, a perforation, then seat and gate. */
@Composable
private fun TicketCard(
    modifier: Modifier, accent: Color, icon: ImageVector, title: String, date: String?,
    from: String?, to: String?, depart: String?, arrive: String?, extras: List<Pair<String, String>>, onClick: () -> Unit,
) {
    val c = Neon.colors
    Column(modifier.fillMaxHeight().glass(Shape).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(15.dp), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(title, style = NeonType.item(), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            date?.let { Text(it, style = NeonType.tnum(NeonType.caption())) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(depart ?: "--:--", style = NeonType.tnum(NeonType.headline().copy(fontSize = 26.sp, lineHeight = 30.sp)))
                Text(from ?: "", style = NeonType.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, Modifier.size(16.dp), tint = accent)
                Spacer(Modifier.height(4.dp))
                Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                    drawLine(accent.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(arrive ?: "--:--", style = NeonType.tnum(NeonType.headline().copy(fontSize = 26.sp, lineHeight = 30.sp)))
                Text(to ?: "", style = NeonType.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (extras.isNotEmpty()) {
            Canvas(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)) {
                drawLine(c.borderCard, Offset(0f, 0f), Offset(size.width, 0f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                extras.forEach { (k, v) ->
                    Column {
                        Text(k, style = NeonType.overline())
                        Text(v, style = NeonType.tnum(NeonType.item()))
                    }
                }
            }
        } else Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ParcelCard(d: Map<String, String>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().heightIn(min = 120.dp).glass(Shape).clickable(role = Role.Button, onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(ParcelOrange), contentAlignment = Alignment.Center) {
                Icon(Lucide.Truck, null, Modifier.size(16.dp), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(d["carrier"] ?: "快递", style = NeonType.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Text(d["status"] ?: "等待更新", style = NeonType.cardTitle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        d["eta"]?.let { Text(it, style = NeonType.caption().copy(color = ParcelOrange), maxLines = 1) }
        d["number"]?.let { Text(it, style = NeonType.tnum(NeonType.mono()), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun NavCard(d: Map<String, String>, modifier: Modifier, editing: Boolean, onEdit: () -> Unit) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val dest = d["address"] ?: d["title"].orEmpty()
    Column(
        modifier.fillMaxHeight().heightIn(min = 120.dp).glass(Shape).clickable(role = Role.Button) {
            if (editing) onEdit() else runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(dest))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }.padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(NavGreen), contentAlignment = Alignment.Center) {
                Icon(Lucide.Navigation, null, Modifier.size(15.dp), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            d["eta"]?.let { Text(it, style = NeonType.tnum(NeonType.label().copy(color = NavGreen))) }
        }
        Spacer(Modifier.height(12.dp))
        Text(d["title"] ?: dest, style = NeonType.cardTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        d["address"]?.takeIf { it != d["title"] }?.let { Text(it, style = NeonType.caption(), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        Text("出发", style = NeonType.label().copy(color = c.onChipActive),
            modifier = Modifier.clip(CircleShape).background(c.chipActive).padding(horizontal = 14.dp, vertical = 5.dp))
    }
}

@Composable
private fun NoteCard(d: Map<String, String>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().heightIn(min = 120.dp).glass(Shape).clickable(role = Role.Button, onClick = onClick).padding(14.dp)) {
        d["emoji"]?.let { Text(it, style = NeonType.headline().copy(fontSize = 26.sp, lineHeight = 30.sp)) }
        d["title"]?.let { Text(it, style = NeonType.cardTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        d["body"]?.let { Text(it, style = NeonType.caption(), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddCardSheet(current: List<SpaceCard>, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val c = Neon.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Glass.tokens.fillStrong.copy(alpha = 0.96f),
        scrimColor = c.scrim.copy(alpha = 0.35f),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("添加卡片", style = NeonType.headline())
            Spacer(Modifier.height(4.dp))
            Text("也可以在对话里让 Neon 帮你加、帮你排", style = NeonType.caption())
            listOf("生活" to true, "功能" to false).forEach { (label, life) ->
                Spacer(Modifier.height(18.dp))
                Text(label, style = NeonType.overline())
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                    SpaceTypes.all.filter { it.life == life }.forEach { t ->
                        val has = !t.life && current.any { it.type == t.id }
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Glass.tokens.tile)
                                .clickable(enabled = !has, role = Role.Button) { onAdd(t.id) }.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(typeIcon(t.id), null, Modifier.size(22.dp), tint = if (has) c.inkTertiary else c.inkPrimary)
                            Spacer(Modifier.height(6.dp))
                            Text(t.title, style = NeonType.label().copy(color = if (has) c.inkTertiary else c.inkPrimary), maxLines = 1)
                            if (has) Text("已在空间", style = NeonType.overline())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillCardSheet(card: SpaceCard, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val c = Neon.colors
    val t = SpaceTypes.of(card.type) ?: return
    val values = remember(card.id) { mutableStateMapOf<String, String>().apply { putAll(card.data) } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Glass.tokens.fillStrong.copy(alpha = 0.96f),
        scrimColor = c.scrim.copy(alpha = 0.35f),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text(t.title, style = NeonType.headline())
            Spacer(Modifier.height(14.dp))
            t.fields.forEach { (key, label) ->
                Text(label, style = NeonType.overline(), modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(Glass.tokens.tile).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        values[key].orEmpty(), { values[key] = it }, singleLine = true,
                        textStyle = NeonType.body(), cursorBrush = SolidColor(c.inkPrimary), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "保存", style = NeonType.item().copy(color = c.onChipActive),
                modifier = Modifier.align(Alignment.End).clip(CircleShape).background(c.chipActive)
                    .clickable { onSave(t.fields.associate { (k, _) -> k to values[k].orEmpty().trim() }) }.padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
    }
}
