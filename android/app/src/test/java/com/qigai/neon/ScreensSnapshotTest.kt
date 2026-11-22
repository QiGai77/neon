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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
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
import com.qigai.neon.ui.connectors.*
import com.qigai.neon.ui.hands.*
import com.qigai.neon.ui.skills.*
import com.qigai.neon.ui.sandbox.*
import com.qigai.neon.ui.memory.*
import com.qigai.neon.ui.components.Risk
import com.qigai.neon.ui.sider.SandboxUi
import com.qigai.neon.ui.sider.HubCounts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import com.qigai.neon.ui.vault.VaultFormContent
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
                    Box(Modifier.fillMaxSize().padding(top = 44.dp, bottom = 20.dp)) { content() }
                    FakeStatusBar()
                }
            }
        }
        rule.mainClock.advanceTimeBy(advanceMs)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name-${if (dark) "dark" else "light"}.png")
    }

    // ───────── v0.4 home tabs ─────────

    @Composable
    private fun HomeFrame(tab: com.qigai.neon.ui.home.HomeTab, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(com.qigai.neon.ui.home.LocalBottomBarInset provides 72.dp) { content() }
            com.qigai.neon.ui.home.NavPill(
                tab, {}, chatBadge = 1, haze = null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 18.dp, end = 18.dp, bottom = 10.dp),
            )
        }
    }

    private val feed = com.qigai.neon.feed.FeedState(
        repos = listOf(
            com.qigai.neon.feed.FeedRepo("sample/agent-runtime", "Sample repository description for the snapshot, two lines long so the card wraps like a real one.", 2310, "Kotlin", "https://github.com", "AI Agent", "2026-11-12"),
            com.qigai.neon.feed.FeedRepo("sample/compose-glass", "Sample description.", 640, "Kotlin", "https://github.com", "Jetpack Compose", "2026-11-15"),
        ),
        posts = listOf(
            com.qigai.neon.feed.XPost("1", "sample_lab", "Sample Lab", null, "Sample post text for the snapshot. We are opening our MCP server so any AI Agent can call it, docs are live today.", null, 4820, System.currentTimeMillis() - 3 * 3_600_000L, "MCP", replies = 60, reposts = 27, verified = true),
            com.qigai.neon.feed.XPost("2", "sample_dev", "Sample Dev", null, "Second sample post, short.", null, 312, System.currentTimeMillis() - 26 * 3_600_000L, null, replies = 2),
        ),
    )

    private fun home(tab: com.qigai.neon.ui.home.HomeTab) = @Composable {
        HomeFrame(tab) {
            when (tab) {
                com.qigai.neon.ui.home.HomeTab.CHAT -> Chat(listOf(ask, answer, results))
                com.qigai.neon.ui.home.HomeTab.GLANCE -> com.qigai.neon.ui.glance.GlanceContent(feed, SettingsRepository.DEFAULT_INTERESTS, {}, {}, {}, {}, today = "11 月 22 日 星期日")
                com.qigai.neon.ui.home.HomeTab.SPACE -> com.qigai.neon.ui.space.SpaceContent(
                    com.qigai.neon.ui.space.SpaceData(
                        skillNames = listOf("账单整理", "查快递", "周报生成"), connectors = 8, connected = 3,
                        taskTitles = listOf("晨间早报"), tasksEnabled = 1, memory = listOf("SOUL.md", "USER.md", "GLOBAL.md"),
                        sandbox = com.qigai.neon.sandbox.SandboxStatus.Ready("aarch64", true), handsEnabled = true,
                    ),
                )
                com.qigai.neon.ui.home.HomeTab.ME -> com.qigai.neon.ui.me.MeContent(
                    com.qigai.neon.ui.me.MeData(
                        modelLabel = "DeepSeek V4 Pro",
                        quota = GoUsage(GoWindow(68.0, null, null), GoWindow(41.0, null, null), GoWindow(23.0, null, null), now),
                        opsCollapsed = true, version = "0.4.5", hands = true,
                        userCall = "QiGai", userName = "QiGai", userBio = "南京，独立开发者，在做 Neon",
                        approvalSummary = "总是允许，付款要指纹", chatStyle = com.qigai.neon.data.ChatStyle.BUBBLE, memoryCount = 12,
                    ),
                    com.qigai.neon.ui.me.MeActions(),
                )
            }
        }
    }

    private val lifeCards = listOf(
        com.qigai.neon.space.SpaceCard("train-1", "train", "full", mapOf("number" to "G7012", "from" to "南京南", "to" to "上海虹桥", "date" to "11月24日", "depart" to "08:12", "arrive" to "09:49", "seat" to "05车 12F", "gate" to "A3")),
        com.qigai.neon.space.SpaceCard("parcel-1", "parcel", "half", mapOf("carrier" to "顺丰", "number" to "SF1234567890", "status" to "派送中，快递员正在路上", "eta" to "今天 18:00 前")),
        com.qigai.neon.space.SpaceCard("nav-1", "nav", "half", mapOf("title" to "新街口", "address" to "南京市秦淮区中山路", "eta" to "18 分钟")),
        com.qigai.neon.space.SpaceCard("flight-1", "flight", "full", mapOf("airline" to "东航", "number" to "MU5102", "from" to "PVG", "to" to "PEK", "date" to "11月28日", "depart" to "10:00", "arrive" to "12:15", "gate" to "C12", "seat" to "32A")),
        com.qigai.neon.space.SpaceCard("skills", "skills"), com.qigai.neon.space.SpaceCard("sandbox", "sandbox"),
    )
    private val spaceData = com.qigai.neon.ui.space.SpaceData(
        skillNames = listOf("账单整理", "查快递", "周报生成"), connectors = 8, connected = 3,
        taskTitles = listOf("晨间早报"), tasksEnabled = 1, memory = listOf("SOUL.md", "USER.md", "GLOBAL.md"),
        sandbox = com.qigai.neon.sandbox.SandboxStatus.Ready("aarch64", true), handsEnabled = true,
    )
    private val mathAnswer = msg("a5", "assistant", listOf(
        ContentBlock.Text("欧拉公式把五个常数连在一起：\$e^{i\\pi} + 1 = 0\$，推广到任意角度是\n\n\$\$e^{i\\theta} = \\cos\\theta + i\\sin\\theta\$\$\n\n讲义做好了。"),
        ContentBlock.ToolUse("t5", "present_file", "{\"path\":\"/workspace/out/欧拉公式讲义.docx\"}"),
        ContentBlock.Text("\n\n[欧拉公式 维基百科](https://zh.wikipedia.org/wiki/欧拉公式)"),
    ), model = "deepseek-v4-pro", out = 286)
    private val upload = msg("u5", "user", listOf(ContentBlock.Attachment("课堂笔记.pdf", "/workspace/uploads/课堂笔记.pdf", "application/pdf", 482_133), ContentBlock.Text("按这份笔记讲讲欧拉公式，做一份 Word 讲义")), ago = 5000)
    @org.junit.Before fun initMath() { com.qigai.neon.markdown.MathRenderer.init(androidx.test.core.app.ApplicationProvider.getApplicationContext()) }
    @Test fun v043ChatDark() = darkShot("v043-chat", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { Chat(listOf(upload, mathAnswer), folded = true) } })
    @Test fun v043ThinkDark() = darkShot("v043-think", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { Chat(listOf(upload), busy = true, thinkOpen = true, effort = "max", attachments = listOf(com.qigai.neon.ui.chat.PendingAttachment("截图.png", "/workspace/uploads/截图.png", "image/png", 120_000))) } })
    @Test fun v043SpaceDark() = darkShot("v043-space", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.SPACE) { com.qigai.neon.ui.space.SpaceContent(spaceData, lifeCards) } })
    @Test fun v043SpaceEditDark() = darkShot("v043-space-edit", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.SPACE) { com.qigai.neon.ui.space.SpaceContent(spaceData, lifeCards, initialEdit = true) } })
    @Test fun v043ChatLight() = shot("v043-chat", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { Chat(listOf(upload, mathAnswer), folded = true) } })
    @Test fun v043ThinkLight() = shot("v043-think", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { Chat(listOf(upload), busy = true, thinkOpen = true, effort = "max", attachments = listOf(com.qigai.neon.ui.chat.PendingAttachment("截图.png", "/workspace/uploads/截图.png", "image/png", 120_000))) } })
    @Test fun v043SpaceEditLight() = shot("v043-space-edit", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.SPACE) { com.qigai.neon.ui.space.SpaceContent(spaceData, lifeCards, initialEdit = true) } })
    @Test fun v043SpaceLight() = shot("v043-space", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.SPACE) { com.qigai.neon.ui.space.SpaceContent(spaceData, lifeCards) } })
    private val liveStep = msg("a2", "assistant", listOf(ContentBlock.ToolUse("t9", "sandbox_exec", "{\"command\":\"sed -i 's/a/b/' app.py\"}")), status = "streaming")
    @Test fun v042FoldedDark() = darkShot("v042-chat-folded", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { Chat(listOf(ask, answer, results), folded = true) } })
    @Test fun v042LiveDark() = darkShot("v042-chat-live", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { Chat(listOf(ask, liveStep), folded = true, busy = true) } })
    @Test fun v04ChatLight() = shot("v04-01-chat", content = home(com.qigai.neon.ui.home.HomeTab.CHAT))
    @Test fun v04ChatDark() = darkShot("v04-01-chat", content = home(com.qigai.neon.ui.home.HomeTab.CHAT))
    @Test fun v04GlanceLight() = shot("v04-02-glance", content = home(com.qigai.neon.ui.home.HomeTab.GLANCE))
    @Test fun v04GlanceDark() = darkShot("v04-02-glance", content = home(com.qigai.neon.ui.home.HomeTab.GLANCE))
    @Test fun v04SpaceLight() = shot("v04-03-space", content = home(com.qigai.neon.ui.home.HomeTab.SPACE))
    @Test fun v04SpaceDark() = darkShot("v04-03-space", content = home(com.qigai.neon.ui.home.HomeTab.SPACE))

    // ───────── v0.4.5 我的、性格、关于你、动手前、对话样式 ─────────

    private val persona = com.qigai.neon.ui.me.PersonaState("Neon", "QiGai", com.qigai.neon.data.Tone.CRISP, "少用感叹号，技术词保留英文，先给结论再解释")
    private val about = com.qigai.neon.ui.me.AboutState(
        "QiGai", "南京", "独立开发者", "在做 Neon，一个安卓上的 AI 助手。熟悉 Kotlin 和 Compose，主力模型是 DeepSeek。周末喜欢骑车，常去玄武湖。",
        listOf("用中文", "代码给完整文件"),
    )
    private val perms = com.qigai.neon.guard.ApprovalSettings(
        mode = com.qigai.neon.guard.ApprovalMode.ALWAYS_ALLOW,
        overrides = mapOf(com.qigai.neon.guard.ActionType.PAYMENT to true),
    )
    private val billAsk = msg("u9", "user", listOf(ContentBlock.Text("帮我把本月账单整理成表格，再查一下快递到哪了")), ago = 9000)
    private val billAnswer = msg("a9", "assistant", listOf(
        ContentBlock.Thinking("读邮件，整理表格，查快递", durationMs = 6000),
        ContentBlock.ToolUse("t91", "gmail_list_messages", "{\"query\":\"账单\"}"),
        ContentBlock.ToolUse("t92", "sandbox_exec", "{\"command\":\"python3 bills.py\"}"),
        ContentBlock.Text("整理好了，4 封账单合计 ¥1,286.40。顺丰包裹在新街口派送中，今天 18:00 前送到。"),
        ContentBlock.ToolUse("t93", "present_file", "{\"path\":\"/workspace/out/11月账单.xlsx\"}"),
    ), ago = 5000, model = "deepseek-v4-pro", out = 212)
    private val billResults = msg("r9", "user", listOf(ContentBlock.ToolResult("t91", "4"), ContentBlock.ToolResult("t92", "ok"), ContentBlock.ToolResult("t93", "ok")), ago = 5200)
    private val remindAsk = msg("u10", "user", listOf(ContentBlock.Text("谢谢，明天提醒我还信用卡")), ago = 3000)
    private val remindAnswer = msg("a10", "assistant", listOf(ContentBlock.Text("好，明天 09:00 提醒你。")), ago = 2000, model = "deepseek-v4-pro", out = 12)

    @Composable
    private fun StyledChat(bubble: Boolean) = ChatContent(
        title = "账单与快递", modelLabel = "DeepSeek V4 Pro", messages = listOf(billAsk, billAnswer, billResults, remindAsk, remindAnswer), busy = false,
        input = "", onInput = {}, paletteOpen = false, onPalette = {}, effort = "high", onEffort = {}, voice = VoiceState(),
        onMenu = {}, onNewChat = {}, onModel = {}, onConnectDeepSeek = {}, onConnectGo = {}, onRegenerate = {},
        onMic = {}, onVoiceCancel = {}, onVoiceConfirm = {}, onPlus = {}, onSend = {}, onStop = {},
        skills = skills, opsCollapsed = true, bubble = bubble,
    )

    @Test fun v045MeDark() = darkShot("v045-01-me", content = home(com.qigai.neon.ui.home.HomeTab.ME))
    @Test fun v045MeLight() = shot("v045-01-me", content = home(com.qigai.neon.ui.home.HomeTab.ME))
    @Test fun v045PersonaDark() = darkShot("v045-02-persona") { com.qigai.neon.ui.me.PersonaContent(persona, {}, {}, {}) }
    @Test fun v045PersonaLight() = shot("v045-02-persona") { com.qigai.neon.ui.me.PersonaContent(persona, {}, {}, {}) }
    @Test fun v045AboutDark() = darkShot("v045-03-about") { com.qigai.neon.ui.me.AboutYouContent(about, 12, {}, {}, {}, {}) }
    @Test fun v045AboutLight() = shot("v045-03-about") { com.qigai.neon.ui.me.AboutYouContent(about, 12, {}, {}, {}, {}) }
    @Test fun v045PermsDark() = darkShot("v045-04-perms") { com.qigai.neon.ui.me.PermissionsContent(perms, true, {}) }
    @Test fun v045PermsLight() = shot("v045-04-perms") { com.qigai.neon.ui.me.PermissionsContent(perms, true, {}) }
    @Test fun v045ChatDocDark() = darkShot("v045-05-chat-doc", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { StyledChat(false) } })
    @Test fun v045ChatDocLight() = shot("v045-05-chat-doc", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { StyledChat(false) } })
    @Test fun v045ChatBubbleDark() = darkShot("v045-06-chat-bubble", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { StyledChat(true) } })
    @Test fun v045ChatBubbleLight() = shot("v045-06-chat-bubble", content = @Composable { HomeFrame(com.qigai.neon.ui.home.HomeTab.CHAT) { StyledChat(true) } })
    @Test fun v045VaultDark() = darkShot("v045-07-vault") {
        com.qigai.neon.ui.vault.VaultListContent(listOf(
            com.qigai.neon.vault.VaultMeta("vf_3a9c1e07b2", "login", "github.com", "github.com", "Qi•••7", true, 0, now),
            com.qigai.neon.vault.VaultMeta("vf_81d0c4aa5f", "cookie", "bilibili.com", "bilibili.com", "", false, 6, now),
        ), {}, {}, {}, {})
    }
    @Test fun v045VaultFormDark() = darkShot("v045-08-vault-form") {
        NeonSheetHost(open = true, onDismiss = {}, content = { StyledChat(true) }) {
            VaultFormContent(
                com.qigai.neon.ui.vault.VaultDraft(site = "github.com", user = "QiGai77", pass = "secret"), {},
                "要登录 GitHub 才能看你的私有仓库", siteLocked = true, onCancel = {}, onSave = {},
            )
        }
    }

    // ───────── sample data ─────────

    private fun msg(id: String, role: String, blocks: List<ContentBlock>, status: String = "done", ago: Long = 0, model: String? = null, out: Int? = null) =
        MessageEntity(id, "c1", role, BlocksJson.encode(blocks), now - ago, status = status, model = model, outputTokens = out)

    private val ask = msg("u1", "user", listOf(ContentBlock.Text("帮我把本月的账单邮件整理成表格存到 Drive，再查一下菜鸟快递到哪了")), ago = 9000)

    private val plan = "命中技能：\n/账单整理 /查快递\n\n规划步骤：\n1. 读取 Gmail 本月账单邮件\n2. 在 Alpine 沙箱用 python3 解析为 CSV\n3. 写入 Drive /Neon/附件/\n4. 用 Hands 打开菜鸟查询物流"

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
        model: String? = "DeepSeek V4 Pro",
        title: String? = "账单与快递",
        skillList: List<PaletteSkill> = skills,
        folded: Boolean = false,
        effort: String = "high",
        thinkOpen: Boolean = false,
        attachments: List<com.qigai.neon.ui.chat.PendingAttachment> = emptyList(),
    ) = ChatContent(
        title = title, modelLabel = model, messages = messages, busy = busy,
        input = input, onInput = {}, paletteOpen = palette, onPalette = {},
        effort = effort, onEffort = {}, voice = voice,
        onMenu = {}, onNewChat = {}, onModel = {}, onConnectDeepSeek = {}, onConnectGo = {}, onRegenerate = {},
        onMic = {}, onVoiceCancel = {}, onVoiceConfirm = {}, onPlus = {}, onSend = {}, onStop = {},
        skills = skillList, opsCollapsed = folded, attachments = attachments, initialThinkOpen = thinkOpen,
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
        sandbox = SandboxUi("Alpine aarch64", true, detail = "Python 3"),
        hubs = HubCounts(connectors = 5, skills = 8, memories = 12, runningTasks = 3),
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
            // the real drawer runs edge to edge under the status bar; mimic that inside the padded frame
            Box(Modifier.width(318.dp).requiredHeight(844.dp).offset(y = (-12).dp).background(Neon.colors.bgDrawer).padding(top = 44.dp, bottom = 20.dp)) { Drawer() }
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

    // ───────── v0.3 capability screens (ported backend) ─────────

    @Test fun s01e_light() = shot("S01e-palette-all") { Chat(listOf(ask, thinkingLive), input = "/", palette = true) }
    @Test fun s01e_dark() = darkShot("S01e-palette-all") { Chat(listOf(ask, thinkingLive), input = "/", palette = true) }

    @Test fun s03c_light() = shot("S03c-connectors") { Connectors() }
    @Test fun s03c_dark() = darkShot("S03c-connectors") { Connectors() }

    @Test fun s04h_light() = shot("S04h-hands-live") { HandsLive() }
    @Test fun s04h_dark() = darkShot("S04h-hands-live") { HandsLive() }

    @Test fun s05s_light() = shot("S05s-sentinel") { Sentinel() }
    @Test fun s05s_dark() = darkShot("S05s-sentinel") { Sentinel() }

    @Test fun s06k_light() = shot("S06k-skills") { Skills() }
    @Test fun s06k_dark() = darkShot("S06k-skills") { Skills() }

    @Test fun s07x_light() = shot("S07x-sandbox") { Sandbox() }
    @Test fun s07x_dark() = darkShot("S07x-sandbox") { Sandbox() }

    @Test fun s08m_light() = shot("S08m-memory-tasks") { MemoryTasks() }
    @Test fun s08m_dark() = darkShot("S08m-memory-tasks") { MemoryTasks() }

    @Composable
    private fun Connectors() = ConnectorsContent(
        connectors = listOf(
            ConnectorUi("g", ConnectorGroup.CLOUD, "Google", "Gmail、日历、Drive", ConnectorStatus.Connected("9 个工具"), monogram = "G"),
            ConnectorUi("n", ConnectorGroup.CLOUD, "Notion", "mcp.notion.com", ConnectorStatus.Authorizing("等待授权"), monogram = "N", badge = "MCP"),
            ConnectorUi("l", ConnectorGroup.LOCAL, "本地 MCP", "sqlite-mcp", ConnectorStatus.Connected("6 个工具"), icon = Lucide.Server, badge = "本地"),
            ConnectorUi("p", ConnectorGroup.LOCAL, "通知", "读取最近的通知", ConnectorStatus.Connected("已授权"), icon = Lucide.Smartphone),
            ConnectorUi("w", ConnectorGroup.AUTOMATION, "Webhook", "hooks.example.com", ConnectorStatus.Idle, icon = Lucide.Workflow, action = "测试"),
        ),
        onBack = {}, onSearch = {}, onOpen = {}, onAction = {}, onAddMcp = {},
    )

    @Composable
    private fun HandsLive() {
        Box(Modifier.fillMaxSize()) {
            FakeCourierApp()
            HandsCapsule(3, 8, "点击「查物流」", paused = false, onPause = {}, onStop = {}, onCollapse = {}, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
            SomMarker(7, Modifier.align(Alignment.TopEnd).padding(top = 300.dp, end = 30.dp))
            HandsEngineCard(
                HandsEngine.ACCESSIBILITY, setOf(HandsEngine.ACCESSIBILITY, HandsEngine.SHIZUKU),
                "运行正常", onEngine = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @Composable
    private fun Sentinel() = NeonSheetHost(open = true, onDismiss = {}, content = {
        HandsTaskContent(
            HandsTaskUi(
                "查快递并代付运费", 6, 8, blockedCountdown = -1,
                reasons = listOf(GuardReason("目标", "确认支付 ¥128.00", true), GuardReason("按钮：确认支付", ""), GuardReason("金额：¥128.00", "")),
                trace = listOf("打开菜鸟", "搜索快递单号", "进入详情", "查看物流", "点击「代付运费」", "点击「确认支付」"),
            ),
            onBack = {}, onMore = {},
        )
    }) {
        SentinelSheetContent(
            SentinelUi("确认这一步", "在菜鸟里点击", "操作", "确认支付 ¥128.00", "com.cainiao.wireless", true),
            onDeny = {}, onLease = {}, onAltAuth = {},
        )
    }

    @Composable
    private fun Skills() = SkillsContent(
        skills = listOf(
            SkillUi("账单整理", "bills", Lucide.Mail, Risk.LOW, listOf("Gmail", "Alpine", "Drive")),
            SkillUi("查快递", "track", Lucide.Truck, Risk.MEDIUM, listOf("Hands", "菜鸟")),
            SkillUi("一键代付", "pay", Lucide.Lock, Risk.HIGH, listOf("Hands", "Sentinel")),
            SkillUi("晨间早报", "brief", Lucide.Bell, Risk.LOW, listOf("Gmail", "Calendar")),
            SkillUi("周报生成", "weekly", Lucide.FileText, Risk.LOW, listOf("Drive", "Notion")),
        ),
        learned = LearnedRunUi("从上次操作生成技能", "11/21 15:02", listOf("读 Gmail", "Alpine CSV", "打开菜鸟", "查物流"), ""),
        driveSynced = 8, query = "", onQuery = {}, onBack = {}, onOpen = {}, onDistill = {}, onViewTrace = {},
    )

    @Composable
    private fun Sandbox() = SandboxContent(
        title = "Alpine 沙箱", subtitle = "就绪",
        facts = listOf("aarch64", "Python", "/workspace"),
        lines = listOf(
            TermLine(LineKind.PROMPT, "uname -a"),
            TermLine(LineKind.DIM, "Linux localhost 6.1.75-android14 aarch64"),
            TermLine(LineKind.PROMPT, "python3 parse_bills.py"),
            TermLine(LineKind.OUT, "[1/4] 读取 gmail/*.eml … 4 封"),
            TermLine(LineKind.OUT, "[2/4] 解析金额与到期日 … ok"),
            TermLine(LineKind.OK, "[3/4] 写入 bills_1121.csv ¥1,286.40"),
            TermLine(LineKind.OUT, "[4/4] 同步 Drive /Neon/附件/ … 0.8s"),
            TermLine(LineKind.PROMPT, "neon-mcp-cli status"),
            TermLine(LineKind.STATUS, "sqlite-mcp", "running", "stdio"),
            TermLine(LineKind.STATUS, "python-tools", "running", "stdio"),
            TermLine(LineKind.STATUS, "notion", "connecting", "oauth", live = false),
            TermLine(LineKind.PROMPT, "rm -rf /workspace/*"),
            TermLine(LineKind.DANGER, "ShellGuard 拦截: rm -rf /workspace/*"),
        ),
        input = "", onInput = {},
        blocked = ShellBlockUi("rm -rf /workspace/*", "会删除或破坏文件"),
        onBack = {}, onMore = {}, onKeepBlocked = {}, onRunAnyway = {}, onKey = {},
    )

    @Composable
    private fun MemoryTasks() = MemoryTasksContent(
        facts = listOf("住在杭州", "快递用菜鸟", "早报 8 点"),
        memorySynced = true,
        tasks = listOf(
            TaskUi("t1", "晨间早报", "每天 08:00", Lucide.Bell, true, ReceiptUi("08:00 成功", true, listOf(Triple("耗时", "4.2", "s")))),
            TaskUi("t2", "账单归档", "每月 1 日 09:00", Lucide.Mail, true),
            TaskUi("t3", "快递追踪", "每 2 小时", Lucide.Truck, true),
            TaskUi("t4", "周报生成", "每周五 18:00", Lucide.FileText, false),
        ),
        onBack = {}, onAdd = {}, onEditMemory = {}, onRemoveFact = {}, onAddFact = {}, onToggle = { _, _ -> }, onOpenReceipt = {}, onRerun = {},
    )

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


/** Stand-in for the third-party app Hands is driving (concept only). */
@Composable
private fun FakeCourierApp() {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().background(c.bgCanvas).padding(top = 120.dp, start = 18.dp, end = 18.dp)) {
        Text("‹  我的快递", style = NeonType.headline())
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(c.bgSurface))
        Spacer(Modifier.height(14.dp))
        Text("全部 6    待取件 1    派送中 2    运输中 2    已签收", style = NeonType.caption())
        Spacer(Modifier.height(12.dp))
        listOf("顺丰速运 · 派送中" to "预计今天 18:00 前送达", "中通快递 · 运输中" to "预计明天送达", "圆通速递 · 已签收" to "11 月 19 日已签收").forEach { (a, b) ->
            Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.bgSurface).padding(16.dp)) {
                Text(a, style = NeonType.label().copy(color = c.inkSecondary))
                Spacer(Modifier.height(6.dp))
                Text(b, style = NeonType.bodyMedium())
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}
