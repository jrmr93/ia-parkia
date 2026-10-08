package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "parkia_balance_channel"
    const val GEOFENCE_CHANNEL_ID = "parkia_geofence_channel"
    const val NOTIFICATION_ID = 1001
    const val GEOFENCE_NOTIFICATION_ID = 2002

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            
            val balanceChannel = NotificationChannel(CHANNEL_ID, "Actualización de Saldo Parkia", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Notifica el saldo actual y fecha de actualización"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(balanceChannel)

            val geofenceChannel = NotificationChannel(GEOFENCE_CHANNEL_ID, "Parqueadero Cercano Parkia", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificaciones automáticas al estar cerca del parqueadero"
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(geofenceChannel)
        }
    }

    fun buildBalanceNotification(
        context: Context,
        balance: Double,
        isSessionActive: Boolean,
        formattedDateTime: String
    ): Notification {
        val stateText = if (isSessionActive) "En Sesión Activa" else "En Reposo"
        val balanceText = String.format(Locale.US, "$%.2f USD", balance)

        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Parkia - Saldo: $balanceText")
            .setContentText("Actualizado: $formattedDateTime • $stateText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun showBalanceNotification(
        context: Context,
        balance: Double,
        isSessionActive: Boolean,
        formattedDateTime: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notification = buildBalanceNotification(context, balance, isSessionActive, formattedDateTime)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // Catch any notification system exception gracefully
        }
    }

    fun showGeofenceNotification(
        context: Context,
        isSessionActive: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        val actionIntent = android.content.Intent(context, com.example.receiver.GeofenceActionReceiver::class.java).apply {
            action = if (isSessionActive) "ACTION_STOP_SESSION" else "ACTION_START_SESSION"
        }
        val actionPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            if (isSessionActive) 1 else 2,
            actionIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            appIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSessionActive) "Parqueadero Detectado" else "Aproximación al Parqueadero"
        val content = if (isSessionActive)
            "Estás en el parqueadero y tienes una sesión activa. ¿Deseas registrar la salida?"
        else
            "Te estás acercando al parqueadero. ¿Deseas registrar tu ingreso?"

        val actionText = if (isSessionActive) "Registrar Salida" else "Registrar Ingreso"

        val notification = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(0, actionText, actionPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(GEOFENCE_NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }
}
