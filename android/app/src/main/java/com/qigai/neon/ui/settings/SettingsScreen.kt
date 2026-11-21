package com.qigai.neon.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.qigai.neon.NeonContainer
import com.qigai.neon.ui.theme.LocalNeonPalette
import com.qigai.neon.ui.theme.NeonShapes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: NeonContainer,
    onBack: () -> Unit,
) {
    val palette = LocalNeonPalette.current
    val scope = rememberCoroutineScope()
    val isDark by container.tokenStore.darkTheme.collectAsState()

    var deepSeekKey by remember { mutableStateOf(container.tokenStore.getDeepSeekKey()) }
    var openCodeKey by remember { mutableStateOf(container.tokenStore.getOpenCodeGoKey()) }
    var authJsonInput by remember { mutableStateOf("") }
    var googleClientId by remember { mutableStateOf(container.tokenStore.getGoogleClientId()) }
    var savedBanner by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = palette.bgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("深色模式", style = MaterialTheme.typography.titleMedium, color = palette.inkPrimary)
                        Text("切换浅色与深色界面", style = MaterialTheme.typography.bodySmall, color = palette.inkSecondary)
                    }
                    Switch(checked = isDark, onCheckedChange = { container.tokenStore.setDarkTheme(it) })
                }
            }

            Surface(
                color = palette.bgSurface,
                shape = NeonShapes.Card,
                border = BorderStroke(1.dp, palette.borderHairline),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("API Key 与 OAuth", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = openCodeKey,
                        onValueChange = { openCodeKey = it },
                        label = { Text("OpenCode Go API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = authJsonInput,
                        onValueChange = { authJsonInput = it },
                        label = { Text("粘贴 auth.json 导入") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = deepSeekKey,
                        onValueChange = { deepSeekKey = it },
                        label = { Text("DeepSeek API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = googleClientId,
                        onValueChange = { googleClientId = it },
                        label = { Text("Google OAuth Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Button(
                        onClick = {
                            if (authJsonInput.isNotBlank()) {
                                container.openCodeGo.extractKeyFromAuthJson(authJsonInput)?.let { openCodeKey = it }
                            }
                            container.tokenStore.setOpenCodeGoKey(openCodeKey)
                            container.tokenStore.setDeepSeekKey(deepSeekKey)
                            container.tokenStore.setGoogleClientId(googleClientId)
                            savedBanner = "已保存"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = NeonShapes.Control,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.inkPrimary, contentColor = Color.White),
                    ) {
                        Text("保存配置")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                container.chatDriveSync.exportFullBackupZip()
                                    .onSuccess { file -> savedBanner = "备份已导出: ${file.absolutePath}" }
                                    .onFailure { savedBanner = "导出失败: ${it.message}" }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = NeonShapes.Control,
                    ) {
                        Text("导出备份 ZIP")
                    }

                    savedBanner?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.neonLime)
                    }
                }
            }
        }
    }
}
