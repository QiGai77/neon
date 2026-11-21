package com.qigai.neon.connectors

import com.qigai.neon.oauth.CustomTabsOAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class GoogleWorkspaceConnector(
    private val httpClient: OkHttpClient,
    private val oauth: CustomTabsOAuth,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun connectAllScopes(): Result<Unit> = oauth.authorizeGoogle(
        listOf(
            "openid",
            "email",
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/gmail.modify",
            "https://www.googleapis.com/auth/calendar.events",
        ),
    ).map { }

    suspend fun ensureNeonDriveFolders(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = oauth.ensureValidGoogleAccessToken() ?: error("Google 未授权")
            val rootId = findOrCreateFolder(token, "Neon", null)
            val subNames = listOf("对话", "附件", "备份", "技能", "记忆", "任务")
            val map = mutableMapOf("root" to rootId)
            subNames.forEach { name ->
                map[name] = findOrCreateFolder(token, name, rootId)
            }
            map
        }
    }

    suspend fun uploadOrUpdateTextFile(folderName: String, fileName: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = oauth.ensureValidGoogleAccessToken() ?: error("Google 未授权")
                val folders = ensureNeonDriveFolders().getOrThrow()
                val parentId = folders[folderName] ?: folders["root"]!!
                val existingId = findFileInFolder(token, fileName, parentId)
                if (existingId != null) {
                    val req = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files/$existingId?uploadType=media")
                        .header("Authorization", "Bearer $token")
                        .patch(content.toRequestBody("text/plain; charset=utf-8".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("Drive 更新失败 (${resp.code})")
                        existingId
                    }
                } else {
                    val boundary = "neon_boundary_${System.currentTimeMillis()}"
                    val meta = buildJsonObject {
                        put("name", fileName)
                        put("parents", buildJsonArray { add(parentId) })
                    }
                    val multipart = buildString {
                        append("--$boundary\r\n")
                        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                        append(meta.toString())
                        append("\r\n--$boundary\r\n")
                        append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                        append(content)
                        append("\r\n--$boundary--")
                    }
                    val req = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                        .header("Authorization", "Bearer $token")
                        .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) error("Drive 上传失败 (${resp.code}): $raw")
                        json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.content ?: ""
                    }
                }
            }
        }

    suspend fun listGmailMessages(query: String, maxResults: Int = 8): String = withContext(Dispatchers.IO) {
        val token = oauth.ensureValidGoogleAccessToken() ?: return@withContext "ERROR: Google 未授权"
        val q = URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=$q&maxResults=$maxResults")
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext "ERROR (${resp.code}): $raw"
            raw
        }
    }

    suspend fun listCalendarEvents(maxResults: Int = 10): String = withContext(Dispatchers.IO) {
        val token = oauth.ensureValidGoogleAccessToken() ?: return@withContext "ERROR: Google 未授权"
        val req = Request.Builder()
            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=$maxResults&singleEvents=true&orderBy=startTime")
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext "ERROR (${resp.code}): $raw"
            raw
        }
    }

    suspend fun readGmailMessage(messageId: String): String = withContext(Dispatchers.IO) {
        val token = oauth.ensureValidGoogleAccessToken() ?: return@withContext "ERROR: Google 未授权"
        val req = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=full")
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext "ERROR (${resp.code}): $raw"
            raw
        }
    }

    suspend fun sendGmailMessage(to: String, subject: String, bodyText: String): String = withContext(Dispatchers.IO) {
        val token = oauth.ensureValidGoogleAccessToken() ?: return@withContext "ERROR: Google 未授权"
        val mime = "To: $to\r\nSubject: =?UTF-8?B?${android.util.Base64.encodeToString(subject.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)}?=\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$bodyText"
        val encoded = android.util.Base64.encodeToString(
            mime.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
        val payload = buildJsonObject { put("raw", encoded) }
        val req = Request.Builder()
            .url("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
            .header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext "ERROR (${resp.code}): $raw"
            "OK: 邮件已发送 ($raw)"
        }
    }

    suspend fun createCalendarEvent(summary: String, startIso: String, endIso: String, description: String = ""): String =
        withContext(Dispatchers.IO) {
            val token = oauth.ensureValidGoogleAccessToken() ?: return@withContext "ERROR: Google 未授权"
            val payload = buildJsonObject {
                put("summary", summary)
                if (description.isNotBlank()) put("description", description)
                put("start", buildJsonObject { put("dateTime", startIso) })
                put("end", buildJsonObject { put("dateTime", endIso) })
            }
            val req = Request.Builder()
                .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
                .header("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext "ERROR (${resp.code}): $raw"
                "OK: 日程已创建 ($raw)"
            }
        }

    private fun findOrCreateFolder(token: String, name: String, parentId: String?): String {
        findFileInFolder(token, name, parentId, isFolder = true)?.let { return it }
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) put("parents", buildJsonArray { add(parentId) })
        }
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer $token")
            .post(meta.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("创建 Drive 目录 $name 失败: $raw")
            return json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.content ?: error("缺少目录 ID")
        }
    }

    private fun findFileInFolder(token: String, name: String, parentId: String?, isFolder: Boolean = false): String? {
        val safeName = name.replace("'", "\\'")
        val q = buildString {
            append("name = '$safeName' and trashed = false")
            if (isFolder) append(" and mimeType = 'application/vnd.google-apps.folder'")
            if (parentId != null) append(" and '$parentId' in parents")
        }
        val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&fields=files(id,name)"
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val raw = resp.body?.string().orEmpty()
            val files = json.parseToJsonElement(raw).jsonObject["files"]?.jsonArray ?: return null
            return files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        }
    }
}
