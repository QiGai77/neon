package com.qigai.neon.ui.sider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qigai.neon.BuildConfig
import com.qigai.neon.MainViewModel
import com.qigai.neon.QuotaState
import com.qigai.neon.data.ConversationEntity
import com.qigai.neon.oauth.CustomTabs
import com.qigai.neon.provider.GoWindow
import com.qigai.neon.sysfiles.DriveClient
import com.qigai.neon.ui.components.BlobLogo
import com.qigai.neon.ui.components.ButtonKind
import com.qigai.neon.ui.components.ChipStyle
import com.qigai.neon.ui.components.IconBtn
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.NeonChip
import com.qigai.neon.ui.components.PillButton
import com.qigai.neon.ui.components.SectionLabel
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonMotion
import com.qigai.neon.ui.theme.NeonShapes
import com.qigai.neon.ui.theme.NeonType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Everything the drawer shows (S02), as plain data. */
data class DriveUi(
    val connected: Boolean,
    val email: String?,
    val busy: Boolean,
    val error: String?,
    val lastSync: Long?,
    val folders: Map<String, String>,
)

@Composable
fun SiderDrawer(
    vm: MainViewModel,
    onOpenSettings: () -> Unit,
    onConnectGo: () -> Unit,
    onAuthorizeDrive: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
) {
    val ctx = LocalContext.current
    val convs by vm.conversations.collectAsState()
    val current by vm.currentId.collectAsState()
    val s by vm.settings.collectAsState()
    val q by vm.quota.collectAsState()
    val busy by vm.driveBusy.collectAsState()
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }
    SiderContent(
        version = BuildConfig.VERSION_NAME,
        quota = q,
        drive = DriveUi(s.driveConnected, s.driveEmail, busy, s.driveError, s.driveLastSync, vm.driveFolders()),
        conversations = convs,
        currentId = current,
        onOpenSettings = onOpenSettings,
        onRefreshQuota = { vm.refreshQuota() },
        onConnectGo = onConnectGo,
        onAuthorizeDrive = onAuthorizeDrive,
        onDriveSync = { vm.driveSyncNow() },
        onOpenFolder = { id -> CustomTabs.open(ctx, DriveClient.folderUrl(id)) },
        onOpenConversation = onOpenConversation,
        onNewChat = onNewChat,
        onRename = { renaming = it },
        onDelete = { deleting = it },
    )
    val c = Neon.colors
    renaming?.let { conv ->
        var t by remember(conv.id) { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名对话", style = NeonType.headline()) },
            text = {
                OutlinedTextField(
                    t, { t = it }, singleLine = true, shape = NeonShapes.item,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.inkPrimary, unfocusedBorderColor = c.borderHairline, cursorColor = c.inkPrimary),
                )
            },
            confirmButton = { TextButton({ vm.rename(conv.id, t); renaming = null }) { Text("保存", color = c.inkPrimary) } },
            dismissButton = { TextButton({ renaming = null }) { Text("取消", color = c.inkSecondary) } },
            containerColor = c.bgSurface, shape = NeonShapes.card,
        )
    }
    deleting?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除对话？", style = NeonType.headline()) },
            text = { Text("「${conv.title}」会从本机删除" + if (conv.driveFileId != null) "，Drive 上的副本移到回收站。" else "。", style = NeonType.secondary()) },
            confirmButton = { TextButton({ vm.delete(conv.id); deleting = null }) { Text("删除", color = c.danger) } },
            dismissButton = { TextButton({ deleting = null }) { Text("取消", color = c.inkSecondary) } },
            containerColor = c.bgSurface, shape = NeonShapes.card,
        )
    }
}

@Composable
fun SiderContent(
    version: String,
    quota: QuotaState,
    drive: DriveUi,
    conversations: List<ConversationEntity>,
    currentId: String?,
    onOpenSettings: () -> Unit,
    onRefreshQuota: () -> Unit,
    onConnectGo: () -> Unit,
    onAuthorizeDrive: () -> Unit,
    onDriveSync: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgDrawer).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BlobLogo(26.dp)
            Spacer(Modifier.width(10.dp))
            Text("Neon", style = NeonType.brand())
            Spacer(Modifier.width(8.dp))
            Text("v$version", style = NeonType.mono().copy(color = c.inkTertiary), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            IconBtn(Lucide.Settings, "设置", tint = c.inkSecondary, onClick = onOpenSettings)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "quota") { QuotaCard(quota, onRefreshQuota, onConnectGo) }
            item(key = "drive") { DriveCard(drive, onAuthorizeDrive, onDriveSync, onOpenFolder) }
            item(key = "convHead") {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("最近对话", Modifier.weight(1f), trailing = if (conversations.isEmpty()) null else "${conversations.size}")
                    Spacer(Modifier.width(8.dp))
                    NeonChip("新对话", style = ChipStyle.Outline, icon = Lucide.SquarePen, height = 30.dp, onClick = onNewChat)
                }
            }
            if (conversations.isEmpty()) item(key = "convEmpty") {
                NeonCard(padding = PaddingValues(18.dp)) {
                    Text("还没有对话", style = NeonType.item())
                    Spacer(Modifier.height(2.dp))
                    Text("发出第一条消息后保存在本机，连接 Drive 后自动备份", style = NeonType.caption().copy(color = c.inkTertiary))
                }
            } else item(key = "convList") {
                NeonCard(padding = PaddingValues(vertical = 6.dp)) {
                    conversations.forEachIndexed { i, conv ->
                        ConversationRow(conv, conv.id == currentId, onOpenConversation, onRename, onDelete)
                        if (i < conversations.lastIndex) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderCard))
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val email = drive.email
            Box(Modifier.size(34.dp).clip(NeonShapes.capsule).background(c.bgSubtle), contentAlignment = Alignment.Center) {
                Text(if (email != null) "G" else "N", style = NeonType.item())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(email?.substringBefore('@') ?: "本机模式", style = NeonType.bodyMedium().copy(fontFamily = Neon.fonts.regular), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (email == null) Text("未连接 Google", style = NeonType.caption().copy(color = c.inkTertiary))
            }
            IconBtn(Lucide.SlidersHorizontal, "设置", tint = c.inkSecondary, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun ConversationRow(
    conv: ConversationEntity,
    selected: Boolean,
    onOpen: (String) -> Unit,
    onRename: (ConversationEntity) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
) {
    val c = Neon.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(conv.id) }.padding(start = 16.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(NeonShapes.capsule).background(if (selected) c.accent else c.bgSurface.copy(alpha = 0f)))
        Spacer(Modifier.width(if (selected) 10.dp else 10.dp))
        Column(Modifier.weight(1f)) {
            Text(conv.title, style = NeonType.item().copy(color = if (selected) c.inkPrimary else c.inkPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(relTime(conv.updatedAt), style = NeonType.tnum(NeonType.overline()))
                if (conv.driveSyncedAt != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Lucide.Cloud, "已同步", Modifier.size(12.dp), tint = c.inkTertiary)
                }
            }
        }
        Box {
            Box(Modifier.size(36.dp).clip(NeonShapes.capsule).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(Lucide.EllipsisVertical, "更多", tint = c.inkTertiary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.bgSurface, shape = NeonShapes.item) {
                DropdownMenuItem(
                    text = { Text("重命名", style = NeonType.label()) },
                    leadingIcon = { Icon(Lucide.Pencil, null, Modifier.size(16.dp), tint = c.inkSecondary) },
                    onClick = { menu = false; onRename(conv) },
                )
                DropdownMenuItem(
                    text = { Text("删除", style = NeonType.label().copy(color = c.danger)) },
                    leadingIcon = { Icon(Lucide.Trash2, null, Modifier.size(16.dp), tint = c.danger) },
                    onClick = { menu = false; onDelete(conv) },
                )
            }
        }
    }
}

fun relTime(t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = t }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "MM/dd HH:mm", Locale.getDefault()).format(Date(t))
}

// ───────────────────────── quota ─────────────────────────

@Composable
private fun QuotaCard(q: QuotaState, onRefresh: () -> Unit, onConnectGo: () -> Unit) {
    val c = Neon.colors
    NeonCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OpenCode Go 额度", style = NeonType.cardTitle(), modifier = Modifier.weight(1f).padding(top = 6.dp))
            if (q !is QuotaState.NotConfigured) {
                if (q is QuotaState.Loading) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.75.dp, color = c.inkSecondary)
                } else IconBtn(Lucide.RefreshCw, "刷新额度", tint = c.inkSecondary, size = 19.dp, onClick = onRefresh)
            } else Spacer(Modifier.height(44.dp))
        }
        Column(Modifier.padding(end = 8.dp)) {
            when (q) {
                QuotaState.NotConfigured -> {
                    Text("导入 Go Key 后显示 5 小时、本周、本月的真实用量", style = NeonType.caption())
                    Spacer(Modifier.height(14.dp))
                    PillButton("导入订阅", icon = Lucide.FileJson, height = 36.dp, onClick = onConnectGo)
                }
                QuotaState.Loading -> Text("正在读取额度…", style = NeonType.caption())
                is QuotaState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.CircleAlert, null, Modifier.size(15.dp), tint = c.danger)
                        Spacer(Modifier.width(6.dp))
                        Text("额度接口不可用", style = NeonType.label().copy(color = c.danger))
                    }
                    Text(q.message, style = NeonType.caption(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                is QuotaState.Ok -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NeonChip("直连无代理", style = ChipStyle.Active, icon = Lucide.Check, height = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("更新于 " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(q.usage.fetchedAt)), style = NeonType.tnum(NeonType.caption().copy(color = c.inkTertiary)))
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        listOf(q.usage.rolling to "5 小时", q.usage.weekly to "本周", q.usage.monthly to "本月").forEachIndexed { i, (w, label) ->
                            Ring(w, label, i, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Riff 11: arcs draw in 520ms decelerate, 60ms apart; the number counts with them. */
@Composable
private fun Ring(w: GoWindow?, label: String, index: Int, modifier: Modifier) {
    val c = Neon.colors
    val target = ((w?.percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    val p = remember { Animatable(0f) }
    LaunchedEffect(target) {
        delay(index * NeonMotion.RING_STAGGER_MS.toLong())
        p.animateTo(target, tween(NeonMotion.RING_MS, easing = FastOutSlowInEasing))
    }
    val hot = (w?.percent ?: 0.0) >= 90
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            val arc = if (hot) c.danger else c.accent
            val track = if (c.isDark) c.borderHairline else c.bgSubtle
            Canvas(Modifier.size(54.dp)) {
                val sw = 4.5.dp.toPx()
                val d = size.minDimension - sw
                val tl = Offset(sw / 2, sw / 2)
                drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
                drawArc(arc, -90f, 360f * p.value, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (w == null) "—" else "${(p.value * 100).toInt()}",
                    style = NeonType.tnum(NeonType.label().copy(fontFamily = Neon.fonts.medium, fontSize = 14.sp)),
                )
                if (w != null) Text("%", style = NeonType.caption().copy(fontSize = 8.sp, lineHeight = 14.sp), modifier = Modifier.padding(bottom = 1.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = NeonType.label())
        Text(resetText(w), style = NeonType.tnum(NeonType.mono().copy(fontSize = 11.sp, color = c.inkTertiary)), maxLines = 1)
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

// ───────────────────────── drive ─────────────────────────

@Composable
private fun DriveCard(d: DriveUi, onAuthorize: () -> Unit, onSync: () -> Unit, onOpenFolder: (String) -> Unit) {
    val c = Neon.colors
    NeonCard(padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconSquircle(Lucide.HardDrive, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Google Drive 工作区", style = NeonType.cardTitle())
                Spacer(Modifier.height(4.dp))
                if (d.connected) NeonChip(d.email ?: "已授权", style = ChipStyle.Soft, icon = Lucide.Check, height = 24.dp)
                else Text("只申请 drive.file，看不到你的其他文件", style = NeonType.caption())
            }
            if (d.busy) CircularProgressIndicator(Modifier.padding(top = 4.dp).size(16.dp), strokeWidth = 1.75.dp, color = c.inkSecondary)
        }
        Spacer(Modifier.height(14.dp))
        if (!d.connected) {
            d.error?.let { Text(it, style = NeonType.caption().copy(color = c.danger)); Spacer(Modifier.height(10.dp)) }
            PillButton("授权 Google Drive", icon = Lucide.Link, height = 36.dp, enabled = !d.busy, onClick = onAuthorize)
        } else {
            Text("我的云端硬盘 / Neon /", style = NeonType.caption().copy(color = c.inkTertiary))
            Spacer(Modifier.height(8.dp))
            DriveClient.SUBFOLDERS.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { name -> FolderChip(name, d.folders[name], Modifier.weight(1f), onOpenFolder) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    d.lastSync?.let { "上次同步 " + relTime(it) } ?: "尚未同步",
                    style = NeonType.tnum(NeonType.caption().copy(color = c.inkTertiary)), modifier = Modifier.weight(1f),
                )
                NeonChip("立即同步", style = ChipStyle.Outline, icon = Lucide.RefreshCw, height = 28.dp, onClick = onSync)
            }
            d.error?.let {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it, style = NeonType.caption().copy(color = c.danger), modifier = Modifier.weight(1f))
                    PillButton("重新授权", kind = ButtonKind.Secondary, height = 30.dp, onClick = onAuthorize)
                }
            }
        }
    }
}

@Composable
private fun FolderChip(name: String, id: String?, modifier: Modifier, onOpen: (String) -> Unit) {
    val c = Neon.colors
    Row(
        modifier.clip(NeonShapes.small).background(c.bgTile)
            .clickable(enabled = id != null) { id?.let(onOpen) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Folder, null, Modifier.size(14.dp), tint = if (id != null) c.inkSecondary else c.inkTertiary)
        Spacer(Modifier.width(6.dp))
        Text(name, style = NeonType.chip().copy(color = if (id != null) c.inkPrimary else c.inkTertiary))
    }
}

@Suppress("unused")
private fun iconRef(): ImageVector = Lucide.Folder
