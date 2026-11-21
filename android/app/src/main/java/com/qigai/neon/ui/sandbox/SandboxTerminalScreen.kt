package com.qigai.neon.ui.sandbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.sandbox.MirrorPreset
import com.qigai.neon.sandbox.SandboxStatus
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxTerminalScreen(
    container: NeonContainer,
    onBack: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val scope = rememberCoroutineScope()
    val status by container.rootfsManager.status.collectAsState()
    val mirrorPreset by container.rootfsManager.mirrorPreset.collectAsState()
    val lines by container.shellExecutor.lines.collectAsState()
    var cmdInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = palette.bgCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alpine 沙箱", style = MaterialTheme.typography.titleLarge)
                        Text("PRoot · /workspace 已挂载", style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { container.shellExecutor.clearHistory() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "清屏")
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val badge = when (val s = status) {
                    is SandboxStatus.Ready -> "arm64 已就绪"
                    is SandboxStatus.Extracting -> "解压中"
                    is SandboxStatus.MissingBundle -> "缺少离线包"
                    is SandboxStatus.Error -> "初始化失败"
                    SandboxStatus.Checking -> "检查中"
                }
                Surface(color = palette.inkPrimary, shape = NeonShapes.Pill) {
                    Text(badge, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Surface(color = palette.bgSubtle, shape = NeonShapes.Pill) {
                    Text("PRoot", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("镜像源:", style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                MirrorPreset.entries.forEach { preset ->
                    val selected = mirrorPreset == preset
                    Surface(
                        color = if (selected) palette.inkPrimary else palette.bgSurface,
                        shape = NeonShapes.Pill,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.clickable { container.rootfsManager.applyMirrorPreset(preset) },
                    ) {
                        Text(
                            preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) Color.White else palette.inkPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // 真实黑底终端输出区
            Surface(
                color = palette.terminalBg,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (lines.isEmpty()) {
                        item {
                            Text(
                                "neon@alpine:/workspace$ (输入命令如 uname -a 或 python3 --version)",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.neonLime,
                            )
                        }
                    } else {
                        items(lines) { line ->
                            Text(
                                text = line.text,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    line.isError -> palette.dangerRed
                                    line.isCommand -> palette.neonLime
                                    else -> palette.terminalFg
                                },
                            )
                        }
                    }
                }
            }

            // 快捷按键栏
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("ls -la", "python3 --version", "uname -a", "/", "-", "~", "&&").forEach { key ->
                    Surface(
                        color = palette.bgSurface,
                        shape = NeonShapes.Control,
                        border = BorderStroke(1.dp, palette.borderHairline),
                        modifier = Modifier.clickable {
                            cmdInput = if (cmdInput.isBlank()) key else "$cmdInput $key"
                        },
                    ) {
                        Text(
                            key,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = cmdInput,
                    onValueChange = { cmdInput = it },
                    placeholder = { Text("输入 Shell 命令…") },
                    singleLine = true,
                    shape = NeonShapes.Control,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val cmd = cmdInput.trim()
                        if (cmd.isNotEmpty()) {
                            cmdInput = ""
                            scope.launch { container.shellExecutor.execute(cmd) }
                        }
                    },
                    modifier = Modifier.background(palette.inkPrimary, NeonShapes.Control),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "执行", tint = Color.White)
                }
            }
        }
    }
}
