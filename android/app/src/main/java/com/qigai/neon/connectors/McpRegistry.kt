package com.qigai.neon.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** One server from the official MCP Registry, reduced to what Neon can run. */
data class RegistryServer(
    val name: String,
    val title: String,
    val description: String,
    /** Streamable HTTP endpoint, when the server is hosted. */
    val remoteUrl: String?,
    /** Header the hosted server wants, such as an API key. */
    val remoteHeader: String?,
    /** Sandbox command for an npm or PyPI package. */
    val command: String?,
    /** Environment variables the package needs before it can start. */
    val envVars: List<String>,
) {
    val runnable: Boolean get() = remoteUrl != null || command != null
}

/** Client for registry.modelcontextprotocol.io. */
class McpRegistry(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, cursor: String? = null): Result<Pair<List<RegistryServer>, String?>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = BASE.toHttpUrl().newBuilder()
                .addQueryParameter("limit", "40")
                .addQueryParameter("version", "latest")
                .apply { if (query.isNotBlank()) addQueryParameter("search", query.trim()) }
                .apply { if (cursor != null) addQueryParameter("cursor", cursor) }
                .build()
            http.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { resp ->
                if (!resp.isSuccessful) error("市场暂时打不开 (${resp.code})")
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                val list = root["servers"]?.jsonArray.orEmpty().mapNotNull { el ->
                    val s = runCatching { el.jsonObject["server"]?.jsonObject ?: el.jsonObject }.getOrNull() ?: return@mapNotNull null
                    parse(s)
                }.distinctBy { it.name }
                val next = root["metadata"]?.jsonObject?.get("nextCursor")?.jsonPrimitive?.contentOrNull
                list to next
            }
        }
    }

    private fun parse(s: JsonObject): RegistryServer? {
        val name = s.str("name") ?: return null
        val remote = s["remotes"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
            .map { it.jsonObject }
            .firstOrNull { it.str("type") == "streamable-http" && it.str("url")?.contains("{") == false }
        val header = remote?.get("headers")?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
            .map { it.jsonObject }.firstOrNull { it.str("isRequired") == "true" || it.str("isSecret") == "true" }?.str("name")
        var envs = emptyList<String>()
        val cmd = s["packages"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty().map { it.jsonObject }
            .firstNotNullOfOrNull { p ->
                val transport = p["transport"]?.let { runCatching { it.jsonObject.str("type") }.getOrNull() }
                if (transport != null && transport != "stdio") return@firstNotNullOfOrNull null
                val id = p.str("identifier") ?: return@firstNotNullOfOrNull null
                val c = when (p.str("registryType") ?: p.str("registry_type")) {
                    "npm" -> "npx -y $id"
                    "pypi" -> "uvx $id"
                    else -> null
                } ?: return@firstNotNullOfOrNull null
                envs = p["environmentVariables"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
                    .map { it.jsonObject }.filter { it.str("isRequired") == "true" }.mapNotNull { it.str("name") }
                c
            }
        return RegistryServer(
            name = name,
            title = s.str("title") ?: name.substringAfterLast('/'),
            description = s.str("description").orEmpty(),
            remoteUrl = remote?.str("url"),
            remoteHeader = header,
            command = cmd,
            envVars = envs,
        )
    }

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    companion object {
        const val BASE = "https://registry.modelcontextprotocol.io/v0/servers"
    }
}
