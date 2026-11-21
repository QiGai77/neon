package com.qigai.neon

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.qigai.neon.data.AppSettings
import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.ConversationEntity
import com.qigai.neon.data.MessageEntity
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.provider.GoUsage
import com.qigai.neon.provider.GoWindow
import com.qigai.neon.provider.ModelInfo
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.ui.chat.ChatContent
import com.qigai.neon.ui.chat.ConnectDeepSeekContent
import com.qigai.neon.ui.chat.ModelPickerContent
import com.qigai.neon.ui.chat.NeonSheetHost
import com.qigai.neon.ui.chat.PaletteSkill
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.settings.SettingsContent
import com.qigai.neon.ui.sider.DriveUi
import com.qigai.neon.ui.sider.SiderContent
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonTheme
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.ThemeMode
import com.qigai.neon.voice.VoiceInput
import com.qigai.neon.voice.VoiceState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every v0.3 screen at the concept board size (390×844 dp, xxhdpi) in light and dark.
 * `gradle :app:recordRoborazziDebug` writes PNGs to app/build/outputs/roborazzi/.
 * Sample content mirrors the concept boards so the two can be compared side by side.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class ScreensSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun shot(name: String, advanceMs: Long = 1200, content: @Composable () -> Unit) = render(name, false, advanceMs, content)
    private fun darkShot(name: String, advanceMs: Long = 1200, content: @Composable () -> Unit) = render(name, true, advanceMs, content)

    private fun render(name: String, dark: Boolean, advanceMs: Long, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            NeonTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Neon.colors.bgCanvas)) {
                    Column(Modifier.fillMaxSize()) {
                        FakeStatusBar()
                        Box(Modifier.weight(1f)) { content() }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(advanceMs)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    // ───────── sample data ─────────

    private fun msg(id: String, role: String, blocks: List<ContentBlock>, status: String = "done", ago: Long = 0, model: String? = null, out: Int? = null) =
        MessageEntity(id, "c1", role, BlocksJson.encode(blocks), now - ago, status = status, model = model, outputTokens = out)

    private val ask = msg("u1", "user", listOf(ContentBlock.Text("帮我把本月的账单邮件整理成表格存到 Drive，再查一下菜鸟快递到哪了")), ago = 9000)

    private val plan = "命中技能：\n/账单整理 · /查快递 · 记忆 2 条\n\n规划步骤：\n1. 读取 Gmail 本月账单邮件（4 封）\n2. 在 Alpine 沙箱用 python3 解析为 CSV\n3. 写入 Drive /Neon/附件/\n4. 用 Hands 打开菜鸟查询物流"

    private val thinkingLive = msg("a1", "assistant", listOf(ContentBlock.Thinking(plan)), status = "streaming", ago = 3800)

    private val answer = msg(
        "a1", "assistant",
        listOf(
            ContentBlock.Thinking(plan, durationMs = 3800),
            ContentBlock.ToolUse("t1", "Gmail", "{\"q\":\"读取 4 封账单\"}"),
            ContentBlock.ToolUse("t2", "Drive", "{\"path\":\"已写入 /Neon/附件/\"}"),
            ContentBlock.ToolUse("t3", "python3", "{\"cmd\":\"parse_bills.py → bills_1121.csv\"}"),
            ContentBlock.Text("已整理 4 封账单，合计 **¥1,286.40**，表格 `bills_1121.csv` 已存入 Drive。菜鸟显示包裹「派送中」，预计今天 18:00 前送达。"),
        ),
        ago = 2000, model = "deepseek-v4-pro", out = 412,
    )
    private val results = msg(
        "r1", "user",
        listOf(
            ContentBlock.ToolResult("t1", "4 messages"),
            ContentBlock.ToolResult("t2", "ok"),
            ContentBlock.ToolResult("t3", "[1/4] 读取 gmail/*.eml … 4 封\n[2/4] 解析金额与到期日 … ok\n[3/4] 写入 bills_1121.csv ¥1,286.40\n[4/4] 同步 Drive /Neon/附件/ … 0.8s"),
        ),
        ago = 2500,
    )

    private val skills = listOf(
        PaletteSkill("账单整理", "bills", Lucide.Mail),
        PaletteSkill("查快递", "track", Lucide.Truck),
        PaletteSkill("晨间早报", "brief", Lucide.Bell),
        PaletteSkill("日程同步", "cal-sync", Lucide.Calendar),
        PaletteSkill("周报生成", "weekly", Lucide.FileText),
        PaletteSkill("沙箱运行", "sh", Lucide.SquareTerminal),
    )

    private val levels = List(VoiceInput.BARS) { i ->
        if (i > 27) 0f else (0.25f + 0.6f * ((i * 37 % 11) / 10f)) * (if (i % 7 == 0) 1f else 0.7f)
    }

    @Composable
    private fun Chat(
        messages: List<MessageEntity>,
        input: String = "",
        palette: Boolean = false,
        voice: VoiceState = VoiceState(),
        busy: Boolean = false,
        model: String? = "OpenCode Go · DeepSeek V4 Pro",
        title: String? = "账单与快递",
        skillList: List<PaletteSkill> = skills,
    ) = ChatContent(
        title = title, modelLabel = model, messages = messages, busy = busy,
        input = input, onInput = {}, paletteOpen = palette, onPalette = {},
        deepThink = true, onDeepThink = {}, voice = voice,
        onMenu = {}, onNewChat = {}, onModel = {}, onConnectDeepSeek = {}, onConnectGo = {}, onRegenerate = {},
        onMic = {}, onVoiceCancel = {}, onVoiceConfirm = {}, onPlus = {}, onSend = {}, onStop = {},
        skills = skillList,
    )

    private val convs = listOf(
        ConversationEntity("c1", "账单与快递", now - 60_000, now - 60_000, driveSyncedAt = now),
        ConversationEntity("c2", "周末去莫干山的路线", now - 3_600_000 * 5, now - 3_600_000 * 5, driveSyncedAt = now),
        ConversationEntity("c3", "解释一下 PRoot 和 chroot 的区别", now - 86_400_000, now - 86_400_000),
        ConversationEntity("c4", "Kotlin Flow 背压", now - 86_400_000 * 3, now - 86_400_000 * 3),
    )

    @Composable
    private fun Drawer() = SiderContent(
        version = "0.3.0",
        quota = QuotaState.Ok(
            GoUsage(
                GoWindow(81.0, now + 6_720_000, null),
                GoWindow(64.0, now + 86_400_000 * 2, null),
                GoWindow(73.0, now + 86_400_000 * 10, null),
                now - 120_000,
            )
        ),
        drive = DriveUi(true, "gaiqi@gmail.com", false, null, now - 300_000, mapOf("对话" to "1", "附件" to "2", "备份" to "3", "技能" to "4", "记忆" to "5", "任务" to "6")),
        conversations = convs, currentId = "c1",
        onOpenSettings = {}, onRefreshQuota = {}, onConnectGo = {}, onAuthorizeDrive = {}, onDriveSync = {},
        onOpenFolder = {}, onOpenConversation = {}, onNewChat = {}, onRename = {}, onDelete = {},
    )

    private val models = ModelsState(
        lists = mapOf(
            ProviderId.DEEPSEEK to listOf(ModelInfo("deepseek-v4-pro", "DeepSeek V4 Pro"), ModelInfo("deepseek-v4-flash", "DeepSeek V4 Flash", reasoning = false)),
            ProviderId.OPENCODE_GO to listOf(
                ModelInfo("deepseek-v4.1-flash", "DeepSeek V4.1 Flash"), ModelInfo("kimi-k3", "Kimi K3"),
                ModelInfo("glm-5.3", "GLM 5.3"), ModelInfo("qwen3.8-max", "Qwen3.8 Max"),
            ),
        ),
    )

    // ───────── screens ─────────

    @Test fun s01a_light() = shot("S01a-chat-palette") { Chat(listOf(ask, thinkingLive), input = "/账", palette = true, busy = false) }
    @Test fun s01a_dark() = darkShot("S01a-chat-palette") { Chat(listOf(ask, thinkingLive), input = "/账", palette = true, busy = false) }

    @Test fun s01b_light() = shot("S01b-chat-voice") { Chat(listOf(ask, answer, results), voice = VoiceState(true, levels, now - 8000, engine = "普通话")) }
    @Test fun s01b_dark() = darkShot("S01b-chat-voice") { Chat(listOf(ask, answer, results), voice = VoiceState(true, levels, now - 8000, engine = "普通话")) }

    @Test fun s01c_light() = shot("S01c-chat-answer") { Chat(listOf(ask, answer, results), input = "") }
    @Test fun s01c_dark() = darkShot("S01c-chat-answer") { Chat(listOf(ask, answer, results), input = "") }

    @Test fun s01d_light() = shot("S01d-palette-empty") { Chat(emptyList(), input = "/", palette = true, title = null, skillList = emptyList()) }
    @Test fun s01d_dark() = darkShot("S01d-palette-empty") { Chat(emptyList(), input = "/", palette = true, title = null, skillList = emptyList()) }

    @Test fun s00_welcome_light() = shot("S00-welcome") { Chat(emptyList(), title = null, model = null) }
    @Test fun s00_welcome_dark() = darkShot("S00-welcome") { Chat(emptyList(), title = null, model = null) }

    @Test fun s00b_ready_light() = shot("S00b-ready") { Chat(emptyList(), title = null) }
    @Test fun s00b_ready_dark() = darkShot("S00b-ready") { Chat(emptyList(), title = null) }

    @Test fun s02_light() = shot("S02-drawer") { DrawerOverChat() }
    @Test fun s02_dark() = darkShot("S02-drawer") { DrawerOverChat() }

    @Test fun s03_light() = shot("S03-settings") { Settings() }
    @Test fun s03_dark() = darkShot("S03-settings") { Settings() }

    @Test fun s04_light() = shot("S04-model-sheet") { ModelSheet() }
    @Test fun s04_dark() = darkShot("S04-model-sheet") { ModelSheet() }

    @Test fun s05_light() = shot("S05-connect-sheet") { ConnectSheet() }
    @Test fun s05_dark() = darkShot("S05-connect-sheet") { ConnectSheet() }

    @Composable
    private fun DrawerOverChat() {
        Box(Modifier.fillMaxSize()) {
            Chat(listOf(ask, answer, results))
            Box(Modifier.fillMaxSize().background(if (Neon.colors.isDark) Color.Black.copy(alpha = 0.62f) else Color(0xFF101113).copy(alpha = 0.26f)))
            Box(Modifier.width(318.dp).fillMaxSize().background(Neon.colors.bgDrawer)) { Drawer() }
        }
    }

    @Composable
    private fun Settings() = SettingsContent(
        s = AppSettings(
            mapOf(
                SettingsRepository.LOADED to "1", SettingsRepository.THEME to "SYSTEM", SettingsRepository.DEEP_THINK to "1",
                SettingsRepository.DRIVE_CONNECTED to "1", SettingsRepository.DRIVE_EMAIL to "gaiqi@gmail.com",
            )
        ),
        hasKey = { it == ProviderId.OPENCODE_GO },
        onBack = {}, onConnect = {}, onRemoveKey = {}, onBase = { _, _ -> }, onDeepThinkDefault = {}, onTheme = {},
        onDriveAutoSync = {}, onDriveSync = {}, onDriveDisconnect = {}, onDriveRevoke = {}, onDriveAuthorize = {},
    )

    @Composable
    private fun ModelSheet() = NeonSheetHost(open = true, onDismiss = {}, content = { Chat(listOf(ask, answer, results)) }) {
        ModelPickerContent(
            active = ProviderId.OPENCODE_GO, selectedModel = { if (it == ProviderId.OPENCODE_GO) "deepseek-v4.1-flash" else "deepseek-v4-pro" },
            hasKey = { true }, models = models, onPick = { _, _ -> }, onConnect = {}, onLoad = {},
        )
    }

    @Composable
    private fun ConnectSheet() = NeonSheetHost(open = true, onDismiss = {}, content = { Chat(emptyList(), title = null, model = null) }) {
        ConnectDeepSeekContent(key = "sk-3f9a2c71e0b84d", onKey = {}, busy = false, error = null, onGetKey = {}, onSave = {})
    }

    @Suppress("unused") private val keepTheme = ThemeMode.LIGHT
}

/** Stand-in for the system status bar so the shots read like the concept boards. */
@Composable
private fun FakeStatusBar() {
    val c = Neon.colors
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("23:12", style = NeonType.label().copy(fontFamily = Neon.fonts.medium, fontSize = 15.sp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            listOf(4, 6, 8, 10).forEach { h ->
                Box(Modifier.padding(end = 2.dp).size(3.dp, h.dp).clip(RoundedCornerShape(1.dp)).background(c.inkPrimary))
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(22.dp, 11.dp).clip(RoundedCornerShape(3.dp)).background(c.inkPrimary))
    }
}
