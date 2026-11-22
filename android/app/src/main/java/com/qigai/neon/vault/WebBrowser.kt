package com.qigai.neon.vault

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

data class BrowserState(val url: String = "", val title: String = "", val loading: Boolean = false)

/** One element Neon can act on, as listed in a page snapshot. */
data class WebElement(val index: Int, val tag: String, val type: String, val label: String, val password: Boolean)

/**
 * Neon's built-in browser: one WebView shared by the agent and the 浏览器 screen.
 * It logs in with 安全表单 by planting cookies or filling the login fields in page JS;
 * neither value is ever returned to the model.
 */
class WebBrowser(private val app: Context, private val vault: Vault) {
    private val json = Json { ignoreUnknownKeys = true }
    private var view: WebView? = null
    private var loaded: CompletableDeferred<Unit>? = null
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    private var elements: List<WebElement> = emptyList()

    /** Site whose login just went through in this browser and is not saved yet; the screen offers to keep it. */
    private val _offerSave = MutableStateFlow<String?>(null)
    val offerSave: StateFlow<String?> = _offerSave.asStateFlow()
    @Volatile private var pendingLogin: String? = null
    private val declined = HashSet<String>()
    val cdp = CdpBridge()

    fun dismissOffer(forever: Boolean) {
        _offerSave.value?.let { if (forever) declined.add(it) }
        _offerSave.value = null
    }

    /** Called from page JS when a form with a password field is submitted. Carries no field values. */
    private inner class LoginProbe {
        @android.webkit.JavascriptInterface
        fun submitted(url: String) { pendingLogin = runCatching { siteOf(url) }.getOrNull() }
    }

    private val probeJs = """(function(){if(window.__neonProbe)return;window.__neonProbe=1;
      function hasPw(f){return f&&f.querySelector&&f.querySelector('input[type=password]');}
      function fire(){try{NeonLogin.submitted(location.href);}catch(e){}}
      document.addEventListener('submit',function(e){if(hasPw(e.target))fire();},true);
      document.addEventListener('click',function(e){var b=e.target&&e.target.closest&&e.target.closest('button,input[type=submit],[role=button]');
        if(!b)return;var f=b.closest('form')||document;if(f.querySelector('input[type=password]')&&f.querySelector('input[type=password]').value)fire();},true);
      document.addEventListener('keydown',function(e){if(e.key==='Enter'&&e.target&&e.target.type==='password'&&e.target.value)fire();},true);
    })()"""

    private fun checkLoginDone(url: String) {
        val site = pendingLogin ?: return
        if (!url.startsWith("http")) return
        val now = runCatching { siteOf(url) }.getOrNull() ?: return
        // still on a page asking for the password: not done yet
        if (now != site && !sameSite(now, site) && !now.contains("google") && !site.contains("google")) return
        val cookies = CookieManager.getInstance().getCookie(url)
        if (cookies.isNullOrBlank()) return
        pendingLogin = null
        if (site in declined || now in declined) return
        val saved = vault.forSite(url).firstOrNull { it.kind == "cookie" }
        if (saved?.cookies == cookies) return
        _offerSave.value = now
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView {
        view?.let { return it }
        val w = WebView(app)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.userAgentString = w.settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.addJavascriptInterface(LoginProbe(), "NeonLogin")
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                _state.value = BrowserState(url, view.title.orEmpty(), true)
            }
            override fun onPageFinished(view: WebView, url: String) {
                _state.value = BrowserState(url, view.title.orEmpty(), false)
                loaded?.complete(Unit)
                view.evaluateJavascript(probeJs, null)
                view.postDelayed({ view.evaluateJavascript("!!document.querySelector('input[type=password]')") { has ->
                    if (has != "true") checkLoginDone(url)
                } }, 800)
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty()
                return scheme != "http" && scheme != "https"
            }
        }
        view = w
        return w
    }

    private suspend fun <T> main(block: suspend () -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun js(script: String): String = main {
        suspendCancellableCoroutine { cont -> webView().evaluateJavascript(script) { cont.resume(it ?: "null") } }
    }

    /** JS string literal for a value, so a password never needs escaping by hand. */
    private fun lit(s: String) = JsonPrimitive(s).toString()

    private fun plantCookies(item: VaultItem) {
        if (item.cookies.isBlank()) return
        val cm = CookieManager.getInstance()
        item.cookies.split(';').map { it.trim() }.filter { '=' in it }.forEach { pair ->
            cm.setCookie("https://${item.site}", "$pair; Domain=.${item.site}; Path=/")
        }
        cm.flush()
    }

    suspend fun open(url: String, vaultId: String? = null): String {
        val target = if ("://" in url) url else "https://$url"
        val item = vaultId?.let { vault.get(it) ?: return "ERROR: 没有编号为 $it 的安全表单" }
        if (item != null && !sameSite(siteOf(target), item.site)) return "ERROR: 安全表单 ${item.id} 属于 ${item.site}，不能用在 ${siteOf(target)}"
        main { item?.let { plantCookies(it) } }
        load(target)
        var note = ""
        if (item != null && item.kind == "login" && item.username.isNotEmpty()) {
            note = if (fillLogin(item)) "\n[已用安全表单 ${item.id} 填好账号和密码，按页面上的登录按钮提交]" else "\n[页面上没找到登录框，可以先点进登录页再调用 web_fill]"
        } else if (item != null) note = "\n[已注入安全表单 ${item.id} 的 Cookie]"
        return snapshot() + note
    }

    private suspend fun load(url: String) {
        val d = CompletableDeferred<Unit>()
        loaded = d
        main { webView().loadUrl(url) }
        withTimeoutOrNull(25_000) { d.await() }
        delay(600) // let late scripts render
    }

    private fun sameSite(host: String, site: String) = host == site || host.endsWith(".$site") || site.endsWith(".$host")

    /** Fills the visible username and password fields from the item. Returns whether a password field was found. */
    suspend fun fillLogin(item: VaultItem): Boolean {
        val host = siteOf(_state.value.url)
        if (!sameSite(host, item.site)) return false
        val r = js(
            """(function(u,p){
              function vis(e){var r=e.getBoundingClientRect();return r.width>0&&r.height>0;}
              function set(e,v){var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(e,v);
                e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}
              var pw=[].slice.call(document.querySelectorAll('input[type=password]')).filter(vis)[0];
              var ins=[].slice.call(document.querySelectorAll('input')).filter(function(e){return vis(e)&&/^(text|email|tel|)$/.test(e.type||'');});
              var user=null;
              if(pw){var all=[].slice.call(document.querySelectorAll('input'));var i=all.indexOf(pw);
                for(var k=i-1;k>=0;k--){if(ins.indexOf(all[k])>=0){user=all[k];break;}}}
              if(!user) user=ins.filter(function(e){return /user|login|email|account|phone|name/i.test((e.name||'')+(e.id||'')+(e.autocomplete||'')+(e.placeholder||''));})[0]||null;
              if(user&&u) set(user,u);
              if(pw&&p) set(pw,p);
              return !!pw||!!user;
            })(${lit(item.username)},${lit(item.password)})""".trimIndent(),
        )
        return r == "true"
    }

    /** Title, url, visible text and numbered elements. Input values are never read. */
    suspend fun snapshot(): String {
        val raw = js(
            """(function(){
              var sel='a,button,input,textarea,select,[role=button],[role=link],[onclick]';
              var out=[],n=0;
              document.querySelectorAll('[data-neon-i]').forEach(function(e){e.removeAttribute('data-neon-i');});
              document.querySelectorAll(sel).forEach(function(e){
                var r=e.getBoundingClientRect(); if(r.width<=0||r.height<=0) return;
                if(e.type==='hidden') return;
                var label;
                if(e.tagName==='INPUT'||e.tagName==='TEXTAREA'||e.tagName==='SELECT') label=e.getAttribute('aria-label')||e.placeholder||e.name||e.id||'';
                else label=e.innerText||e.value||e.getAttribute('aria-label')||e.title||'';
                label=(label+'').replace(/\s+/g,' ').trim().slice(0,60);
                e.setAttribute('data-neon-i',n);
                out.push({i:n,tag:e.tagName.toLowerCase(),type:(e.type||'')+'',label:label});
                n++; if(n>=150) return;
              });
              var text=(document.body?document.body.innerText:'').replace(/\n{3,}/g,'\n\n').slice(0,5000);
              return JSON.stringify({title:document.title,url:location.href,text:text,items:out});
            })()""".trimIndent(),
        )
        val str = runCatching { json.parseToJsonElement(raw).jsonPrimitive.content }.getOrNull() ?: return "ERROR: 页面还没加载好"
        val o = runCatching { json.parseToJsonElement(str).jsonObject }.getOrNull() ?: return "ERROR: 读取页面失败"
        elements = o["items"]?.jsonArray?.map { e ->
            val m = e.jsonObject
            val type = m["type"]?.jsonPrimitive?.content.orEmpty()
            WebElement(m["i"]!!.jsonPrimitive.content.toInt(), m["tag"]?.jsonPrimitive?.content.orEmpty(), type, m["label"]?.jsonPrimitive?.content.orEmpty(), type == "password")
        } ?: emptyList()
        return buildString {
            append("标题：").append(o["title"]?.jsonPrimitive?.content.orEmpty()).append('\n')
            append("网址：").append(o["url"]?.jsonPrimitive?.content.orEmpty()).append("\n\n")
            append(o["text"]?.jsonPrimitive?.content.orEmpty()).append("\n\n可操作元素：\n")
            elements.forEach { e ->
                append('[').append(e.index).append("] ").append(e.tag)
                if (e.type.isNotBlank() && e.tag == "input") append('(').append(e.type).append(')')
                if (e.label.isNotBlank()) append(' ').append(e.label)
                append('\n')
            }
        }
    }

    fun element(index: Int) = elements.firstOrNull { it.index == index }

    suspend fun click(index: Int): String {
        val ok = js("""(function(){var e=document.querySelector('[data-neon-i="$index"]');if(!e)return false;e.scrollIntoView({block:'center'});e.click();return true;})()""")
        if (ok != "true") return "ERROR: 没找到元素 [$index]，先调用 web_read 刷新"
        val d = CompletableDeferred<Unit>()
        loaded = d
        withTimeoutOrNull(4_000) { d.await() }
        delay(500)
        return snapshot()
    }

    suspend fun type(index: Int, text: String, submit: Boolean): String {
        val ok = js(
            """(function(v){var e=document.querySelector('[data-neon-i="$index"]');if(!e)return false;e.focus();
              var proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
              Object.getOwnPropertyDescriptor(proto,'value').set.call(e,v);
              e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));
              ${if (submit) "if(e.form){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}" else ""}
              return true;})(${lit(text)})""".trimIndent(),
        )
        return if (ok == "true") "OK" else "ERROR: 没找到元素 [$index]"
    }

    /** Copies the browser's cookies for the current site into a 安全表单 (new or [existingId]). */
    suspend fun saveLogin(existingId: String?, label: String?): VaultItem? = main {
        val url = _state.value.url.takeIf { it.startsWith("http") } ?: return@main null
        val cookies = CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() } ?: return@main null
        val site = siteOf(url)
        val base = existingId?.let { vault.get(it) } ?: VaultItem(Vault.newId(), "cookie", site, label ?: site)
        vault.save(base.copy(cookies = cookies, label = label ?: base.label.ifBlank { site }))
    }

    fun clearCookies() { CookieManager.getInstance().removeAllCookies(null) }
}
