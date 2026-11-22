package com.qigai.neon.connectors

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

private const val NOT_SIGNED_IN = "ERROR: Google 未连接。请用户到 空间 > 连接器 > Google 点连接，选账号后点允许即可，不需要自己建客户端"

class GoogleWorkspaceConnector(
    private val httpClient: OkHttpClient,
    /** Access token for drive.file + gmail.modify + calendar.events, or null when not granted. */
    private val token: suspend () -> String?,
    /** drive.file only (the grant Drive sync already holds). */
    private val driveToken: suspend () -> String?,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureNeonDriveFolders(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val token = driveToken() ?: error("Google 未授权")
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
                val token = driveToken() ?: error("Google 未授权")
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

    /** Matching messages with sender, subject, date and snippet, so one call is enough to answer most questions. */
    suspend fun listGmailMessages(query: String, maxResults: Int = 10): String = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext NOT_SIGNED_IN
        val q = URLEncoder.encode(query, "UTF-8")
        val ids = get(token, "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=$q&maxResults=$maxResults")
            .getOrElse { return@withContext "ERROR: ${it.message}" }
            .let { json.parseToJsonElement(it).jsonObject["messages"]?.jsonArray.orEmpty() }
            .mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
        if (ids.isEmpty()) return@withContext "没有匹配的邮件"
        ids.joinToString("\n\n") { id ->
            val raw = get(token, "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date")
                .getOrNull() ?: return@joinToString "id: $id"
            val m = json.parseToJsonElement(raw).jsonObject
            val h = m["payload"]?.jsonObject?.get("headers")?.jsonArray.orEmpty().associate {
                it.jsonObject["name"]!!.jsonPrimitive.content to it.jsonObject["value"]!!.jsonPrimitive.content
            }
            val unread = m["labelIds"]?.jsonArray?.any { it.jsonPrimitive.content == "UNREAD" } == true
            buildString {
                append("id: $id"); if (unread) append("  未读")
                append("\n发件人: ${h["From"].orEmpty()}\n主题: ${h["Subject"].orEmpty()}\n时间: ${h["Date"].orEmpty()}")
                append("\n摘要: ${m["snippet"]?.jsonPrimitive?.content.orEmpty()}")
            }
        }
    }

    suspend fun searchDrive(query: String, maxResults: Int = 15): String = withContext(Dispatchers.IO) {
        val token = driveToken() ?: return@withContext NOT_SIGNED_IN
        val esc = query.replace("\\", "\\\\").replace("'", "\\'")
        val q = if (query.isBlank()) "trashed = false" else "(name contains '$esc' or fullText contains '$esc') and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${URLEncoder.encode(q, "UTF-8")}&pageSize=$maxResults" +
            (if (query.isBlank()) "&orderBy=modifiedTime%20desc" else "") +
            "&fields=${URLEncoder.encode("files(id,name,mimeType,modifiedTime,size,webViewLink)", "UTF-8")}"
        get(token, url).fold({ it }, { "ERROR: ${it.message}" })
    }

    /** Text of a Drive file: Google Docs, Sheets and Slides are exported, plain files are downloaded. */
    suspend fun readDriveFile(fileId: String, maxChars: Int = 40_000): String = withContext(Dispatchers.IO) {
        val token = driveToken() ?: return@withContext NOT_SIGNED_IN
        val meta = get(token, "https://www.googleapis.com/drive/v3/files/$fileId?fields=name,mimeType")
            .getOrElse { return@withContext "ERROR: ${it.message}" }
            .let { json.parseToJsonElement(it).jsonObject }
        val mime = meta["mimeType"]?.jsonPrimitive?.content.orEmpty()
        val export = when (mime) {
            "application/vnd.google-apps.document", "application/vnd.google-apps.presentation" -> "text/plain"
            "application/vnd.google-apps.spreadsheet" -> "text/csv"
            else -> null
        }
        val url = if (export != null) "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=${URLEncoder.encode(export, "UTF-8")}"
        else "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        if (export == null && !(mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("csv")))
            return@withContext "${meta["name"]?.jsonPrimitive?.content}: $mime 不是文本文件，读不了内容"
        get(token, url).fold({ t -> if (t.length > maxChars) t.take(maxChars) + "\n…已截断" else t }, { "ERROR: ${it.message}" })
    }

    private fun get(token: String, url: String): Result<String> = runCatching {
        httpClient.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
            val raw = r.body?.string().orEmpty()
            if (!r.isSuccessful) error(friendly(r.code, raw))
            raw
        }
    }

    private fun friendly(code: Int, raw: String): String = when {
        "SERVICE_DISABLED" in raw || "has not been used in project" in raw ->
            "这项 Google 服务暂时不可用，到连接器里重新连接 Google 再试"
        "insufficient" in raw.lowercase() -> "Google 登录时没给这项权限，到连接器里重新登录 Google"
        code == 401 -> "Google 登录已失效，到连接器里重新登录 Google"
        else -> "($code) ${raw.take(500)}"
    }

    suspend fun listCalendarEvents(maxResults: Int = 15): String = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext NOT_SIGNED_IN
        val req = Request.Builder()
            .url("https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=$maxResults&singleEvents=true&orderBy=startTime&timeMin=${URLEncoder.encode(java.time.Instant.now().toString(), "UTF-8")}")
            .header("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext "ERROR (${resp.code}): $raw"
            raw
        }
    }

    suspend fun readGmailMessage(messageId: String): String = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext NOT_SIGNED_IN
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
        val token = token() ?: return@withContext NOT_SIGNED_IN
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
            val token = token() ?: return@withContext NOT_SIGNED_IN
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
