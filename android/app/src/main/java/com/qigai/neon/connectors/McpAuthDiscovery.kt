package com.qigai.neon.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

data class McpOAuthMetadata(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val registrationEndpoint: String?,
    val scopesSupported: List<String>,
)

class McpAuthDiscovery(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(mcpServerUrl: String): McpOAuthMetadata? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(mcpServerUrl) }.getOrNull() ?: return@withContext null
        val origin = "${uri.scheme}://${uri.authority}"

        val prUrl = "$origin/.well-known/oauth-protected-resource"
        val authServerOrigin = runCatching {
            httpClient.newCall(Request.Builder().url(prUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use origin
                val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                obj["authorization_servers"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: origin
            }
        }.getOrDefault(origin)

        val metaUrl = "${authServerOrigin.trimEnd('/')}/.well-known/oauth-authorization-server"
        runCatching {
            httpClient.newCall(Request.Builder().url(metaUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                parseAuthServerMetadata(resp.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    fun parseAuthServerMetadata(rawJson: String): McpOAuthMetadata? = runCatching {
        val obj = json.parseToJsonElement(rawJson).jsonObject
        val auth = obj["authorization_endpoint"]?.jsonPrimitive?.content ?: return null
        val token = obj["token_endpoint"]?.jsonPrimitive?.content ?: return null
        val reg = obj["registration_endpoint"]?.jsonPrimitive?.content
        val scopes = obj["scopes_supported"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
        McpOAuthMetadata(auth, token, reg, scopes)
    }.getOrNull()

    suspend fun dynamicRegisterClient(registrationEndpoint: String, redirectUri: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = """{"client_name":"Neon Android","redirect_uris":["$redirectUri"],"token_endpoint_auth_method":"none"}"""
                val req = Request.Builder()
                    .url(registrationEndpoint)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    obj["client_id"]?.jsonPrimitive?.content
                }
            }.getOrNull()
        }
}
