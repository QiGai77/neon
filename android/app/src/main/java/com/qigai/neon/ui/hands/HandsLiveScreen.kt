package com.qigai.neon.ui.hands

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.hands.HandsEngineMode
import com.qigai.neon.hands.NeonAccessibilityService
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandsLiveScreen(
    container: NeonContainer,
    onBack: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val context = LocalContext.current
    val accSvc by NeonAccessibilityService.instance.collectAsState()
    val engineMode by container.handsOperator.engineMode.collectAsState()
    val capsuleState by container.handsOperator.capsuleState.collectAsState()
    val stuckWarning by container.handsOperator.stuckWarning.collectAsState()
    val traces by container.db.handsTraceDao().observeRecent().collectAsState(initial = emptyList())

    val shizukuGranted = container.shizuku.hasPermission()

    Scaffold(
        containerColor = palette.bgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Hands 操控引擎", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bgCanvas),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 双引擎权限与切换卡
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("双引擎配置", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isA = engineMode == HandsEngineMode.ACCESSIBILITY
                        Button(
                            onClick = { container.handsOperator.setEngineMode(HandsEngineMode.ACCESSIBILITY) },
                            modifier = Modifier.weight(1f),
                            shape = NeonShapes.Control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isA) palette.inkPrimary else palette.bgSubtle,
                                contentColor = if (isA) Color.White else palette.inkPrimary,
                            ),
                        ) {
                            Text(if (accSvc != null) "无障碍 已开启" else "无障碍 未开启")
                        }

                        Button(
                            onClick = {
                                if (!shizukuGranted) container.shizuku.requestPermission()
                                container.handsOperator.setEngineMode(HandsEngineMode.SHIZUKU)
                            },
                            modifier = Modifier.weight(1f),
                            shape = NeonShapes.Control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isA) palette.inkPrimary else palette.bgSubtle,
                                contentColor = if (!isA) Color.White else palette.inkPrimary,
                            ),
                        ) {
                            Text(if (shizukuGranted) "Shizuku 已授权" else "Shizuku")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (accSvc == null) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                shape = NeonShapes.Control,
                            ) {
                                Text("前往开启无障碍服务")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (capsuleState.active) container.handsOperator.stopRun()
                                    else container.handsOperator.startRun()
                                },
                                shape = NeonShapes.Control,
                            ) {
                                Text(if (capsuleState.active) "关闭悬浮胶囊" else "显示悬浮胶囊")
                            }
                        }
                    }

                    Text(
                        if (stuckWarning) "连续 3 步页面未变化，建议人工接管" else "已记录 ${traces.size} 条轨迹",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stuckWarning) palette.dangerRed else palette.inkSecondary,
                    )
                }
            }

            Text("真实操控轨迹记录", style = MaterialTheme.typography.titleMedium)

            if (traces.isEmpty()) {
                Surface(
                    color = palette.bgSurface,
                    shape = NeonShapes.Card,
                    border = BorderStroke(1.dp, palette.borderHairline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "暂无操控轨迹。开启无障碍后，在对话中打开 Hands 开关即可实时记录每一步。",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(traces, key = { it.id }) { trace ->
                        Surface(
                            color = palette.bgSurface,
                            shape = NeonShapes.Control,
                            border = BorderStroke(1.dp, palette.borderHairline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${trace.stepIndex}. ${trace.actionType} · ${trace.targetLabel}", style = MaterialTheme.typography.labelMedium)
                                    Text("${trace.targetPackage} · ${trace.detail}", style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                                }
                                Surface(
                                    color = if (trace.status == "ok") palette.neonLimeSoft else palette.dangerSoft,
                                    shape = NeonShapes.Pill,
                                ) {
                                    Text(trace.status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
