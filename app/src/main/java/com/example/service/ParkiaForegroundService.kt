package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkiaForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var periodicJob: Job? = null
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val forceUpdate = intent?.getBooleanExtra(EXTRA_FORCE_UPDATE, false) ?: false

        startServiceForeground()
        startPeriodicUpdates(forceUpdate)

        return START_STICKY
    }

    private fun startServiceForeground() {
        val repository = ParkingRepository(
            AppDatabase.getDatabase(applicationContext).parkingDao()
        )
        serviceScope.launch {
            val config = repository.getOrCreateConfig()
            val nowFormatted = dateTimeFormat.format(Date())
            val notification = NotificationHelper.buildBalanceNotification(
                context = this@ParkiaForegroundService,
                balance = config.balance,
                isSessionActive = config.isSessionActive,
                formattedDateTime = nowFormatted
            )
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                    startForeground(NotificationHelper.NOTIFICATION_ID, notification, serviceType)
                } else {
                    startForeground(NotificationHelper.NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startPeriodicUpdates(forceImmediateUpdate: Boolean) {
        if (forceImmediateUpdate) {
            periodicJob?.cancel()
            periodicJob = null
        }

        if (periodicJob?.isActive == true) {
            return
        }

        periodicJob = serviceScope.launch {
            val repository = ParkingRepository(
                AppDatabase.getDatabase(applicationContext).parkingDao()
            )

            while (isActive) {
                val config = repository.getOrCreateConfig()
                if (!config.notificationsEnabled) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }

                updateNotification(repository)

                val intervalMs = (config.notificationIntervalMinutes * 60000L).coerceAtLeast(5000L)
                delay(intervalMs)
            }
        }
    }

    private suspend fun updateNotification(repository: ParkingRepository) {
        val config = repository.getOrCreateConfig()
        if (config.isSessionActive) {
            repository.catchUpSession()
        }
        val updatedConfig = repository.getOrCreateConfig()
        val nowFormatted = dateTimeFormat.format(Date())

        NotificationHelper.showBalanceNotification(
            context = applicationContext,
            balance = updatedConfig.balance,
            isSessionActive = updatedConfig.isSessionActive,
            formattedDateTime = nowFormatted
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        periodicJob?.cancel()
    }

    companion object {
        const val ACTION_START_SERVICE = "ACTION_START_PARKIA_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_PARKIA_SERVICE"
        const val EXTRA_FORCE_UPDATE = "EXTRA_FORCE_UPDATE"

        fun startOrUpdate(context: Context, forceUpdate: Boolean = false) {
            val intent = Intent(context, ParkiaForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
                putExtra(EXTRA_FORCE_UPDATE, forceUpdate)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ParkiaForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
