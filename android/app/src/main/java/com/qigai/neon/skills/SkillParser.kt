package com.qigai.neon.skills

data class SkillDocument(
    val id: String,
    val name: String,
    val command: String,
    val description: String,
    val connectors: List<String>,
    val risk: String,
    val body: String,
    val fileName: String,
)

object SkillParser {
    fun parse(fileName: String, rawMarkdown: String): SkillDocument? {
        val trimmed = rawMarkdown.trim()
        if (!trimmed.startsWith("---")) return null
        val endIdx = trimmed.indexOf("\n---", startIndex = 3)
        if (endIdx == -1) return null

        val frontmatter = trimmed.substring(3, endIdx).trim()
        val body = trimmed.substring(endIdx + 4).trim()

        val meta = mutableMapOf<String, String>()
        frontmatter.lines().forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) {
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                meta[key] = value
            }
        }

        val name = meta["name"]?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".md")
        val command = meta["command"]?.takeIf { it.isNotBlank() }
            ?: "/${name.lowercase().replace(' ', '_')}"
        val desc = meta["description"].orEmpty()
        val connectors = meta["connectors"]
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val risk = meta["risk"]?.takeIf { it.isNotBlank() } ?: "低风险"

        return SkillDocument(
            id = fileName,
            name = name,
            command = if (command.startsWith("/")) command else "/$command",
            description = desc,
            connectors = connectors,
            risk = risk,
            body = body,
            fileName = fileName,
        )
    }
}
