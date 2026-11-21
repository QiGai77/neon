package com.qigai.neon.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "connectors")
data class ConnectorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // "cloud" | "remote_mcp" | "local_stdio" | "native" | "automation"
    val subtitle: String,
    val endpointUrl: String? = null,
    val status: String = "disconnected", // "disconnected" | "connecting" | "connected" | "error"
    val toolCount: Int = 0,
    val toolsJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "hands_traces",
    indices = [Index(value = ["runId", "stepIndex"])],
)
data class HandsTraceEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sessionId: String,
    val stepIndex: Int,
    val engine: String, // "accessibility" | "shizuku"
    val targetPackage: String,
    val actionType: String, // "tap" | "input" | "swipe" | "open_app" | "back" | "tool"
    val targetLabel: String,
    val detail: String,
    val status: String, // "ok" | "blocked" | "failed"
    val createdAt: Long,
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val skillName: String,
    val prompt: String,
    val scheduleLabel: String,
    val intervalMinutes: Long,
    val requiresWifi: Boolean = false,
    val requiresScreenOn: Boolean = false,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_receipts",
    indices = [Index(value = ["taskId", "executedAt"])],
)
data class TaskReceiptEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val sessionId: String?,
    val status: String, // "success" | "failed" | "needs_unlock"
    val summary: String,
    val emailsScanned: Int = 0,
    val eventsSynced: Int = 0,
    val durationMs: Long = 0L,
    val executedAt: Long,
)

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
