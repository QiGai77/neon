package com.qigai.neon.sysfiles

import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.sandbox.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** One remembered fact. [kind] core is always in the prompt (who the user is, standing rules); fact is recalled by relevance. */
@Serializable
data class MemoryItem(
    val id: String,
    val text: String,
    val kind: String = KIND_FACT,
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val hits: Int = 0,
) {
    companion object {
        const val KIND_CORE = "core"
        const val KIND_FACT = "fact"
    }
}

/**
 * Long-term memory, after mem0 and Letta:
 * - Letta's split: a small core block that is always in context, and an archive recalled by relevance.
 * - mem0's write path: after a turn, the model compares what it just learned with what is stored and
 *   answers ADD, UPDATE, DELETE or NONE per fact, so memory corrects itself instead of piling up.
 * Stored as memory.json, mirrored to memory.md for people and the sandbox, and to Drive when connected.
 */
class MemoryManager(
    private val rootfsManager: RootfsManager,
    private val googleWorkspace: GoogleWorkspaceConnector,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Mutex()
    private val _all = MutableStateFlow<List<MemoryItem>>(emptyList())
    val all: StateFlow<List<MemoryItem>> = _all.asStateFlow()

    /** Plain texts, for screens that only list and remove. */
    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    private fun dir() = rootfsManager.memoryDir.apply { mkdirs() }
    private fun jsonFile() = File(dir(), "memory.json")
    private fun mdFile() = File(dir(), "memory.md")

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            val list = runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(MemoryItem.serializer()), jsonFile().readText()) }.getOrNull()
                ?: mdFile().takeIf { it.exists() }?.readLines()?.map { it.trim() }
                    ?.filter { it.startsWith("- ") }?.map { it.removePrefix("- ").removePrefix("[常驻] ").trim() }
                    ?.filter { it.isNotEmpty() }?.distinct()?.map { MemoryItem(newId(), it) }
                ?: emptyList()
            publish(list)
        }
    }

    suspend fun addMemory(entry: String, kind: String = MemoryItem.KIND_FACT) = withContext(Dispatchers.IO) {
        val clean = entry.trim()
        if (clean.isEmpty()) return@withContext
        lock.withLock {
            if (_all.value.any { it.text == clean }) return@withLock
            save(_all.value + MemoryItem(newId(), clean, kind))
        }
    }

    suspend fun removeMemory(entry: String) = withContext(Dispatchers.IO) {
        lock.withLock { save(_all.value.filterNot { it.text == entry || it.id == entry }) }
    }

    suspend fun setKind(id: String, kind: String) = withContext(Dispatchers.IO) {
        lock.withLock { save(_all.value.map { if (it.id == id) it.copy(kind = kind, updated = now()) else it }) }
    }

    /**
     * mem0-style consolidation after a finished turn. [complete] runs one model call; failures leave memory untouched.
     * Returns how many entries changed.
     */
    suspend fun reflect(userText: String, assistantText: String, complete: suspend (String) -> String): Int {
        if (userText.isBlank() || userText.length < 4) return 0
        val existing = _all.value
        val shown = (existing.filter { it.kind == MemoryItem.KIND_CORE } + relevant(userText + " " + assistantText, 30))
            .distinctBy { it.id }
        val prompt = buildString {
            appendLine("你负责维护用户的长期记忆。只记关于用户本人、且以后的对话用得上的事：身份、所在地、工作、家人朋友、偏好、习惯、长期计划、明确说过的规则。")
            appendLine("不记：一次性的任务细节、模型自己说的话、闲聊、密码和验证码、卡号、证件号。")
            appendLine("对照已有记忆，对新信息逐条给出操作：ADD 新增，UPDATE 修改已有一条（给 id），DELETE 删掉已被推翻的一条（给 id）。没有值得记的就返回空数组。")
            appendLine("kind 只有两种：core 是身份和长期规则，每次对话都要带上；fact 是其他。每条用一句完整的中文陈述，不超过 60 字。")
            appendLine("只输出 JSON 数组，如 [{\"op\":\"ADD\",\"text\":\"用户住在杭州\",\"kind\":\"core\"},{\"op\":\"UPDATE\",\"id\":\"m3\",\"text\":\"...\"},{\"op\":\"DELETE\",\"id\":\"m5\"}]")
            appendLine()
            appendLine("已有记忆：")
            if (shown.isEmpty()) appendLine("（无）") else shown.forEach { appendLine("${it.id} [${it.kind}] ${it.text}") }
            appendLine()
            appendLine("用户：${userText.take(1500)}")
            appendLine("助手：${assistantText.take(800)}")
        }
        val raw = runCatching { complete(prompt) }.getOrNull() ?: return 0
        val arr = Regex("""\[.*]""", RegexOption.DOT_MATCHES_ALL).find(raw)?.value ?: return 0
        val ops = runCatching { Json.parseToJsonElement(arr).jsonArray.map { it.jsonObject } }.getOrNull() ?: return 0
        return withContext(Dispatchers.IO) {
            lock.withLock {
                var list = _all.value
                var changed = 0
                for (o in ops.take(8)) {
                    val op = o["op"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    val id = o["id"]?.jsonPrimitive?.contentOrNull
                    val text = o["text"]?.jsonPrimitive?.contentOrNull?.trim()?.take(120)
                    val kind = o["kind"]?.jsonPrimitive?.contentOrNull?.takeIf { it == MemoryItem.KIND_CORE || it == MemoryItem.KIND_FACT }
                    when (op) {
                        "ADD" -> if (!text.isNullOrBlank() && list.none { it.text == text } && !looksSecret(text)) {
                            list = list + MemoryItem(newId(), text, kind ?: MemoryItem.KIND_FACT); changed++
                        }
                        "UPDATE" -> if (id != null && !text.isNullOrBlank() && !looksSecret(text) && list.any { it.id == id }) {
                            list = list.map { if (it.id == id) it.copy(text = text, kind = kind ?: it.kind, updated = now()) else it }; changed++
                        }
                        "DELETE" -> if (id != null && list.any { it.id == id }) { list = list.filterNot { it.id == id }; changed++ }
                    }
                }
                if (changed > 0) save(list)
                changed
            }
        }
    }

    /** Core block plus the facts most related to [query]. */
    fun formatForPrompt(query: String = ""): String {
        val list = _all.value
        if (list.isEmpty()) return ""
        val core = list.filter { it.kind == MemoryItem.KIND_CORE }.takeLast(20)
        val facts = if (list.size - core.size <= 12) list.filter { it.kind != MemoryItem.KIND_CORE }
        else relevant(query, 10).filter { it.kind != MemoryItem.KIND_CORE }.ifEmpty { list.filter { it.kind != MemoryItem.KIND_CORE }.sortedByDescending { it.updated }.take(6) }
        return buildString {
            append("【关于用户的长期记忆】以下是之前对话里记下的事，用来把事情做对，不要主动复述。")
            core.forEach { append("\n- ").append(it.text) }
            if (facts.isNotEmpty()) {
                append("\n相关的过往信息：")
                facts.forEach { append("\n- ").append(it.text) }
            }
            if (list.size > core.size + facts.size) append("\n还有 ${list.size - core.size - facts.size} 条没列出，需要时用 memory_search 查。")
        }
    }

    /** Ranks facts by character-bigram overlap, which works for Chinese without a tokenizer, then by recency. */
    fun relevant(query: String, limit: Int): List<MemoryItem> {
        val q = grams(query)
        if (q.isEmpty()) return _all.value.sortedByDescending { it.updated }.take(limit)
        return _all.value.map { it to grams(it.text).count { g -> g in q } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MemoryItem, Int>> { it.second }.thenByDescending { it.first.updated })
            .take(limit).map { it.first }
    }

    fun search(query: String): String {
        val hits = relevant(query, 15)
        return if (hits.isEmpty()) "没有相关记忆" else hits.joinToString("\n") { "${it.id} [${it.kind}] ${it.text}" }
    }

    private fun grams(s: String): Set<String> {
        val t = s.lowercase().replace(Regex("""[\s\p{Punct}，。！？、；：“”‘’（）《》]+"""), " ")
        val out = HashSet<String>()
        Regex("""[a-z0-9]{2,}""").findAll(t).forEach { out.add(it.value) }
        val han = t.filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
        for (i in 0 until han.length - 1) out.add(han.substring(i, i + 2))
        return out
    }

    private fun looksSecret(s: String) = Regex("""(密码|验证码|password|\b\d{13,19}\b|\b\d{17}[\dXx]\b)""", RegexOption.IGNORE_CASE).containsMatchIn(s)

    private fun publish(list: List<MemoryItem>) {
        _all.value = list
        _items.value = list.map { it.text }
    }

    private suspend fun save(list: List<MemoryItem>) {
        publish(list)
        jsonFile().writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(MemoryItem.serializer()), list))
        val md = buildString {
            appendLine("# Neon 个人长期记忆")
            appendLine()
            list.filter { it.kind == MemoryItem.KIND_CORE }.forEach { appendLine("- [常驻] ${it.text}") }
            list.filter { it.kind != MemoryItem.KIND_CORE }.forEach { appendLine("- ${it.text}") }
        }
        mdFile().writeText(md)
        runCatching { googleWorkspace.uploadOrUpdateTextFile("记忆", "memory.md", md) }
    }

    private fun newId(): String {
        val used = _all.value.map { it.id }.toHashSet()
        var n = _all.value.size + 1
        while ("m$n" in used) n++
        return "m$n"
    }

    private fun now() = System.currentTimeMillis()
}
