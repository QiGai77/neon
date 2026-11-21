package com.qigai.neon.scheduled

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qigai.neon.NeonApp
import com.qigai.neon.R
import com.qigai.neon.data.TaskReceiptEntity
import java.util.UUID

class ScheduledTaskWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("task_id") ?: return Result.failure()
        val container = (applicationContext as NeonApp).container
        val task = container.db.scheduledTaskDao().getById(taskId) ?: return Result.success()
        if (!task.enabled && !inputData.getBoolean("force_run", false)) return Result.success()

        val startMs = System.currentTimeMillis()
        var sessionId: String? = null

        val reply = runCatching {
            val (cid, text) = container.agent.runHeadless(task.title, task.prompt, container.defaultTurnConfig() ?: error("未连接模型"))
            sessionId = cid
            text
        }.getOrElse { e ->
            "执行失败: ${e.message}"
        }

        val durationMs = System.currentTimeMillis() - startMs
        val status = if (reply.startsWith("执行失败")) "failed" else "success"

        container.db.taskReceiptDao().insert(
            TaskReceiptEntity(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                sessionId = sessionId,
                status = status,
                summary = reply.take(260).ifBlank { "任务已执行完成" },
                durationMs = durationMs,
                executedAt = System.currentTimeMillis(),
            ),
        )
        container.db.scheduledTaskDao().upsert(task.copy(lastRunAt = System.currentTimeMillis()))
        postReceiptNotification(task.title, reply.take(120).ifBlank { "已完成" })
        return Result.success()
    }

    private fun postReceiptNotification(title: String, body: String) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("neon_receipts", "Neon 定时任务回执", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notif = NotificationCompat.Builder(applicationContext, "neon_receipts")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        nm.notify(title.hashCode(), notif)
    }
}
