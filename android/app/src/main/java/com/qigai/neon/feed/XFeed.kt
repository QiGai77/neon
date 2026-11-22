package com.qigai.neon.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class XPost(
    val id: String,
    val handle: String,
    val name: String,
    val avatar: String?,
    val text: String,
    val image: String?,
    val likes: Int,
    val createdAt: Long,
    /** the interest the text mentions, if any */
    val matched: String?,
    val replies: Int = 0,
    val reposts: Int = 0,
    val verified: Boolean = false,
    /** the 重点关注 word the text contains, if any */
    val focus: String? = null,
) {
    val url get() = "https://x.com/$handle/status/$id"
}

/**
 * 速览 X source with no login: the public embed timeline each account exposes
 * (syndication.twitter.com, the same feed X's own profile embeds use). Some accounts get a
 * cached "top posts" list instead of the latest, so only posts from the last [days] are kept.
 */
class XFeed(private val http: OkHttpClient, private val cacheFile: java.io.File) {
    private val json = Json { ignoreUnknownKeys = true }

    /** handle -> (fetched at, posts). The embed feed is rate limited per IP, so each account is re-read at most every 30 min. */
    private val cache = HashMap<String, Pair<Long, List<XPost>>>().also { load(it) }
    private val dateFmt = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
    private val nextData = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)

    suspend fun fetch(handles: List<String>, interests: List<String>, focus: List<String> = emptyList(), days: Int = 7, limit: Int = 30): List<XPost> = coroutineScope {
        val list = handles.map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.take(20)
        val now = System.currentTimeMillis()
        // stalest first, a few per refresh, stop at the first rate limit
        val due = list.filter { h -> (cache[h.lowercase()]?.first ?: 0L) < now - 30 * 60_000L }
            .sortedBy { cache[it.lowercase()]?.first ?: 0L }.take(5)
        var lastError: Exception? = null
        var limited = false
        for (h in due) {
            if (limited) break
            try {
                cache[h.lowercase()] = now to one(h, interests)
            } catch (e: Exception) {
                lastError = e
                if (e.message?.contains("限流") == true) limited = true
            }
        }
        if (due.isNotEmpty()) save()
        val cutoff = now - days * 86_400_000L
        val all = list.flatMap { cache[it.lowercase()]?.second.orEmpty() }
            .map { p ->
                p.copy(
                    matched = interests.firstOrNull { p.text.contains(it, ignoreCase = true) },
                    focus = focus.firstOrNull { p.text.contains(it, ignoreCase = true) },
                )
            }
        val recent = all.filter { it.createdAt >= cutoff }.distinctBy { it.id }
        if (recent.isEmpty() && lastError != null) throw lastError!!
        recent.sortedWith(compareByDescending<XPost> { it.focus != null }.thenByDescending { it.createdAt }).take(limit)
    }

    private fun load(into: HashMap<String, Pair<Long, List<XPost>>>) = runCatching {
        if (!cacheFile.exists()) return@runCatching
        val root = json.parseToJsonElement(cacheFile.readText()).jsonObject
        for ((h, v) in root) {
            val o = v.jsonObject
            val at = o["at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val posts = o["posts"]?.jsonArray?.map { pe ->
                val p = pe.jsonObject
                XPost(
                    p.str("id")!!, p.str("handle")!!, p.str("name").orEmpty(), p.str("avatar"), p.str("text").orEmpty(), p.str("image"),
                    p.str("likes")?.toIntOrNull() ?: 0, p.str("at")?.toLongOrNull() ?: 0L, null,
                    replies = p.str("replies")?.toIntOrNull() ?: 0, reposts = p.str("reposts")?.toIntOrNull() ?: 0,
                    verified = p.str("verified") == "1",
                )
            }.orEmpty()
            into[h] = at to posts
        }
    }

    private fun save() = runCatching {
        val root = kotlinx.serialization.json.buildJsonObject {
            for ((h, v) in cache) put(h, kotlinx.serialization.json.buildJsonObject {
                put("at", kotlinx.serialization.json.JsonPrimitive(v.first.toString()))
                put("posts", kotlinx.serialization.json.JsonArray(v.second.map { p ->
                    kotlinx.serialization.json.buildJsonObject {
                        put("id", kotlinx.serialization.json.JsonPrimitive(p.id)); put("handle", kotlinx.serialization.json.JsonPrimitive(p.handle))
                        put("name", kotlinx.serialization.json.JsonPrimitive(p.name)); put("avatar", kotlinx.serialization.json.JsonPrimitive(p.avatar))
                        put("text", kotlinx.serialization.json.JsonPrimitive(p.text)); put("image", kotlinx.serialization.json.JsonPrimitive(p.image))
                        put("likes", kotlinx.serialization.json.JsonPrimitive(p.likes.toString())); put("at", kotlinx.serialization.json.JsonPrimitive(p.createdAt.toString()))
                        put("replies", kotlinx.serialization.json.JsonPrimitive(p.replies.toString())); put("reposts", kotlinx.serialization.json.JsonPrimitive(p.reposts.toString()))
                        put("verified", kotlinx.serialization.json.JsonPrimitive(if (p.verified) "1" else "0"))
                    }
                }))
            })
        }
        cacheFile.writeText(root.toString())
    }

    private suspend fun one(handle: String, interests: List<String>): List<XPost> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://syndication.twitter.com/srv/timeline-profile/screen-name/$handle")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131 Mobile Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        val html = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error(if (r.code == 429) "X 限流了，过会儿再刷" else "X ${r.code}")
            r.body!!.string()
        }
        val data = nextData.find(html)?.groupValues?.get(1) ?: return@withContext emptyList()
        val entries = json.parseToJsonElement(data).jsonObject["props"]?.jsonObject?.get("pageProps")?.jsonObject
            ?.get("timeline")?.jsonObject?.get("entries")?.jsonArray ?: return@withContext emptyList()
        val keepAfter = System.currentTimeMillis() - 14 * 86_400_000L
        entries.mapNotNull { e ->
            val t = e.jsonObject["content"]?.jsonObject?.get("tweet")?.jsonObject ?: return@mapNotNull null
            if (t.str("in_reply_to_status_id_str") != null) return@mapNotNull null
            val user = t["user"]?.jsonObject
            val text = clean(t.str("full_text") ?: t.str("text") ?: return@mapNotNull null)
            val created = runCatching { ZonedDateTime.parse(t.str("created_at"), dateFmt).toInstant().toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val media = (t["extended_entities"] ?: t["entities"])?.jsonObject?.get("media")?.jsonArray?.firstOrNull()?.jsonObject
            XPost(
                id = t.str("id_str") ?: return@mapNotNull null,
                handle = user?.str("screen_name") ?: handle,
                name = user?.str("name") ?: handle,
                avatar = user?.str("profile_image_url_https")?.replace("_normal.", "_bigger."),
                text = text,
                image = media?.str("media_url_https")?.let { "$it?name=small" },
                likes = t["favorite_count"]?.jsonPrimitive?.intOrNull ?: 0,
                createdAt = created,
                matched = null,
                replies = t["reply_count"]?.jsonPrimitive?.intOrNull ?: 0,
                reposts = t["retweet_count"]?.jsonPrimitive?.intOrNull ?: 0,
                verified = user?.let { u -> u.str("is_blue_verified") == "true" || u.str("verified") == "true" } ?: false,
            )
        }.filter { it.createdAt >= keepAfter }
    }

    /** Drops trailing media links and decodes the few entities X escapes. */
    private fun clean(s: String) = s
        .replace(Regex("""\s*https://t\.co/\w+\s*$"""), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .trim()

    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
}
