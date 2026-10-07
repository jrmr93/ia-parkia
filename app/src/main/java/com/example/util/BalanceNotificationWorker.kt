package com.example.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BalanceNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = ParkingRepository(
            AppDatabase.getDatabase(applicationContext).parkingDao()
        )
        val config = repository.getOrCreateConfig()

        if (config.notificationsEnabled) {
            val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val nowFormatted = dateTimeFormat.format(Date())
            NotificationHelper.showBalanceNotification(
                context = applicationContext,
                balance = config.balance,
                isSessionActive = config.isSessionActive,
                formattedDateTime = nowFormatted
            )
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "parkia_balance_periodic_work"

        fun scheduleOrCancel(context: Context, enabled: Boolean, intervalMinutes: Int) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
            } else {
                val interval = intervalMinutes.toLong().coerceAtLeast(15L)
                val request = PeriodicWorkRequestBuilder<BalanceNotificationWorker>(interval, TimeUnit.MINUTES)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }
    }
}
