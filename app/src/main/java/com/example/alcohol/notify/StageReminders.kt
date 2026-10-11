package com.example.alcohol.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.alcohol.data.AppDatabase
import com.example.alcohol.data.Warehouse
import com.example.alcohol.ui.stageTitle
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "stages"
private const val WORK_NAME = "stage-reminders"

/**
 * Раз в сутки проверяет этапы, которые наступают в ближайшие сутки или просрочены,
 * и показывает уведомление по каждому.
 */
class StageReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val warehouse = Warehouse(AppDatabase.get(applicationContext))
        val until = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        val due = warehouse.dueStages(until)

        if (due.isEmpty()) return Result.success()

        ensureChannel(applicationContext)
        val manager = NotificationManagerCompat.from(applicationContext)
        for (stage in due) {
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(stage.batchName)
                .setContentText("Пора: ${stageTitle(stage.type)}")
                .setAutoCancel(true)
                .build()
            // Идентификатор этапа: повторный запуск обновит уведомление, а не продублирует его.
            try {
                manager.notify(stage.stageId.toInt(), notification)
            } catch (_: SecurityException) {
                // Пользователь не разрешил уведомления. Расчёт всё равно ок.
            }
        }
        return Result.success()
    }
}

fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Этапы партий", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

fun scheduleStageReminders(context: Context) {
    val request = PeriodicWorkRequestBuilder<StageReminderWorker>(1, TimeUnit.DAYS).build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
