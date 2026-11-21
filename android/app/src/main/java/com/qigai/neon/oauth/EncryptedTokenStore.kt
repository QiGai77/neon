package com.qigai.neon.oauth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.qigai.neon.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 凭据完全隔离存储：所有 API Key、OAuth Refresh/Access Token、MCP Token、Webhook 密钥
 * 均存储在 Android Keystore AES256-GCM 加密的 SharedPreferences 中，绝不进入 LLM 上下文。
 */
class EncryptedTokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "neon_secure_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _googleEmail = MutableStateFlow(prefs.getString(KEY_GOOGLE_EMAIL, null))
    val googleEmail: StateFlow<String?> = _googleEmail.asStateFlow()

    private val _darkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, false))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, dark).apply()
        _darkTheme.value = dark
    }

    fun getDeepSeekKey(): String = prefs.getString(KEY_DEEPSEEK_API, "").orEmpty()
    fun setDeepSeekKey(key: String) {
        prefs.edit().putString(KEY_DEEPSEEK_API, key.trim()).apply()
    }

    fun getOpenCodeGoKey(): String = prefs.getString(KEY_OPENCODE_GO_API, "").orEmpty()
    fun setOpenCodeGoKey(key: String) {
        prefs.edit().putString(KEY_OPENCODE_GO_API, key.trim()).apply()
    }

    fun getActiveProvider(): String = prefs.getString(KEY_ACTIVE_PROVIDER, "opencode_go") ?: "opencode_go"
    fun setActiveProvider(provider: String) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider).apply()
    }

    fun getActiveModel(): String = prefs.getString(KEY_ACTIVE_MODEL, "deepseek-v4-pro") ?: "deepseek-v4-pro"
    fun setActiveModel(model: String) {
        prefs.edit().putString(KEY_ACTIVE_MODEL, model).apply()
    }

    fun getGoogleClientId(): String {
        val saved = prefs.getString(KEY_GOOGLE_CLIENT_ID, "").orEmpty()
        return saved.ifBlank { BuildConfig.DEFAULT_GOOGLE_CLIENT_ID }
    }

    fun setGoogleClientId(clientId: String) {
        prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, clientId.trim()).apply()
    }

    fun saveGoogleTokens(accessToken: String, refreshToken: String?, expiresAtMs: Long, email: String?) {
        prefs.edit().apply {
            putString(KEY_GOOGLE_ACCESS, accessToken)
            if (!refreshToken.isNullOrBlank()) putString(KEY_GOOGLE_REFRESH, refreshToken)
            putLong(KEY_GOOGLE_EXPIRES_AT, expiresAtMs)
            if (!email.isNullOrBlank()) putString(KEY_GOOGLE_EMAIL, email)
        }.apply()
        if (!email.isNullOrBlank()) _googleEmail.value = email
    }

    fun getGoogleAccessToken(): String? = prefs.getString(KEY_GOOGLE_ACCESS, null)
    fun getGoogleRefreshToken(): String? = prefs.getString(KEY_GOOGLE_REFRESH, null)
    fun getGoogleExpiresAt(): Long = prefs.getLong(KEY_GOOGLE_EXPIRES_AT, 0L)

    fun clearGoogleTokens() {
        prefs.edit()
            .remove(KEY_GOOGLE_ACCESS)
            .remove(KEY_GOOGLE_REFRESH)
            .remove(KEY_GOOGLE_EXPIRES_AT)
            .remove(KEY_GOOGLE_EMAIL)
            .apply()
        _googleEmail.value = null
    }

    fun saveConnectorToken(connectorId: String, token: String, refreshToken: String? = null) {
        prefs.edit().apply {
            putString("conn_access_$connectorId", token)
            if (!refreshToken.isNullOrBlank()) putString("conn_refresh_$connectorId", refreshToken)
        }.apply()
    }

    fun getConnectorToken(connectorId: String): String? = prefs.getString("conn_access_$connectorId", null)

    fun saveWebhookConfig(url: String, secret: String) {
        prefs.edit()
            .putString(KEY_WEBHOOK_URL, url.trim())
            .putString(KEY_WEBHOOK_SECRET, secret.trim())
            .apply()
    }

    fun getWebhookUrl(): String = prefs.getString(KEY_WEBHOOK_URL, "").orEmpty()
    fun getWebhookSecret(): String = prefs.getString(KEY_WEBHOOK_SECRET, "").orEmpty()

    companion object {
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_DEEPSEEK_API = "deepseek_api_key"
        private const val KEY_OPENCODE_GO_API = "opencode_go_api_key"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_ACTIVE_MODEL = "active_model"
        private const val KEY_GOOGLE_CLIENT_ID = "google_client_id"
        private const val KEY_GOOGLE_ACCESS = "google_access_token"
        private const val KEY_GOOGLE_REFRESH = "google_refresh_token"
        private const val KEY_GOOGLE_EXPIRES_AT = "google_expires_at"
        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_WEBHOOK_SECRET = "webhook_secret"
    }
}
