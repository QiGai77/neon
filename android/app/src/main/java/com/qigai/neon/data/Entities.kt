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
    entities = [ConversationEntity::class, MessageEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NeonDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun settings(): SettingDao
}
