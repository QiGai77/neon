package com.qigai.neon

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.qigai.neon.ui.chat.ChatScreen
import com.qigai.neon.ui.chat.NeonSheets
import com.qigai.neon.ui.chat.SheetKind
import com.qigai.neon.ui.settings.SettingsScreen
import com.qigai.neon.ui.theme.NeonTheme
import com.qigai.neon.ui.theme.ThemeMode

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by vm.settings.collectAsState()
            val dark = when (s.theme) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            NeonTheme(s.theme) { NeonRoot(vm) }
        }
    }
}

@Composable
fun NeonRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    var sheet by remember { mutableStateOf<SheetKind?>(null) }
    val ctx = LocalContext.current
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) {
        toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); vm.toast(null) }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    crash?.let { text ->
        CrashDialog(text, onDismiss = { CrashLog.clear(ctx); crash = null })
    }
    com.qigai.neon.ui.caps.SentinelHost(vm, onSandboxScreen = route == "sandbox") {
    NeonSheets(vm, sheet, { sheet = it }) {
        NavHost(
            nav, startDestination = "chat",
            enterTransition = { slideInHorizontally(spring(0.9f, 420f)) { it / 5 } + fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(140)) + scaleOut(targetScale = 0.97f) },
            popEnterTransition = { fadeIn(tween(180)) + scaleIn(initialScale = 0.97f) },
            popExitTransition = { slideOutHorizontally(spring(0.9f, 420f)) { it / 5 } + fadeOut(tween(140)) },
        ) {
            composable("chat") { ChatScreen(vm, openSheet = { sheet = it }, onOpenSettings = { nav.navigate("settings") }, onNavigate = { nav.navigate(it) }) }
            composable("settings") { SettingsScreen(vm, openSheet = { sheet = it }, onBack = { nav.popBackStack() }) }
            composable("connectors") { com.qigai.neon.ui.caps.ConnectorsRoute(vm, onBack = { nav.popBackStack() }, onOpenHands = { nav.navigate("hands") }, onMarket = { nav.navigate("connector-market") }) }
            composable("connector-market") { com.qigai.neon.ui.market.ConnectorMarketRoute(vm, onBack = { nav.popBackStack() }) }
            composable("skill-market") { com.qigai.neon.ui.market.SkillMarketRoute(vm, onBack = { nav.popBackStack() }) }
            composable("hands") { com.qigai.neon.ui.caps.HandsRoute(vm, onBack = { nav.popBackStack() }) }
            composable("skills") { com.qigai.neon.ui.caps.SkillsRoute(vm, onBack = { nav.popBackStack() }, onMarket = { nav.navigate("skill-market") }) }
            composable("sandbox") { com.qigai.neon.ui.caps.SandboxRoute(vm, onBack = { nav.popBackStack() }) }
            composable("memory") { com.qigai.neon.ui.caps.MemoryTasksRoute(vm, onBack = { nav.popBackStack() }) }
        }
    }
    }
}

@Suppress("unused")
private fun unusedArgb() = androidx.compose.ui.graphics.Color.Black.toArgb()


@Composable
private fun CrashDialog(text: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val c = com.qigai.neon.ui.theme.Neon.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("上次闪退了", style = com.qigai.neon.ui.theme.NeonType.headline()) },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                androidx.compose.material3.Text(
                    text,
                    style = com.qigai.neon.ui.theme.NeonType.mono().copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    modifier = androidx.compose.ui.Modifier.heightIn(max = androidx.compose.ui.unit.Dp(360f))
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Neon crash", text))
                Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { androidx.compose.material3.Text("复制并关闭", color = c.inkPrimary) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onDismiss) { androidx.compose.material3.Text("关闭", color = c.inkSecondary) }
        },
        containerColor = c.bgSurface,
    )
}
