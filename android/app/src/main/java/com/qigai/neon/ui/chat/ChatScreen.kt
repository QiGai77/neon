package com.qigai.neon.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qigai.neon.NeonContainer
import com.qigai.neon.data.db.MessageEntity
import com.qigai.neon.provider.ToolExecutionChip
import com.qigai.neon.ui.components.MarkdownText
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    container: NeonContainer,
    sessionId: String,
    initialSharedText: String?,
    onConsumeSharedText: () -> Unit,
    onNewSession: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSandbox: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val context = LocalContext.current
    val messages by container.db.messageDao().observeBySession(sessionId).collectAsState(initial = emptyList())
    val isRunning by container.agentRuntime.isRunning.collectAsState()
    val skills by container.skillRepository.skills.collectAsState()
    val terminalLines by container.shellExecutor.lines.collectAsState()
    val session by remember(sessionId) { container.db.sessionDao().observeAll() }.collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }
    var enableThinking by remember { mutableStateOf(true) }
    var enableHands by remember { mutableStateOf(false) }
    var showSkillGrid by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var activeProvider by remember { mutableStateOf(container.tokenStore.getActiveProvider()) }
    var activeModel by remember { mutableStateOf(container.tokenStore.getActiveModel()) }

    val amplitudes = remember { mutableStateListOf<Float>().apply { repeat(32) { add(0.15f) } } }
    var voiceSeconds by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()

    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            inputText = initialSharedText
            onConsumeSharedText()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // 真实 AudioRecord 声波采样 + SpeechRecognizer
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceMode = true
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(Unit) {
        onDispose { speechRecognizer?.destroy() }
    }

    LaunchedEffect(voiceMode) {
        if (!voiceMode) {
            voiceSeconds = 0
            return@LaunchedEffect
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) inputText = partial
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) inputText = text
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching { speechRecognizer?.startListening(intent) }

        withContext(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val recorder = runCatching {
                AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf.coerceAtLeast(2048))
            }.getOrNull()
            if (recorder?.state == AudioRecord.STATE_INITIALIZED) {
                val buf = ShortArray(512)
                recorder.startRecording()
                val startAt = System.currentTimeMillis()
                while (isActive && voiceMode) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) sum += buf[i] * buf[i].toDouble()
                        val rms = (sqrt(sum / read) / 12000.0).toFloat().coerceIn(0.1f, 1f)
                        withContext(Dispatchers.Main) {
                            amplitudes.removeAt(0)
                            amplitudes.add(rms)
                            voiceSeconds = ((System.currentTimeMillis() - startAt) / 1000L).toInt()
                        }
                    }
                }
                recorder.stop()
                recorder.release()
            }
        }
    }

    val currentTitle = session.firstOrNull { it.id == sessionId }?.title ?: "新对话"

    Scaffold(
        containerColor = palette.bgCanvas,
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bgCanvas),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = "侧边栏")
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(currentTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.clickable { showModelMenu = true },
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(palette.neonLime))
                                Text(
                                    "${if (activeProvider == "deepseek") "DeepSeek" else "OpenCode Go"} / $activeModel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkSecondary,
                                )
                            }
                            DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                                listOf(
                                    "opencode_go" to "deepseek-v4-pro",
                                    "opencode_go" to "deepseek-flash",
                                    "deepseek" to "deepseek-v4-pro",
                                    "deepseek" to "deepseek-flash",
                                ).forEach { (prov, mod) ->
                                    DropdownMenuItem(
                                        text = { Text("${if (prov == "deepseek") "DeepSeek 原生" else "OpenCode Go"} · $mod") },
                                        onClick = {
                                            activeProvider = prov
                                            activeModel = mod
                                            container.tokenStore.setActiveProvider(prov)
                                            container.tokenStore.setActiveModel(mod)
                                            showModelMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNewSession) {
                        Icon(Icons.Rounded.Add, contentDescription = "新对话")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = palette.neonLime, modifier = Modifier.size(34.dp))
                        Text("Neon", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "输入指令开始对话，或输入 / 选择技能。",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            msg = msg,
                            latestTerminalLine = terminalLines.lastOrNull()?.text,
                            onOpenSandbox = onOpenSandbox,
                        )
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }

            // `/` 技能网格面板（实时绑定 SkillRepository，无写死示例）
            AnimatedVisibility(visible = showSkillGrid) {
                Surface(
                    color = palette.bgSurface,
                    shape = NeonShapes.Card,
                    border = BorderStroke(1.dp, palette.borderHairline),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("技能 (${skills.size})", style = MaterialTheme.typography.labelMedium, color = palette.inkSecondary)
                        if (skills.isEmpty()) {
                            Text("暂无已保存的 SKILL.md，可在侧边栏 → 技能库中创建。", style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                skills.take(6).forEach { sk ->
                                    Surface(
                                        color = palette.bgCanvas,
                                        shape = NeonShapes.Control,
                                        border = BorderStroke(1.dp, palette.borderHairline),
                                        modifier = Modifier.clickable {
                                            inputText = "${sk.command} "
                                            showSkillGrid = false
                                        },
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(sk.name, style = MaterialTheme.typography.labelMedium)
                                            Text(sk.command, style = MaterialTheme.typography.labelSmall, color = palette.inkSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 底部 24dp 圆角悬浮输入底座
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Composer,
                border = BorderStroke(1.dp, palette.borderHairline),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (voiceMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(
                            onClick = {
                                speechRecognizer?.cancel()
                                voiceMode = false
                            },
                            modifier = Modifier.size(38.dp).background(palette.bgSubtle, CircleShape),
                        ) { Icon(Icons.Rounded.Close, contentDescription = "取消") }

                        Row(
                            modifier = Modifier.weight(1f).height(28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            amplitudes.forEach { amp ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((6 + amp * 20).dp)
                                        .clip(CircleShape)
                                        .background(palette.inkPrimary),
                                )
                            }
                        }

                        Text(
                            "%02d:%02d".format(voiceSeconds / 60, voiceSeconds % 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.inkSecondary,
                        )

                        IconButton(
                            onClick = {
                                speechRecognizer?.stopListening()
                                voiceMode = false
                            },
                            modifier = Modifier.size(38.dp).background(palette.inkPrimary, CircleShape),
                        ) { Icon(Icons.Rounded.Check, contentDescription = "完成", tint = Color.White) }
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (inputText.isEmpty()) {
                                Text("输入问题或 / 技能", style = MaterialTheme.typography.bodyMedium, color = palette.inkTertiary)
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { next ->
                                    inputText = next
                                    showSkillGrid = next.startsWith("/")
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.inkPrimary),
                                cursorBrush = SolidColor(palette.inkPrimary),
                                maxLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ComposerChip("/ 技能", showSkillGrid) { showSkillGrid = !showSkillGrid }
                                ComposerChip("深度思考", enableThinking) { enableThinking = !enableThinking }
                                ComposerChip("Hands", enableHands) { enableHands = !enableHands }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (granted) voiceMode = true else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) { Icon(Icons.Rounded.Mic, contentDescription = "语音输入") }

                                IconButton(
                                    onClick = {
                                        if (isRunning) {
                                            container.agentRuntime.stopCurrentRun()
                                        } else if (inputText.isNotBlank()) {
                                            val text = inputText
                                            inputText = ""
                                            showSkillGrid = false
                                            container.agentRuntime.sendUserMessage(sessionId, text, enableThinking, enableHands)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp).background(palette.inkPrimary, CircleShape),
                                ) {
                                    Icon(
                                        if (isRunning) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                        contentDescription = if (isRunning) "停止" else "发送",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalNeonPalette.current
    Surface(
        color = if (selected) palette.inkPrimary else palette.bgSubtle,
        shape = NeonShapes.Pill,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else palette.inkPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageBubble(
    msg: MessageEntity,
    latestTerminalLine: String?,
    onOpenSandbox: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    if (msg.role == "user") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = palette.bgSubtle,
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Text(msg.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        return
    }

    var thinkingExpanded by remember { mutableStateOf(msg.status == "thinking") }
    val chips = remember(msg.toolCallsJson) {
        runCatching {
            msg.toolCallsJson?.let { Json.decodeFromString<List<ToolExecutionChip>>(it) } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!msg.thinking.isNullOrBlank()) {
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
                modifier = Modifier.fillMaxWidth().clickable { thinkingExpanded = !thinkingExpanded },
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(palette.neonLime))
                            Text(
                                if (msg.status == "thinking") "思考中" else "思考耗时 ${msg.thinkingDurationMs / 1000.0}s",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(16.dp))
                    }
                    if (thinkingExpanded) {
                        Text(msg.thinking, style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                    }
                }
            }
        }

        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { chip ->
                    Surface(
                        color = when (chip.status) {
                            "ok" -> palette.neonLimeSoft
                            "blocked", "error" -> palette.dangerSoft
                            else -> palette.bgSubtle
                        },
                        shape = NeonShapes.Pill,
                    ) {
                        Text(
                            "${chip.label} (${chip.status})",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (chips.any { it.label == "sandbox_exec" } && !latestTerminalLine.isNullOrBlank()) {
                Surface(
                    color = palette.terminalBg,
                    shape = NeonShapes.Control,
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSandbox),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Terminal, null, tint = palette.neonLime, modifier = Modifier.size(14.dp))
                            Text(
                                latestTerminalLine,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.terminalFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("终端", style = MaterialTheme.typography.labelSmall, color = palette.neonLime, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (msg.content.isNotBlank()) {
            MarkdownText(markdown = msg.content)
        }
    }
}
