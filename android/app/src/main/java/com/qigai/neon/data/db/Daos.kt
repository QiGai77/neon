package com.qigai.neon.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun listBySession(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
}

@Dao
interface ConnectorDao {
    @Query("SELECT * FROM connectors ORDER BY category ASC, name ASC")
    fun observeAll(): Flow<List<ConnectorEntity>>

    @Query("SELECT * FROM connectors")
    suspend fun listAll(): List<ConnectorEntity>

    @Query("SELECT * FROM connectors WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConnectorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connector: ConnectorEntity)

    @Query("DELETE FROM connectors WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HandsTraceDao {
    @Query("SELECT * FROM hands_traces WHERE runId = :runId ORDER BY stepIndex ASC")
    fun observeRun(runId: String): Flow<List<HandsTraceEntity>>

    @Query("SELECT * FROM hands_traces ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<HandsTraceEntity>>

    @Query("SELECT * FROM hands_traces WHERE runId = (SELECT runId FROM hands_traces ORDER BY createdAt DESC LIMIT 1) ORDER BY stepIndex ASC")
    fun observeLatestRun(): Flow<List<HandsTraceEntity>>

    @Query("SELECT * FROM hands_traces WHERE runId = :runId ORDER BY stepIndex ASC")
    suspend fun listRun(runId: String): List<HandsTraceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trace: HandsTraceEntity)
}

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TaskReceiptDao {
    @Query("SELECT * FROM task_receipts ORDER BY executedAt DESC LIMIT 30")
    fun observeRecent(): Flow<List<TaskReceiptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: TaskReceiptEntity)
}
