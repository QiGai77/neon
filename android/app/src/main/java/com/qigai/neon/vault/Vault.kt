package com.qigai.neon.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** One saved 安全表单: a login (account + password) or a cookie string for a site. */
@Serializable
data class VaultItem(
    val id: String,
    /** login | cookie */
    val kind: String,
    /** Host the item belongs to, e.g. github.com */
    val site: String,
    val label: String = "",
    val username: String = "",
    val password: String = "",
    /** Cookie header form: a=1; b=2 */
    val cookies: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** What the model and the list UI may see: never the password or cookie values. */
data class VaultMeta(
    val id: String,
    val kind: String,
    val site: String,
    val label: String,
    val user: String,
    val hasPassword: Boolean,
    val cookieCount: Int,
    val updatedAt: Long,
)

fun VaultItem.meta() = VaultMeta(
    id, kind, site, label, maskUser(username), password.isNotEmpty(),
    cookies.split(';').count { it.contains('=') }, updatedAt,
)

private fun maskUser(u: String): String = when {
    u.isBlank() -> ""
    u.contains('@') -> u.substringBefore('@').let { it.take(2) + "•••" } + "@" + u.substringAfter('@')
    u.length <= 3 -> u.first() + "••"
    else -> u.take(2) + "•••" + u.takeLast(1)
}

/** Normalises "https://www.Example.com/login" to "example.com". */
fun siteOf(input: String): String {
    val s = input.trim().lowercase()
    val host = runCatching { android.net.Uri.parse(if ("://" in s) s else "https://$s").host }.getOrNull() ?: s
    return host.removePrefix("www.").trimEnd('/')
}

/** Encrypted on-device store for 安全表单. Values stay on this phone (Android Keystore AES-256-GCM). */
class Vault(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs: SharedPreferences = try {
        create(context)
    } catch (_: Exception) {
        context.deleteSharedPreferences("neon_vault")
        create(context)
    }

    private fun create(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, "neon_vault", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<VaultMeta>> = _items.asStateFlow()

    private fun all(): List<VaultItem> = prefs.all.values.mapNotNull { v ->
        (v as? String)?.let { runCatching { json.decodeFromString(VaultItem.serializer(), it) }.getOrNull() }
    }.sortedByDescending { it.updatedAt }

    private fun load() = all().map { it.meta() }

    fun get(id: String): VaultItem? = prefs.getString(id, null)?.let { runCatching { json.decodeFromString(VaultItem.serializer(), it) }.getOrNull() }

    fun forSite(site: String): List<VaultItem> {
        val s = siteOf(site)
        return all().filter { s == it.site || s.endsWith("." + it.site) || it.site.endsWith(".$s") }
    }

    fun save(item: VaultItem): VaultItem {
        val clean = item.copy(site = siteOf(item.site), updatedAt = System.currentTimeMillis())
        prefs.edit().putString(clean.id, json.encodeToString(VaultItem.serializer(), clean)).apply()
        _items.value = load()
        return clean
    }

    fun remove(id: String) {
        prefs.edit().remove(id).apply()
        _items.value = load()
    }

    companion object {
        fun newId() = "vf_" + UUID.randomUUID().toString().replace("-", "").take(10)
    }
}

/** A secure form Neon asked the user to fill. */
data class VaultFormRequest(
    val id: String = UUID.randomUUID().toString(),
    val site: String,
    /** login | cookie */
    val kind: String,
    val reason: String,
    /** Update this item instead of creating one. */
    val existingId: String? = null,
)

/** Holds the one secure form on screen; the agent waits on it. The values never pass through the model. */
class VaultFormGate(private val vault: Vault) {
    private val _pending = MutableStateFlow<VaultFormRequest?>(null)
    val pending: StateFlow<VaultFormRequest?> = _pending.asStateFlow()
    private var deferred: CompletableDeferred<VaultItem?>? = null

    suspend fun request(r: VaultFormRequest): VaultItem? {
        deferred?.complete(null)
        val d = CompletableDeferred<VaultItem?>()
        deferred = d
        _pending.value = r
        return try { d.await() } finally {
            if (_pending.value?.id == r.id) _pending.value = null
            if (deferred === d) deferred = null
        }
    }

    /** From the sheet: null = cancelled. */
    fun submit(item: VaultItem?) {
        val saved = item?.let { vault.save(it) }
        val d = deferred
        _pending.value = null
        d?.complete(saved)
    }
}
