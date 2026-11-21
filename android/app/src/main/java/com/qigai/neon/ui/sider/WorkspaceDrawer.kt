package com.qigai.neon.ui.sider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.provider.OpenCodeGoQuota
import com.qigai.neon.provider.QuotaWindow
import com.qigai.neon.sandbox.SandboxStatus
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch

@Composable
fun WorkspaceDrawer(
    container: NeonContainer,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val scope = rememberCoroutineScope()
    val sandboxStatus by container.rootfsManager.status.collectAsState()
    val googleEmail by container.tokenStore.googleEmail.collectAsState()
    val skills by container.skillRepository.skills.collectAsState()
    val memoryItems by container.memoryManager.items.collectAsState()
    val connectors by container.db.connectorDao().observeAll().collectAsState(initial = emptyList())
    val tasks by container.db.scheduledTaskDao().observeAll().collectAsState(initial = emptyList())
    val sessions by container.db.sessionDao().observeAll().collectAsState(initial = emptyList())

    var quota by remember { mutableStateOf<OpenCodeGoQuota?>(null) }

    fun refreshQuota() {
        scope.launch {
            quota = container.openCodeGo.fetchQuota(container.tokenStore.getOpenCodeGoKey())
        }
    }

    LaunchedEffect(Unit) {
        refreshQuota()
    }

    Surface(
        color = palette.bgCanvas,
        modifier = Modifier.width(326.dp).fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = palette.neonLime, modifier = Modifier.size(20.dp))
                    Text("Neon", style = MaterialTheme.typography.titleLarge, color = palette.inkPrimary)
                    Text("v0.3.0", style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                }
                IconButton(onClick = {
                    onCloseDrawer()
                    onNavigate("settings")
                }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = palette.inkPrimary)
                }
            }

            // OpenCode Go 真实额度卡片
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("OpenCode Go 额度", style = MaterialTheme.typography.titleMedium, color = palette.inkPrimary)
                            Text(
                                if (quota?.error == null) "直连无代理" else quota?.error.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (quota?.error == null) palette.neonLime else palette.dangerRed,
                            )
                        }
                        IconButton(onClick = { refreshQuota() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "刷新额度", modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val q = quota
                        QuotaRing(q?.fiveHour ?: QuotaWindow("5 小时", 0, "—"))
                        QuotaRing(q?.weekly ?: QuotaWindow("本周", 0, "—"))
                        QuotaRing(q?.monthly ?: QuotaWindow("本月", 0, "—"))
                    }
                }
            }

            // 离线 Linux 沙箱状态卡
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
                modifier = Modifier.fillMaxWidth().clickable {
                    onCloseDrawer()
                    onNavigate("sandbox")
                },
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(16.dp), tint = palette.inkPrimary)
                            Text("离线 Linux 沙箱", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("PRoot", style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                    }
                    val statusLabel = when (val s = sandboxStatus) {
                        is SandboxStatus.Ready -> "Alpine ${s.arch} 已就绪"
                        is SandboxStatus.Extracting -> "正在初始化"
                        is SandboxStatus.MissingBundle -> "缺少离线包"
                        is SandboxStatus.Error -> s.message
                        SandboxStatus.Checking -> "检查中"
                    }
                    Surface(
                        color = if (sandboxStatus is SandboxStatus.Ready) palette.neonLimeSoft else palette.bgSubtle,
                        shape = NeonShapes.Pill,
                    ) {
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Google Drive 工作区卡
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.CloudDone, null, modifier = Modifier.size(16.dp), tint = palette.inkPrimary)
                            Text("Google Drive 工作区", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            if (googleEmail != null) "已授权" else "未连接",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (googleEmail != null) palette.neonLime else palette.inkSecondary,
                        )
                    }
                    Text(
                        "我的云端硬盘/Neon/{对话,附件,备份,技能,记忆,任务}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkSecondary,
                    )
                }
            }

            // 2x2 快捷导航宫格
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerTile(
                    icon = Icons.Rounded.Extension,
                    title = "连接器中心",
                    subtitle = "${connectors.count { it.status == "connected" }} 个已连接",
                    modifier = Modifier.weight(1f),
                    onClick = { onCloseDrawer(); onNavigate("connectors") },
                )
                DrawerTile(
                    icon = Icons.Rounded.Bolt,
                    title = "技能库",
                    subtitle = "${skills.size} 个 SKILL.md",
                    modifier = Modifier.weight(1f),
                    onClick = { onCloseDrawer(); onNavigate("skills") },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrawerTile(
                    icon = Icons.Rounded.Psychology,
                    title = "长期记忆",
                    subtitle = "${memoryItems.size} 条记忆",
                    modifier = Modifier.weight(1f),
                    onClick = { onCloseDrawer(); onNavigate("memory") },
                )
                DrawerTile(
                    icon = Icons.Rounded.Schedule,
                    title = "定时任务",
                    subtitle = "${tasks.count { it.enabled }} 个运行中",
                    modifier = Modifier.weight(1f),
                    onClick = { onCloseDrawer(); onNavigate("memory") },
                )
            }

            DrawerTile(
                icon = Icons.Rounded.AutoAwesome,
                title = "Hands 操控",
                subtitle = "无障碍与 Shizuku",
                modifier = Modifier.fillMaxWidth(),
                onClick = { onCloseDrawer(); onNavigate("hands") },
            )

            if (sessions.isNotEmpty()) {
                Text("历史对话", style = MaterialTheme.typography.labelMedium, color = palette.inkSecondary)
                sessions.take(12).forEach { sess ->
                    val selected = sess.id == activeSessionId
                    Surface(
                        color = if (selected) palette.bgSubtle else palette.bgSurface,
                        shape = NeonShapes.Control,
                        border = BorderStroke(1.dp, if (selected) palette.inkPrimary else palette.borderHairline),
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelectSession(sess.id)
                            onCloseDrawer()
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                sess.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        container.db.messageDao().deleteBySession(sess.id)
                                        container.db.sessionDao().deleteById(sess.id)
                                    }
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "删除对话", modifier = Modifier.size(15.dp), tint = palette.inkSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = googleEmail ?: "未登录 Google 账号",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun QuotaRing(window: QuotaWindow) {
    val palette = LocalNeonPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(46.dp)) {
                drawArc(
                    color = palette.borderHairline,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = palette.inkPrimary,
                    startAngle = -90f,
                    sweepAngle = 360f * (window.remainingPercent / 100f),
                    useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Text("${window.remainingPercent}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(window.label, style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
    }
}

@Composable
private fun DrawerTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    Surface(
        color = palette.bgSurface,
        shape = NeonShapes.Control,
        border = BorderStroke(1.dp, palette.borderHairline),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = palette.inkPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = palette.inkPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
        }
    }
}
