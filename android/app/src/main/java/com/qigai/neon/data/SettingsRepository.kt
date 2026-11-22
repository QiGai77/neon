package com.qigai.neon.data

import com.qigai.neon.ui.theme.ThemeMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppSettings(
    val raw: Map<String, String> = emptyMap(),
) {
    val loaded: Boolean get() = raw.containsKey(SettingsRepository.LOADED)
    val theme: ThemeMode get() = runCatching { ThemeMode.valueOf(raw[SettingsRepository.THEME] ?: "LIGHT") }.getOrDefault(ThemeMode.LIGHT)
    val provider: String? get() = raw[SettingsRepository.PROVIDER]
    val deepseekModel: String get() = raw[SettingsRepository.DEEPSEEK_MODEL] ?: "deepseek-v4-pro"
    val goModel: String get() = raw[SettingsRepository.GO_MODEL] ?: "deepseek-v4.1-flash"
    val deepThinkDefault: Boolean get() = thinkEffort != "none"
    /** none (极速) | low | medium | high | max */
    val thinkEffort: String get() = raw[SettingsRepository.THINK_EFFORT] ?: "high"
    val deepseekBase: String get() = raw[SettingsRepository.DEEPSEEK_BASE]?.trimEnd('/') ?: SettingsRepository.DEFAULT_DEEPSEEK_BASE
    val goBase: String get() = raw[SettingsRepository.GO_BASE]?.trimEnd('/') ?: SettingsRepository.DEFAULT_GO_BASE
    val driveAutoSync: Boolean get() = raw[SettingsRepository.DRIVE_AUTOSYNC] != "0"
    val driveConnected: Boolean get() = raw[SettingsRepository.DRIVE_CONNECTED] == "1"
    val driveEmail: String? get() = raw[SettingsRepository.DRIVE_EMAIL]
    val driveFolders: String? get() = raw[SettingsRepository.DRIVE_FOLDERS]
    val driveLastSync: Long? get() = raw[SettingsRepository.DRIVE_LAST_SYNC]?.toLongOrNull()
    val driveError: String? get() = raw[SettingsRepository.DRIVE_ERROR]
    val handsEnabled: Boolean get() = raw[SettingsRepository.HANDS] == "1"
    /** Chat shows each turn's thinking and tool calls as one folded line. */
    val opsCollapsed: Boolean get() = raw[SettingsRepository.OPS_COLLAPSED] != "0"
    val workspaceConnected: Boolean get() = raw[SettingsRepository.WORKSPACE] == "1"
    /** Ordered drawer cards the user kept; null = default set. */
    val interests: List<String> get() = raw[SettingsRepository.INTERESTS]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: SettingsRepository.DEFAULT_INTERESTS
    val xHandles: List<String> get() = raw[SettingsRepository.X_HANDLES]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: SettingsRepository.DEFAULT_X_HANDLES
    // ── v0.4.5 我的 ──
    val agentName: String get() = raw[SettingsRepository.AGENT_NAME]?.takeIf { it.isNotBlank() } ?: "Neon"
    /** How Neon addresses the user; empty = no form of address. */
    val userCall: String get() = raw[SettingsRepository.USER_CALL].orEmpty()
    val tone: Tone get() = Tone.of(raw[SettingsRepository.TONE])
    val toneExtra: String get() = raw[SettingsRepository.TONE_EXTRA].orEmpty()
    val userName: String get() = raw[SettingsRepository.USER_NAME].orEmpty()
    val userCity: String get() = raw[SettingsRepository.USER_CITY].orEmpty()
    val userJob: String get() = raw[SettingsRepository.USER_JOB].orEmpty()
    val userBio: String get() = raw[SettingsRepository.USER_BIO].orEmpty()
    val answerPrefs: List<String> get() = SettingsRepository.decodeList(raw[SettingsRepository.ANSWER_PREFS])
    val approval: com.qigai.neon.guard.ApprovalSettings get() = com.qigai.neon.guard.ApprovalSettings(
        mode = com.qigai.neon.guard.ApprovalMode.of(raw[SettingsRepository.APPROVAL_MODE]),
        overrides = SettingsRepository.decodeOverrides(raw[SettingsRepository.APPROVAL_OVERRIDES]),
        biometric = raw[SettingsRepository.CONFIRM_BIOMETRIC] != "0",
    )
    /** doc: Neon's reply is plain body text; bubble: it sits in a glass bubble next to the orb. */
    val chatStyle: ChatStyle get() = if (raw[SettingsRepository.CHAT_STYLE] == "bubble") ChatStyle.BUBBLE else ChatStyle.DOC

    val drawerCards: List<String> get() = raw[SettingsRepository.DRAWER_CARDS]?.split(',')?.filter { it.isNotBlank() } ?: SettingsRepository.DEFAULT_DRAWER
}

enum class ChatStyle(val key: String) { DOC("doc"), BUBBLE("bubble") }

/** Neon 的性格: how it talks. [prompt] goes into the system prompt, [sample] is the local preview line. */
enum class Tone(val key: String, val label: String, val prompt: String) {
    CRISP("crisp", "干脆", "说话干脆，先给结论，不绕弯子，不说客套话"),
    GENTLE("gentle", "温和", "语气温和耐心，照顾对方感受"),
    FRIEND("friend", "像朋友", "像熟悉的朋友一样自然随意地聊，可以用口语"),
    PRO("pro", "专业", "专业严谨，用词准确，必要时说明依据"),
    WITTY("witty", "幽默", "轻松幽默，可以适当俏皮，但不耽误正事"),
    BRIEF("brief", "只说结论", "只说结论，不解释过程，除非用户追问");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: CRISP
    }
}

class SettingsRepository(private val dao: SettingDao, scope: CoroutineScope) {
    companion object {
        const val LOADED = "_loaded"
        const val THEME = "theme"
        const val PROVIDER = "provider"
        const val DEEPSEEK_MODEL = "deepseek_model"
        const val GO_MODEL = "go_model"
        const val DEEP_THINK = "deep_think_default"
        const val THINK_EFFORT = "think_effort"
        const val DEEPSEEK_BASE = "deepseek_base"
        const val GO_BASE = "go_base"
        const val DRIVE_AUTOSYNC = "drive_autosync"
        const val DRIVE_CONNECTED = "drive_connected"
        const val DRIVE_EMAIL = "drive_email"
        const val DRIVE_FOLDERS = "drive_folders"
        const val DRIVE_LAST_SYNC = "drive_last_sync"
        const val DRIVE_ERROR = "drive_error"
        const val HANDS = "hands_enabled"
        const val OPS_COLLAPSED = "ops_collapsed"
        const val WORKSPACE = "workspace_connected"
        const val DRAWER_CARDS = "drawer_cards"
        const val AGENT_NAME = "agent_name"
        const val USER_CALL = "user_call"
        const val TONE = "tone"
        const val TONE_EXTRA = "tone_extra"
        const val USER_NAME = "user_name"
        const val USER_CITY = "user_city"
        const val USER_JOB = "user_job"
        const val USER_BIO = "user_bio"
        /** JSON array of the user's answer preferences, e.g. ["用中文","代码给完整文件"]. */
        const val ANSWER_PREFS = "answer_prefs"
        const val APPROVAL_MODE = "approval_mode"
        /** JSON object {payment, delete, send, hands} → "ask" | "allow". */
        const val APPROVAL_OVERRIDES = "approval_overrides"
        const val CONFIRM_BIOMETRIC = "confirm_biometric"
        const val CHAT_STYLE = "chat_style"
        val ANSWER_PREF_PRESETS = listOf("用中文", "代码给完整文件", "不要表情")

        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun decodeList(raw: String?): List<String> = raw?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
        }?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        fun encodeList(list: List<String>): String = json.encodeToString<List<String>>(list.distinct())

        fun decodeOverrides(raw: String?): Map<com.qigai.neon.guard.ActionType, Boolean> {
            if (raw == null) return com.qigai.neon.guard.ApprovalSettings.DEFAULT_OVERRIDES
            val m = runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrNull() ?: return com.qigai.neon.guard.ApprovalSettings.DEFAULT_OVERRIDES
            return com.qigai.neon.guard.ActionType.pinnable.mapNotNull { t ->
                when (m[t.key]) { "ask" -> t to true; "allow" -> t to false; else -> null }
            }.toMap()
        }

        fun encodeOverrides(m: Map<com.qigai.neon.guard.ActionType, Boolean>): String =
            json.encodeToString<Map<String, String>>(m.entries.associate { (k, v) -> k.key to if (v) "ask" else "allow" })
        /** 速览 interests, comma separated */
        const val INTERESTS = "glance_interests"
        /** 速览 X accounts, comma separated handles */
        const val X_HANDLES = "glance_x_handles"
        val DEFAULT_X_HANDLES = listOf("OpenAI", "AnthropicAI", "GoogleDeepMind", "deepseek_ai", "Alibaba_Qwen", "huggingface", "AndroidDev")
        val DEFAULT_INTERESTS = listOf("AI Agent", "Jetpack Compose", "独立开发", "开源模型", "MCP")
        val DEFAULT_DRAWER = listOf("quota", "sandbox", "drive", "hubs")
        val ALL_DRAWER = listOf("quota", "sandbox", "drive", "hubs")
        const val DEFAULT_DEEPSEEK_BASE = "https://api.deepseek.com"
        const val DEFAULT_GO_BASE = "https://opencode.ai/inference/go"
    }

    val state: StateFlow<AppSettings> = dao.observeAll()
        .map { list -> AppSettings(list.associate { it.key to it.value } + (LOADED to "1")) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun put(key: String, value: String?) {
        if (value == null) dao.remove(key) else dao.put(SettingEntity(key, value))
    }

    suspend fun get(key: String): String? = dao.get(key)
}
