package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.receiver.BalanceAlarmReceiver

object BalanceAlarmScheduler {

    private const val REQUEST_CODE = 4004

    /**
     * Schedules or cancels the alarm that ticks notifications at the configured interval.
     */
    fun scheduleOrCancel(context: Context, enabled: Boolean, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BalanceAlarmReceiver::class.java).apply {
            action = BalanceAlarmReceiver.ACTION_BALANCE_ALARM_TICK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

        if (!enabled) {
            alarmManager.cancel(pendingIntent)
        } else {
            val interval = intervalMinutes.coerceAtLeast(1)
            val triggerAtMillis = android.os.SystemClock.elapsedRealtime() + (interval * 60_000L)
            scheduleAlarm(alarmManager, triggerAtMillis, pendingIntent)
        }
    }

    /**
     * Schedules the next alarm tick.
     */
    fun scheduleNext(context: Context, intervalMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, BalanceAlarmReceiver::class.java).apply {
            action = BalanceAlarmReceiver.ACTION_BALANCE_ALARM_TICK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)

        val interval = intervalMinutes.coerceAtLeast(1)
        val triggerAtMillis = android.os.SystemClock.elapsedRealtime() + (interval * 60_000L)
        scheduleAlarm(alarmManager, triggerAtMillis, pendingIntent)
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
        }
    }
}
