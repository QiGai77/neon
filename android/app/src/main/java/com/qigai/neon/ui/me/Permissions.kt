package com.qigai.neon.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.MainViewModel
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.guard.ActionType
import com.qigai.neon.guard.ApprovalMode
import com.qigai.neon.guard.ApprovalSettings
import com.qigai.neon.ui.components.NeonSwitch
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop

/** 动手前: changes apply at once, there is no 完成. */
@Composable
fun PermissionsRoute(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val a = s.approval
    PermissionsContent(
        a, s.opsCollapsed, onBack,
        onMode = { vm.putSetting(SettingsRepository.APPROVAL_MODE, it.key) },
        onPin = { type, ask -> vm.putSetting(SettingsRepository.APPROVAL_OVERRIDES, SettingsRepository.encodeOverrides(a.overrides + (type to ask))) },
        onBiometric = { vm.putSetting(SettingsRepository.CONFIRM_BIOMETRIC, if (it) "1" else "0") },
        onOps = { vm.putSetting(SettingsRepository.OPS_COLLAPSED, if (it) "1" else "0") },
    )
}

private val modeSubs = mapOf(
    ApprovalMode.ALWAYS_ASK to "写文件、发消息、打开 App 都先问你",
    ApprovalMode.IMPORTANT to "查和读直接做，发送、删除、付款先问",
    ApprovalMode.ALWAYS_ALLOW to "不再弹确认，Neon 直接动手",
)

private val typeIcons = mapOf(
    ActionType.PAYMENT to Lucide.CreditCard,
    ActionType.DELETE to Lucide.Trash2,
    ActionType.SEND to Lucide.Send,
    ActionType.HANDS to Lucide.Smartphone,
)

@Composable
fun PermissionsContent(
    a: ApprovalSettings,
    opsCollapsed: Boolean,
    onBack: () -> Unit,
    onMode: (ApprovalMode) -> Unit = {},
    onPin: (ActionType, Boolean) -> Unit = { _, _ -> },
    onBiometric: (Boolean) -> Unit = {},
    onOps: (Boolean) -> Unit = {},
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding()) {
        SubPageBar("动手前", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ApprovalMode.entries.forEach { m -> ModeCard(m, a.mode == m) { onMode(m) } }
            if (a.mode == ApprovalMode.ALWAYS_ALLOW) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.warn.copy(alpha = if (c.isDark) 0.14f else 0.12f))
                        .border(1.dp, c.warn.copy(alpha = 0.45f), RoundedCornerShape(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 2.dp).size(20.dp), tint = c.warn)
                    Spacer(Modifier.width(12.dp))
                    Text("Neon 会直接发消息、删文件、操作 App。每一步仍会记在对话里，随时能停。", style = NeonType.item().copy(lineHeight = 22.sp))
                }
            }
            MeSection("这些还是问我")
            GlassGroup {
                ActionType.pinnable.forEachIndexed { i, t ->
                    if (i > 0) GroupDivider()
                    GroupRow(typeIcons.getValue(t), t.label) {
                        MiniSegmented(listOf("问我", "允许"), if (a.asks(t)) 0 else 1, onSelect = { onPin(t, it == 0) })
                    }
                }
            }
            GlassGroup {
                ToggleGroupRow(Lucide.Fingerprint, "问我时用指纹确认", null, a.biometric, onBiometric)
                GroupDivider()
                ToggleGroupRow(Lucide.Layers, "思考和命令收成一行", "点按展开每一步", opsCollapsed, onOps)
            }
        }
    }
}

@Composable
private fun ToggleGroupRow(icon: ImageVector, title: String, sub: String?, on: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.clickable(role = Role.Switch) { onChange(!on) }) {
        GroupRow(icon, title, sub) { NeonSwitch(on, onChange) }
    }
}

@Composable
private fun ModeCard(m: ApprovalMode, on: Boolean, onClick: () -> Unit) {
    val c = Neon.colors
    val shape = RoundedCornerShape(26.dp)
    var mod = Modifier.fillMaxWidth().clip(shape).glass(shape)
    if (on) mod = mod.border(1.5.dp, if (c.isDark) c.lime else c.inkPrimary, shape)
    Row(
        mod.clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(if (on) Modifier.background(if (c.isDark) c.lime else c.inkPrimary) else Modifier.border(1.5.dp, c.inkTertiary, CircleShape)),
            contentAlignment = Alignment.Center,
        ) { if (on) Box(Modifier.size(9.dp).clip(CircleShape).background(if (c.isDark) c.bgCanvas else c.bgSurface)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.label, style = NeonType.bodyMedium().copy(fontSize = 17.sp))
                if (m == ApprovalMode.IMPORTANT) {
                    Spacer(Modifier.width(8.dp))
                    Text("默认", style = NeonType.caption(), modifier = Modifier.clip(CircleShape).background(com.qigai.neon.ui.theme.Glass.tokens.tile).padding(horizontal = 9.dp, vertical = 3.dp))
                }
            }
            Text(modeSubs.getValue(m), style = NeonType.label().copy(color = c.inkSecondary))
        }
    }
}
