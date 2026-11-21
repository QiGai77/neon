package com.qigai.neon.ui.sider

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.BuildConfig
import com.qigai.neon.MainViewModel
import com.qigai.neon.QuotaState
import com.qigai.neon.R
import com.qigai.neon.data.ConversationEntity
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.provider.GoWindow
import com.qigai.neon.sysfiles.DriveClient
import com.qigai.neon.ui.components.BlobLogo
import com.qigai.neon.ui.components.Capsule
import com.qigai.neon.ui.components.IconBtn
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.PrimaryButton
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SiderDrawer(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onConnectGo: () -> Unit,
    onAuthorizeDrive: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    val c = Neon.colors
    val convs by vm.conversations.collectAsState()
    val current by vm.currentId.collectAsState()
    val s by vm.settings.collectAsState()
    Column(Modifier.fillMaxSize().background(c.bgCanvas).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BlobLogo(30.dp)
            Spacer(Modifier.width(10.dp))
            Text("Neon", style = NeonType.headline().copy(fontSize = 26.sp))
            Spacer(Modifier.width(8.dp))
            Text("v${BuildConfig.VERSION_NAME}", style = NeonType.caption().copy(fontFamily = Neon.fonts.code))
            Spacer(Modifier.weight(1f))
            IconBtn(R.drawable.ic_settings, "设置", tint = c.inkSecondary, onClick = onOpenSettings)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { QuotaCard(vm, onConnectGo) }
            item { DriveCard(vm, onAuthorizeDrive) }
            item { SandboxCard() }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("对话", style = NeonType.title(), modifier = Modifier.weight(1f))
                    Text("${convs.size}", style = NeonType.tnum(NeonType.caption()))
                    Spacer(Modifier.width(10.dp))
                    Capsule("新对话", outlined = true, icon = painterResource(R.drawable.ic_compose)) { onNewChat() }
                }
            }
            if (convs.isEmpty()) item {
                Text("还没有对话。发出第一条消息后会保存在本机数据库。", style = NeonType.secondary(), modifier = Modifier.padding(4.dp))
            }
            items(convs, key = { it.id }) { conv ->
                ConversationRow(conv, conv.id == current, vm, onOpenConversation)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.bgCanvas).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val email = s.driveEmail
            Box(Modifier.size(36.dp).clip(NeonShapes.capsule).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                Text((email?.firstOrNull()?.uppercaseChar() ?: 'N').toString(), style = NeonType.bodyMedium())
            }
            Spacer(Modifier.width(12.dp))
            Text(email ?: "本机模式 · 未连接 Google", style = NeonType.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(conv: ConversationEntity, selected: Boolean, vm: MainViewModel, onOpen: (String) -> Unit) {
    val c = Neon.colors
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(NeonShapes.item).background(if (selected) c.bgSurface else c.bgCanvas)
            .clickable { onOpen(conv.id) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(conv.title, style = NeonType.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(relTime(conv.updatedAt) + if (conv.driveSyncedAt != null) " · 已同步" else "", style = NeonType.tnum(NeonType.caption()))
        }
        Box {
            Box(Modifier.size(40.dp).clip(NeonShapes.capsule).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, "更多", tint = c.inkSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("删除", color = c.danger) }, onClick = { menu = false; confirmDelete = true })
            }
        }
    }
    if (renaming) {
        var t by remember { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名对话") },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.rename(conv.id, t); renaming = false }) { Text("保存") } },
            dismissButton = { TextButton({ renaming = false }) { Text("取消") } },
            containerColor = c.bgSurface,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除对话？") },
            text = { Text("「${conv.title}」会从本机删除" + if (conv.driveFileId != null) "，Drive 上的副本移到回收站。" else "。") },
            confirmButton = { TextButton({ vm.delete(conv.id); confirmDelete = false }) { Text("删除", color = c.danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
            containerColor = c.bgSurface,
        )
    }
}

fun relTime(t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "MM/dd HH:mm", Locale.getDefault()).format(Date(t))
}

@Composable
private fun QuotaCard(vm: MainViewModel, onConnectGo: () -> Unit) {
    val c = Neon.colors
    val q by vm.quota.collectAsState()
    NeonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OpenCode Go 额度", style = NeonType.title(), modifier = Modifier.weight(1f))
            if (q !is QuotaState.NotConfigured) {
                if (q is QuotaState.Loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.inkSecondary)
                else IconBtn(R.drawable.ic_refresh, "刷新额度", tint = c.inkSecondary, size = 20.dp) { vm.refreshQuota() }
            }
        }
        when (val st = q) {
            QuotaState.NotConfigured -> {
                Spacer(Modifier.height(6.dp))
                Text("未配置。导入 Go Key 后显示 5 小时 / 本周 / 本月的真实用量。", style = NeonType.secondary())
                Spacer(Modifier.height(12.dp))
                PrimaryButton("去配置", onClick = onConnectGo)
            }
            QuotaState.Loading -> {
                Spacer(Modifier.height(8.dp))
                Text("正在读取额度…", style = NeonType.secondary())
            }
            is QuotaState.Error -> {
                Spacer(Modifier.height(6.dp))
                Text("额度接口不可用", style = NeonType.label().copy(color = c.danger))
                Text(st.message, style = NeonType.caption())
            }
            is QuotaState.Ok -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Capsule("直连无代理", filled = true, icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.Check))
                    Spacer(Modifier.width(10.dp))
                    Text("更新于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.usage.fetchedAt)), style = NeonType.tnum(NeonType.caption()))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Ring(st.usage.rolling, "5 小时")
                    Ring(st.usage.weekly, "本周")
                    Ring(st.usage.monthly, "本月")
                }
            }
        }
    }
}

@Composable
private fun Ring(w: GoWindow?, label: String) {
    val c = Neon.colors
    val target = ((w?.percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val p by animateFloatAsState(target, tween(520), label = "ring")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(70.dp)) {
                val sw = 6.dp.toPx()
                val d = size.minDimension - sw
                val tl = Offset(sw / 2, sw / 2)
                drawArc(c.bgSubtle, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
                drawArc(if ((w?.percent ?: 0.0) >= 90) c.danger else c.inkPrimary, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(if (w == null) "—" else "${w.percent.toInt()}%", style = NeonType.tnum(NeonType.bodyMedium()))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = NeonType.label())
        Text(resetText(w), style = NeonType.tnum(NeonType.caption().copy(fontFamily = Neon.fonts.code)))
    }
}

private fun resetText(w: GoWindow?): String {
    if (w == null) return "无数据"
    val at = w.resetsAt ?: return w.status ?: ""
    val left = at - System.currentTimeMillis()
    if (left <= 0) return "已重置"
    val h = left / 3_600_000
    val m = (left % 3_600_000) / 60_000
    return if (h < 24) "${h}h${m}m 后重置" else SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(at)) + " 重置"
}

@Composable
private fun DriveCard(vm: MainViewModel, onAuthorize: () -> Unit) {
    val c = Neon.colors
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val busy by vm.driveBusy.collectAsState()
    NeonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(NeonShapes.item).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_drive), null, tint = c.inkPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Google Drive 工作区", style = NeonType.title())
                if (s.driveConnected) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, Modifier.size(14.dp), tint = c.inkSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(s.driveEmail ?: "已授权", style = NeonType.caption(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = c.inkSecondary)
        }
        Spacer(Modifier.height(10.dp))
        if (!s.driveConnected) {
            Text("未授权。授权后把对话以 JSON 同步到 我的云端硬盘 / Neon /（仅 drive.file 权限，看不到你的其他文件）。", style = NeonType.secondary())
            s.driveError?.let { Spacer(Modifier.height(6.dp)); Text(it, style = NeonType.caption().copy(color = c.danger)) }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("去授权", enabled = !busy, onClick = onAuthorize)
        } else {
            Text("我的云端硬盘 / Neon /", style = NeonType.caption())
            Spacer(Modifier.height(8.dp))
            val folders = vm.driveFolders()
            DriveClient.SUBFOLDERS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { name ->
                        val id = folders[name]
                        Row(
                            Modifier.weight(1f).clip(NeonShapes.item).background(c.bgSubtle)
                                .clickable(enabled = id != null) { id?.let { CustomTabs.open(ctx, DriveClient.folderUrl(it)) } }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(16.dp), tint = if (id != null) c.inkPrimary else c.inkSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text(name, style = NeonType.label().copy(color = if (id != null) c.inkPrimary else c.inkSecondary))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.driveLastSync?.let { "上次同步 " + relTime(it) } ?: "尚未同步",
                    style = NeonType.tnum(NeonType.caption()), modifier = Modifier.weight(1f),
                )
                Capsule("立即同步", outlined = true) { vm.driveSyncNow() }
            }
            s.driveError?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, style = NeonType.caption().copy(color = c.danger), modifier = Modifier.weight(1f))
                    Capsule("重新授权", outlined = true) { onAuthorize() }
                }
            }
        }
    }
}

/** Sandbox arrives in M2. Honest state: not installed, runs nothing. */
@Composable
private fun SandboxCard() {
    val c = Neon.colors
    NeonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(NeonShapes.item).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_terminal), null, tint = c.inkSecondary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("离线 Linux 沙箱", style = NeonType.title())
                Text("未安装", style = NeonType.caption())
            }
            Text("PRoot", style = NeonType.caption().copy(fontFamily = Neon.fonts.code))
        }
        Spacer(Modifier.height(8.dp))
        Text("Alpine arm64 rootfs 与终端在下个里程碑（M2）随 APK 内置；当前版本不执行任何命令。", style = NeonType.secondary())
    }
}
