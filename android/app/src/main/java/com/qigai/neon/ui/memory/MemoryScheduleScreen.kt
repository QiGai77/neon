package com.qigai.neon.ui.memory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScheduleScreen(
    container: NeonContainer,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val palette = LocalNeonPalette.current
    val scope = rememberCoroutineScope()
    val memoryItems by container.memoryManager.items.collectAsState()
    val tasks by container.db.scheduledTaskDao().observeAll().collectAsState(initial = emptyList())
    val receipts by container.db.taskReceiptDao().observeRecent().collectAsState(initial = emptyList())

    var showAddMemory by remember { mutableStateOf(false) }
    var memoryInput by remember { mutableStateOf("") }
    var showAddTask by remember { mutableStateOf(false) }
    var taskTitle by remember { mutableStateOf("") }
    var taskPrompt by remember { mutableStateOf("") }
    var taskInterval by remember { mutableStateOf("1440") }

    Scaffold(
        containerColor = palette.bgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("记忆与任务", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddTask = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新增定时任务")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bgCanvas),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // memory.md 长期记忆卡片
            item {
                Surface(
                    color = palette.bgSurface,
                    shape = NeonShapes.Card,
                    border = BorderStroke(1.dp, palette.borderHairline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("memory.md", style = MaterialTheme.typography.titleMedium)
                                Text("自动写入本地与 Drive/Neon/记忆/", style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                            }
                            TextButton(onClick = { showAddMemory = true }) { Text("+ 添加") }
                        }

                        if (memoryItems.isEmpty()) {
                            Text("暂无长期记忆条目。", style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                memoryItems.forEach { item ->
                                    Surface(color = palette.bgSubtle, shape = NeonShapes.Pill) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(item, style = MaterialTheme.typography.bodySmall)
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "遗忘",
                                                modifier = Modifier.size(14.dp).clickable {
                                                    scope.launch { container.memoryManager.removeMemory(item) }
                                                },
                                                tint = palette.inkSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("定时任务 (${tasks.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (tasks.isEmpty()) {
                item {
                    Surface(
                        color = palette.bgSurface,
                        shape = NeonShapes.Card,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "暂无定时任务。点击右上角 + 创建一个 WorkManager 定时任务。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    val latestReceipt = receipts.firstOrNull { it.taskId == task.id }
                    Surface(
                        color = palette.bgSurface,
                        shape = NeonShapes.Card,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                                    Text(task.scheduleLabel, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                                }
                                Switch(
                                    checked = task.enabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { container.taskScheduler.setEnabled(task, enabled) }
                                    },
                                )
                            }

                            if (latestReceipt != null) {
                                HorizontalDivider(color = palette.borderHairline)
                                Text(
                                    "最近运行 ${latestReceipt.status} ${latestReceipt.durationMs / 1000.0}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (latestReceipt.status == "success") palette.neonLime else palette.dangerRed,
                                )
                                Text(latestReceipt.summary, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (latestReceipt?.sessionId != null) {
                                    OutlinedButton(
                                        onClick = { onOpenSession(latestReceipt.sessionId) },
                                        shape = NeonShapes.Control,
                                        modifier = Modifier.weight(1f),
                                    ) { Text("在对话中打开") }
                                }
                                OutlinedButton(
                                    onClick = { container.taskScheduler.runNow(task.id) },
                                    shape = NeonShapes.Control,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("立即重跑")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.size(20.dp)) }
        }
    }

    if (showAddMemory) {
        AlertDialog(
            onDismissRequest = { showAddMemory = false },
            title = { Text("添加长期记忆") },
            text = {
                OutlinedTextField(
                    value = memoryInput,
                    onValueChange = { memoryInput = it },
                    label = { Text("例如：常用城市：杭州") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val v = memoryInput.trim()
                    memoryInput = ""
                    showAddMemory = false
                    if (v.isNotEmpty()) scope.launch { container.memoryManager.addMemory(v) }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddMemory = false }) { Text("取消") } },
        )
    }

    if (showAddTask) {
        AlertDialog(
            onDismissRequest = { showAddTask = false },
            title = { Text("新建定时任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it }, label = { Text("任务名称") }, singleLine = true)
                    OutlinedTextField(value = taskPrompt, onValueChange = { taskPrompt = it }, label = { Text("执行指令 Prompt") }, minLines = 3)
                    OutlinedTextField(value = taskInterval, onValueChange = { taskInterval = it }, label = { Text("周期（分钟，最小 15）") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val t = taskTitle.trim()
                    val p = taskPrompt.trim()
                    val mins = taskInterval.toLongOrNull() ?: 1440L
                    showAddTask = false
                    if (t.isNotEmpty() && p.isNotEmpty()) {
                        scope.launch {
                            container.taskScheduler.createAndSchedule(
                                title = t,
                                skillName = "",
                                prompt = p,
                                scheduleLabel = "每 $mins 分钟",
                                intervalMinutes = mins,
                                requiresWifi = false,
                            )
                        }
                    }
                }) { Text("创建并启用") }
            },
            dismissButton = { TextButton(onClick = { showAddTask = false }) { Text("取消") } },
        )
    }
}
