package com.qigai.neon.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val provider: String? = null,
    val model: String? = null,
    val driveFileId: String? = null,
    val driveSyncedAt: Long? = null,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    /** user | assistant */
    val role: String,
    /** JSON array of [ContentBlock] */
    val blocks: String,
    val createdAt: Long,
    /** streaming | done | stopped | error */
    val status: String = "done",
    val error: String? = null,
    val stopReason: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Upsert
    suspend fun upsert(c: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE conversations SET driveFileId = :fileId, driveSyncedAt = :at WHERE id = :id")
    suspend fun setDrive(id: String, fileId: String?, at: Long?)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeCount(): Flow<Int>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    fun observe(cid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY createdAt ASC")
    suspend fun list(cid: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity)

    @Upsert
    suspend fun upsert(m: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun deleteFor(cid: String)

    @Query("UPDATE messages SET status = 'stopped' WHERE status = 'streaming'")
    suspend fun markDanglingStopped()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(s: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)
}

@Database(
    entities = [
        ConversationEntity::class, MessageEntity::class, SettingEntity::class,
        ConnectorEntity::class, HandsTraceEntity::class, ScheduledTaskEntity::class, TaskReceiptEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NeonDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun settings(): SettingDao
    abstract fun connectorDao(): ConnectorDao
    abstract fun handsTraceDao(): HandsTraceDao
    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun taskReceiptDao(): TaskReceiptDao

    companion object {
        /** v0.3.1: connectors, Hands traces, scheduled tasks, receipts. Conversations are kept. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `connectors` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `endpointUrl` TEXT, `status` TEXT NOT NULL, `toolCount` INTEGER NOT NULL, `toolsJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `hands_traces` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `sessionId` TEXT NOT NULL, `stepIndex` INTEGER NOT NULL, `engine` TEXT NOT NULL, `targetPackage` TEXT NOT NULL, `actionType` TEXT NOT NULL, `targetLabel` TEXT NOT NULL, `detail` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hands_traces_runId_stepIndex` ON `hands_traces` (`runId`, `stepIndex`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `scheduled_tasks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `skillName` TEXT NOT NULL, `prompt` TEXT NOT NULL, `scheduleLabel` TEXT NOT NULL, `intervalMinutes` INTEGER NOT NULL, `requiresWifi` INTEGER NOT NULL, `requiresScreenOn` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `lastRunAt` INTEGER, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `task_receipts` (`id` TEXT NOT NULL, `taskId` TEXT NOT NULL, `sessionId` TEXT, `status` TEXT NOT NULL, `summary` TEXT NOT NULL, `emailsScanned` INTEGER NOT NULL, `eventsSynced` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `executedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_receipts_taskId_executedAt` ON `task_receipts` (`taskId`, `executedAt`)")
            }
        }
    }
}
