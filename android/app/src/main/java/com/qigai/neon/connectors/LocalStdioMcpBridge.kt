package com.qigai.neon.connectors

import com.qigai.neon.sandbox.ShellExecutor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LocalStdioMcpBridge(
    private val shellExecutor: ShellExecutor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listTools(command: String): Result<List<McpRemoteTool>> = runCatching {
        val escaped = command.replace("\"", "\\\"")
        val res = shellExecutor.execute("python3 /usr/local/bin/neon-mcp-cli list --cmd \"$escaped\"")
        if (res.exitCode != 0) error("本地 stdio MCP 执行失败: ${res.output}")
        parseToolsResponse(res.output)
    }

    suspend fun callTool(command: String, toolName: String, arguments: JsonObject): Result<String> = runCatching {
        val escapedCmd = command.replace("\"", "\\\"")
        val escapedArgs = arguments.toString().replace("'", "'\"'\"'")
        val res = shellExecutor.execute("python3 /usr/local/bin/neon-mcp-cli call --cmd \"$escapedCmd\" --tool \"$toolName\" --args '$escapedArgs'")
        if (res.exitCode != 0) error("本地 stdio MCP 调用失败: ${res.output}")
        res.output
    }

    fun parseToolsResponse(rawJsonLine: String): List<McpRemoteTool> {
        val jsonLine = rawJsonLine.lines().lastOrNull { it.trim().startsWith("{") } ?: return emptyList()
        val obj = json.parseToJsonElement(jsonLine).jsonObject
        val toolsArr = obj["result"]?.jsonObject?.get("tools")?.jsonArray ?: emptyList()
        return toolsArr.mapNotNull { el ->
            val t = el.jsonObject
            val name = t["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val desc = t["description"]?.jsonPrimitive?.content.orEmpty()
            val schema = t["inputSchema"]?.jsonObject ?: JsonObject(emptyMap())
            McpRemoteTool(name, desc, schema)
        }
    }
}
