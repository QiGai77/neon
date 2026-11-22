package com.qigai.neon.connectors

import com.qigai.neon.data.ConnectorDao
import com.qigai.neon.data.ConnectorEntity
import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.oauth.McpOAuth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Owns every MCP connector: connecting (public, token, URL key or OAuth), storing the tool list,
 * and calling tools with a token refresh when the server answers 401.
 */
class McpHub(
    private val dao: ConnectorDao,
    private val tokens: EncryptedTokenStore,
    private val client: McpClient,
    private val discovery: McpAuthDiscovery,
    private val oauth: McpOAuth,
    private val local: LocalStdioMcpBridge,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Connects a remote server. [headers] carries a token or API key the user typed. */
    suspend fun connectRemote(id: String, name: String, url: String, headers: Map<String, String> = emptyMap()): Result<Int> {
        if (headers.isNotEmpty()) saveHeaders(id, headers)
        dao.upsert(ConnectorEntity(id, name, "remote_mcp", "正在连接", url, "connecting", 0))
        client.forget(url)
        var tools = client.listTools(url, tokens.getConnectorToken(id), headers(id))
        val err = tools.exceptionOrNull()
        if (err is McpHttpException && err.code == 401) {
            tools = refresh(id, url)?.let { client.listTools(url, it, headers(id)) } ?: tools
            if (tools.isFailure && (tools.exceptionOrNull() as? McpHttpException)?.code == 401) {
                tools = authorize(id, name, url, (tools.exceptionOrNull() as McpHttpException).wwwAuthenticate ?: err.wwwAuthenticate)
            }
        }
        return store(id, name, "remote_mcp", url, tools)
    }

    suspend fun connectLocal(id: String, name: String, command: String): Result<Int> {
        dao.upsert(ConnectorEntity(id, name, "local_stdio", "首次启动需要下载，请稍候", command, "connecting", 0))
        return store(id, name, "local_stdio", command, local.listTools(command))
    }

    suspend fun disconnect(id: String) {
        dao.getById(id)?.endpointUrl?.let { client.forget(it) }
        dao.deleteById(id)
        tokens.removeConnectorToken(id)
        tokens.remove(KEY_HEADERS + id)
        tokens.remove(KEY_OAUTH + id)
    }

    suspend fun call(connId: String, tool: String, args: JsonObject): Result<String> {
        val conn = dao.getById(connId) ?: return Result.failure(IllegalStateException("未找到连接器 $connId"))
        val target = conn.endpointUrl.orEmpty()
        if (conn.category == "local_stdio") return local.callTool(target, tool, args)
        val first = client.callTool(target, tokens.getConnectorToken(connId), tool, args, headers(connId))
        val e = first.exceptionOrNull()
        if (e is McpHttpException && e.code == 401) {
            val fresh = refresh(connId, target) ?: return Result.failure(IllegalStateException("${conn.name} 的登录已失效，请在连接器里重新连接"))
            return client.callTool(target, fresh, tool, args, headers(connId))
        }
        return first
    }

    private suspend fun authorize(id: String, name: String, url: String, wwwAuthenticate: String?): Result<List<McpRemoteTool>> {
        val meta = discovery.discover(url, wwwAuthenticate)
            ?: return Result.failure(IllegalStateException("需要登录，但没有找到授权方式"))
        dao.upsert(ConnectorEntity(id, name, "remote_mcp", "在浏览器里完成授权", url, "connecting", 0))
        val flow = oauth.runPkceFlow(meta.authorizationEndpoint, meta.tokenEndpoint, meta.scopesSupported, meta.resource) { redirect ->
            meta.registrationEndpoint?.let { discovery.dynamicRegisterClient(it, redirect) }
        }
        val (tok, oc) = flow.getOrElse { return Result.failure(it) }
        tokens.saveConnectorToken(id, tok.accessToken, tok.refreshToken)
        tokens.put(KEY_OAUTH + id, buildJsonObject {
            put("token", meta.tokenEndpoint); put("client", oc.clientId)
            oc.clientSecret?.let { put("secret", it) }
            put("resource", meta.resource)
        }.toString())
        client.forget(url)
        return client.listTools(url, tok.accessToken, headers(id))
    }

    /** Uses the stored refresh token; returns the new access token or null. */
    private suspend fun refresh(id: String, url: String): String? {
        val meta = tokens.get(KEY_OAUTH + id)?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return null
        val rt = tokens.get("conn_refresh_$id") ?: return null
        val tok = oauth.refreshToken(
            meta.str("token") ?: return null, meta.str("client") ?: return null, rt,
            meta.str("secret"), meta.str("resource"),
        ).getOrNull() ?: return null
        tokens.saveConnectorToken(id, tok.accessToken, tok.refreshToken ?: rt)
        client.forget(url)
        return tok.accessToken
    }

    private suspend fun store(id: String, name: String, category: String, target: String, tools: Result<List<McpRemoteTool>>): Result<Int> =
        tools.fold(
            onSuccess = { list ->
                val arr = JsonArray(list.map { t ->
                    buildJsonObject { put("name", t.name); put("description", t.description); put("inputSchema", t.inputSchema) }
                })
                dao.upsert(ConnectorEntity(id, name, category, "已连接", target, "connected", list.size, arr.toString()))
                Result.success(list.size)
            },
            onFailure = { e ->
                dao.upsert(ConnectorEntity(id, name, category, friendly(e), target, "error", 0))
                Result.failure(e)
            },
        )

    private fun friendly(e: Throwable): String = when {
        e is McpHttpException && e.code == 401 -> "需要登录"
        e is McpHttpException && e.code == 403 -> "没有权限"
        e is java.net.UnknownHostException -> "网络不可用"
        e is java.net.SocketTimeoutException -> "连接超时"
        else -> e.message?.take(80) ?: "连接失败"
    }

    fun headers(id: String): Map<String, String> =
        tokens.get(KEY_HEADERS + id)?.let { raw ->
            runCatching { json.parseToJsonElement(raw).jsonObject.mapValues { (_, v) -> (v as JsonPrimitive).content } }.getOrNull()
        } ?: emptyMap()

    private fun saveHeaders(id: String, h: Map<String, String>) {
        tokens.put(KEY_HEADERS + id, JsonObject(h.mapValues { JsonPrimitive(it.value) }).toString())
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        private const val KEY_HEADERS = "conn_headers_"
        private const val KEY_OAUTH = "conn_oauth_"

        /** Connector ids become tool-name prefixes, so keep them to letters and digits. */
        fun safeId(raw: String): String {
            val s = raw.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
            return if (s.length <= 14) s.ifBlank { "mcp" } else s.take(8) + Integer.toHexString(raw.hashCode()).take(6)
        }
    }
}
