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

    /**
     * Authorization code + PKCE through a Custom Tab, redirecting to a loopback port.
     * [client] receives the exact redirect URI so dynamic registration can register it.
     */
    suspend fun runPkceFlow(
        authEndpoint: String,
        tokenEndpoint: String,
        scopes: List<String>,
        resource: String?,
        client: suspend (redirectUri: String) -> com.qigai.neon.connectors.OAuthClient?,
    ): Result<Pair<OAuthTokenResponse, com.qigai.neon.connectors.OAuthClient>> = runCatching {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateCodeVerifier().take(24)
        val (socket, redirectUri) = LoopbackCallback.createLoopbackServer()
        val oc = try { client(redirectUri) } catch (e: Exception) { socket.close(); throw e }
            ?: run { socket.close(); error("该服务不支持自动注册客户端，请改用令牌连接") }

        val uriBuilder = Uri.parse(authEndpoint).buildUpon()
            .appendQueryParameter("client_id", oc.clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
        if (scopes.isNotEmpty()) uriBuilder.appendQueryParameter("scope", scopes.joinToString(" "))
        if (!resource.isNullOrBlank()) uriBuilder.appendQueryParameter("resource", resource)

        val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        customTabs.launchUrl(context, uriBuilder.build())

        val callback = LoopbackCallback.awaitCallback(socket)
        if (!callback.error.isNullOrBlank()) error("授权被拒绝：${callback.error}")
        if (callback.state != state || callback.code.isNullOrBlank()) error("授权回调校验失败")
        exchangeCode(tokenEndpoint, oc, callback.code, redirectUri, verifier, resource) to oc
    }

    private suspend fun exchangeCode(
        tokenEndpoint: String,
        client: com.qigai.neon.connectors.OAuthClient,
        code: String,
        redirectUri: String,
        verifier: String,
        resource: String?,
    ): OAuthTokenResponse = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", client.clientId)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
        client.clientSecret?.let { body.add("client_secret", it) }
        if (!resource.isNullOrBlank()) body.add("resource", resource)
        val req = Request.Builder().url(tokenEndpoint).header("Accept", "application/json").post(body.build()).build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("令牌换取失败 (${resp.code})：${raw.take(200)}")
            parseToken(raw)
        }
    }

    private fun parseToken(raw: String): OAuthTokenResponse {
        val obj = json.parseToJsonElement(raw).jsonObject
        return OAuthTokenResponse(
            accessToken = obj["access_token"]?.jsonPrimitive?.content ?: error("缺少 access_token"),
            refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
            expiresInSec = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600L,
        )
    }

    suspend fun refreshToken(
        tokenEndpoint: String,
        clientId: String,
        refreshToken: String,
        clientSecret: String? = null,
        resource: String? = null,
    ): Result<OAuthTokenResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val b = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
            clientSecret?.let { b.add("client_secret", it) }
            if (!resource.isNullOrBlank()) b.add("resource", resource)
            val body = b.build()
            val req = Request.Builder().url(tokenEndpoint).header("Accept", "application/json").post(body).build()
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
