package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.ParkingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ParkiaTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileStateInternal()
    }

    private fun updateTileStateInternal() {
        val tile = qsTile ?: return
        val repository = ParkingRepository(
            AppDatabase.getDatabase(applicationContext).parkingDao()
        )

        CoroutineScope(Dispatchers.IO).launch {
            val config = repository.getOrCreateConfig()
            val label = if (config.tileLabel.isBlank()) "Parkia" else config.tileLabel

            tile.label = label
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_car)

            if (config.isSessionActive) {
                tile.subtitle = "Sesión Activa"
                tile.state = Tile.STATE_ACTIVE
            } else {
                tile.subtitle = "En Reposo"
                tile.state = Tile.STATE_INACTIVE
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_FROM_QUICK_TILE", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this@ParkiaTileService,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        fun updateQuickTileState(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val component = ComponentName(context, ParkiaTileService::class.java)
                    requestListeningState(context, component)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun setTileComponentEnabled(context: Context, enabled: Boolean) {
            try {
                val component = ComponentName(context, ParkiaTileService::class.java)
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                context.packageManager.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
                if (enabled) {
                    updateQuickTileState(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun isTileComponentEnabled(context: Context): Boolean {
            return try {
                val component = ComponentName(context, ParkiaTileService::class.java)
                val state = context.packageManager.getComponentEnabledSetting(component)
                state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } catch (_: Exception) {
                true
            }
        }
    }
}
