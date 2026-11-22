package com.qigai.neon.skills

import android.content.Context
import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.sandbox.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Skills live in /workspace/skills, either as a single `name.md` or as a folder with a SKILL.md
 * plus whatever scripts and references it ships (the skills.sh layout).
 */
class SkillRepository(
    private val rootfsManager: RootfsManager,
    private val googleWorkspace: GoogleWorkspaceConnector,
    private val context: Context? = null,
) {
    private val _skills = MutableStateFlow<List<SkillDocument>>(emptyList())
    val skills: StateFlow<List<SkillDocument>> = _skills.asStateFlow()

    private val dir: File get() = rootfsManager.skillsDir

    suspend fun reloadFromDisk() = withContext(Dispatchers.IO) {
        val d = dir.apply { mkdirs() }
        val files = d.listFiles { f -> f.isFile && f.name.endsWith(".md") }.orEmpty()
            .mapNotNull { f -> runCatching { SkillParser.parse(f.name, f.readText()) }.getOrNull() }
        val folders = d.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty()
            .mapNotNull { f ->
                val md = File(f, "SKILL.md").takeIf { it.exists() } ?: return@mapNotNull null
                val source = File(f, SOURCE_FILE).takeIf { it.exists() }?.readText()?.trim()
                runCatching { SkillParser.parse("SKILL.md", md.readText(), dir = f.name, source = source) }.getOrNull()
            }
        _skills.value = (files + folders).sortedBy { it.name.lowercase() }
    }

    fun isInstalled(id: String): Boolean = File(dir, id).isDirectory || _skills.value.any { it.name == id || it.dir == id }

    suspend fun saveSkill(fileName: String, markdownContent: String): Result<SkillDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safeName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
                val parsed = SkillParser.parse(safeName, markdownContent)
                    ?: error("SKILL.md 缺少有效的 YAML frontmatter (--- ... ---)")
                dir.mkdirs()
                File(dir, safeName).writeText(markdownContent)
                reloadFromDisk()
                runCatching { googleWorkspace.uploadOrUpdateTextFile("技能", safeName, markdownContent) }
                parsed
            }
        }

    /** Writes a whole skill folder. [files] maps relative paths to bytes. */
    suspend fun installFolder(id: String, source: String, files: Map<String, ByteArray>): Result<SkillDocument> =
        withContext(Dispatchers.IO) {
            runCatching {
                val md = files["SKILL.md"] ?: error("没有找到 SKILL.md")
                SkillParser.parse("SKILL.md", md.decodeToString()) ?: error("SKILL.md 格式不对")
                val target = File(dir, id)
                val staging = File(dir, ".$id.tmp").apply { deleteRecursively(); mkdirs() }
                files.forEach { (path, bytes) ->
                    val f = File(staging, path).canonicalFile
                    if (!f.path.startsWith(staging.canonicalPath + File.separator)) return@forEach
                    f.parentFile?.mkdirs()
                    f.writeBytes(bytes)
                    if (path.endsWith(".sh") || path.endsWith(".py") || path.startsWith("scripts/")) f.setExecutable(true, false)
                }
                File(staging, SOURCE_FILE).writeText(source)
                target.deleteRecursively()
                if (!staging.renameTo(target)) error("写入技能目录失败")
                reloadFromDisk()
                _skills.value.first { it.dir == id }
            }
        }

    suspend fun deleteSkill(doc: SkillDocument) = withContext(Dispatchers.IO) {
        if (doc.dir != null) File(dir, doc.dir).deleteRecursively() else File(dir, doc.fileName).delete()
        reloadFromDisk()
    }

    suspend fun deleteSkill(fileName: String) = withContext(Dispatchers.IO) {
        File(dir, fileName).delete()
        reloadFromDisk()
    }

    /** Copies the skills bundled in assets/skills once; a skill the user deleted stays deleted. */
    suspend fun seedBundled() = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext
        val am = ctx.assets
        val index = runCatching { am.open("skills/index.txt").bufferedReader().readLines() }.getOrNull() ?: return@withContext
        val sources = runCatching { am.open("skills/sources.txt").bufferedReader().readLines() }.getOrDefault(emptyList())
            .mapNotNull { l -> l.split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        val marker = File(dir.apply { mkdirs() }, ".seeded")
        val seeded = if (marker.exists()) marker.readLines().toSet() else emptySet()
        val byId = index.filter { it.isNotBlank() }.groupBy { it.substringBefore('/') }
        val fresh = byId.keys - seeded
        fresh.forEach { id ->
            if (File(dir, id).exists()) return@forEach
            val files = byId.getValue(id).associate { path ->
                path.substringAfter('/') to am.open("skills/$path").use { it.readBytes() }
            }
            installFolder(id, sources[id]?.let { "$it/$id" } ?: "neon/$id", files)
        }
        if (fresh.isNotEmpty()) marker.writeText((seeded + fresh).joinToString("\n"))
    }

    fun matchByPrompt(prompt: String): SkillDocument? {
        val trimmed = prompt.trim()
        val skillsList = _skills.value
        if (trimmed.startsWith("/")) {
            val cmd = trimmed.substringBefore(" ").lowercase()
            return skillsList.firstOrNull { it.command.lowercase() == cmd }
        }
        return skillsList.firstOrNull { s ->
            s.name.length >= 2 && trimmed.contains(s.name, ignoreCase = true)
        }
    }

    /** Short catalog for the system prompt, so the model knows what it can read and use. */
    fun catalogForPrompt(limit: Int = 60): String {
        val list = _skills.value
        if (list.isEmpty()) return ""
        return buildString {
            append("【可用技能】需要时先用 sandbox_exec 读取对应 SKILL.md，再按里面的步骤做；技能目录里的脚本可以直接在沙箱运行。\n")
            list.take(limit).forEach { s ->
                append("- ").append(s.name).append("：").append(s.description.replace('\n', ' ').take(160))
                append("  ").append(s.sandboxPath).append('\n')
            }
        }
    }

    companion object {
        const val SOURCE_FILE = ".source"
    }
}
