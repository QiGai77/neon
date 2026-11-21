package com.qigai.neon.sysfiles

import com.qigai.neon.provider.Http
import com.qigai.neon.provider.ProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DriveAuthExpired : Exception("Google 授权已过期")

/** Minimal Drive v3 REST client (drive.file scope: only files Neon created are visible). */
class DriveClient(private val token: suspend () -> String) {
    companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        /** 我的云端硬盘/Neon/{对话,附件,备份,技能,记忆,任务} */
        val SUBFOLDERS = listOf("对话", "附件", "备份", "技能", "记忆", "任务")
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun folderUrl(id: String) = "https://drive.google.com/drive/folders/$id"
    }

    private suspend fun exec(build: (Request.Builder) -> Request.Builder): JsonObject = withContext(Dispatchers.IO) {
        val req = build(Request.Builder().header("Authorization", "Bearer ${token()}")).build()
        Http.client.newCall(req).execute().use { r ->
            if (r.code == 401) throw DriveAuthExpired()
            if (!r.isSuccessful) throw Http.errorOf(r)
            val t = r.body?.string().orEmpty()
            if (t.isBlank()) JsonObject(emptyMap()) else Http.json.parseToJsonElement(t).jsonObject
        }
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content

    suspend fun aboutEmail(): String? {
        val o = exec { it.url("$API/about?fields=user(emailAddress,displayName)").get() }
        return (o["user"] as? JsonObject)?.s("emailAddress")
    }

    suspend fun findFolder(name: String, parent: String?): String? {
        val q = buildString {
            append("mimeType='$FOLDER_MIME' and trashed=false and name='${name.replace("'", "\\'")}'")
            append(" and '${parent ?: "root"}' in parents")
        }
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q).addQueryParameter("fields", "files(id,name)").addQueryParameter("pageSize", "10").build()
        val o = exec { it.url(url).get() }
        return ((o["files"] as? JsonArray)?.firstOrNull() as? JsonObject)?.s("id")
    }

    suspend fun createFolder(name: String, parent: String?): String {
        val body = buildJsonObject {
            put("name", name); put("mimeType", FOLDER_MIME)
            if (parent != null) put("parents", buildJsonArray { add(JsonPrimitive(parent)) })
        }
        val o = exec { it.url("$API/files?fields=id").post(body.toString().toRequestBody(JSON)) }
        return o.s("id") ?: throw ProviderException("创建文件夹失败")
    }

    suspend fun isAlive(id: String): Boolean = runCatching {
        val o = exec { it.url("$API/files/$id?fields=id,trashed").get() }
        o.s("trashed") != "true"
    }.getOrElse { if (it is DriveAuthExpired) throw it else false }

    /** Finds or creates Neon/ and its six subfolders. Returns name→id with "root" for Neon/. */
    suspend fun ensureFolders(cached: Map<String, String>): Map<String, String> {
        val root = cached["root"]
        if (root != null && SUBFOLDERS.all { cached[it] != null } && isAlive(root)) return cached
        val r = findFolder("Neon", null) ?: createFolder("Neon", null)
        val out = linkedMapOf("root" to r)
        for (n in SUBFOLDERS) out[n] = findFolder(n, r) ?: createFolder(n, r)
        return out
    }

    /** Creates or overwrites a file (multipart upload). Returns file id. */
    suspend fun putFile(name: String, mime: String, bytes: ByteArray, parent: String, existingId: String?): String {
        val meta = buildJsonObject {
            put("name", name); put("mimeType", mime)
            if (existingId == null) put("parents", buildJsonArray { add(JsonPrimitive(parent)) })
        }
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toString().toRequestBody(JSON))
            .addPart(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val o = if (existingId != null) {
            exec { it.url("$UPLOAD/files/$existingId?uploadType=multipart&fields=id").patch(body) }
        } else {
            exec { it.url("$UPLOAD/files?uploadType=multipart&fields=id").post(body) }
        }
        return o.s("id") ?: throw ProviderException("上传失败")
    }

    suspend fun trash(id: String) {
        exec { it.url("$API/files/$id").patch("{\"trashed\":true}".toRequestBody(JSON)) }
    }
}
