package com.qigai.neon.connectors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class McpRemoteTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

class McpClient(private val httpClient: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listTools(endpointUrl: String, bearerToken: String?): Result<List<McpRemoteTool>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "tools/list")
                    putJsonObject("params") {}
                }
                val reqBuilder = Request.Builder()
                    .url(endpointUrl)
                    .header("Accept", "application/json, text/event-stream")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                if (!bearerToken.isNullOrBlank()) {
                    reqBuilder.header("Authorization", "Bearer $bearerToken")
                }
                httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("MCP tools/list 失败 (${resp.code}): $raw")
                    val obj = json.parseToJsonElement(raw).jsonObject
                    val toolsArr = obj["result"]?.jsonObject?.get("tools")?.jsonArray ?: emptyList()
                    toolsArr.mapNotNull { el ->
                        val t = el.jsonObject
                        val name = t["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val desc = t["description"]?.jsonPrimitive?.content.orEmpty()
                        val schema = t["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
                        McpRemoteTool(name, desc, schema)
                    }
                }
            }
        }

    suspend fun callTool(
        endpointUrl: String,
        bearerToken: String?,
        toolName: String,
        arguments: JsonObject,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", System.currentTimeMillis())
                put("method", "tools/call")
                putJsonObject("params") {
                    put("name", toolName)
                    put("arguments", arguments)
                }
            }
            val reqBuilder = Request.Builder()
                .url(endpointUrl)
                .header("Accept", "application/json, text/event-stream")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            if (!bearerToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $bearerToken")
            }
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("MCP 调用失败 (${resp.code}): $raw")
                raw
            }
        }
    }
}
