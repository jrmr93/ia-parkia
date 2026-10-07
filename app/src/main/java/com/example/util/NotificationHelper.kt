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
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Actualización de Saldo Parkia"
            val descriptionText = "Notifica el saldo actual y fecha de actualización"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
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
}
