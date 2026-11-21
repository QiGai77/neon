package com.qigai.neon.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * API keys / tokens live only here: EncryptedSharedPreferences (Android Keystore AES256-GCM).
 * Values are injected into request headers by the provider layer; they are never put in prompts,
 * never logged, never synced to Drive. [configured] exposes only which keys exist.
 */
class EncryptedTokenStore(context: Context) {
    companion object {
        const val DEEPSEEK = "deepseek_api_key"
        const val OPENCODE_GO = "opencode_go_key"
    }

    private val prefs: SharedPreferences = try {
        create(context)
    } catch (_: Exception) {
        // Keystore corruption (e.g. after restore): reset the encrypted file rather than crash.
        context.deleteSharedPreferences("neon_secure")
        create(context)
    }

    private fun create(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, "neon_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _configured = MutableStateFlow(snapshot())
    val configured: StateFlow<Set<String>> = _configured.asStateFlow()

    private fun snapshot(): Set<String> = prefs.all.filter { (it.value as? String)?.isNotBlank() == true }.keys.toSet()

    fun get(name: String): String? = prefs.getString(name, null)?.takeIf { it.isNotBlank() }

    fun put(name: String, value: String) {
        prefs.edit().putString(name, value.trim()).apply()
        _configured.value = snapshot() + name
    }

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
        _configured.value = snapshot() - name
    }

    fun has(name: String) = get(name) != null
}
