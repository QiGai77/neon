package com.qigai.neon.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qigai.neon.ui.components.AccentCircle
import com.qigai.neon.ui.components.ChipStyle
import com.qigai.neon.ui.components.NeonChip
import com.qigai.neon.ui.components.OutlineCircle
import com.qigai.neon.ui.components.floating
import com.qigai.neon.ui.components.formatClock
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonMotion
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.voice.VoiceState
import kotlinx.coroutines.delay

/** One entry of the "/" palette. Skills are real SKILL.md files; the app passes none until they exist. */
data class PaletteSkill(val name: String, val trigger: String, val icon: ImageVector)

@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    busy: Boolean,
    deepThink: Boolean,
    onDeepThink: (Boolean) -> Unit,
    paletteOpen: Boolean,
    onPalette: (Boolean) -> Unit,
    voice: VoiceState,
    onMic: () -> Unit,
    onVoiceCancel: () -> Unit,
    onVoiceConfirm: () -> Unit,
    onPlus: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    skills: List<PaletteSkill> = emptyList(),
    onPickSkill: (PaletteSkill) -> Unit = {},
) {
    val c = Neon.colors
    val voiceMode = voice.recording || voice.transcribing
    Column(modifier) {
        AnimatedVisibility(
            paletteOpen && !voiceMode,
            enter = slideInVertically(NeonMotion.palette()) { it / 3 } + fadeIn(tween(160)),
            exit = slideOutVertically(NeonMotion.palette()) { it / 4 } + fadeOut(tween(120)),
        ) {
            val query = text.removePrefix("/").trim()
            SkillPalette(skills, query, onPickSkill, Modifier.padding(bottom = 10.dp))
        }
        Column(
            Modifier.fillMaxWidth()
                .floating(NeonShapes.composer, c.shadow)
                .clip(NeonShapes.composer).background(c.bgSurface).border(1.dp, c.borderCard, NeonShapes.composer)
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
        ) {
            AnimatedContent(
                voiceMode,
                transitionSpec = { (fadeIn(tween(180, delayMillis = 60)) + scaleIn(NeonMotion.composer(), 0.96f)) togetherWith fadeOut(tween(120)) },
                label = "composer",
            ) { v ->
                if (v) VoiceSpectrum(voice) else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 6.dp, vertical = 6.dp)) {
                        if (text.isEmpty()) Text(
                            if (paletteOpen) "输入技能名或触发词" else "给 Neon 发消息",
                            style = NeonType.body().copy(color = c.inkTertiary),
                        )
                        BasicTextField(
                            value = text, onValueChange = onText, maxLines = 6,
                            textStyle = NeonType.body(), cursorBrush = SolidColor(c.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlineCircle(Lucide.Plus, "附件", onClick = onPlus)
                    NeonChip(
                        "技能", style = if (paletteOpen) ChipStyle.Active else ChipStyle.Outline,
                        icon = Lucide.Slash, height = 34.dp,
                        onClick = { onPalette(!paletteOpen) },
                    )
                    NeonChip(
                        "深度思考", style = if (deepThink) ChipStyle.Active else ChipStyle.Outline,
                        icon = Lucide.Sparkles, height = 34.dp,
                        onClick = { onDeepThink(!deepThink) },
                    )
                }
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    when { voiceMode -> 2; busy -> 1; else -> 0 },
                    transitionSpec = { (fadeIn(tween(140)) + scaleIn(NeonMotion.composer(), 0.8f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.8f)) },
                    label = "actions",
                ) { mode ->
                    when (mode) {
                        2 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlineCircle(Lucide.X, "取消", onClick = onVoiceCancel)
                            AccentCircle(Lucide.Check, "完成", enabled = voice.recording, onClick = onVoiceConfirm)
                        }
                        1 -> AccentCircle(Lucide.Square, "停止", onClick = onStop)
                        else -> if (text.isBlank()) AccentCircle(Lucide.Mic, "语音输入", onClick = onMic)
                        else AccentCircle(Lucide.ArrowUp, "发送", onClick = onSend)
                    }
                }
            }
        }
    }
}

/** Riff 04: real mic levels as 40 bars; slots not yet filled stay hairline. */
@Composable
private fun VoiceSpectrum(v: VoiceState) {
    val c = Neon.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(v.recording) { while (v.recording) { now = System.currentTimeMillis(); delay(200) } }
    Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (v.transcribing) {
            BreathingDot()
            Spacer(Modifier.width(10.dp))
            Text("正在转写…", style = NeonType.label().copy(color = c.inkSecondary), modifier = Modifier.weight(1f))
        } else {
            val ink = c.inkPrimary
            val idle = c.borderHairline
            Canvas(Modifier.weight(1f).height(32.dp)) {
                val n = v.levels.size.coerceAtLeast(1)
                val bw = 2.4.dp.toPx()
                val step = size.width / n
                v.levels.forEachIndexed { i, l ->
                    val live = l > 0.02f
                    val h = (if (live) 6.dp.toPx() + l * (size.height - 6.dp.toPx()) else size.height * 0.42f)
                    val x = i * step + (step - bw) / 2
                    drawRoundRect(
                        if (live) ink else idle, Offset(x, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2, bw / 2),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Text(
                formatClock(now - v.startedAt),
                style = NeonType.tnum(NeonType.code().copy(color = c.inkSecondary, fontSize = NeonType.body().fontSize)),
            )
        }
    }
}

/** "/" palette (S01a). Riff 07: the card rises, tiles follow 30ms apart. */
@Composable
private fun SkillPalette(skills: List<PaletteSkill>, query: String, onPick: (PaletteSkill) -> Unit, modifier: Modifier = Modifier) {
    val c = Neon.colors
    val shown = remember(skills, query) {
        if (query.isBlank()) skills else skills.filter { it.name.contains(query, true) || it.trigger.contains(query, true) }
    }
    Column(
        modifier.fillMaxWidth().floating(NeonShapes.composer, c.shadow, 14.dp)
            .clip(NeonShapes.composer).background(c.bgSurface).border(1.dp, c.borderCard, NeonShapes.composer)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)
    ) {
        Row(Modifier.padding(start = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("技能", style = NeonType.bodyMedium())
            Spacer(Modifier.width(10.dp))
            Text(
                if (skills.isEmpty()) "SKILL.md" else "输入以筛选 · 共 ${skills.size} 个",
                style = NeonType.tnum(NeonType.caption().copy(color = c.inkTertiary)), modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            NeonChip("Drive 同步", style = ChipStyle.Soft, icon = Lucide.Cloud, height = 28.dp)
        }
        if (skills.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(NeonShapes.tile).background(c.bgTile).padding(vertical = 22.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Lucide.Book, null, Modifier.size(22.dp), tint = c.inkTertiary)
                Spacer(Modifier.height(8.dp))
                Text("还没有技能", style = NeonType.label())
                Spacer(Modifier.height(2.dp))
                Text("技能是 Drive /Neon/技能/ 里的 SKILL.md，接入后会出现在这里", style = NeonType.caption().copy(color = c.inkTertiary))
            }
        } else if (shown.isEmpty()) {
            Text("没有匹配「$query」的技能", style = NeonType.caption(), modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        } else {
            shown.take(6).chunked(2).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = if (r < 2) 8.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { k, s ->
                        SkillTile(s, selected = r == 0 && k == 0 && query.isNotBlank(), index = r * 2 + k, modifier = Modifier.weight(1f)) { onPick(s) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SkillTile(s: PaletteSkill, selected: Boolean, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = Neon.colors
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(index * NeonMotion.STAGGER_MS.toLong()); appear.animateTo(1f, NeonMotion.palette()) }
    val edge by animateFloatAsState(if (selected) 1f else 0f, label = "edge")
    val edgeColor = if (c.isDark) c.chipActiveBorder else c.inkPrimary
    Row(
        modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 16.dp.toPx() }
            .clip(NeonShapes.tile).background(c.bgTile)
            .border(1.5.dp, edgeColor.copy(alpha = edgeColor.alpha * edge), NeonShapes.tile)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(NeonShapes.small).background(if (c.isDark) c.bgCanvas else c.bgSurface),
            contentAlignment = Alignment.Center,
        ) { Icon(s.icon, null, Modifier.size(18.dp), tint = c.inkPrimary) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(s.name, style = NeonType.item(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("/" + s.trigger, style = NeonType.mono(), maxLines = 1)
        }
    }
}
