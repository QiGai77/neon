package com.qigai.neon.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.zip.GZIPInputStream

data class MarketSkill(
    /** owner/repo */
    val source: String,
    val skillId: String,
    val name: String,
    val installs: Long,
) {
    val key: String get() = "$source/$skillId"
}

enum class MarketView(val label: String, val path: String) {
    ALL("热门", "all-time"),
    TRENDING("上升", "trending"),
    HOT("今日", "hot"),
}

/**
 * skills.sh catalog (the same public endpoints the `npx skills` CLI uses) plus an installer that
 * pulls the skill folder straight from its GitHub repo.
 */
class SkillMarket(
    private val http: OkHttpClient,
    private val repo: SkillRepository,
    private val githubToken: () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(view: MarketView, page: Int = 0): Result<List<MarketSkill>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = getJson("$SITE/api/skills/${view.path}/$page")
            root["skills"]?.jsonArray.orEmpty().mapNotNull { parse(it.jsonObject) }
        }
    }

    suspend fun search(q: String): Result<List<MarketSkill>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$SITE/api/search".toHttpUrl().newBuilder().addQueryParameter("q", q.trim()).addQueryParameter("limit", "50").build().toString()
            getJson(url)["skills"]?.jsonArray.orEmpty().mapNotNull { parse(it.jsonObject) }
        }
    }

    private fun parse(o: JsonObject): MarketSkill? {
        val source = o["source"]?.jsonPrimitive?.contentOrNull ?: return null
        // well-known sources (a bare domain) are not GitHub repos
        if (source.count { it == '/' } != 1) return null
        val id = o["skillId"]?.jsonPrimitive?.contentOrNull ?: o["slug"]?.jsonPrimitive?.contentOrNull ?: return null
        return MarketSkill(source, id, o["name"]?.jsonPrimitive?.contentOrNull ?: id, o["installs"]?.jsonPrimitive?.longOrNull ?: 0)
    }

    /** Downloads the skill folder and installs it under /workspace/skills/<skillId>. */
    suspend fun install(s: MarketSkill): Result<SkillDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val files = runCatching { viaTreeApi(s) }.getOrNull() ?: viaTarball(s)
            repo.installFolder(safeDir(s.skillId), s.key, files).getOrThrow()
        }
    }

    /** GitHub trees API: one request to find the folder, then raw downloads. */
    private fun viaTreeApi(s: MarketSkill): Map<String, ByteArray>? {
        val tree = getJson("https://api.github.com/repos/${s.source}/git/trees/HEAD?recursive=1", github = true)
        val blobs = tree["tree"]?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "blob" }
            .mapNotNull { it["path"]?.jsonPrimitive?.contentOrNull }
        val skillMds = blobs.filter { it == "SKILL.md" || it.endsWith("/SKILL.md") }
        val folder = pickFolder(skillMds, s) { path -> getText("https://raw.githubusercontent.com/${s.source}/HEAD/$path") }
            ?: error("仓库里没有找到这个技能")
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        val wanted = blobs.filter { it.startsWith(prefix) && !it.removePrefix(prefix).contains("/.") }
        if (wanted.size > MAX_FILES) error("技能文件太多")
        return wanted.associate { path -> path.removePrefix(prefix) to getBytes("https://raw.githubusercontent.com/${s.source}/HEAD/$path") }
    }

    /** Fallback when the API is rate limited: stream the repo tarball and keep only the skill folder. */
    private fun viaTarball(s: MarketSkill): Map<String, ByteArray> {
        val req = Request.Builder().url("https://codeload.github.com/${s.source}/tar.gz/HEAD").build()
        val all = mutableMapOf<String, ByteArray>()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败 (${resp.code})")
            var total = 0L
            readTar(GZIPInputStream(resp.body!!.byteStream().buffered(1 shl 16))) { name, size, read ->
                if (size > 4_000_000) return@readTar
                total += size
                if (total > 60_000_000) error("仓库太大")
                all[name.substringAfter('/')] = read()
            }
        }
        val mds = all.keys.filter { it == "SKILL.md" || it.endsWith("/SKILL.md") }
        val folder = pickFolder(mds, s) { all[it]?.decodeToString() } ?: error("仓库里没有找到这个技能")
        val prefix = if (folder.isEmpty()) "" else "$folder/"
        return all.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    }

    /** Minimal tar reader: regular files with ustar prefixes, GNU long names and pax paths. */
    private fun readTar(input: java.io.InputStream, onFile: (name: String, size: Long, read: () -> ByteArray) -> Unit) {
        val header = ByteArray(512)
        var longName: String? = null
        fun full(buf: ByteArray): Boolean {
            var off = 0
            while (off < buf.size) { val n = input.read(buf, off, buf.size - off); if (n < 0) return false; off += n }
            return true
        }
        fun str(off: Int, len: Int) = String(header, off, len, Charsets.UTF_8).substringBefore('\u0000')
        fun skip(n: Long) { var left = n; val b = ByteArray(8192); while (left > 0) { val r = input.read(b, 0, minOf(b.size.toLong(), left).toInt()); if (r < 0) break; left -= r } }
        while (full(header)) {
            if (header.all { it.toInt() == 0 }) break
            val size = str(124, 12).trim().ifEmpty { "0" }.toLong(8)
            val type = header[156].toInt().toChar()
            val padded = (size + 511) / 512 * 512
            var name = longName ?: str(345, 155).let { pre -> if (pre.isNotEmpty()) "$pre/${str(0, 100)}" else str(0, 100) }
            longName = null
            when (type) {
                'L', 'x' -> {
                    val data = ByteArray(size.toInt()).also { full(it) }
                    skip(padded - size)
                    val text = data.toString(Charsets.UTF_8)
                    longName = if (type == 'L') text.trimEnd('\u0000') else Regex("\\d+ path=([^\n]+)\n").find(text)?.groupValues?.get(1)
                }
                '0', '\u0000' -> {
                    var consumed = false
                    onFile(name, size) { consumed = true; ByteArray(size.toInt()).also { full(it) } }
                    skip(if (consumed) padded - size else padded)
                }
                else -> skip(padded)
            }
        }
    }

    /** Folder whose name is the skill id, otherwise the one whose frontmatter name matches. */
    private fun pickFolder(skillMds: List<String>, s: MarketSkill, read: (String) -> String?): String? {
        val dirs = skillMds.map { it.removeSuffix("SKILL.md").trimEnd('/') }
        dirs.firstOrNull { it.substringAfterLast('/') == s.skillId }?.let { return it }
        dirs.firstOrNull { it.substringAfterLast('/').equals(s.name, true) }?.let { return it }
        for (d in dirs.take(40)) {
            val md = read(if (d.isEmpty()) "SKILL.md" else "$d/SKILL.md") ?: continue
            val doc = SkillParser.parse("SKILL.md", md) ?: continue
            if (doc.name == s.skillId || doc.name.equals(s.name, true) || s.skillId.endsWith(doc.name)) return d
        }
        return if (dirs.size == 1) dirs.first() else null
    }

    private fun safeDir(id: String) = id.lowercase().map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }.joinToString("").take(60)

    private fun request(url: String, github: Boolean = false): Request {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        if (github) githubToken()?.let { b.header("Authorization", "Bearer $it") }
        return b.build()
    }

    private fun getJson(url: String, github: Boolean = false): JsonObject =
        http.newCall(request(url, github)).execute().use { resp ->
            if (!resp.isSuccessful) error("请求失败 (${resp.code})")
            json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
        }

    private fun getText(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()

    private fun getBytes(url: String): ByteArray =
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败 (${resp.code})")
            resp.body!!.bytes()
        }

    companion object {
        const val SITE = "https://skills.sh"
        private const val MAX_FILES = 120
    }
}
