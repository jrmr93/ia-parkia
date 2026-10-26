package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import com.example.service.ParkiaForegroundService
import com.example.util.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = ParkingRepository(
                        AppDatabase.getDatabase(appContext).parkingDao()
                    )
                    val config = repository.getOrCreateConfig()
                    if (config.notificationsEnabled) {
                        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        val nowFormatted = dateTimeFormat.format(Date())
                        // Show immediately on boot so the dynamic island and notification are visible right away
                        NotificationHelper.showBalanceNotification(
                            context = appContext,
                            balance = config.balance,
                            isSessionActive = config.isSessionActive,
                            formattedDateTime = nowFormatted
                        )
                        ParkiaForegroundService.startOrUpdate(appContext, forceUpdate = true)
                        com.example.util.BalanceAlarmScheduler.scheduleOrCancel(
                            appContext,
                            true,
                            config.notificationIntervalMinutes
                        )
                        com.example.util.BalanceNotificationWorker.scheduleOrCancel(
                            appContext,
                            true,
                            config.notificationIntervalMinutes
                        )
                    }
                    if (config.geofenceEnabled) {
                        com.example.util.GeofenceManager.registerGeofence(
                            appContext,
                            config.geofenceLatitude,
                            config.geofenceLongitude,
                            config.geofenceRadiusMeters
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
