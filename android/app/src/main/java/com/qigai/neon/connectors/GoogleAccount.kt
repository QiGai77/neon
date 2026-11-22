package com.qigai.neon.connectors

import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.oauth.McpOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The user's Google account, signed in once with their own Desktop OAuth client.
 *
 * Gmail, Calendar and Drive are called through their normal REST APIs with this token, so nothing
 * beyond the client itself is needed: no preview program, no MCP. Drive backup uses it too.
 */
class GoogleAccount(
    private val tokens: EncryptedTokenStore,
    private val oauth: McpOAuth,
    private val http: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val _email = MutableStateFlow(load()?.email)
    /** Signed-in address, or null. */
    val email: StateFlow<String?> = _email

    private data class Session(val access: String, val refresh: String?, val expiresAt: Long, val email: String?)

    fun client(): OAuthClient? = tokens.get(KEY_CLIENT)?.let { raw ->
        runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            OAuthClient(o["id"]!!.jsonPrimitive.content, o["secret"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        }.getOrNull()
    }

    fun saveClient(id: String, secret: String) {
        tokens.put(KEY_CLIENT, buildJsonObject { put("id", id.trim()); put("secret", secret.trim()) }.toString())
    }

    val signedIn: Boolean get() = _email.value != null

    /** Opens Google sign-in in the browser. Returns the signed-in address. */
    suspend fun signIn(): Result<String> = runCatching {
        val oc = client() ?: error("先填写 OAuth 客户端")
        val (tok, _) = oauth.runPkceFlow(
            AUTH, TOKEN, SCOPES, resource = null,
            extraParams = mapOf("access_type" to "offline", "prompt" to "consent", "include_granted_scopes" to "true"),
        ) { oc }.getOrThrow()
        val mail = fetchEmail(tok.accessToken) ?: "Google 账号"
        save(Session(tok.accessToken, tok.refreshToken, now() + tok.expiresInSec * 1000, mail))
        _email.value = mail
        mail
    }

    fun signOut() {
        tokens.remove(KEY_SESSION)
        _email.value = null
    }

    /** A valid access token, refreshed when it is about to expire; null when not signed in. */
    suspend fun accessToken(): String? = lock.withLock {
        val s = load() ?: return null
        if (s.expiresAt - now() > 60_000) return s.access
        val oc = client() ?: return null
        val rt = s.refresh ?: return null
        val tok = oauth.refreshToken(TOKEN, oc.clientId, rt, oc.clientSecret).getOrNull() ?: return null
        save(s.copy(access = tok.accessToken, refresh = tok.refreshToken ?: rt, expiresAt = now() + tok.expiresInSec * 1000))
        tok.accessToken
    }

    private suspend fun fetchEmail(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://openidconnect.googleapis.com/v1/userinfo")
                .header("Authorization", "Bearer $token").build()
            http.newCall(req).execute().use { r ->
                json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["email"]?.jsonPrimitive?.content
            }
        }.getOrNull()
    }

    private fun load(): Session? = tokens.get(KEY_SESSION)?.let { raw ->
        runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            Session(
                o["access"]!!.jsonPrimitive.content,
                o["refresh"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                o["exp"]?.jsonPrimitive?.long ?: 0L,
                o["email"]?.jsonPrimitive?.content,
            )
        }.getOrNull()
    }

    private fun save(s: Session) {
        tokens.put(KEY_SESSION, buildJsonObject {
            put("access", s.access); put("refresh", s.refresh ?: ""); put("exp", s.expiresAt); put("email", s.email ?: "")
        }.toString())
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val KEY_CLIENT = "google_mcp_client"
        private const val KEY_SESSION = "google_account_session"
        const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN = "https://oauth2.googleapis.com/token"
        /** Personal mode setup in Google Cloud, pre-set to a Desktop app client. */
        const val SETUP_URL = "https://console.cloud.google.com/auth/overview/personal?apis=ws&clientType=desktop&isAgent=true"
        private const val G = "https://www.googleapis.com/auth/"
        val SCOPES = listOf(
            "openid", "email",
            G + "gmail.modify",
            G + "calendar.events",
            G + "drive.readonly", G + "drive.file",
        )
        /** Connector ids the earlier MCP version created; cleared on sign-in. */
        val legacyIds = listOf("gmail", "gcalendar", "gdrive", "gdocs", "gsheets", "gslides", "gpeople")
    }
}
