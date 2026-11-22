package com.qigai.neon

import com.qigai.neon.data.ContentBlock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qigai.neon.agent.AgentRuntime
import com.qigai.neon.agent.AgentState
import com.qigai.neon.agent.TurnConfig
import com.qigai.neon.data.AppSettings
import com.qigai.neon.data.ConversationEntity
import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.data.MessageEntity
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.provider.GoUsage
import com.qigai.neon.provider.ModelInfo
import com.qigai.neon.provider.ModelProvider
import com.qigai.neon.provider.OpenCodeGoClient
import com.qigai.neon.provider.ProviderException
import com.qigai.neon.provider.ProviderId
import com.qigai.neon.sysfiles.DriveAuthExpired
import com.qigai.neon.sysfiles.DriveClient
import com.qigai.neon.sysfiles.DriveSync
import com.qigai.neon.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

sealed class QuotaState {
    data object NotConfigured : QuotaState()
    data object Loading : QuotaState()
    data class Ok(val usage: GoUsage) : QuotaState()
    data class Error(val message: String) : QuotaState()
}

data class ModelsState(
    val lists: Map<ProviderId, List<ModelInfo>> = emptyMap(),
    val loading: Set<ProviderId> = emptySet(),
    val errors: Map<ProviderId, String> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val c = (app as NeonApp).container

    val settings: StateFlow<AppSettings> = c.settings.state
    val configured: StateFlow<Set<String>> = c.tokens.configured
    /** The open conversation's state. Other conversations keep running on their own. */
    val agentState: StateFlow<AgentState> by lazy {
        kotlinx.coroutines.flow.combine(_current, c.agent.states) { id, m -> id?.let { m[it] } ?: AgentState.Idle }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AgentState.Idle)
    }
    /** Conversations with a turn in progress. */
    val runningIds: StateFlow<Set<String>> = c.agent.states
        .let { f -> kotlinx.coroutines.flow.flow { f.collect { m -> emit(m.filterValues { it.busy }.keys) } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val voice = c.voice

    val conversations: StateFlow<List<ConversationEntity>> = c.db.conversations().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _current = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _current.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = _current.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else c.db.messages().observe(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentConversation: StateFlow<ConversationEntity?> = _current.flatMapLatest { id ->
        if (id == null) flowOf(null) else c.db.conversations().observe(id)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _effort = MutableStateFlow("high")
    /** Thinking level for new turns: none (极速) | low | medium | high | max. */
    val effort: StateFlow<String> = _effort.asStateFlow()

    private val _models = MutableStateFlow(ModelsState())
    val models: StateFlow<ModelsState> = _models.asStateFlow()

    private val _quota = MutableStateFlow<QuotaState>(QuotaState.NotConfigured)
    val quota: StateFlow<QuotaState> = _quota.asStateFlow()

    private val _driveBusy = MutableStateFlow(false)
    val driveBusy: StateFlow<Boolean> = _driveBusy.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.first { it.loaded }
            _effort.value = s.thinkEffort
            if (c.tokens.has(EncryptedTokenStore.DEEPSEEK)) loadModels(ProviderId.DEEPSEEK)
            loadModels(ProviderId.OPENCODE_GO) // public list
            if (c.tokens.has(EncryptedTokenStore.OPENCODE_GO)) refreshQuota()
        }
    }

    fun toast(msg: String?) { _toast.value = msg }

    // ---------- 速览 ----------
    private val githubFeed = com.qigai.neon.feed.GitHubFeed(com.qigai.neon.provider.Http.client) { c.tokens.getConnectorToken("github") }
    private val xFeed = com.qigai.neon.feed.XFeed(com.qigai.neon.provider.Http.client, java.io.File(app.cacheDir, "x_feed.json"))
    private val _feed = MutableStateFlow(com.qigai.neon.feed.FeedState())
    val feed: StateFlow<com.qigai.neon.feed.FeedState> = _feed.asStateFlow()

    fun refreshFeed(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (_feed.value.loading) return
        if (!force && (_feed.value.repos.isNotEmpty() || _feed.value.posts.isNotEmpty()) && now - _feed.value.fetchedAt < 30 * 60_000L) return
        _feed.value = _feed.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val st = settings.value
            val gh = async { runCatching { githubFeed.fetch(st.interests) } }
            val x = async { runCatching { xFeed.fetch(st.xHandles, st.interests) } }
            val g = gh.await(); val xr = x.await()
            _feed.value = com.qigai.neon.feed.FeedState(
                repos = g.getOrElse { _feed.value.repos }, error = g.exceptionOrNull()?.let { it.message ?: "加载失败" },
                posts = xr.getOrElse { _feed.value.posts }, xError = xr.exceptionOrNull()?.let { it.message ?: "加载失败" },
                fetchedAt = System.currentTimeMillis(),
            )
        }
    }

    fun setInterests(list: List<String>) = viewModelScope.launch {
        c.settings.put(SettingsRepository.INTERESTS, list.map { it.replace(",", " ").trim() }.filter { it.isNotBlank() }.distinct().joinToString(","))
        _feed.value = _feed.value.copy(fetchedAt = 0L)
    }

    fun setXHandles(list: List<String>) = viewModelScope.launch {
        c.settings.put(SettingsRepository.X_HANDLES, list.map { it.trim().removePrefix("@").replace(",", "") }.filter { it.isNotBlank() }.distinct().joinToString(","))
        _feed.value = _feed.value.copy(fetchedAt = 0L)
    }

    /** Starts a fresh chat with [prompt]. Returns false when Neon is busy or no model is connected. */
    fun askNeon(prompt: String): Boolean {
        if (turnConfig() == null) { toast("请先连接一个模型"); return false }
        _current.value = null
        return send(prompt)
    }

    // ---------- provider selection ----------
    fun activeProviderId(s: AppSettings = settings.value, keys: Set<String> = configured.value): ProviderId? {
        val has = { p: ProviderId -> keyName(p) in keys }
        val pref = ProviderId.of(s.provider)
        if (pref != null && has(pref)) return pref
        return ProviderId.entries.firstOrNull(has)
    }

    fun keyName(p: ProviderId) = if (p == ProviderId.DEEPSEEK) EncryptedTokenStore.DEEPSEEK else EncryptedTokenStore.OPENCODE_GO
    fun client(p: ProviderId): ModelProvider = if (p == ProviderId.DEEPSEEK) c.deepseek else c.go
    fun modelFor(p: ProviderId, s: AppSettings = settings.value) = if (p == ProviderId.DEEPSEEK) s.deepseekModel else s.goModel

    fun selectModel(p: ProviderId, model: String) = viewModelScope.launch {
        c.settings.put(SettingsRepository.PROVIDER, p.id)
        c.settings.put(if (p == ProviderId.DEEPSEEK) SettingsRepository.DEEPSEEK_MODEL else SettingsRepository.GO_MODEL, model)
    }

    fun loadModels(p: ProviderId) = viewModelScope.launch {
        _models.value = _models.value.copy(loading = _models.value.loading + p)
        try {
            val list = client(p).listModels()
            _models.value = _models.value.copy(lists = _models.value.lists + (p to list), errors = _models.value.errors - p)
        } catch (e: Exception) {
            _models.value = _models.value.copy(errors = _models.value.errors + (p to (e.message ?: "加载失败")))
        } finally {
            _models.value = _models.value.copy(loading = _models.value.loading - p)
        }
    }

    // ---------- chat ----------
    fun setEffort(e: String) {
        _effort.value = e
        viewModelScope.launch { c.settings.put(SettingsRepository.THINK_EFFORT, e) }
    }

    private fun turnConfig(): TurnConfig? {
        val p = activeProviderId() ?: return null
        val model = modelFor(p)
        val info = _models.value.lists[p]?.firstOrNull { it.id == model }
        return TurnConfig(client(p), model, info, _effort.value != "none", _effort.value)
    }

    private val _pending = MutableStateFlow<List<com.qigai.neon.ui.chat.PendingAttachment>>(emptyList())
    /** Files picked for the next message, already in /workspace/uploads. */
    val pending: StateFlow<List<com.qigai.neon.ui.chat.PendingAttachment>> = _pending.asStateFlow()

    fun attach(uris: List<android.net.Uri>) = viewModelScope.launch {
        val r = getApplication<Application>().contentResolver
        val added = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            uris.mapNotNull { c.files.importUpload(r, it) }
        }
        if (added.size < uris.size) toast("有 ${uris.size - added.size} 个文件没能读取")
        _pending.value = _pending.value + added.map { com.qigai.neon.ui.chat.PendingAttachment(it.name, it.path, it.mime, it.size) }
    }

    fun removeAttachment(a: com.qigai.neon.ui.chat.PendingAttachment) { _pending.value = _pending.value - a }

    fun send(text: String): Boolean {
        val t = text.trim()
        val files = _pending.value
        if ((t.isEmpty() && files.isEmpty()) || agentState.value.busy) return false
        val cfg = turnConfig() ?: run { toast("请先连接一个模型"); return false }
        val before = _current.value
        _pending.value = emptyList()
        val blocks = files.map { ContentBlock.Attachment(it.name, it.path, it.mime, it.size) }
        viewModelScope.launch {
            val cid = c.agent.send(before, t, cfg, blocks)
            if (_current.value == before) _current.value = cid
        }
        return true
    }

    fun stop() { _current.value?.let { c.agent.stop(it) } }

    fun regenerate() {
        val cid = _current.value ?: return
        val cfg = turnConfig() ?: run { toast("请先连接一个模型"); return }
        if (agentState.value.busy) return
        viewModelScope.launch { c.agent.regenerate(cid, cfg) }
    }

    fun newChat() {
        _current.value = null
    }

    fun open(id: String) { _current.value = id }

    fun rename(id: String, title: String) = viewModelScope.launch {
        c.db.conversations().rename(id, title.trim().ifEmpty { "新对话" }, System.currentTimeMillis())
        if (settings.value.driveConnected && settings.value.driveAutoSync) DriveSync.enqueue(getApplication(), id)
    }

    fun delete(id: String) = viewModelScope.launch {
        c.agent.stop(id)
        val conv = c.db.conversations().get(id)
        c.db.messages().deleteFor(id)
        c.db.conversations().delete(id)
        if (_current.value == id) _current.value = null
        val fileId = conv?.driveFileId
        if (fileId != null && settings.value.driveConnected) {
            runCatching { c.drive.client().trash(fileId) }
        }
    }

    // ---------- keys ----------
    suspend fun saveDeepSeekKey(raw: String): String? {
        val key = raw.trim()
        if (key.length < 10) return "Key 格式不对"
        return try {
            val list = c.deepseek.listModelsWith(key)
            c.tokens.put(EncryptedTokenStore.DEEPSEEK, key)
            _models.value = _models.value.copy(lists = _models.value.lists + (ProviderId.DEEPSEEK to list))
            if (activeProviderId() == ProviderId.DEEPSEEK || settings.value.provider == null) c.settings.put(SettingsRepository.PROVIDER, ProviderId.DEEPSEEK.id)
            if (list.isNotEmpty() && list.none { it.id == settings.value.deepseekModel }) {
                c.settings.put(SettingsRepository.DEEPSEEK_MODEL, list.first().id)
            }
            null
        } catch (e: Exception) {
            e.message ?: "验证失败"
        }
    }

    /** Validates with the real usage endpoint (free) like the web app; 401 rejects. */
    suspend fun saveGoKey(raw: String): String? {
        val key = OpenCodeGoClient.parseImport(raw) ?: return "没有识别到 Key"
        return try {
            val usage = c.go.fetchUsage(key)
            c.tokens.put(EncryptedTokenStore.OPENCODE_GO, key)
            _quota.value = QuotaState.Ok(usage)
            c.settings.put(SettingsRepository.PROVIDER, ProviderId.OPENCODE_GO.id)
            null
        } catch (e: ProviderException) {
            if (e.status == 401) e.message else {
                // usage endpoint unreachable for another reason: keep the key, show the honest error on the quota card
                c.tokens.put(EncryptedTokenStore.OPENCODE_GO, key)
                c.settings.put(SettingsRepository.PROVIDER, ProviderId.OPENCODE_GO.id)
                _quota.value = QuotaState.Error(e.message ?: "额度接口不可用")
                null
            }
        } catch (e: Exception) {
            e.message ?: "验证失败"
        }
    }

    fun removeKey(p: ProviderId) = viewModelScope.launch {
        c.tokens.remove(keyName(p))
        if (p == ProviderId.OPENCODE_GO) _quota.value = QuotaState.NotConfigured
        if (p == ProviderId.DEEPSEEK) _models.value = _models.value.copy(lists = _models.value.lists - p)
    }

    fun refreshQuota() = viewModelScope.launch {
        if (!c.tokens.has(EncryptedTokenStore.OPENCODE_GO)) { _quota.value = QuotaState.NotConfigured; return@launch }
        val prev = _quota.value
        if (prev !is QuotaState.Ok) _quota.value = QuotaState.Loading
        _quota.value = try {
            QuotaState.Ok(c.go.fetchUsage())
        } catch (e: Exception) {
            QuotaState.Error(e.message ?: "额度接口不可用")
        }
    }

    // ---------- settings ----------
    fun setTheme(m: ThemeMode) = viewModelScope.launch { c.settings.put(SettingsRepository.THEME, m.name) }
    fun setDeepThinkDefault(on: Boolean) = viewModelScope.launch {
        setEffort(if (on) "high" else "none")
    }
    fun setBase(key: String, url: String) = viewModelScope.launch {
        c.settings.put(key, url.trim().trimEnd('/').ifEmpty { null })
    }
    fun setDriveAutoSync(on: Boolean) = viewModelScope.launch { c.settings.put(SettingsRepository.DRIVE_AUTOSYNC, if (on) "1" else "0") }

    // ---------- Google Drive ----------
    fun onDriveAuthorized(accessToken: String?) = viewModelScope.launch {
        if (accessToken == null) { toast("没有拿到 Google 授权"); return@launch }
        _driveBusy.value = true
        try {
            val client = DriveClient { accessToken }
            val email = client.aboutEmail()
            c.settings.put(SettingsRepository.DRIVE_EMAIL, email)
            c.settings.put(SettingsRepository.DRIVE_CONNECTED, "1")
            c.settings.put(SettingsRepository.DRIVE_ERROR, null)
            c.drive.folders(client)
            DriveSync.enqueue(getApplication(), null)
            toast("Google Drive 已连接")
        } catch (e: Exception) {
            c.settings.put(SettingsRepository.DRIVE_ERROR, e.message ?: "连接失败")
            toast("Drive 连接失败：${e.message}")
        } finally {
            _driveBusy.value = false
        }
    }

    fun onDriveAuthFailed(msg: String) = viewModelScope.launch {
        c.settings.put(SettingsRepository.DRIVE_ERROR, msg)
        toast(msg)
    }

    fun driveSyncNow() = viewModelScope.launch {
        _driveBusy.value = true
        try {
            c.drive.syncAll()
            toast("已同步到 Drive")
        } catch (e: DriveAuthExpired) {
            c.settings.put(SettingsRepository.DRIVE_ERROR, "Google 授权已过期，请重新授权")
        } catch (e: Exception) {
            c.settings.put(SettingsRepository.DRIVE_ERROR, e.message ?: "同步失败")
        } finally {
            _driveBusy.value = false
        }
    }

    fun driveDisconnect() = viewModelScope.launch {
        for (k in listOf(SettingsRepository.DRIVE_CONNECTED, SettingsRepository.DRIVE_EMAIL, SettingsRepository.DRIVE_FOLDERS,
            SettingsRepository.DRIVE_LAST_SYNC, SettingsRepository.DRIVE_ERROR)) c.settings.put(k, null)
    }

    fun driveFolders(): Map<String, String> = settings.value.driveFolders?.let { s ->
        runCatching {
            com.qigai.neon.provider.Http.json.parseToJsonElement(s).let { e ->
                (e as kotlinx.serialization.json.JsonObject).mapValues { (it.value as kotlinx.serialization.json.JsonPrimitive).content }
            }
        }.getOrNull()
    } ?: emptyMap()

    companion object {
        const val NEW_ID_PLACEHOLDER = ""
        fun id() = AgentRuntime.newId()
    }
}
