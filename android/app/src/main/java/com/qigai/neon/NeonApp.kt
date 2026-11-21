package com.qigai.neon

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qigai.neon.ui.chat.ChatScreen
import com.qigai.neon.ui.connectors.ConnectorsScreen
import com.qigai.neon.ui.guard.SentinelSheet
import com.qigai.neon.ui.hands.HandsLiveScreen
import com.qigai.neon.ui.memory.MemoryScheduleScreen
import com.qigai.neon.ui.sandbox.SandboxTerminalScreen
import com.qigai.neon.ui.settings.SettingsScreen
import com.qigai.neon.ui.sider.WorkspaceDrawer
import com.qigai.neon.ui.skills.SkillsScreen
import com.qigai.neon.ui.theme.NeonTheme
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun NeonApp(
    container: NeonContainer,
    sharedText: String?,
    onConsumeSharedText: () -> Unit,
) {
    val isDark by container.tokenStore.darkTheme.collectAsState()
    val pendingGuard by container.riskGate.pendingRequest.collectAsState()
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var activeSessionId by remember { mutableStateOf(UUID.randomUUID().toString()) }

    NeonTheme(darkTheme = isDark) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                WorkspaceDrawer(
                    container = container,
                    activeSessionId = activeSessionId,
                    onSelectSession = { sid -> activeSessionId = sid },
                    onNavigate = { route -> navController.navigate(route) },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                )
            },
        ) {
            NavHost(navController = navController, startDestination = "chat") {
                composable("chat") {
                    ChatScreen(
                        container = container,
                        sessionId = activeSessionId,
                        initialSharedText = sharedText,
                        onConsumeSharedText = onConsumeSharedText,
                        onNewSession = { activeSessionId = UUID.randomUUID().toString() },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenSandbox = { navController.navigate("sandbox") },
                    )
                }
                composable("connectors") {
                    ConnectorsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable("hands") {
                    HandsLiveScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable("skills") {
                    SkillsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable("sandbox") {
                    SandboxTerminalScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable("memory") {
                    MemoryScheduleScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { sid ->
                            activeSessionId = sid
                            navController.popBackStack("chat", inclusive = false)
                        },
                    )
                }
                composable("settings") {
                    SettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
            }
        }

        pendingGuard?.let { req ->
            SentinelSheet(
                request = req,
                onResolve = { decision -> container.riskGate.resolve(decision) },
            )
        }
    }
}
