package com.qigai.neon.agent

import com.qigai.neon.provider.ToolSpec
import kotlinx.serialization.json.JsonObject

/** Result of a real tool execution. */
data class ToolOutcome(val content: String, val isError: Boolean = false)

/** A real tool. M2 registers sandbox / connectors / native tools here. */
interface NeonTool {
    val spec: ToolSpec
    suspend fun execute(input: JsonObject): ToolOutcome
}

/**
 * Tool registry. Intentionally EMPTY in M1 — no placeholder or fake tools.
 * With no tools registered nothing is declared to the model, so it cannot emit tool_use.
 */
class ToolRegistry {
    private val tools = linkedMapOf<String, NeonTool>()

    fun register(tool: NeonTool) { tools[tool.spec.name] = tool }
    fun unregister(name: String) { tools.remove(name) }
    fun get(name: String): NeonTool? = tools[name]
    fun specs(): List<ToolSpec> = tools.values.map { it.spec }
    val isEmpty: Boolean get() = tools.isEmpty()
}
