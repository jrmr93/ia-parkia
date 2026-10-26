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
    const val NOTIFICATION_ID_ALT = 1002
    const val GEOFENCE_NOTIFICATION_ID = 2002

    var currentNotificationId: Int = NOTIFICATION_ID
    var isDismissedByUser: Boolean = false
    var sessionGeneration: Int = 1

    fun onNotificationDismissed(context: Context) {
        isDismissedByUser = true
        sessionGeneration++
        try {
            NotificationManagerCompat.from(context).cancel(currentNotificationId)
        } catch (_: Throwable) {
        }
        currentNotificationId = if (currentNotificationId == NOTIFICATION_ID) NOTIFICATION_ID_ALT else NOTIFICATION_ID
    }

    fun cancelBalanceNotification(context: Context) {
        try {
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(NOTIFICATION_ID)
            manager.cancel(NOTIFICATION_ID_ALT)
        } catch (_: Throwable) {
        }
    }

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

    var isHyperIslandForcedForTesting: Boolean? = null

    fun formatHyperIslandBalance(balance: Double): String {
        val isDebt = balance < -0.0001
        val formattedAmount = String.format(Locale.US, "%.2f", if (isDebt) -balance else balance)
        return if (isDebt) "-$$formattedAmount" else "$$formattedAmount"
    }

    fun isHyperIslandSupported(context: Context): Boolean {
        isHyperIslandForcedForTesting?.let { return it }
        return try {
            if (io.github.d4viddf.hyperisland_kit.HyperIslandNotification.isSupported(context)) {
                return true
            }
            if (isXiaomiHyperOsDevice()) {
                val hasFocusPermission = try {
                    val bundle = context.contentResolver.call(
                        android.net.Uri.parse("content://miui.statusbar.notification.public"),
                        "canShowFocus",
                        null,
                        android.os.Bundle()
                    )
                    bundle?.getBoolean("canShowFocus", false) ?: false
                } catch (_: Throwable) {
                    false
                }
                val isIslandProp = try {
                    val spClass = Class.forName("android.os.SystemProperties")
                    val getBooleanMethod = spClass.getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                    (getBooleanMethod.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false) ||
                    (getBooleanMethod.invoke(null, "ro.feature.island", false) as? Boolean ?: false)
                } catch (_: Throwable) {
                    false
                }
                hasFocusPermission && isIslandProp
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun isXiaomiHyperOsDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        val isXiaomiFamily = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")
        if (!isXiaomiFamily) return false
        return try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val miOs = getMethod.invoke(null, "ro.mi.os.version.name", "") as? String ?: ""
            val miui = getMethod.invoke(null, "ro.miui.ui.version.name", "") as? String ?: ""
            miOs.isNotBlank() || miui.isNotBlank()
        } catch (_: Throwable) {
            isXiaomiFamily
        }
    }

    fun applyHyperIsland(
        context: Context,
        builder: NotificationCompat.Builder,
        balanceOnlyText: String,
        isDebt: Boolean = false,
        isUpdate: Boolean = !isDismissedByUser
    ) {
        try {
            val carIcon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_car)
            } else null

            val subtitle = if (isDebt) "Deuda pendiente" else "Saldo disponible"
            val businessId = "Parkia_$sessionGeneration"

            val hyperBuilder = io.github.d4viddf.hyperisland_kit.HyperIslandNotification.Builder(context, businessId, balanceOnlyText)
                .setEnableFloat(false)
                .setShowNotification(true)
                .setIslandFirstFloat(false)
                .setCancel(false)
                .setReopen(true)
                .setBaseInfo(
                    title = balanceOnlyText,
                    content = subtitle
                )

            if (carIcon != null) {
                hyperBuilder.addPicture(io.github.d4viddf.hyperisland_kit.HyperPicture("car_icon", carIcon))
                hyperBuilder.setTickerIcon(carIcon)
            }

            // Status bar pill / capsule around camera punch-hole cutout (Dynamic Island)
            hyperBuilder.setSmallIsland("car_icon")

            // Compact dual-sided capsule around the camera cutout:
            // Left of camera: car icon
            val imgTextLeft = io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft(
                type = 1,
                picInfo = io.github.d4viddf.hyperisland_kit.models.PicInfo(
                    type = 1,
                    pic = "car_icon"
                ),
                textInfo = null,
                progressInfo = null
            )

            // Right of camera: user balance text
            val imgTextRight = io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight(
                type = 1,
                picInfo = null,
                textInfo = io.github.d4viddf.hyperisland_kit.models.TextInfo(
                    title = balanceOnlyText,
                    content = null
                )
            )

            hyperBuilder.setBigIslandInfo(
                left = imgTextLeft,
                right = imgTextRight,
                centerText = io.github.d4viddf.hyperisland_kit.models.TextInfo(
                    title = balanceOnlyText,
                    content = null
                ),
                pic = null,
                progressText = null
            )

            hyperBuilder.setIslandConfig(
                2,
                null,
                false,
                false,
                null,
                null,
                false
            )

            val rawJsonParam = hyperBuilder.buildJsonParam()
            val jsonParam = try {
                val rootObj = org.json.JSONObject(rawJsonParam)
                val paramV2 = if (rootObj.has("param_v2")) rootObj.getJSONObject("param_v2") else null
                if (paramV2 != null) {
                    paramV2.put("updatable", isUpdate)
                    paramV2.put("cancel", false)
                    paramV2.put("can_cancel", false)
                    paramV2.put("reopen", true)
                    paramV2.put("needCloseAnimation", false)
                }
                val paramIsland = when {
                    paramV2?.has("param_island") == true -> paramV2.getJSONObject("param_island")
                    rootObj.has("param_island") -> rootObj.getJSONObject("param_island")
                    else -> null
                }
                if (paramIsland != null) {
                    paramIsland.put("islandProperty", 1) // 1 = Ongoing / Persistent status bar island
                    paramIsland.put("dismissIsland", false)
                    paramIsland.put("maxSize", false) // Compact shrunk capsule around camera
                    paramIsland.put("needCloseAnimation", false)
                    paramIsland.put("slideDismiss", false)
                    paramIsland.put("canDismiss", false)
                    paramIsland.put("allowDismiss", false)
                    paramIsland.put("slideHide", false)
                    paramIsland.put("canHide", false)
                    paramIsland.put("allowHide", false)
                    paramIsland.put("canSlide", false)
                    paramIsland.put("allowSlide", false)
                }
                rootObj.toString()
            } catch (_: Throwable) {
                rawJsonParam
            }

            val resourceBundle = hyperBuilder.buildResourceBundle()
            val customExtras = hyperBuilder.buildCustomExtras()

            builder.addExtras(resourceBundle)
            builder.addExtras(customExtras)
            builder.extras.putString("miui.focus.param", jsonParam)
            builder.extras.putBoolean("miui.enableFloat", false)
            builder.extras.putString("miui.focus.ticker", balanceOnlyText)
            builder.extras.putBoolean("miui.focus.show_notification", true)
            builder.extras.putBoolean("miui.focus.updatable", isUpdate)
            builder.extras.putBoolean("miui.focus.island_first_float", false)
            builder.extras.putBoolean("miui.focus.cancel", false)
            builder.extras.putBoolean("miui.focus.can_cancel", false)
            builder.extras.putBoolean("miui.focus.reopen", true)
            builder.extras.putBoolean("miui.focus.need_action_cancel", false)
            builder.extras.putBoolean("miui.focus.slide_dismiss", false)
            builder.extras.putBoolean("miui.focus.dismiss_island", false)
            builder.extras.putBoolean("miui.focus.can_slide", false)
            builder.extras.putBoolean("miui.focus.allow_dismiss", false)
            builder.extras.putBoolean("miui.focus.slide_hide", false)
            builder.extras.putBoolean("miui.focus.can_hide", false)
            builder.extras.putBoolean("miui.focus.allow_hide", false)

            if (carIcon != null) {
                builder.extras.putParcelable("miui.focus.pic_car_icon", carIcon)
                builder.extras.putParcelable("miui.focus.pic_ticker", carIcon)
                val picsBundle = builder.extras.getBundle("miui.focus.pics") ?: android.os.Bundle()
                picsBundle.putParcelable("miui.focus.pic_car_icon", carIcon)
                picsBundle.putParcelable("miui.focus.pic_ticker", carIcon)
                builder.extras.putBundle("miui.focus.pics", picsBundle)
            }
        } catch (t: Throwable) {
            android.util.Log.w("NotificationHelper", "Failed to configure HyperIsland notification", t)
        }
    }

    fun buildBalanceNotification(
        context: Context,
        balance: Double,
        isSessionActive: Boolean,
        formattedDateTime: String
    ): Notification {
        val isDebt = balance < -0.0001
        val stateText = when {
            isSessionActive && isDebt -> "En Sesión Activa (En Deuda)"
            isSessionActive -> "En Sesión Activa"
            isDebt -> "En Reposo (Deuda Pendiente)"
            else -> "En Reposo"
        }
        val formattedAmount = String.format(Locale.US, "%.2f", if (isDebt) -balance else balance)
        val balanceOnlyText = if (isDebt) "-$$formattedAmount" else "$$formattedAmount"
        val balanceText = if (isDebt) "-$$formattedAmount USD" else "$$formattedAmount USD"
        val titleText = if (isDebt) "Parkia - Deuda: $balanceText" else "Parkia - Saldo: $balanceText"

        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = android.content.Intent(context, com.example.receiver.BalanceAlarmReceiver::class.java).apply {
            action = com.example.receiver.BalanceAlarmReceiver.ACTION_NOTIFICATION_DISMISSED
        }
        val deletePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            9009,
            deleteIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = "Actualizado: $formattedDateTime • $stateText"
        val detailText = if (isDebt) {
            if (isSessionActive) {
                "⚠️ Saldo negativo por deuda: -$formattedAmount USD\nSesión en curso acumulando deuda.\nÚltima actualización: $formattedDateTime"
            } else {
                "⚠️ Saldo negativo por deuda: -$formattedAmount USD\nDeuda pendiente por saldar.\nÚltima actualización: $formattedDateTime"
            }
        } else {
            "Saldo disponible: $balanceText\nEstado: $stateText\nÚltima actualización: $formattedDateTime"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle(titleText)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setAutoCancel(false)
            .setLocalOnly(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setOnlyAlertOnce(true)

        // Compatible with Xiaomi HyperOS HyperIsland: displays strictly user balance (e.g. $25.50)
        // Keeps conventional notification as alternative for incompatible devices
        if (isHyperIslandSupported(context)) {
            applyHyperIsland(context, builder, balanceOnlyText, isDebt, isUpdate = !isDismissedByUser)
        }

        val notification = builder.build()
        notification.flags = notification.flags or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_FOREGROUND_SERVICE
        return notification
    }

    fun showBalanceNotification(
        context: Context,
        balance: Double,
        isSessionActive: Boolean,
        formattedDateTime: String
    ) {
        createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val targetId = currentNotificationId
        val notification = buildBalanceNotification(context, balance, isSessionActive, formattedDateTime)

        try {
            val manager = NotificationManagerCompat.from(context)
            val oldId = if (targetId == NOTIFICATION_ID) NOTIFICATION_ID_ALT else NOTIFICATION_ID
            manager.cancel(oldId)
            manager.notify(targetId, notification)
            isDismissedByUser = false
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
            if (!isSessionActive) {
                putExtra("EXTRA_SHOW_START_OPTIONS_MODAL", true)
            }
        }
        val appPendingIntent = android.app.PendingIntent.getActivity(
            context,
            GEOFENCE_NOTIFICATION_ID,
            appIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSessionActive) "Parqueadero Detectado - Sesión Activa" else "Parqueadero Detectado - Sin Sesión Activa"
        val content = if (isSessionActive)
            "Tienes una sesión de parqueo activa en esta ubicación. Toca para registrar la salida y terminar la sesión."
        else
            "Te encuentras cerca del parqueadero y no tienes ninguna sesión activa. Toca para registrar el ingreso e iniciar sesión."

        val actionText = if (isSessionActive) "Terminar Sesión" else "Iniciar Sesión"

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
