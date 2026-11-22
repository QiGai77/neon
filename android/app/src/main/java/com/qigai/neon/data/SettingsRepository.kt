package com.qigai.neon.data

import com.qigai.neon.ui.theme.ThemeMode
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
    val deepThinkDefault: Boolean get() = raw[SettingsRepository.DEEP_THINK] == "1"
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
    val opsCollapsed: Boolean get() = raw[SettingsRepository.OPS_COLLAPSED] == "1"
    val workspaceConnected: Boolean get() = raw[SettingsRepository.WORKSPACE] == "1"
    /** Ordered drawer cards the user kept; null = default set. */
    val interests: List<String> get() = raw[SettingsRepository.INTERESTS]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: SettingsRepository.DEFAULT_INTERESTS
    val xHandles: List<String> get() = raw[SettingsRepository.X_HANDLES]?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: SettingsRepository.DEFAULT_X_HANDLES
    val drawerCards: List<String> get() = raw[SettingsRepository.DRAWER_CARDS]?.split(',')?.filter { it.isNotBlank() } ?: SettingsRepository.DEFAULT_DRAWER
}

class SettingsRepository(private val dao: SettingDao, scope: CoroutineScope) {
    companion object {
        const val LOADED = "_loaded"
        const val THEME = "theme"
        const val PROVIDER = "provider"
        const val DEEPSEEK_MODEL = "deepseek_model"
        const val GO_MODEL = "go_model"
        const val DEEP_THINK = "deep_think_default"
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
