package com.qigai.neon.scheduled

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.qigai.neon.data.db.ScheduledTaskDao
import com.qigai.neon.data.db.ScheduledTaskEntity
import java.util.UUID
import java.util.concurrent.TimeUnit

class TaskScheduler(
    private val context: Context,
    private val taskDao: ScheduledTaskDao,
) {
    private val wm = WorkManager.getInstance(context)

    suspend fun createAndSchedule(
        title: String,
        skillName: String,
        prompt: String,
        scheduleLabel: String,
        intervalMinutes: Long,
        requiresWifi: Boolean,
    ): ScheduledTaskEntity {
        val task = ScheduledTaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            skillName = skillName,
            prompt = prompt,
            scheduleLabel = scheduleLabel,
            intervalMinutes = intervalMinutes.coerceAtLeast(15L),
            requiresWifi = requiresWifi,
            enabled = true,
        )
        taskDao.upsert(task)
        enqueuePeriodic(task)
        return task
    }

    suspend fun setEnabled(task: ScheduledTaskEntity, enabled: Boolean) {
        val updated = task.copy(enabled = enabled)
        taskDao.upsert(updated)
        if (enabled) enqueuePeriodic(updated) else wm.cancelUniqueWork("neon_task_${task.id}")
    }

    fun runNow(taskId: String) {
        val req = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
            .setInputData(workDataOf("task_id" to taskId, "force_run" to true))
            .build()
        wm.enqueue(req)
    }

    private fun enqueuePeriodic(task: ScheduledTaskEntity) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (task.requiresWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val req = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(task.intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInputData(workDataOf("task_id" to task.id))
            .build()
        wm.enqueueUniquePeriodicWork("neon_task_${task.id}", ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
