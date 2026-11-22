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
    /** Folder under /workspace/skills for skills that ship with scripts or references; null for a single file. */
    val dir: String? = null,
    /** owner/repo the skill came from, when installed from the market. */
    val source: String? = null,
) {
    /** Path of the SKILL.md inside the sandbox. */
    val sandboxPath: String get() = if (dir != null) "/workspace/skills/$dir/SKILL.md" else "/workspace/skills/$fileName"
}

object SkillParser {
    fun parse(fileName: String, rawMarkdown: String, dir: String? = null, source: String? = null): SkillDocument? {
        val trimmed = rawMarkdown.trim()
        if (!trimmed.startsWith("---")) return null
        val endIdx = trimmed.indexOf("\n---", startIndex = 3)
        if (endIdx == -1) return null

        val frontmatter = trimmed.substring(3, endIdx).trim()
        val body = trimmed.substring(endIdx + 4).trim()

        val meta = mutableMapOf<String, String>()
        var blockKey: String? = null
        val block = StringBuilder()
        fun closeBlock() {
            blockKey?.let { meta[it] = block.toString().trim() }
            blockKey = null; block.clear()
        }
        frontmatter.lines().forEach { line ->
            val indented = line.startsWith(" ") || line.startsWith("\t")
            if (blockKey != null && (indented || line.isBlank())) {
                if (block.isNotEmpty()) block.append(' ')
                block.append(line.trim())
                return@forEach
            }
            closeBlock()
            val colon = line.indexOf(':')
            if (colon > 0 && !indented) {
                val key = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                if (value == ">" || value == "|" || value == ">-" || value == "|-" || value.isEmpty()) blockKey = key
                else meta[key] = value.removeSurrounding("\"").removeSurrounding("'")
            }
        }
        closeBlock()

        val name = meta["name"]?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".md").removeSuffix(".SKILL")
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
            dir = dir,
            source = source,
        )
    }
}
