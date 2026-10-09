package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import com.example.service.ParkiaForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repository = ParkingRepository(
                        AppDatabase.getDatabase(context.applicationContext).parkingDao()
                    )
                    val config = repository.getOrCreateConfig()
                    if (config.notificationsEnabled) {
                        ParkiaForegroundService.startOrUpdate(context.applicationContext)
                    }
                    if (config.geofenceEnabled) {
                        com.example.util.GeofenceManager.registerGeofence(
                            context.applicationContext,
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
