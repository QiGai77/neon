package com.qigai.neon.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Content blocks persisted per message (JSON array in messages.blocks). */
@Serializable
sealed class ContentBlock {
    @Serializable @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable @SerialName("thinking")
    data class Thinking(val thinking: String, val signature: String? = null, val durationMs: Long? = null) : ContentBlock()

    @Serializable @SerialName("tool_use")
    data class ToolUse(val id: String, val name: String, val inputJson: String = "{}") : ContentBlock()

    @Serializable @SerialName("server_tool_use")
    data class ServerToolUse(val id: String, val name: String, val inputJson: String = "{}") : ContentBlock()

    /** A file the user picked, copied into the sandbox. */
    @Serializable @SerialName("attachment")
    data class Attachment(val name: String, val path: String, val mime: String = "", val size: Long = 0) : ContentBlock()

    @Serializable @SerialName("tool_result")
    data class ToolResult(val toolUseId: String, val content: String, val isError: Boolean = false) : ContentBlock()
}

object BlocksJson {
    val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type"; encodeDefaults = false }
    fun encode(blocks: List<ContentBlock>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ContentBlock.serializer()), blocks)
    fun decode(s: String): List<ContentBlock> = try {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ContentBlock.serializer()), s)
    } catch (_: Exception) {
        emptyList()
    }
}

fun List<ContentBlock>.plainText(): String = filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
