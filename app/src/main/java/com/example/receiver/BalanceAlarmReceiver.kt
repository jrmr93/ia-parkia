package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import com.example.service.ParkiaForegroundService
import com.example.service.ParkiaTileService
import com.example.util.BalanceAlarmScheduler
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BalanceAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ParkingRepository(
                    AppDatabase.getDatabase(appContext).parkingDao()
                )
                val config = repository.getOrCreateConfig()

                if (!config.notificationsEnabled) {
                    BalanceAlarmScheduler.scheduleOrCancel(appContext, false, config.notificationIntervalMinutes)
                    return@launch
                }

                if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
                    NotificationHelper.onNotificationDismissed(appContext)
                    if (config.notificationsEnabled) {
                        BalanceAlarmScheduler.scheduleNext(appContext, config.notificationIntervalMinutes)
                    }
                    return@launch
                }

                // If session is active, advance and catch up so negative balance (debt) is accurately calculated
                val wasActive = config.isSessionActive
                if (config.isSessionActive) {
                    repository.catchUpSession()
                }
                val updatedConfig = repository.getOrCreateConfig()
                if (wasActive != updatedConfig.isSessionActive) {
                    ParkiaTileService.updateQuickTileState(appContext)
                }

                val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                val nowFormatted = dateTimeFormat.format(Date())

                // Show/update notification with current balance, including negative balance / debt
                NotificationHelper.showBalanceNotification(
                    context = appContext,
                    balance = updatedConfig.balance,
                    isSessionActive = updatedConfig.isSessionActive,
                    formattedDateTime = nowFormatted
                )

                // Try to keep or restart foreground service if possible
                try {
                    ParkiaForegroundService.startOrUpdate(appContext, forceUpdate = false)
                } catch (_: Throwable) {
                }

                // Schedule next alarm tick for the configured interval
                BalanceAlarmScheduler.scheduleNext(appContext, updatedConfig.notificationIntervalMinutes)
            } catch (_: Throwable) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BALANCE_ALARM_TICK = "com.example.action.BALANCE_ALARM_TICK"
        const val ACTION_NOTIFICATION_DISMISSED = "com.example.action.NOTIFICATION_DISMISSED"
    }
}
