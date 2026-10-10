package com.example.service

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.ParkingConfig
import com.example.data.ParkingHistoryItem
import com.example.data.ParkingRepository
import com.example.util.TtsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max

class ParkiaCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return ParkiaCarSession()
    }
}

class ParkiaCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return ParkiaCarScreen(carContext)
    }
}

class ParkiaCarScreen(carContext: CarContext) : Screen(carContext) {

    private val repository by lazy {
        ParkingRepository(
            AppDatabase.getDatabase(carContext.applicationContext).parkingDao()
        )
    }

    private var config: ParkingConfig = ParkingConfig()
    private var lastHistoryItem: ParkingHistoryItem? = null
    private var refreshJob: Job? = null

    init {
        TtsManager.init(carContext.applicationContext)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                startAutoRefresh()
            }

            override fun onStop(owner: LifecycleOwner) {
                stopAutoRefresh()
            }
        })
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    repository.catchUpSession()
                    val current = repository.getOrCreateConfig()
                    val history = if (!current.isSessionActive) repository.getLastHistoryItem() else null
                    withContext(Dispatchers.Main) {
                        config = current
                        lastHistoryItem = history
                        invalidate()
                    }
                } catch (_: Exception) {
                }
                delay(1000L)
            }
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    @Volatile
    private var isActionProcessing: Boolean = false

    private fun toggleSession() {
        if (isActionProcessing) return
        isActionProcessing = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val current = repository.getOrCreateConfig()
                if (current.isSessionActive) {
                    repository.stopSession(reason = "Finalizado desde Android Auto")
                    ParkiaForegroundService.stop(carContext.applicationContext)
                    val updated = repository.getOrCreateConfig()
                    val history = repository.getLastHistoryItem()

                    withContext(Dispatchers.Main) {
                        config = updated
                        lastHistoryItem = history
                        invalidate()
                    }

                    TtsManager.announceSessionStop(
                        carContext.applicationContext,
                        updated.balance,
                        current.ttsAnnouncementsEnabled
                    )
                } else {
                    if (current.balance < current.rateAmount || current.rateAmount <= 0.0) {
                        TtsManager.speak(
                            carContext.applicationContext,
                            "Saldo insuficiente para iniciar parqueo"
                        )
                    } else {
                        val startBalance = current.balance
                        val success = repository.startSession()
                        if (success) {
                            ParkiaForegroundService.startOrUpdate(carContext.applicationContext, forceUpdate = true)
                            val updated = repository.getOrCreateConfig()

                            withContext(Dispatchers.Main) {
                                config = updated
                                invalidate()
                            }

                            TtsManager.announceSessionStart(
                                carContext.applicationContext,
                                startBalance,
                                current.ttsAnnouncementsEnabled
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isActionProcessing = false
            }
        }
    }

    private fun formatElapsedTime(elapsedSeconds: Long): String {
        val hrs = elapsedSeconds / 3600
        val mins = (elapsedSeconds % 3600) / 60
        val secs = elapsedSeconds % 60
        return if (hrs > 0) {
            String.format("%dh %02dm %02ds", hrs, mins, secs)
        } else {
            String.format("%02dm %02ds", mins, secs)
        }
    }

    /**
     * Creates a high-contrast Left Column Card with GIGANTIC centered Balance value.
     */
    private fun createBalanceCardIcon(balance: Double, isSessionActive: Boolean): CarIcon {
        val width = 500
        val height = 360
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // White background card
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 32f, 32f, bgPaint)

        // Subtle dark gray border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(3f, 3f, (width - 3).toFloat(), (height - 3).toFloat(), 32f, 32f, borderPaint)

        // Header: "SALDO ACTUAL"
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#444444")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SALDO ACTUAL", width / 2f, 65f, labelPaint)

        // GIGANTIC Centered Balance: "$15.50"
        val balanceStr = String.format(Locale.US, "\$%.2f", balance)
        val fontSize = when {
            balanceStr.length > 7 -> 95f
            balanceStr.length > 6 -> 110f
            else -> 125f
        }

        val balancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(balanceStr, width / 2f, 205f, balancePaint)

        // Bottom Status Badge: 🟢 SESIÓN ACTIVA or ⚪ PARQUEO DETENIDO
        val statusText = if (isSessionActive) "🟢 SESIÓN ACTIVA" else "⚪ PARQUEO DETENIDO"
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSessionActive) Color.parseColor("#2E7D32") else Color.parseColor("#666666")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(statusText, width / 2f, 305f, statusPaint)

        val iconCompat = IconCompat.createWithBitmap(bitmap)
        return CarIcon.Builder(iconCompat).build()
    }

    private fun formatDateTime(timestampMs: Long): String {
        if (timestampMs <= 0L) return "--/--/---- --:--"
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault())
        return sdf.format(java.util.Date(timestampMs))
    }

    private var isConfirmingAction: Boolean = false
    private var confirmResetJob: Job? = null

    private fun handleActionButtonClick() {
        if (isActionProcessing) return

        if (!isConfirmingAction) {
            // First tap: Enter confirmation mode with 4s auto-cancel timeout
            isConfirmingAction = true
            invalidate()

            confirmResetJob?.cancel()
            confirmResetJob = lifecycleScope.launch(Dispatchers.Main) {
                delay(4000L)
                if (isConfirmingAction) {
                    isConfirmingAction = false
                    invalidate()
                }
            }
        } else {
            // Second tap within 4 seconds: Confirmed! Execute action
            confirmResetJob?.cancel()
            confirmResetJob = null
            isConfirmingAction = false
            toggleSession()
        }
    }

    override fun onGetTemplate(): Template {
        val isSessionActive = config.isSessionActive
        val balanceCardIcon = createBalanceCardIcon(config.balance, isSessionActive)

        // Right Column Rows
        val row1Title: String
        val row1Subtitle: String
        val row2Title: String
        val row2Subtitle: String

        if (isSessionActive) {
            val now = System.currentTimeMillis()
            val elapsed = if (config.sessionStartTimestamp > 0L) max(0L, (now - config.sessionStartTimestamp) / 1000L) else config.elapsedSeconds
            row1Title = "⏱️ Transcurrido: ${formatElapsedTime(elapsed)}"
            row1Subtitle = "Sesión de parqueo activa"

            val entryTimeStr = formatDateTime(config.sessionStartTimestamp)
            val initialBalanceStr = String.format(Locale.US, "\$%.2f", config.sessionInitialBalance)
            row2Title = "📥 Entrada: $entryTimeStr"
            row2Subtitle = "Ingresó con saldo de: $initialBalanceStr"
        } else {
            val lastDuration = if (config.lastSessionDurationSeconds > 0L) {
                config.lastSessionDurationSeconds
            } else {
                lastHistoryItem?.durationSeconds ?: 0L
            }

            if (lastDuration > 0L) {
                row1Title = "⏱️ Última sesión: ${formatElapsedTime(lastDuration)}"
                row1Subtitle = "Duración total consumida"
            } else {
                row1Title = "⏱️ Transcurrido: --"
                row1Subtitle = "Parqueo detenido"
            }

            val lastEndMs = if (config.lastSessionEndTimestamp > 0L) {
                config.lastSessionEndTimestamp
            } else {
                lastHistoryItem?.endTimestamp ?: 0L
            }

            val lastExitBal = if (config.lastSessionEndTimestamp > 0L) {
                config.lastSessionExitBalance
            } else if (lastHistoryItem != null) {
                max(0.0, config.balance)
            } else {
                0.0
            }

            if (lastEndMs > 0L) {
                val exitTimeStr = formatDateTime(lastEndMs)
                val exitBalanceStr = String.format(Locale.US, "\$%.2f", lastExitBal)
                row2Title = "📤 Salida: $exitTimeStr"
                row2Subtitle = "Salió con saldo de: $exitBalanceStr"
            } else {
                row2Title = "📤 Salida: Sin registro previo"
                row2Subtitle = "No hay sesiones anteriores"
            }
        }

        val paneBuilder = Pane.Builder()
            .setImage(balanceCardIcon)
            .addRow(
                Row.Builder()
                    .setTitle(row1Title)
                    .addText(row1Subtitle)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle(row2Title)
                    .addText(row2Subtitle)
                    .build()
            )

        // Safety-Locked Action Button (2-Tap Confirmation with 4s auto-cancel)
        val actionTitle = if (isConfirmingAction) {
            if (isSessionActive) "⚠️ ¿CONFIRMAR FINALIZAR? (Tocar de nuevo)" else "⚠️ ¿CONFIRMAR INICIO? (Tocar de nuevo)"
        } else {
            if (isSessionActive) "🛑 FINALIZAR PARQUEO" else "▶️ INICIAR PARQUEO"
        }

        paneBuilder.addAction(
            Action.Builder()
                .setTitle(actionTitle)
                .setFlags(Action.FLAG_PRIMARY)
                .setOnClickListener {
                    handleActionButtonClick()
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Parkia Auto — Parquímetro Inteligente")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
