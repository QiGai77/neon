package com.qigai.neon.ui.guard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.qigai.neon.guard.DeviceCredential
import com.qigai.neon.guard.GuardDecision
import com.qigai.neon.guard.PendingGuardRequest
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SentinelSheet(
    request: PendingGuardRequest,
    onResolve: (GuardDecision) -> Unit,
) {
    val palette = LocalNeonPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bioError by remember { mutableStateOf<String?>(null) }
    var verified by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onResolve(GuardDecision.DENY) },
        containerColor = palette.bgSurface,
        shape = NeonShapes.BottomSheet,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = palette.dangerSoft,
                shape = NeonShapes.Pill,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Shield, null, tint = palette.dangerRed, modifier = Modifier.size(16.dp))
                    Text("等待确认", color = palette.dangerRed, style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(request.title, style = MaterialTheme.typography.titleLarge, color = palette.inkPrimary)
            Text(
                request.stepSummary,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary,
            )

            Surface(
                color = palette.bgCanvas,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("目标动作", style = MaterialTheme.typography.labelMedium, color = palette.inkSecondary)
                    Text(request.targetLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = palette.inkPrimary)
                    Text(request.targetPackage, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = palette.inkSecondary)

                    if (request.reasons.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            request.reasons.forEach { reason ->
                                Surface(color = palette.dangerSoft, shape = NeonShapes.Pill) {
                                    Text(
                                        reason,
                                        color = palette.dangerRed,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (request.requireBiometric) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (verified) palette.neonLimeSoft else palette.bgSubtle)
                            .border(1.5.dp, if (verified) palette.neonLime else palette.inkPrimary, CircleShape)
                            .clickable {
                                val act = context as? FragmentActivity
                                if (act != null && DeviceCredential.canAuthenticate(act)) {
                                    scope.launch {
                                        DeviceCredential.authenticate(act, request.title, request.targetLabel)
                                            .onSuccess {
                                                verified = true
                                                onResolve(GuardDecision.ALLOW_ONCE)
                                            }
                                            .onFailure { bioError = it.message }
                                    }
                                } else {
                                    verified = true
                                    onResolve(GuardDecision.ALLOW_ONCE)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (verified) Icons.Rounded.Check else Icons.Rounded.Fingerprint,
                            contentDescription = "生物识别确认",
                            tint = if (verified) palette.neonLime else palette.inkPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        bioError ?: "验证指纹以继续",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bioError != null) palette.dangerRed else palette.inkSecondary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onResolve(GuardDecision.DENY) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = NeonShapes.Control,
                    border = BorderStroke(1.dp, palette.dangerRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.dangerRed),
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("拒绝并中止")
                }

                Button(
                    onClick = {
                        onResolve(if (request.requireBiometric) GuardDecision.ALLOW_ONCE else GuardDecision.ALLOW_LEASE)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = NeonShapes.Control,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.inkPrimary, contentColor = Color.White),
                ) {
                    Text(if (request.requireBiometric) "允许一次" else "本次会话租约")
                }
            }
        }
    }
}
