package com.qigai.neon.sysfiles

import com.qigai.neon.connectors.GoogleWorkspaceConnector
import com.qigai.neon.data.db.MessageDao
import com.qigai.neon.data.db.SessionDao
import com.qigai.neon.sandbox.RootfsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class SessionBackupPayload(
    val id: String,
    val title: String,
    val providerId: String,
    val modelId: String,
    val updatedAt: Long,
    val messages: List<MessageBackupItem>,
)

@Serializable
data class MessageBackupItem(
    val id: String,
    val role: String,
    val content: String,
    val thinking: String? = null,
    val createdAt: Long,
)

class ChatDriveSync(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val rootfsManager: RootfsManager,
    private val googleWorkspace: GoogleWorkspaceConnector,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun syncSessionToDrive(sessionId: String) = withContext(Dispatchers.IO) {
        val session = sessionDao.getById(sessionId) ?: return@withContext
        val msgs = messageDao.listBySession(sessionId).map {
            MessageBackupItem(it.id, it.role, it.content, it.thinking, it.createdAt)
        }
        val payload = SessionBackupPayload(
            id = session.id,
            title = session.title,
            providerId = session.providerId,
            modelId = session.modelId,
            updatedAt = session.updatedAt,
            messages = msgs,
        )
        googleWorkspace.uploadOrUpdateTextFile("对话", "${session.id}.json", json.encodeToString(payload))
    }

    suspend fun exportFullBackupZip(): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = sessionDao.observeAll().first()
            val backupFile = File(rootfsManager.tmpDir.apply { mkdirs() }, "neon-backup-${System.currentTimeMillis()}.zip")
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                sessions.forEach { s ->
                    val msgs = messageDao.listBySession(s.id).map {
                        MessageBackupItem(it.id, it.role, it.content, it.thinking, it.createdAt)
                    }
                    val payload = SessionBackupPayload(s.id, s.title, s.providerId, s.modelId, s.updatedAt, msgs)
                    zos.putNextEntry(ZipEntry("sessions/${s.id}.json"))
                    zos.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                rootfsManager.skillsDir.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        zos.putNextEntry(ZipEntry("skills/${f.name}"))
                        zos.write(f.readBytes())
                        zos.closeEntry()
                    }
                }
                val mem = File(rootfsManager.memoryDir, "memory.md")
                if (mem.exists()) {
                    zos.putNextEntry(ZipEntry("memory/memory.md"))
                    zos.write(mem.readBytes())
                    zos.closeEntry()
                }
            }
            backupFile.writeBytes(baos.toByteArray())
            backupFile
        }
    }
}
