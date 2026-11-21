package com.qigai.neon.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
)

/** OAuth 2.1 + PKCE in a Custom Tab with a loopback redirect (remote MCP servers). */
class McpOAuth(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun runPkceFlow(
        authEndpoint: String,
        tokenEndpoint: String,
        clientId: String,
        scopes: List<String>,
        extraAuthParams: Map<String, String> = emptyMap(),
    ): Result<OAuthTokenResponse> = runCatching {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateCodeVerifier().take(24)
        val (socket, redirectUri) = LoopbackCallback.createLoopbackServer()

        val uriBuilder = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scopes.joinToString(" "))
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
        extraAuthParams.forEach { (k, v) -> uriBuilder.appendQueryParameter(k, v) }

        val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabs.launchUrl(context, uriBuilder.build())

        val callback = LoopbackCallback.awaitCallback(socket)
        if (!callback.error.isNullOrBlank()) {
            error("OAuth 授权失败: ${callback.error}")
        }
        if (callback.state != state || callback.code.isNullOrBlank()) {
            error("OAuth 状态校验失败或未返回授权码")
        }
        exchangeCode(tokenEndpoint, clientId, callback.code, redirectUri, verifier)
    }

    private suspend fun exchangeCode(
        tokenEndpoint: String,
        clientId: String,
        code: String,
        redirectUri: String,
        verifier: String,
    ): OAuthTokenResponse = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .build()
        val req = Request.Builder().url(tokenEndpoint).post(body).build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Token 换取失败 (${resp.code}): $raw")
            val obj = json.parseToJsonElement(raw).jsonObject
            OAuthTokenResponse(
                accessToken = obj["access_token"]?.jsonPrimitive?.content ?: error("缺少 access_token"),
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
                expiresInSec = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L,
            )
        }
    }

    suspend fun refreshToken(
        tokenEndpoint: String,
        clientId: String,
        refreshToken: String,
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .build()
            val req = Request.Builder().url(tokenEndpoint).post(body).build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Token 刷新失败 (${resp.code}): $raw")
                val obj = json.parseToJsonElement(raw).jsonObject
                OAuthTokenResponse(
                    accessToken = obj["access_token"]?.jsonPrimitive?.content ?: error("缺少 access_token"),
                    refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
                    expiresInSec = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L,
                )
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
