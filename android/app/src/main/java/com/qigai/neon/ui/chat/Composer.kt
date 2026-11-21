package com.qigai.neon.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.qigai.neon.R
import com.qigai.neon.ui.components.Capsule
import com.qigai.neon.ui.components.EmptyState
import com.qigai.neon.ui.components.formatClock
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.voice.VoiceState
import kotlinx.coroutines.delay

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
) {
    val c = Neon.colors
    Column(modifier) {
        if (paletteOpen) {
            SkillPalette(Modifier.padding(bottom = 8.dp))
        }
        Column(
            Modifier.fillMaxWidth()
                .shadow(12.dp, NeonShapes.composer, ambientColor = c.inkPrimary.copy(alpha = 0.08f), spotColor = c.inkPrimary.copy(alpha = 0.08f))
                .clip(NeonShapes.composer).background(c.bgSurface).border(1.dp, c.borderHairline, NeonShapes.composer)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            val voiceMode = voice.recording || voice.transcribing
            AnimatedContent(voiceMode, label = "composer") { vm ->
                if (vm) VoiceBars(voice) else {
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 6.dp, vertical = 8.dp)) {
                        if (text.isEmpty()) Text("给 Neon 发消息", style = NeonType.body().copy(color = c.inkSecondary))
                        BasicTextField(
                            value = text, onValueChange = onText, maxLines = 6,
                            textStyle = NeonType.body(), cursorBrush = SolidColor(c.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoundOutlined(Icons.Default.Add, "附件", onPlus)
                    Capsule("/  技能", filled = paletteOpen, outlined = !paletteOpen) { onPalette(!paletteOpen) }
                    Capsule("深度思考", filled = deepThink, outlined = !deepThink, icon = painterResource(R.drawable.ic_sparkle)) { onDeepThink(!deepThink) }
                }
                if (voiceMode) {
                    RoundOutlined(Icons.Default.Close, "取消", onVoiceCancel)
                    RoundInk(enabled = voice.recording, onClick = onVoiceConfirm) {
                        Icon(Icons.Default.Check, "确认", tint = c.onInk)
                    }
                } else if (busy) {
                    RoundInk(onClick = onStop) { Icon(painterResource(R.drawable.ic_stop), "停止", Modifier.size(18.dp), tint = c.onInk) }
                } else {
                    if (text.isBlank()) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onMic), contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_mic), "语音输入", tint = c.inkPrimary)
                        }
                    }
                    RoundInk(enabled = text.isNotBlank(), onClick = onSend) {
                        Icon(painterResource(R.drawable.ic_send), "发送", tint = if (text.isNotBlank()) c.onInk else c.inkSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceBars(v: VoiceState) {
    val c = Neon.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(v.recording) { while (v.recording) { now = System.currentTimeMillis(); delay(200) } }
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (v.transcribing) {
            Text("转写中…", style = NeonType.label().copy(color = c.inkSecondary), modifier = Modifier.weight(1f))
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                v.levels.forEach { l ->
                    val h = (4 + l * 28).dp
                    Box(Modifier.width(3.dp).height(h).clip(CircleShape).background(if (l > 0.05f) c.inkPrimary else c.borderHairline))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(formatClock(now - v.startedAt), style = NeonType.tnum(NeonType.label().copy(fontFamily = Neon.fonts.code, color = c.inkSecondary)))
        }
    }
}

@Composable
private fun RoundOutlined(icon: ImageVector, desc: String, onClick: () -> Unit) {
    val c = Neon.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.borderHairline, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = c.inkPrimary) }
}

@Composable
private fun RoundInk(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = Neon.colors
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (enabled) c.inkPrimary else c.bgSubtle).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "/" skill palette. Skills (SKILL.md) arrive in M4 — until then a real, empty list. */
@Composable
private fun SkillPalette(modifier: Modifier = Modifier) {
    val c = Neon.colors
    Column(
        modifier.fillMaxWidth().clip(NeonShapes.composer).background(c.bgSurface).border(1.dp, c.borderHairline, NeonShapes.composer).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("技能", style = NeonType.title())
            Spacer(Modifier.width(10.dp))
            Text("共 0 个", style = NeonType.tnum(NeonType.secondary()))
        }
        Spacer(Modifier.height(16.dp))
        EmptyState(
            title = "还没有技能",
            subtitle = "技能库（SKILL.md 与 Drive 同步）将在后续版本接入；这里只显示真实存在的技能。",
        )
        Spacer(Modifier.height(8.dp))
    }
}
