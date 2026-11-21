package com.qigai.neon.connectors

import com.qigai.neon.oauth.EncryptedTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WebhookConnector(
    private val httpClient: OkHttpClient,
    private val tokenStore: EncryptedTokenStore,
) {
    suspend fun trigger(payloadJson: String, overrideUrl: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = overrideUrl?.takeIf { it.isNotBlank() } ?: tokenStore.getWebhookUrl()
                if (url.isBlank()) error("未配置 Webhook URL")
                val secret = tokenStore.getWebhookSecret()
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payloadJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (secret.isNotBlank()) {
                    reqBuilder.header("X-Neon-Signature", secret)
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Webhook 返回 ${resp.code}: $body")
                    body.ifBlank { "HTTP ${resp.code} OK" }
                }
            }
        }
}
