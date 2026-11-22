package com.qigai.neon.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qigai.neon.MainViewModel
import com.qigai.neon.ui.chat.ChatScreen
import com.qigai.neon.ui.chat.SheetKind
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glassEdge
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/** Space the floating tab bar takes at the bottom; screens pad their last item by it. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Screens set this to hide the tab bar, e.g. while the chat's session drawer is open. */
val LocalHideBottomBar = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

enum class HomeTab(val label: String, val icon: ImageVector) {
    CHAT("对话", Lucide.MessageCircle),
    GLANCE("速览", Lucide.LayoutDashboard),
    SPACE("空间", Lucide.Boxes),
    ME("我的", Lucide.CircleUserRound),
}

const val BAR_HEIGHT_DP = 62
const val BAR_GAP_DP = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScaffold(vm: MainViewModel, openSheet: (SheetKind?) -> Unit, onNavigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.CHAT) }
    val haze = remember { HazeState() }
    val hide = remember { mutableStateOf(false) }
    val pending by vm.c.riskGate.pendingRequest.collectAsState()
    val showBar = !WindowInsets.isImeVisible && !hide.value
    val inset = if (showBar) (BAR_HEIGHT_DP + BAR_GAP_DP).dp else 0.dp
    val holder = rememberSaveableStateHolder()

    BackHandler(tab != HomeTab.CHAT) { tab = HomeTab.CHAT }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().haze(haze)) {
            CompositionLocalProvider(LocalBottomBarInset provides inset, LocalHideBottomBar provides hide) {
                AnimatedContent(
                    tab,
                    transitionSpec = { fadeIn(tween(180, 40)) togetherWith fadeOut(tween(120)) },
                    label = "tab",
                ) { t ->
                    holder.SaveableStateProvider(t.name) {
                        when (t) {
                            HomeTab.CHAT -> ChatScreen(vm, openSheet = openSheet, onOpenSettings = { onNavigate("settings") }, onNavigate = onNavigate)
                            HomeTab.GLANCE -> com.qigai.neon.ui.glance.GlanceScreen(vm, onAsk = { p -> if (vm.askNeon(p)) tab = HomeTab.CHAT })
                            HomeTab.SPACE -> com.qigai.neon.ui.space.SpaceScreen(vm, onNavigate = onNavigate)
                            HomeTab.ME -> com.qigai.neon.ui.me.MeScreen(vm, onNavigate = onNavigate, openSheet = openSheet)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.8f, 380f)) { it } + fadeIn(),
            exit = slideOutVertically(spring(0.9f, 500f)) { it } + fadeOut(),
        ) {
            NavPill(
                selected = tab, onSelect = { tab = it },
                chatBadge = if (pending != null) 1 else 0,
                haze = haze,
                modifier = Modifier.navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = BAR_GAP_DP.dp),
            )
        }
    }
}

/**
 * Floating frosted pill. The active tab stretches into a filled capsule with its label
 * (lime in dark, ink in light) on spring(0.72, 380); the others are icons.
 */
@Composable
fun NavPill(selected: HomeTab, onSelect: (HomeTab) -> Unit, chatBadge: Int, haze: HazeState?, modifier: Modifier = Modifier) {
    val c = Neon.colors
    val g = Glass.tokens
    val shape = CircleShape
    val style = HazeStyle(
        backgroundColor = c.bgCanvas,
        tint = HazeTint(if (c.isDark) Color(0xFF1C1C20).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f)),
        blurRadius = 24.dp,
        noiseFactor = 0.04f,
        fallbackTint = HazeTint(if (c.isDark) Color(0xFF1C1C20).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.90f)),
    )
    var m = modifier.fillMaxWidth().height(BAR_HEIGHT_DP.dp)
        .shadow(20.dp, shape, ambientColor = c.shadow.copy(alpha = 0.18f), spotColor = c.shadow.copy(alpha = 0.22f))
        .clip(shape)
    m = if (haze != null) m.hazeChild(haze, shape, style) else m.background(style.fallbackTint.color)
    Row(
        m.border(1.dp, glassEdge(), shape).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HomeTab.entries.forEach { t ->
            NavItem(t, t == selected, badge = if (t == HomeTab.CHAT) chatBadge else 0) { onSelect(t) }
        }
    }
}

@Composable
private fun NavItem(tab: HomeTab, active: Boolean, badge: Int, onClick: () -> Unit) {
    val c = Neon.colors
    val bg by animateColorAsState(if (active) c.accent else Color.Transparent, spring(0.8f, 400f), label = "navBg")
    val fg by animateColorAsState(if (active) c.onAccent else c.inkSecondary, label = "navFg")
    Box(
        Modifier.height(50.dp).clip(CircleShape).background(bg)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = if (active) 20.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(tab.icon, tab.label, Modifier.size(22.dp), tint = fg)
                if (badge > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-5).dp).size(16.dp)
                            .clip(CircleShape).background(c.danger),
                        contentAlignment = Alignment.Center,
                    ) { Text("$badge", style = NeonType.caption().copy(color = Color.White, fontSize = NeonType.caption().fontSize * 0.85f)) }
                }
            }
            AnimatedVisibility(
                active,
                enter = expandHorizontally(spring(0.72f, 380f)) + fadeIn(tween(160, 60)),
                exit = shrinkHorizontally(spring(0.9f, 500f)) + fadeOut(tween(90)),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(tab.label, style = NeonType.label().copy(color = fg, fontFamily = Neon.fonts.medium))
                }
            }
        }
    }
}

@Suppress("unused")
private val keepDp: Dp = 0.dp
