package com.qigai.neon.connectors

import com.qigai.neon.data.EncryptedTokenStore
import com.qigai.neon.oauth.McpOAuth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One of Google's hosted Workspace MCP servers. */
data class GoogleMcpService(
    /** Connector id and tool prefix. */
    val id: String,
    val name: String,
    val desc: String,
    val url: String,
    val scopes: List<String>,
)

/**
 * Google Workspace through Google's official remote MCP servers (Developer Preview).
 *
 * Google has no dynamic client registration, so the user brings an OAuth client made with
 * Personal mode in Google Cloud (Desktop app type, marked for an AI-powered agent). One sign-in
 * covers every service picked; the refresh token is stored per connector so McpHub renews it.
 */
class GoogleMcp(
    private val tokens: EncryptedTokenStore,
    private val oauth: McpOAuth,
    private val hub: McpHub,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun client(): OAuthClient? = tokens.get(KEY_CLIENT)?.let { raw ->
        runCatching {
            val o = json.parseToJsonElement(raw).jsonObject
            OAuthClient(o["id"]!!.jsonPrimitive.content, o["secret"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
        }.getOrNull()
    }

    fun saveClient(id: String, secret: String) {
        tokens.put(KEY_CLIENT, buildJsonObject { put("id", id.trim()); put("secret", secret.trim()) }.toString())
    }

    fun forgetClient() = tokens.remove(KEY_CLIENT)

    /** Signs in once for [services] and connects each. Returns the services that connected, with their tool counts. */
    suspend fun signIn(services: List<GoogleMcpService>): Result<List<Pair<GoogleMcpService, Int>>> = runCatching {
        val oc = client() ?: error("先填写 OAuth 客户端")
        require(services.isNotEmpty()) { "至少选一个服务" }
        val scopes = (listOf("openid", "email") + services.flatMap { it.scopes }).distinct()
        val (tok, _) = oauth.runPkceFlow(
            AUTH, TOKEN, scopes, resource = null,
            extraParams = mapOf("access_type" to "offline", "prompt" to "consent", "include_granted_scopes" to "true"),
        ) { oc }.getOrThrow()
        val done = mutableListOf<Pair<GoogleMcpService, Int>>()
        var last: Throwable? = null
        for (s in services) {
            hub.connectPreauthorized(s.id, "Google ${s.name}", s.url, tok.accessToken, tok.refreshToken, TOKEN, oc)
                .onSuccess { done += s to it }
                .onFailure { last = it }
        }
        if (done.isEmpty()) throw last ?: IllegalStateException("连接失败")
        done
    }

    companion object {
        private const val KEY_CLIENT = "google_mcp_client"
        const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN = "https://oauth2.googleapis.com/token"
        /** Personal mode setup in Google Cloud, pre-set to a Desktop app client marked for an agent. */
        const val SETUP_URL = "https://console.cloud.google.com/auth/overview/personal?apis=ws,wsmcp,tasksmcp&clientType=desktop&isAgent=true"
        const val PREVIEW_URL = "https://developers.google.com/workspace/preview"
        const val GUIDE_URL = "https://developers.google.com/workspace/guides/configure-mcp-servers"

        private const val G = "https://www.googleapis.com/auth/"
        val services = listOf(
            GoogleMcpService("gmail", "Gmail", "搜索邮件、读线程、写草稿", "https://gmailmcp.googleapis.com/mcp/v1",
                listOf(G + "gmail.readonly", G + "gmail.compose", G + "gmail.modify")),
            GoogleMcpService("gcalendar", "日历", "查日程、找空档、建日程", "https://calendarmcp.googleapis.com/mcp/v1",
                listOf(G + "calendar.calendarlist.readonly", G + "calendar.events.freebusy", G + "calendar.events")),
            GoogleMcpService("gdrive", "Drive", "搜文件、读内容、建文件", "https://drivemcp.googleapis.com/mcp/v1",
                listOf(G + "drive.readonly", G + "drive.file")),
            GoogleMcpService("gdocs", "文档", "读写 Google 文档", "https://docsmcp.googleapis.com/mcp/v1",
                listOf(G + "drive.readonly", G + "drive.file", G + "documents")),
            GoogleMcpService("gsheets", "表格", "读写表格的值和公式", "https://sheetsmcp.googleapis.com/mcp/v1",
                listOf(G + "drive.readonly", G + "drive.file", G + "spreadsheets")),
            GoogleMcpService("gslides", "幻灯片", "读写演示文稿", "https://slidesmcp.googleapis.com/mcp/v1",
                listOf(G + "drive.readonly", G + "drive.file", G + "presentations")),
            GoogleMcpService("gpeople", "联系人", "你的资料和联系人", "https://people.googleapis.com/mcp/v1",
                listOf(G + "userinfo.profile", G + "contacts.readonly")),
        )
        val defaultPicks = setOf("gmail", "gcalendar", "gdrive")
        fun byId(id: String) = services.firstOrNull { it.id == id }
    }
}
