package com.qigai.neon.ui.glance

import androidx.compose.foundation.border

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qigai.neon.MainViewModel
import com.qigai.neon.feed.FeedRepo
import com.qigai.neon.ui.components.ButtonKind
import com.qigai.neon.ui.components.IconSquircle
import com.qigai.neon.ui.components.NetImage
import com.qigai.neon.ui.components.NeonCard
import com.qigai.neon.ui.components.PillButton
import com.qigai.neon.ui.home.GlassChip
import com.qigai.neon.ui.home.GlassCircle
import com.qigai.neon.ui.home.LocalBottomBarInset
import com.qigai.neon.ui.home.PageHeader
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Glass
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.neonBackdrop
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Filter(val label: String) { ALL("全部"), GAP("信息差"), X("X"), GITHUB("GitHub") }

/**
 * 速览: what Neon pushes to you. 信息差 (asks Neon to dig), X (new posts from accounts you pick), GitHub (new repos
 * that match your interests, ranked by stars). Every card says why it is here and can be handed to Neon.
 */
@Composable
fun GlanceScreen(vm: MainViewModel, onAsk: (String) -> Unit) {
    val feed by vm.feed.collectAsState()
    val s by vm.settings.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    LaunchedEffect(s.interests, s.xHandles) { vm.refreshFeed() }
    GlanceContent(
        feed, s.interests,
        onAsk = onAsk, onRefresh = { vm.refreshFeed(force = true) }, onEdit = { editing = true },
        onOpen = { url -> runCatching { uri.openUri(url) } },
    )
    if (editing) InterestsSheet(s.interests, s.xHandles, onDismiss = { editing = false }) { i, h ->
        vm.setInterests(i); vm.setXHandles(h); editing = false
    }
}

@Composable
fun GlanceContent(
    feed: com.qigai.neon.feed.FeedState,
    interests: List<String>,
    onAsk: (String) -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onOpen: (String) -> Unit,
    today: String = LocalDate.now().format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)),
) {
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val bottom = LocalBottomBarInset.current
    LazyColumn(
        Modifier.fillMaxSize().neonBackdrop().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottom + 24.dp),
    ) {
        item {
            PageHeader("速览", overline = today) {
                GlassCircle(Lucide.SlidersHorizontal, "推送什么", onClick = onEdit)
                GlassCircle(Lucide.RefreshCw, "刷新", onClick = onRefresh)
            }
        }
        item {
            LazyRow(Modifier.padding(top = 6.dp, bottom = 12.dp), contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries) { f -> GlassChip(f.label, f == filter) { filter = f } }
                item { GlassChip("兴趣", false, icon = Lucide.Plus, onClick = onEdit) }
            }
        }
        if (filter == Filter.ALL || filter == Filter.GAP) item {
            Box(Modifier.padding(bottom = 8.dp)) { GapCard(interests) { onAsk(gapPrompt(interests)) } }
        }
        if (filter == Filter.X) {
            when {
                feed.posts.isEmpty() && feed.loading -> item { Hint("正在取你关注账号的新帖") }
                feed.posts.isEmpty() && feed.xError != null -> item { Hint("X 暂时没取到：${feed.xError}", action = "重试", onAction = onRefresh) }
                feed.posts.isEmpty() -> item { Hint("这些账号最近一周没有新帖，加几个你常看的账号", action = "加账号", onAction = onEdit) }
                else -> items(feed.posts, key = { "x" + it.id }) { p -> PostCard(p, onOpen = { onOpen(p.url) }, onAsk = { onAsk(postPrompt(p)) }) }
            }
        }
        if (filter == Filter.ALL && (feed.posts.isNotEmpty() || feed.repos.isNotEmpty())) {
            val mixed = buildList<Any> {
                val a = feed.posts.iterator(); val b = feed.repos.iterator()
                while (a.hasNext() || b.hasNext()) {
                    repeat(2) { if (a.hasNext()) add(a.next()) }
                    if (b.hasNext()) add(b.next())
                }
            }
            items(mixed, key = { if (it is com.qigai.neon.feed.XPost) "x" + it.id else (it as FeedRepo).fullName }) { e ->
                when (e) {
                    is com.qigai.neon.feed.XPost -> PostCard(e, onOpen = { onOpen(e.url) }, onAsk = { onAsk(postPrompt(e)) })
                    is FeedRepo -> Box(Modifier.padding(vertical = 10.dp)) { RepoCard(e, onOpen = { onOpen(e.url) }, onAsk = { onAsk(repoPrompt(e)) }) }
                }
            }
        }
        if (filter == Filter.ALL && feed.posts.isEmpty() && feed.repos.isEmpty()) item {
            Hint(if (feed.loading) "正在按你的兴趣挑内容" else "暂时没取到内容", action = if (feed.loading) null else "重试", onAction = onRefresh)
        }
        if (filter == Filter.GITHUB) {
            when {
                feed.repos.isEmpty() && feed.loading -> item { Hint("正在按你的兴趣找新项目") }
                feed.repos.isEmpty() && feed.error != null -> item {
                    Hint("GitHub 暂时没取到：${feed.error}", action = "重试", onAction = onRefresh)
                }
                feed.repos.isEmpty() -> item { Hint("最近两周没有匹配你兴趣的新项目，换几个兴趣试试", action = "改兴趣", onAction = onEdit) }
                else -> items(feed.repos, key = { it.fullName }) { r ->
                    Box(Modifier.padding(vertical = 6.dp)) { RepoCard(r, onOpen = { onOpen(r.url) }, onAsk = { onAsk(repoPrompt(r)) }) }
                }
            }
        }
    }
}

private fun gapPrompt(interests: List<String>) =
    "按我的兴趣（${interests.joinToString("、")}），去 X、Hacker News 和 GitHub 找最近 24 小时里大多数人还没注意到、但对我有用的信息差。挑 3 条，每条说清楚是什么、为什么对我有用，附上原始链接。"

private fun postPrompt(p: com.qigai.neon.feed.XPost) =
    "帮我看看这条 X 帖子：${p.url}\n@${p.handle}：${p.text}\n讲了什么、背景是什么、对我有没有用。"

private fun repoPrompt(r: FeedRepo) =
    "帮我看看这个 GitHub 项目：${r.url}\n它做什么、靠不靠谱、和我的 Neon 项目有没有关系，值不值得试。"

@Composable
private fun GapCard(interests: List<String>, onDig: () -> Unit) {
    val c = Neon.colors
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(c.terminalBg.copy(alpha = if (c.isDark) 0.62f else 0.86f)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Zap, null, Modifier.size(16.dp), tint = c.lime)
            Spacer(Modifier.width(6.dp))
            Text("信息差", style = NeonType.caption().copy(color = c.lime))
        }
        Spacer(Modifier.height(10.dp))
        Text("让 Neon 去 X、Hacker News 和 GitHub 找大多数人还没注意到的消息", style = NeonType.bodyMedium().copy(color = c.terminalInk))
        Spacer(Modifier.height(6.dp))
        Text("按你的 ${interests.size} 个兴趣挑，每条附原始链接", style = NeonType.caption().copy(color = c.terminalDim))
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.height(38.dp).clip(CircleShape).background(c.lime).clickable(onClick = onDig).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Sparkles, null, Modifier.size(15.dp), tint = Color(0xFF0B0B0C))
            Spacer(Modifier.width(6.dp))
            Text("现在去找", style = NeonType.label().copy(color = Color(0xFF0B0B0C), fontFamily = Neon.fonts.medium))
        }
    }
}

private val XBlue = Color(0xFF1D9BF0)

/** One post the way X draws it: avatar on the left, name, badge, handle and time on one line, text, media, the action row. */
@Composable
private fun PostCard(p: com.qigai.neon.feed.XPost, onOpen: () -> Unit, onAsk: () -> Unit) {
    val c = Neon.colors
    var expanded by rememberSaveable(p.id) { mutableStateOf(false) }
    var overflow by remember(p.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
            val av = Modifier.size(42.dp).clip(CircleShape).background(Glass.tokens.tile)
            if (p.avatar != null) NetImage(p.avatar, av) else Box(av)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = NeonType.item().copy(fontFamily = Neon.fonts.medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.verified) {
                        Spacer(Modifier.width(3.dp))
                        VerifiedBadge()
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("@${p.handle} · ${xAgo(p.createdAt)}", style = NeonType.secondary().copy(color = c.inkTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                  }
                    Icon(Lucide.Ellipsis, null, Modifier.size(18.dp), tint = c.inkTertiary)
                }
                p.matched?.let { m ->
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Sparkles, null, Modifier.size(13.dp), tint = c.inkTertiary)
                        Spacer(Modifier.width(4.dp))
                        Text("和你关注的 $m 有关", style = NeonType.caption().copy(color = c.inkTertiary))
                    }
                }
                Text(
                    p.text,
                    style = NeonType.body().copy(fontSize = NeonType.item().fontSize, lineHeight = NeonType.secondary().lineHeight * 1.06f),
                    maxLines = if (expanded) Int.MAX_VALUE else 7, overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (overflow && !expanded) Text(
                    "显示更多", style = NeonType.item().copy(color = XBlue),
                    modifier = Modifier.padding(top = 2.dp).clickable { expanded = true },
                )
                p.image?.let {
                    Spacer(Modifier.height(10.dp))
                    NetImage(
                        it, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
                            .border(1.dp, c.borderCard, RoundedCornerShape(16.dp)).background(Glass.tokens.tile),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    XAction(Lucide.MessageCircle, p.replies, "回复", Modifier.weight(1f), onOpen)
                    XAction(Lucide.Repeat2, p.reposts, "转帖", Modifier.weight(1f), onOpen)
                    XAction(Lucide.Heart, p.likes, "喜欢", Modifier.weight(1f), onOpen)
                    XAction(Lucide.Sparkles, null, "问 Neon", Modifier.weight(1.3f), onAsk, label = "问 Neon")
                    XAction(Lucide.Share2, null, "分享", Modifier, onOpen)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.borderCard.copy(alpha = 0.6f)))
    }
}

@Composable
private fun VerifiedBadge() {
    Box(Modifier.size(16.dp).clip(CircleShape).background(XBlue), contentAlignment = Alignment.Center) {
        Icon(Lucide.Check, "已认证", Modifier.size(10.dp), tint = Color.White)
    }
}

@Composable
private fun XAction(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int?, desc: String, modifier: Modifier, onClick: () -> Unit, label: String? = null) {
    val c = Neon.colors
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, desc, Modifier.size(18.dp), tint = c.inkTertiary)
        val t = label ?: count?.takeIf { it > 0 }?.let { formatStars(it) }
        if (t != null) {
            Spacer(Modifier.width(4.dp))
            Text(t, style = NeonType.tnum(NeonType.caption().copy(color = c.inkTertiary)), maxLines = 1)
        }
    }
}

private fun xAgo(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "${m}分钟"
        m < 60 * 24 -> "${m / 60}小时"
        m < 60 * 24 * 7 -> "${m / 1440}天"
        else -> java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).let { "${it.monthValue}月${it.dayOfMonth}日" }
    }
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 60 * 24 -> "${m / 60} 小时前"
        else -> "${m / 1440} 天前"
    }
}

@Composable
private fun RepoCard(r: FeedRepo, onOpen: () -> Unit, onAsk: () -> Unit) {
    val c = Neon.colors
    NeonCard(Modifier.padding(horizontal = 16.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSquircle(Lucide.GitBranch, size = 34.dp, iconSize = 17.dp, bg = Glass.tokens.tile)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.fullName, style = NeonType.code().copy(fontSize = NeonType.label().fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("GitHub  ${r.createdAt} 新建", style = NeonType.caption())
            }
            Icon(Lucide.Star, null, Modifier.size(14.dp), tint = c.inkSecondary)
            Spacer(Modifier.width(4.dp))
            Text(formatStars(r.stars), style = NeonType.tnum(NeonType.label()))
        }
        if (r.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(r.description, style = NeonType.body().copy(fontSize = NeonType.item().fontSize, lineHeight = NeonType.secondary().lineHeight), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.chipActive.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Sparkles, null, Modifier.size(14.dp), tint = c.onChipActive)
            Spacer(Modifier.width(6.dp))
            Text("匹配你的兴趣 ${r.matched}", style = NeonType.caption().copy(color = c.onChipActive))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            r.language?.let { Text(it, style = NeonType.caption()) }
            Spacer(Modifier.weight(1f))
            PillButton("打开", kind = ButtonKind.Secondary, icon = Lucide.ExternalLink, height = 34.dp, onClick = onOpen)
            Spacer(Modifier.width(8.dp))
            PillButton("问 Neon", icon = Lucide.MessageCircle, height = 34.dp, onClick = onAsk)
        }
    }
}

private fun formatStars(n: Int) = if (n >= 1000) String.format(Locale.US, "%.1fk", n / 1000f) else "$n"

@Composable
private fun Hint(text: String, action: String? = null, onAction: () -> Unit = {}) {
    NeonCard(Modifier.padding(horizontal = 16.dp)) {
        Text(text, style = NeonType.secondary())
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            PillButton(action, kind = ButtonKind.Secondary, height = 34.dp, onClick = onAction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InterestsSheet(current: List<String>, handles: List<String>, onDismiss: () -> Unit, onSave: (List<String>, List<String>) -> Unit) {
    val c = Neon.colors
    val list = remember { mutableStateListOf<String>().apply { addAll(current) } }
    val xs = remember { mutableStateListOf<String>().apply { addAll(handles) } }
    var draft by remember { mutableStateOf("") }
    var xDraft by remember { mutableStateOf("") }
    fun add() { val t = draft.trim(); if (t.isNotEmpty() && t !in list) list.add(t); draft = "" }
    fun addX() {
        val t = xDraft.trim().removePrefix("https://").removePrefix("x.com/").removePrefix("twitter.com/").removePrefix("@").substringBefore('/').substringBefore('?')
        if (t.isNotEmpty() && xs.none { it.equals(t, true) }) xs.add(t); xDraft = ""
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Glass.tokens.fillStrong.copy(alpha = 0.96f),
        scrimColor = c.scrim.copy(alpha = 0.35f),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("推送什么给你", style = NeonType.headline())
            Spacer(Modifier.height(4.dp))
            Text("点一下去掉。兴趣用来挑 GitHub 新项目和信息差，X 账号的新帖直接推给你", style = NeonType.caption())
            Spacer(Modifier.height(14.dp))
            Text("兴趣", style = NeonType.overline())
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { t -> GlassChip(t, true, icon = Lucide.X) { list.remove(t) } }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(Glass.tokens.tile).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) Text("加一个兴趣，比如 Rust 或 出海", style = NeonType.label().copy(color = c.inkTertiary))
                    BasicTextField(
                        draft, { draft = it }, singleLine = true,
                        textStyle = NeonType.label(), cursorBrush = SolidColor(c.inkPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PillButton("添加", kind = ButtonKind.Secondary, height = 32.dp, enabled = draft.isNotBlank()) { add() }
            }
            Spacer(Modifier.height(18.dp))
            Text("X 账号", style = NeonType.overline())
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                xs.forEach { t -> GlassChip("@$t", true, icon = Lucide.X) { xs.remove(t) } }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(Glass.tokens.tile).padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (xDraft.isEmpty()) Text("账号名或主页链接，比如 karpathy", style = NeonType.label().copy(color = c.inkTertiary))
                    BasicTextField(
                        xDraft, { xDraft = it }, singleLine = true,
                        textStyle = NeonType.label(), cursorBrush = SolidColor(c.inkPrimary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addX() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PillButton("添加", kind = ButtonKind.Secondary, height = 32.dp, enabled = xDraft.isNotBlank()) { addX() }
            }
            Spacer(Modifier.height(18.dp))
            PillButton("保存", modifier = Modifier.fillMaxWidth(), height = 46.dp, enabled = list.isNotEmpty()) { onSave(list.toList(), xs.toList()) }
        }
    }
}
