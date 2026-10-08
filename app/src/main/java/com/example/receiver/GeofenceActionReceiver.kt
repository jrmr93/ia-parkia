package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import com.example.util.NotificationHelper
import com.example.util.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.getDatabase(context).parkingDao()
                val repository = ParkingRepository(dao)
                val config = repository.getOrCreateConfig()

                when (action) {
                    "ACTION_START_SESSION" -> {
                        if (!config.isSessionActive) {
                            val success = repository.startSession()
                            if (success && config.ttsAnnouncementsEnabled) {
                                TtsManager.announceSessionStart(context, config.balance)
                            }
                        }
                    }
                    "ACTION_STOP_SESSION" -> {
                        if (config.isSessionActive) {
                            repository.stopSession(reason = "Finalizado automáticamente por geocerca")
                            val updatedConfig = repository.getOrCreateConfig()
                            if (config.ttsAnnouncementsEnabled) {
                                TtsManager.announceSessionStop(context, updatedConfig.balance)
                            }
                        }
                    }
                }

                NotificationManagerCompat.from(context).cancel(NotificationHelper.GEOFENCE_NOTIFICATION_ID)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
