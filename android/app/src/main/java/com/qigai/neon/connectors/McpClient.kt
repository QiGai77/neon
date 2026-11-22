package com.qigai.neon.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class McpRemoteTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

/** HTTP status the server answered with, so callers can tell "needs sign-in" apart from other failures. */
class McpHttpException(val code: Int, val wwwAuthenticate: String?, message: String) : Exception(message)

/**
 * MCP Streamable HTTP client: initialize handshake, Mcp-Session-Id, JSON or SSE replies,
 * tools/list pagination. Sessions are cached per endpoint and re-opened when the server drops them.
 */
class McpClient(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessions = ConcurrentHashMap<String, String>()
    private val ids = AtomicLong(1)

    suspend fun listTools(endpointUrl: String, bearerToken: String?, headers: Map<String, String> = emptyMap()): Result<List<McpRemoteTool>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val h = authHeaders(bearerToken, headers)
                val out = mutableListOf<McpRemoteTool>()
                var cursor: String? = null
                var pages = 0
                do {
                    val result = request(endpointUrl, h, "tools/list", buildJsonObject { cursor?.let { put("cursor", it) } })
                    result["tools"]?.jsonArray?.forEach { el ->
                        val t = el.jsonObject
                        val name = t["name"]?.jsonPrimitive?.content ?: return@forEach
                        out += McpRemoteTool(name, t["description"]?.jsonPrimitive?.contentOrNull.orEmpty(), t["inputSchema"]?.jsonObject ?: JsonObject(emptyMap()))
                    }
                    cursor = result["nextCursor"]?.jsonPrimitive?.contentOrNull
                } while (cursor != null && ++pages < 10)
                out
            }
        }

    suspend fun callTool(
        endpointUrl: String,
        bearerToken: String?,
        toolName: String,
        arguments: JsonObject,
        headers: Map<String, String> = emptyMap(),
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = request(endpointUrl, authHeaders(bearerToken, headers), "tools/call", buildJsonObject {
                put("name", toolName)
                put("arguments", arguments)
            })
            renderToolResult(result)
        }
    }

    fun forget(endpointUrl: String) { sessions.remove(endpointUrl) }

    private fun authHeaders(token: String?, extra: Map<String, String>): Map<String, String> =
        if (token.isNullOrBlank()) extra else extra + ("Authorization" to "Bearer $token")

    private fun request(url: String, headers: Map<String, String>, method: String, params: JsonObject): JsonObject {
        if (!sessions.containsKey(url)) initialize(url, headers)
        return try {
            rpc(url, headers, method, params)
        } catch (e: McpHttpException) {
            // 404 means the session expired; 400 is what some servers send for an unknown session id
            if ((e.code == 404 || e.code == 400) && sessions.containsKey(url)) {
                sessions.remove(url)
                initialize(url, headers)
                rpc(url, headers, method, params)
            } else throw e
        }
    }

    private fun initialize(url: String, headers: Map<String, String>) {
        sessions.remove(url)
        val id = ids.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", "initialize")
            putJsonObject("params") {
                put("protocolVersion", PROTOCOL)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") { put("name", "neon-android"); put("version", "0.3") }
            }
        }
        post(url, headers, body.toString(), null).use { resp ->
            val sid = resp.header("Mcp-Session-Id")
            readReply(resp, id)
            sessions[url] = sid.orEmpty()
        }
        val note = buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/initialized") }
        runCatching { post(url, headers, note.toString(), sessions[url]?.takeIf { it.isNotEmpty() }).close() }
    }

    private fun rpc(url: String, headers: Map<String, String>, method: String, params: JsonObject): JsonObject {
        val id = ids.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params)
        }
        return post(url, headers, body.toString(), sessions[url]?.takeIf { it.isNotEmpty() }).use { readReply(it, id) }
    }

    private fun post(url: String, headers: Map<String, String>, body: String, session: String?): Response {
        val b = Request.Builder().url(url)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL)
            .post(body.toRequestBody("application/json".toMediaType()))
        headers.forEach { (k, v) -> b.header(k, v) }
        if (session != null) b.header("Mcp-Session-Id", session)
        val resp = httpClient.newCall(b.build()).execute()
        if (!resp.isSuccessful && resp.code != 202) {
            val raw = resp.body?.string().orEmpty().take(300)
            val wa = resp.header("WWW-Authenticate")
            resp.close()
            throw McpHttpException(resp.code, wa, "MCP ${resp.code}: ${raw.ifBlank { resp.message }}")
        }
        return resp
    }

    /** Reads the JSON-RPC reply for [id] from a plain JSON body or an SSE stream. */
    private fun readReply(resp: Response, id: Long): JsonObject {
        val type = resp.header("Content-Type").orEmpty()
        val body = resp.body ?: error("MCP 无响应")
        val msg: JsonObject = if (type.contains("text/event-stream")) {
            val src = body.source()
            var found: JsonObject? = null
            val data = StringBuilder()
            while (found == null) {
                val line = src.readUtf8Line() ?: break
                when {
                    line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.removePrefix("data:").trimStart()) }
                    line.isEmpty() && data.isNotEmpty() -> {
                        val obj = runCatching { json.parseToJsonElement(data.toString()).jsonObject }.getOrNull()
                        data.clear()
                        if (obj != null && obj.idEquals(id)) found = obj
                    }
                }
            }
            if (found == null && data.isNotEmpty()) found = runCatching { json.parseToJsonElement(data.toString()).jsonObject }.getOrNull()
            found ?: error("MCP 流中没有找到回复")
        } else {
            val raw = body.string()
            val el = json.parseToJsonElement(raw)
            // batched replies arrive as an array
            (runCatching { el.jsonArray.map { it.jsonObject }.firstOrNull { it.idEquals(id) } }.getOrNull()) ?: el.jsonObject
        }
        msg["error"]?.let { err ->
            val m = runCatching { err.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull() ?: err.toString()
            error("MCP 错误：$m")
        }
        return msg["result"]?.jsonObject ?: JsonObject(emptyMap())
    }

    private fun JsonObject.idEquals(id: Long): Boolean {
        val v = this["id"] as? JsonPrimitive ?: return false
        return v.content == id.toString()
    }

    companion object {
        const val PROTOCOL = "2025-06-18"

        /** Flattens a tools/call result into text the model can read. */
        fun renderToolResult(result: JsonObject): String {
            val parts = result["content"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty().mapNotNull { el: JsonElement ->
                val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                when (o["type"]?.jsonPrimitive?.contentOrNull) {
                    "text" -> o["text"]?.jsonPrimitive?.contentOrNull
                    "image" -> "[图片 ${o["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()}]"
                    "resource" -> o["resource"]?.jsonObject?.let { r -> r["text"]?.jsonPrimitive?.contentOrNull ?: r["uri"]?.jsonPrimitive?.contentOrNull }
                    "resource_link" -> o["uri"]?.jsonPrimitive?.contentOrNull
                    else -> o.toString()
                }
            }
            val text = parts.joinToString("\n").ifBlank { result["structuredContent"]?.toString() ?: result.toString() }
            val isError = result["isError"]?.jsonPrimitive?.contentOrNull == "true"
            return if (isError) "ERROR: $text" else text
        }
    }
}
