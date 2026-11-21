package com.qigai.neon.skills

import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.sandbox.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class SkillRepository(
    private val rootfsManager: RootfsManager,
    private val googleWorkspace: GoogleWorkspaceConnector,
) {
    private val _skills = MutableStateFlow<List<SkillDocument>>(emptyList())
    val skills: StateFlow<List<SkillDocument>> = _skills.asStateFlow()

    suspend fun reloadFromDisk() = withContext(Dispatchers.IO) {
        val dir = rootfsManager.skillsDir.apply { mkdirs() }
        val loaded = dir.listFiles { f -> f.isFile && f.name.endsWith(".md") }
            ?.sortedBy { it.name }
            ?.mapNotNull { file -> SkillParser.parse(file.name, file.readText()) }
            ?: emptyList()
        _skills.value = loaded
    }

    suspend fun saveSkill(fileName: String, markdownContent: String): Result<SkillDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
                val parsed = SkillParser.parse(safeName, markdownContent)
                    ?: error("SKILL.md 缺少有效的 YAML frontmatter (--- ... ---)")
                rootfsManager.skillsDir.mkdirs()
                File(rootfsManager.skillsDir, safeName).writeText(markdownContent)
                reloadFromDisk()
                googleWorkspace.uploadOrUpdateTextFile("技能", safeName, markdownContent)
                parsed
            }
        }

    suspend fun deleteSkill(fileName: String) = withContext(Dispatchers.IO) {
        File(rootfsManager.skillsDir, fileName).delete()
        reloadFromDisk()
    }

    fun matchByPrompt(prompt: String): SkillDocument? {
        val trimmed = prompt.trim()
        val skillsList = _skills.value
        if (trimmed.startsWith("/")) {
            val cmd = trimmed.substringBefore(" ").lowercase()
            return skillsList.firstOrNull { it.command.lowercase() == cmd }
        }
        return skillsList.firstOrNull { s ->
            trimmed.contains(s.name, ignoreCase = true) ||
                (s.description.isNotBlank() && s.name.length >= 2 && trimmed.contains(s.name.take(4)))
        }
    }
}
