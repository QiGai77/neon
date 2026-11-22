package com.qigai.neon.feed

import kotlinx.coroutines.Dispatchers
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
import java.net.URLEncoder
import java.time.LocalDate

data class FeedRepo(
    val fullName: String,
    val description: String,
    val stars: Int,
    val language: String?,
    val url: String,
    /** the interest that matched, shown as the "why it is for you" line */
    val matched: String,
    val createdAt: String,
)

data class FeedState(
    val loading: Boolean = false,
    val repos: List<FeedRepo> = emptyList(),
    val error: String? = null,
    val posts: List<XPost> = emptyList(),
    val xError: String? = null,
    val fetchedAt: Long = 0L,
)

/**
 * 速览 GitHub source: new repositories from the last two weeks that match the user's
 * interests, ranked by stars. Uses the public search API; a GitHub connector token, when
 * present, only raises the rate limit.
 */
class GitHubFeed(private val http: OkHttpClient, private val token: () -> String?) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(interests: List<String>, perInterest: Int = 6, limit: Int = 18): List<FeedRepo> = withContext(Dispatchers.IO) {
        val since = LocalDate.now().minusDays(14).toString()
        val seen = HashMap<String, FeedRepo>()
        var lastError: Exception? = null
        for (interest in interests.take(6)) {
            val q = URLEncoder.encode("$interest created:>$since", "UTF-8")
            val req = Request.Builder()
                .url("https://api.github.com/search/repositories?q=$q&sort=stars&order=desc&per_page=$perInterest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Neon-Android")
                .apply { token()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
                .build()
            try {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("GitHub ${r.code}")
                    val items = json.parseToJsonElement(r.body!!.string()).jsonObject["items"]?.jsonArray ?: return@use
                    for (it in items) {
                        val o = it.jsonObject
                        val name = o.str("full_name") ?: continue
                        if (seen.containsKey(name)) continue
                        seen[name] = FeedRepo(
                            fullName = name,
                            description = o.str("description").orEmpty(),
                            stars = o["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0,
                            language = o.str("language"),
                            url = o.str("html_url") ?: "https://github.com/$name",
                            matched = interest,
                            createdAt = o.str("created_at").orEmpty().take(10),
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        if (seen.isEmpty() && lastError != null) throw lastError!!
        seen.values.sortedByDescending { it.stars }.take(limit)
    }

    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
}
