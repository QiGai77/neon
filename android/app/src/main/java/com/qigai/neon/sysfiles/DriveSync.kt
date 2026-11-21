package com.qigai.neon.sysfiles

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.qigai.neon.NeonApp
import com.qigai.neon.data.BlocksJson
import com.qigai.neon.data.ContentBlock
import com.qigai.neon.data.SettingsRepository
import com.qigai.neon.oauth.GoogleDriveAuth
import com.qigai.neon.provider.Http
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** One-way sync of conversations → 我的云端硬盘/Neon/对话/<标题>.json */
class DriveSync(private val context: Context) {
    private val c get() = (context.applicationContext as NeonApp).container

    private suspend fun token(): String = GoogleDriveAuth.silentToken(context) ?: throw DriveAuthExpired()

    fun client() = DriveClient { token() }

    suspend fun folders(client: DriveClient): Map<String, String> {
        val cached: Map<String, String> = c.settings.get(SettingsRepository.DRIVE_FOLDERS)?.let { s ->
            runCatching { Http.json.parseToJsonElement(s).jsonObject.mapValues { (it.value as JsonPrimitive).content } }.getOrNull()
        } ?: emptyMap()
        val f = client.ensureFolders(cached)
        if (f != cached) c.settings.put(SettingsRepository.DRIVE_FOLDERS, JsonObject(f.mapValues { JsonPrimitive(it.value) }).toString())
        return f
    }

    private fun safeName(t: String) = (t.trim().ifEmpty { "未命名对话" }).replace(Regex("[\\\\/:*?\"<>|\\n\\r]+"), " ").take(80)

    suspend fun syncConversation(cid: String) {
        val conv = c.db.conversations().get(cid) ?: return
        val client = client()
        val f = folders(client)
        val msgs = c.db.messages().list(cid).filter { it.status != "streaming" }
        val doc = buildJsonObject {
            put("app", "neon-android"); put("format", 1); put("exportedAt", Instant.now().toString())
            put("conversation", buildJsonObject {
                put("id", conv.id); put("title", conv.title)
                put("createdAt", conv.createdAt); put("updatedAt", conv.updatedAt)
            })
            putJsonArray("messages") {
                msgs.forEach { m ->
                    addJsonObject {
                        put("id", m.id); put("role", m.role); put("createdAt", m.createdAt); put("status", m.status)
                        m.model?.let { put("model", it) }
                        put("content", Http.json.parseToJsonElement(
                            BlocksJson.json.encodeToString(ListSerializer(ContentBlock.serializer()), BlocksJson.decode(m.blocks))
                        ))
                    }
                }
            }
        }
        val bytes = doc.toString().toByteArray()
        val name = safeName(conv.title) + ".json"
        val id = try {
            client.putFile(name, "application/json", bytes, f.getValue("对话"), conv.driveFileId)
        } catch (e: DriveAuthExpired) {
            throw e
        } catch (e: Exception) {
            if (conv.driveFileId != null) client.putFile(name, "application/json", bytes, f.getValue("对话"), null) else throw e
        }
        val now = System.currentTimeMillis()
        c.db.conversations().setDrive(cid, id, now)
        c.settings.put(SettingsRepository.DRIVE_LAST_SYNC, now.toString())
        c.settings.put(SettingsRepository.DRIVE_ERROR, null)
    }

    suspend fun syncAll() {
        for (conv in c.db.conversations().all()) syncConversation(conv.id)
    }

    companion object {
        fun enqueue(context: Context, cid: String?) {
            val req = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setInputData(workDataOf("cid" to cid))
                .setInitialDelay(if (cid == null) 0 else 3, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("drive-sync-${cid ?: "all"}", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

class DriveSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val sync = DriveSync(applicationContext)
        val settings = (applicationContext as NeonApp).container.settings
        return try {
            val cid = inputData.getString("cid")
            if (cid == null) sync.syncAll() else sync.syncConversation(cid)
            Result.success()
        } catch (e: DriveAuthExpired) {
            settings.put(SettingsRepository.DRIVE_ERROR, "Google 授权已过期，请重新授权")
            Result.failure()
        } catch (e: Exception) {
            settings.put(SettingsRepository.DRIVE_ERROR, e.message ?: "同步失败")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
