package com.qigai.neon.ui.skills

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillsScreen(
    container: NeonContainer,
    onBack: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val scope = rememberCoroutineScope()
    val skills by container.skillRepository.skills.collectAsState()
    val latestTraces by container.db.handsTraceDao().observeLatestRun().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newContent by remember {
        mutableStateOf(
            """
            ---
            name: 新技能
            command: /new_skill
            description: 描述该技能的触发场景
            connectors: [gmail]
            risk: 低风险
            ---
            1. 读取上下文
            2. 执行步骤并输出摘要
            """.trimIndent(),
        )
    }

    val filtered = remember(skills, query) {
        if (query.isBlank()) skills
        else skills.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.command.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = palette.bgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("技能库", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "新建 SKILL.md")
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
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索技能名或 /触发词") },
                    singleLine = true,
                    shape = NeonShapes.Pill,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            statusMsg?.let { msg ->
                item {
                    Surface(color = palette.neonLimeSoft, shape = NeonShapes.Control) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // Learn-from-Run 真实轨迹蒸馏卡片
            if (latestTraces.isNotEmpty()) {
                item {
                    Surface(
                        color = palette.bgSurface,
                        shape = NeonShapes.Card,
                        border = BorderStroke(1.dp, palette.inkPrimary),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = palette.neonLime, modifier = Modifier.size(18.dp))
                                Text("从最近轨迹生成技能", style = MaterialTheme.typography.titleMedium)
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                latestTraces.take(6).forEach { t ->
                                    Surface(color = palette.bgSubtle, shape = NeonShapes.Pill) {
                                        Text(
                                            "${t.stepIndex}. ${t.targetLabel.take(14)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        statusMsg = "正在调用模型蒸馏为 SKILL.md…"
                                        container.skillSynthesizer.synthesizeFromTraces(latestTraces)
                                            .onSuccess { doc -> statusMsg = "已生成技能 ${doc.command} 并同步 Drive" }
                                            .onFailure { statusMsg = "蒸馏失败: ${it.message}" }
                                    }
                                },
                                shape = NeonShapes.Control,
                                colors = ButtonDefaults.buttonColors(containerColor = palette.inkPrimary, contentColor = Color.White),
                            ) {
                                Text("沉淀为技能")
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Surface(
                        color = palette.bgSurface,
                        shape = NeonShapes.Card,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "暂无技能文件。点击右上角 + 创建 SKILL.md，或完成一次 Hands 操作后从轨迹一键沉淀。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { skill ->
                    Surface(
                        color = palette.bgSurface,
                        shape = NeonShapes.Card,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(skill.name, style = MaterialTheme.typography.titleMedium)
                                    Surface(color = palette.bgSubtle, shape = NeonShapes.Pill) {
                                        Text(skill.command, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }
                                if (skill.description.isNotBlank()) {
                                    Text(skill.description, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                                }
                            }
                            IconButton(onClick = { scope.launch { container.skillRepository.deleteSkill(skill.fileName) } }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = palette.inkSecondary)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.size(20.dp)) }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建 SKILL.md") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newFileName, onValueChange = { newFileName = it }, label = { Text("文件名 (如 morning_brief)") }, singleLine = true)
                    OutlinedTextField(value = newContent, onValueChange = { newContent = it }, label = { Text("SKILL.md 内容") }, minLines = 6, maxLines = 10)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fn = newFileName.trim().ifBlank { "skill_${System.currentTimeMillis()}" }
                    showCreate = false
                    scope.launch {
                        container.skillRepository.saveSkill(fn, newContent)
                            .onSuccess { statusMsg = "已保存 ${it.fileName}" }
                            .onFailure { statusMsg = "保存失败: ${it.message}" }
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } },
        )
    }
}
