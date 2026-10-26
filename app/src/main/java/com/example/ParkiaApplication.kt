package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import com.example.service.ParkiaForegroundService
import com.example.util.BalanceAlarmScheduler
import com.example.util.BalanceNotificationWorker
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ParkingRepository(AppDatabase.getDatabase(this@ParkiaApplication).parkingDao())
                val config = repository.getOrCreateConfig()
                if (config.notificationsEnabled) {
                    val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                    val nowFormatted = dateTimeFormat.format(Date())
                    NotificationHelper.showBalanceNotification(
                        context = this@ParkiaApplication,
                        balance = config.balance,
                        isSessionActive = config.isSessionActive,
                        formattedDateTime = nowFormatted
                    )
                    ParkiaForegroundService.startOrUpdate(this@ParkiaApplication, forceUpdate = true)
                    BalanceAlarmScheduler.scheduleNext(this@ParkiaApplication, config.notificationIntervalMinutes)
                    BalanceNotificationWorker.scheduleOrCancel(this@ParkiaApplication, true, config.notificationIntervalMinutes)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
