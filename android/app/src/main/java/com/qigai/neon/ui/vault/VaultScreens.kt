package com.qigai.neon.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qigai.neon.MainViewModel
import com.qigai.neon.ui.chat.NeonField
import com.qigai.neon.ui.chat.NeonSheetHost
import com.qigai.neon.ui.chat.SheetHeader
import com.qigai.neon.ui.components.ButtonKind
import com.qigai.neon.ui.components.PillButton
import com.qigai.neon.ui.home.GlassCircle
import com.qigai.neon.ui.icons.Lucide
import com.qigai.neon.ui.theme.Neon
import com.qigai.neon.ui.theme.NeonType
import com.qigai.neon.ui.theme.glass
import com.qigai.neon.ui.theme.neonBackdrop
import com.qigai.neon.vault.Vault
import com.qigai.neon.vault.VaultItem
import com.qigai.neon.vault.VaultMeta
import com.qigai.neon.vault.siteOf
import kotlinx.coroutines.launch

// ───────── the form ─────────

/** What the user types into a 安全表单. Lives only in this composable and the encrypted store. */
data class VaultDraft(val site: String = "", val kind: String = "login", val user: String = "", val pass: String = "", val cookies: String = "", val label: String = "")

@Composable
fun ColumnScope.VaultFormContent(
    draft: VaultDraft,
    onDraft: (VaultDraft) -> Unit,
    reason: String?,
    siteLocked: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val c = Neon.colors
    SheetHeader(Lucide.Lock, "安全表单", draft.site.ifBlank { "网站登录" })
    if (!reason.isNullOrBlank()) Text(reason, style = NeonType.label().copy(color = c.inkSecondary), modifier = Modifier.padding(top = 12.dp))
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!siteLocked) NeonField(draft.site, { onDraft(draft.copy(site = it)) }, "网站，如 github.com")
        com.qigai.neon.ui.home.Segmented(
            listOf("账号密码", "Cookie"), if (draft.kind == "cookie") 1 else 0,
            onSelect = { onDraft(draft.copy(kind = if (it == 1) "cookie" else "login")) },
        )
        if (draft.kind == "login") {
            NeonField(draft.user, { onDraft(draft.copy(user = it)) }, "账号、邮箱或手机号")
            NeonField(draft.pass, { onDraft(draft.copy(pass = it)) }, "密码", secret = true)
        } else {
            NeonField(draft.cookies, { onDraft(draft.copy(cookies = it)) }, "粘贴 Cookie，如 sid=…; token=…", secret = false, minLines = 3)
        }
    }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(Lucide.Lock, null, Modifier.padding(top = 1.dp).size(13.dp), tint = c.inkTertiary)
        Spacer(Modifier.width(6.dp))
        Text("加密存在这台手机上，Neon 只拿到编号，看不到这些值", style = NeonType.caption().copy(color = c.inkTertiary))
    }
    Spacer(Modifier.height(18.dp))
    val ok = siteOf(draft.site).isNotBlank() && if (draft.kind == "login") draft.user.isNotBlank() && draft.pass.isNotEmpty() else draft.cookies.contains('=')
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton("取消", kind = ButtonKind.Secondary, height = 44.dp, modifier = Modifier.weight(1f), onClick = onCancel)
        PillButton("保存", height = 44.dp, enabled = ok, modifier = Modifier.weight(1f), onClick = onSave)
    }
}

private fun VaultDraft.toItem(id: String?, base: VaultItem?) = VaultItem(
    id = id ?: Vault.newId(), kind = kind, site = siteOf(site),
    label = base?.label?.ifBlank { null } ?: siteOf(site),
    username = if (kind == "login") user.trim() else base?.username.orEmpty(),
    password = if (kind == "login") pass else base?.password.orEmpty(),
    cookies = if (kind == "cookie") cookies.trim() else base?.cookies.orEmpty(),
)

/** Shows the secure form whenever Neon asks for one (vault_request), over everything else. */
@Composable
fun VaultFormHost(vm: MainViewModel, content: @Composable () -> Unit) {
    val gate = vm.c.vaultForms
    val pending by gate.pending.collectAsState()
    var draft by remember(pending?.id) {
        val base = pending?.existingId?.let { vm.c.vault.get(it) }
        mutableStateOf(VaultDraft(site = pending?.site.orEmpty(), kind = pending?.kind ?: "login", user = base?.username.orEmpty()))
    }
    NeonSheetHost(open = pending != null, onDismiss = { gate.submit(null) }, content = content) {
        val r = pending
        if (r != null) VaultFormContent(
            draft, { draft = it }, r.reason, siteLocked = true,
            onCancel = { gate.submit(null) },
            onSave = {
                val base = r.existingId?.let { vm.c.vault.get(it) }
                gate.submit(draft.toItem(base?.id, base))
            },
        )
    }
}

// ───────── the list ─────────

@Composable
fun VaultRoute(vm: MainViewModel, onBack: () -> Unit, onBrowser: () -> Unit) {
    val items by vm.c.vault.items.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    var draft by remember { mutableStateOf(VaultDraft()) }
    NeonSheetHost(open = adding, onDismiss = { adding = false }, content = {
        VaultListContent(items, onBack, onAdd = { draft = VaultDraft(); adding = true }, onDelete = { vm.c.vault.remove(it) }, onBrowser = onBrowser)
    }) {
        VaultFormContent(draft, { draft = it }, null, siteLocked = false, onCancel = { adding = false }, onSave = {
            vm.c.vault.save(draft.toItem(null, null)); adding = false
        })
    }
}

@Composable
fun VaultListContent(items: List<VaultMeta>, onBack: () -> Unit, onAdd: () -> Unit, onDelete: (String) -> Unit, onBrowser: () -> Unit) {
    val c = Neon.colors
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding()) {
        VaultBar(onBack, onAdd)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("网站的账号密码和 Cookie 加密存在这台手机上。Neon 登录时用编号调取，看不到具体的值。", style = NeonType.label().copy(color = c.inkSecondary), modifier = Modifier.padding(horizontal = 6.dp))
            if (items.isEmpty()) {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Lucide.Lock, null, Modifier.size(26.dp), tint = c.inkSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("还没有安全表单", style = NeonType.item())
                    Text("Neon 需要登录时会请你填，也可以右上角手动添加", style = NeonType.caption())
                }
            } else Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp))) {
                items.forEachIndexed { i, m ->
                    if (i > 0) Box(Modifier.padding(start = 64.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(c.borderCard))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(com.qigai.neon.ui.theme.Glass.tokens.tile), contentAlignment = Alignment.Center) {
                            Text(m.site.firstOrNull()?.uppercaseChar()?.toString() ?: "?", style = NeonType.bodyMedium())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.site, style = NeonType.item(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (m.kind == "login") "账号 ${m.user}" else "Cookie ${m.cookieCount} 项") + "  ${m.id}",
                                style = NeonType.tnum(NeonType.caption()), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { onDelete(m.id) }, contentAlignment = Alignment.Center) {
                            Icon(Lucide.Trash2, "删除", Modifier.size(17.dp), tint = c.inkSecondary)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).glass(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onBrowser).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Globe, null, Modifier.size(20.dp), tint = c.inkPrimary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("内置浏览器", style = NeonType.item())
                    Text("看 Neon 在操作的网页，或登录后保存", style = NeonType.caption())
                }
                Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = c.inkSecondary)
            }
        }
    }
}

@Composable
private fun VaultBar(onBack: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(48.dp)) {
        GlassCircle(Lucide.ChevronLeft, "返回", size = 44.dp, onClick = onBack)
        Text("安全表单", style = NeonType.title().copy(fontFamily = Neon.fonts.medium), modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd)) { GlassCircle(Lucide.Plus, "添加", size = 44.dp, onClick = onAdd) }
    }
}

// ───────── the browser ─────────

@Composable
fun BrowserRoute(vm: MainViewModel, onBack: () -> Unit) {
    val b = vm.c.browser
    val st by b.state.collectAsState()
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(st.url) { if (st.url.isNotBlank()) url = st.url }
    val c = Neon.colors
    Column(Modifier.fillMaxSize().neonBackdrop().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassCircle(Lucide.ChevronLeft, "返回", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                NeonField(url, { url = it }, "输入网址")
            }
            Spacer(Modifier.width(8.dp))
            GlassCircle(Lucide.ArrowRight, "打开", size = 40.dp) {
                val u = url.trim()
                if (u.isNotBlank()) scope.launch { b.open(u) }
            }
        }
        if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.accent)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { b.webView().also { (it.parent as? android.view.ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("后退", kind = ButtonKind.Secondary, icon = Lucide.ArrowLeft, height = 42.dp, modifier = Modifier.weight(1f)) {
                b.webView().let { if (it.canGoBack()) it.goBack() }
            }
            PillButton("保存登录状态", icon = Lucide.Lock, height = 42.dp, enabled = st.url.startsWith("http"), modifier = Modifier.weight(1.4f)) {
                scope.launch {
                    val existing = vm.c.vault.forSite(st.url).firstOrNull { it.kind == "cookie" }?.id
                    val saved = b.saveLogin(existing, null)
                    vm.toast(if (saved != null) "已存为安全表单 ${saved.id}" else "这个页面还没有登录状态")
                }
            }
        }
    }
}
