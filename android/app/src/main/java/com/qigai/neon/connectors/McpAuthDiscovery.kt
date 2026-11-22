package com.qigai.neon.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
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
    /** RFC 8707 resource indicator for this MCP server. */
    val resource: String = "",
)

data class OAuthClient(val clientId: String, val clientSecret: String?)

/** MCP authorization discovery: protected resource metadata, then the authorization server's metadata. */
class McpAuthDiscovery(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun discover(mcpServerUrl: String, wwwAuthenticate: String? = null): McpOAuthMetadata? = withContext(Dispatchers.IO) {
        val uri = runCatching { URI(mcpServerUrl) }.getOrNull() ?: return@withContext null
        val origin = "${uri.scheme}://${uri.authority}"
        val path = uri.path.orEmpty().trimEnd('/')

        val hinted = wwwAuthenticate?.let { Regex("resource_metadata=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        val prmCandidates = listOfNotNull(
            hinted,
            if (path.isNotEmpty()) "$origin/.well-known/oauth-protected-resource$path" else null,
            "$origin/.well-known/oauth-protected-resource",
        )
        val prm = prmCandidates.firstNotNullOfOrNull { getJson(it) }
        val authServer = prm?.get("authorization_servers")?.let { runCatching { it.jsonArray.firstOrNull()?.jsonPrimitive?.content }.getOrNull() } ?: origin
        val resource = prm?.get("resource")?.jsonPrimitive?.contentOrNull ?: mcpServerUrl
        val prmScopes = prm?.get("scopes_supported")?.let { runCatching { it.jsonArray.mapNotNull { s -> s.jsonPrimitive.contentOrNull } }.getOrNull() }
        val hintScopes = wwwAuthenticate?.let { Regex("scope=\"([^\"]+)\"").find(it)?.groupValues?.get(1)?.split(' ')?.filter { s -> s.isNotBlank() } }

        val asUri = runCatching { URI(authServer) }.getOrNull() ?: return@withContext null
        val asOrigin = "${asUri.scheme}://${asUri.authority}"
        val asPath = asUri.path.orEmpty().trimEnd('/')
        val metaCandidates = listOf(
            "$asOrigin/.well-known/oauth-authorization-server$asPath",
            "$asOrigin/.well-known/openid-configuration$asPath",
            "${authServer.trimEnd('/')}/.well-known/openid-configuration",
            "${authServer.trimEnd('/')}/.well-known/oauth-authorization-server",
        ).distinct()
        val meta = metaCandidates.firstNotNullOfOrNull { getJson(it)?.let { o -> parse(o) } } ?: return@withContext null
        meta.copy(scopesSupported = hintScopes ?: prmScopes ?: emptyList(), resource = resource)
    }

    private fun getJson(url: String): JsonObject? = runCatching {
        httpClient.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { resp ->
            if (!resp.isSuccessful) null else json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
        }
    }.getOrNull()

    fun parseAuthServerMetadata(rawJson: String): McpOAuthMetadata? =
        runCatching { parse(json.parseToJsonElement(rawJson).jsonObject) }.getOrNull()

    private fun parse(obj: JsonObject): McpOAuthMetadata? {
        val auth = obj["authorization_endpoint"]?.jsonPrimitive?.contentOrNull ?: return null
        val token = obj["token_endpoint"]?.jsonPrimitive?.contentOrNull ?: return null
        val reg = obj["registration_endpoint"]?.jsonPrimitive?.contentOrNull
        val scopes = obj["scopes_supported"]?.let { runCatching { it.jsonArray.mapNotNull { s -> s.jsonPrimitive.contentOrNull } }.getOrNull() } ?: emptyList()
        return McpOAuthMetadata(auth, token, reg, scopes)
    }

    /** Dynamic client registration (RFC 7591) for the exact redirect URI the flow will use. */
    suspend fun dynamicRegisterClient(registrationEndpoint: String, redirectUri: String): OAuthClient? =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("client_name", "Neon")
                    putJsonArray("redirect_uris") { add(redirectUri) }
                    putJsonArray("grant_types") { add("authorization_code"); add("refresh_token") }
                    putJsonArray("response_types") { add("code") }
                    put("token_endpoint_auth_method", "none")
                }
                val req = Request.Builder().url(registrationEndpoint)
                    .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val id = obj["client_id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                    OAuthClient(id, obj["client_secret"]?.jsonPrimitive?.contentOrNull)
                }
            }.getOrNull()
        }
}
