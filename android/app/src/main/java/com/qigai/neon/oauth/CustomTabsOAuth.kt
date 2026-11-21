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

class CustomTabsOAuth(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val tokenStore: EncryptedTokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun authorizeGoogle(scopes: List<String>): Result<OAuthTokenResponse> {
        val clientId = tokenStore.getGoogleClientId()
        if (clientId.isBlank()) {
            return Result.failure(IllegalStateException("请先在设置中填写 Google OAuth Client ID"))
        }
        return runPkceFlow(
            authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            clientId = clientId,
            scopes = scopes,
            extraAuthParams = mapOf("access_type" to "offline", "prompt" to "consent"),
        ).onSuccess { tokens ->
            val email = fetchGoogleEmail(tokens.accessToken)
            tokenStore.saveGoogleTokens(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAtMs = System.currentTimeMillis() + tokens.expiresInSec * 1000L,
                email = email,
            )
        }
    }

    suspend fun ensureValidGoogleAccessToken(): String? {
        val current = tokenStore.getGoogleAccessToken()
        val expiresAt = tokenStore.getGoogleExpiresAt()
        if (!current.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000L) {
            return current
        }
        val refresh = tokenStore.getGoogleRefreshToken() ?: return null
        val clientId = tokenStore.getGoogleClientId()
        if (clientId.isBlank()) return null

        val refreshed = refreshToken(
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            clientId = clientId,
            refreshToken = refresh,
        ).getOrNull() ?: return null

        tokenStore.saveGoogleTokens(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken ?: refresh,
            expiresAtMs = System.currentTimeMillis() + refreshed.expiresInSec * 1000L,
            email = null,
        )
        return refreshed.accessToken
    }

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

    private suspend fun fetchGoogleEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/oauth2/v2/userinfo")
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val raw = resp.body?.string().orEmpty()
                json.parseToJsonElement(raw).jsonObject["email"]?.jsonPrimitive?.content
            }
        }.getOrNull()
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
