package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ParkingConfig
import com.example.data.ParkingHistoryItem
import com.example.data.ParkingRepository
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.data.RechargeHistoryItem
import com.example.util.BackupUtil
import com.example.util.ReceiptAnalysisResult
import com.example.util.ReceiptStorageUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParkingUiState(
    val config: ParkingConfig = ParkingConfig(),
    val history: List<ParkingHistoryItem> = emptyList(),
    val rechargeHistory: List<RechargeHistoryItem> = emptyList(),
    val availableTimeFormatted: String = "0m",
    val isLowBalance: Boolean = true,
    val estimatedExhaustionFormatted: String? = null,
    val totalSpentHistorical: Double = 0.0,
    val totalSessionsCount: Int = 0,
    val showRechargeCustomDialog: Boolean = false,
    val showRechargeOptionsModal: Boolean = false,
    val showRechargeHistoryModal: Boolean = false,
    val isAnalyzingReceipt: Boolean = false,
    val analyzedReceiptResult: com.example.util.ReceiptAnalysisResult? = null,
    val analyzedReceiptPhotoPath: String? = null,
    val showConfirmRechargeDialog: Boolean = false,
    val showEditRechargeDialog: Boolean = false,
    val editingRechargeItem: RechargeHistoryItem? = null,
    val showDeleteRechargeDialog: Boolean = false,
    val deletingRechargeItem: RechargeHistoryItem? = null,
    val viewingReceiptPath: String? = null,
    val showResetBalanceDialog: Boolean = false,
    val showTariffSettingsDialog: Boolean = false,
    val showResetAllDialog: Boolean = false,
    val showExhaustedBalanceAlert: Boolean = false,
    val showNoFundsToStartAlert: Boolean = false,
    val showEditActiveSessionDialog: Boolean = false,
    val showConfirmStartDialog: Boolean = false,
    val showConfirmStopDialog: Boolean = false,
    val showQuickTileStartModal: Boolean = false,
    val showVoiceAiDialog: Boolean = false,
    val showVisionAiDialog: Boolean = false,
    val voiceAiResult: com.example.util.AiRecognitionResult? = null,
    val visionAiResult: com.example.util.AiRecognitionResult? = null,
    val capturedBitmap: android.graphics.Bitmap? = null,
    val capturedPhotoPath: String? = null,
    val isAiProcessing: Boolean = false,
    val isScanningNfcForRegistration: Boolean = false,
    val scannedNfcTagId: String? = null
)

class ParkingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ParkingRepository = ParkingRepository(
        AppDatabase.getDatabase(application).parkingDao()
    )

    private val _uiState = MutableStateFlow(ParkingUiState())
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()

    private var activeTickerJob: Job? = null
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private val friendlyDateFormat = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault())

    init {
        NotificationHelper.createNotificationChannel(application)

        // Initial config setup and catch up
        viewModelScope.launch {
            val initial = repository.getOrCreateConfig()
            _uiState.update { it.copy(config = initial) }
            repository.catchUpSession()
            recalculateDerivedValues()
            if (initial.notificationsEnabled) {
                com.example.service.ParkiaForegroundService.startOrUpdate(getApplication(), forceUpdate = false)
                com.example.util.BalanceAlarmScheduler.scheduleOrCancel(getApplication(), true, initial.notificationIntervalMinutes)
                com.example.util.BalanceNotificationWorker.scheduleOrCancel(getApplication(), true, initial.notificationIntervalMinutes)
            }
        }

        // Collect config updates reactively from Room for UI screen updates
        viewModelScope.launch {
            repository.configFlow.collect { updatedConfig ->
                if (updatedConfig != null) {
                    _uiState.update { it.copy(config = updatedConfig) }
                    com.example.util.SecurityManager.setQuickTileBiometricEnabled(getApplication(), updatedConfig.quickTileBiometricEnabled)
                    com.example.util.SecurityManager.setGlobalAuthEnabled(getApplication(), updatedConfig.globalSecurityAuthEnabled)
                    recalculateDerivedValues()
                    manageTicker(updatedConfig)
                    com.example.service.ParkiaTileService.updateQuickTileState(getApplication())
                }
            }
        }

        // Collect history updates reactively from Room
        viewModelScope.launch {
            repository.historyFlow.collect { historyList ->
                val totalSpent = historyList.sumOf { it.costCharged }
                _uiState.update {
                    it.copy(
                        history = historyList,
                        totalSpentHistorical = totalSpent,
                        totalSessionsCount = historyList.size
                    )
                }
            }
        }

        // Collect recharge history updates reactively from Room
        viewModelScope.launch {
            repository.rechargeHistoryFlow.collect { recharges ->
                _uiState.update { it.copy(rechargeHistory = recharges) }
            }
        }
    }

    /**
     * Posts a notification with current balance, update timestamp, and session state.
     */
    private fun postRealtimeNotification() {
        val config = _uiState.value.config
        if (!config.notificationsEnabled) return
        val nowFormatted = dateTimeFormat.format(Date())
        NotificationHelper.showBalanceNotification(
            context = getApplication(),
            balance = config.balance,
            isSessionActive = config.isSessionActive,
            formattedDateTime = nowFormatted
        )
        com.example.util.BalanceAlarmScheduler.scheduleNext(getApplication(), config.notificationIntervalMinutes)
    }

    /**
     * Called when the app returns to foreground.
     */
    fun onAppResume() {
        viewModelScope.launch {
            repository.catchUpSession()
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    private fun manageTicker(config: ParkingConfig) {
        if (config.isSessionActive) {
            if (activeTickerJob == null || activeTickerJob?.isActive == false) {
                startActiveTicker()
            }
        } else {
            activeTickerJob?.cancel()
            activeTickerJob = null
        }
    }

    private fun startActiveTicker() {
        activeTickerJob?.cancel()
        activeTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                repository.advanceOneSecond()
                recalculateDerivedValues()
            }
        }
    }

    private fun recalculateDerivedValues() {
        val config = _uiState.value.config
        val availableTime = repository.calculateAvailableTime(
            config.balance,
            config.rateAmount,
            config.rateMinutes
        )
        val isLow = repository.isLowBalance(
            config.balance,
            config.rateAmount,
            config.rateMinutes
        )

        // Estimated exhaustion date/time: calculated ONLY when session is active!
        val estimatedExhaustion = if (config.isSessionActive) {
            val exhaustionMs = repository.calculateEstimatedExhaustionMs(config)
            if (exhaustionMs != null) {
                if (System.currentTimeMillis() >= exhaustionMs) {
                    "Saldo agotado (${friendlyDateFormat.format(Date(exhaustionMs))}) • En deuda"
                } else {
                    friendlyDateFormat.format(Date(exhaustionMs))
                }
            } else if (config.balance < -0.0001) {
                "En deuda • Saldo agotado"
            } else {
                null
            }
        } else {
            null
        }

        _uiState.update {
            it.copy(
                availableTimeFormatted = availableTime,
                isLowBalance = isLow,
                estimatedExhaustionFormatted = estimatedExhaustion
            )
        }
    }

    // Confirmation Triggers for Start & Stop
    fun requestStartSession() {
        val config = _uiState.value.config
        if (config.balance < config.rateAmount || config.rateAmount <= 0.0) {
            _uiState.update { it.copy(showNoFundsToStartAlert = true) }
        } else {
            _uiState.update { it.copy(showConfirmStartDialog = true) }
        }
    }

    fun requestStopSession() {
        _uiState.update { it.copy(showConfirmStopDialog = true) }
    }

    fun confirmStartSession() {
        _uiState.update { it.copy(showConfirmStartDialog = false) }
        startSession()
    }

    fun confirmStopSession() {
        _uiState.update { it.copy(showConfirmStopDialog = false) }
        stopSession()
    }

    fun dismissConfirmStartDialog() {
        _uiState.update { it.copy(showConfirmStartDialog = false) }
    }

    fun dismissConfirmStopDialog() {
        _uiState.update { it.copy(showConfirmStopDialog = false) }
    }

    // Direct Session Controls
    fun startSession(photoPath: String = "") {
        viewModelScope.launch {
            val configBefore = repository.getOrCreateConfig()
            val startBalance = configBefore.balance
            val started = repository.startSession(photoPath = photoPath)
            if (!started) {
                _uiState.update { it.copy(showNoFundsToStartAlert = true) }
            } else {
                com.example.util.TtsManager.announceSessionStart(getApplication(), startBalance, configBefore.ttsAnnouncementsEnabled)
                recalculateDerivedValues()
                postRealtimeNotification()
                if (configBefore.notificationsEnabled) {
                    com.example.service.ParkiaForegroundService.startOrUpdate(getApplication(), forceUpdate = true)
                    com.example.util.BalanceAlarmScheduler.scheduleNext(getApplication(), configBefore.notificationIntervalMinutes)
                }
            }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            repository.stopSession(reason = "Finalizado por usuario")
            val configAfter = repository.getOrCreateConfig()
            com.example.util.TtsManager.announceSessionStop(getApplication(), configAfter.balance, configAfter.ttsAnnouncementsEnabled)
            recalculateDerivedValues()
            postRealtimeNotification()
            if (configAfter.notificationsEnabled) {
                com.example.service.ParkiaForegroundService.startOrUpdate(getApplication(), forceUpdate = true)
                com.example.util.BalanceAlarmScheduler.scheduleNext(getApplication(), configAfter.notificationIntervalMinutes)
            }
        }
    }

    fun resumeSessionFromHistory(item: ParkingHistoryItem) {
        viewModelScope.launch {
            val resumed = repository.resumeSessionFromHistory(item)
            if (resumed) {
                val config = repository.getOrCreateConfig()
                com.example.util.TtsManager.announceSessionStart(getApplication(), config.sessionInitialBalance, config.ttsAnnouncementsEnabled)
                recalculateDerivedValues()
                postRealtimeNotification()
                if (config.notificationsEnabled) {
                    com.example.service.ParkiaForegroundService.startOrUpdate(getApplication(), forceUpdate = true)
                    com.example.util.BalanceAlarmScheduler.scheduleNext(getApplication(), config.notificationIntervalMinutes)
                }
                com.example.service.ParkiaTileService.updateQuickTileState(getApplication())
            }
        }
    }

    /**
     * Toggles session state when an NFC tag is detected.
     * Validates registered tag if strict matching is enabled.
     */
    fun toggleSessionFromNfc(scannedTagId: String? = null, onFeedback: (isStarted: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            val configBefore = repository.getOrCreateConfig()

            // Check strict NFC tag matching if enabled
            if (configBefore.nfcStrictMatchingEnabled && configBefore.registeredNfcTagId.isNotBlank()) {
                val registered = configBefore.registeredNfcTagId.trim()
                if (scannedTagId.isNullOrBlank() || !scannedTagId.equals(registered, ignoreCase = true)) {
                    val tagMsg = if (scannedTagId.isNullOrBlank()) "Tag desconocido" else "ID: $scannedTagId"
                    onFeedback(false, "Tarjeta NFC no autorizada ($tagMsg)")
                    return@launch
                }
            }

            if (configBefore.isSessionActive) {
                repository.stopSession(reason = "Finalizado por tag NFC")
                val configAfter = repository.getOrCreateConfig()
                com.example.util.TtsManager.announceSessionStop(getApplication(), configAfter.balance, configAfter.ttsAnnouncementsEnabled)
                recalculateDerivedValues()
                postRealtimeNotification()
                onFeedback(false, "Sesión finalizada mediante NFC")
            } else {
                if (configBefore.balance < configBefore.rateAmount || configBefore.rateAmount <= 0.0) {
                    _uiState.update { it.copy(showNoFundsToStartAlert = true) }
                    onFeedback(false, "Saldo insuficiente para iniciar sesión")
                } else {
                    val startBalance = configBefore.balance
                    val started = repository.startSession()
                    if (!started) {
                        _uiState.update { it.copy(showNoFundsToStartAlert = true) }
                        onFeedback(false, "No se pudo iniciar la sesión")
                    } else {
                        com.example.util.TtsManager.announceSessionStart(getApplication(), startBalance, configBefore.ttsAnnouncementsEnabled)
                        recalculateDerivedValues()
                        postRealtimeNotification()
                        onFeedback(true, "¡Sesión iniciada por NFC!")
                    }
                }
            }
        }
    }

    fun setIsScanningNfcForRegistration(isScanning: Boolean) {
        _uiState.update { it.copy(isScanningNfcForRegistration = isScanning) }
    }

    fun onNfcTagScannedForRegistration(tagId: String) {
        _uiState.update {
            it.copy(
                isScanningNfcForRegistration = false,
                scannedNfcTagId = tagId
            )
        }
    }

    fun clearScannedNfcTagId() {
        _uiState.update { it.copy(scannedNfcTagId = null) }
    }

    fun updateActiveSession(newStartTimestamp: Long, newInitialBalance: Double) {
        viewModelScope.launch {
            val depleted = repository.updateActiveSession(newStartTimestamp, newInitialBalance)
            if (depleted) {
                _uiState.update { it.copy(showExhaustedBalanceAlert = true) }
            }
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    // Wallet Controls
    fun rechargeBalance(amount: Double) {
        viewModelScope.launch {
            repository.rechargeBalance(amount)
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun resetBalance() {
        viewModelScope.launch {
            repository.resetBalance()
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun updateTariff(amount: Double, minutes: Int) {
        viewModelScope.launch {
            repository.updateTariff(amount, minutes)
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun updateTariffAndNotifications(
        amount: Double,
        minutes: Int,
        notifyEnabled: Boolean,
        notifyInterval: Int,
        tileLabel: String = "Parkia",
        geminiApiKey: String = "",
        geminiModel: String = "gemini-2.0-flash",
        isGeminiEnabled: Boolean = true,
        quickTileBiometricEnabled: Boolean = true,
        globalSecurityAuthEnabled: Boolean = true,
        ttsAnnouncementsEnabled: Boolean = true,
        geofenceEnabled: Boolean = false,
        geofenceLatitude: Double = 0.0,
        geofenceLongitude: Double = 0.0,
        geofenceRadiusMeters: Float = 100f,
        registeredNfcTagId: String = "",
        nfcStrictMatchingEnabled: Boolean = false
    ) {
        viewModelScope.launch {
            repository.updateTariffAndNotifications(
                amount = amount,
                minutes = minutes,
                notifyEnabled = notifyEnabled,
                notifyInterval = notifyInterval,
                tileLabel = tileLabel,
                geminiApiKey = geminiApiKey,
                geminiModel = geminiModel,
                isGeminiEnabled = isGeminiEnabled,
                quickTileBiometricEnabled = quickTileBiometricEnabled,
                globalSecurityAuthEnabled = globalSecurityAuthEnabled,
                ttsAnnouncementsEnabled = ttsAnnouncementsEnabled,
                geofenceEnabled = geofenceEnabled,
                geofenceLatitude = geofenceLatitude,
                geofenceLongitude = geofenceLongitude,
                geofenceRadiusMeters = geofenceRadiusMeters,
                registeredNfcTagId = registeredNfcTagId,
                nfcStrictMatchingEnabled = nfcStrictMatchingEnabled
            )
            com.example.util.SecurityManager.setQuickTileBiometricEnabled(getApplication(), quickTileBiometricEnabled)
            com.example.util.SecurityManager.setGlobalAuthEnabled(getApplication(), globalSecurityAuthEnabled)
            if (geofenceEnabled) {
                com.example.util.GeofenceManager.registerGeofence(getApplication(), geofenceLatitude, geofenceLongitude, geofenceRadiusMeters)
            } else {
                com.example.util.GeofenceManager.removeGeofence(getApplication())
            }
            if (notifyEnabled) {
                com.example.service.ParkiaForegroundService.startOrUpdate(getApplication(), forceUpdate = true)
            } else {
                com.example.service.ParkiaForegroundService.stop(getApplication())
            }
            com.example.util.BalanceAlarmScheduler.scheduleOrCancel(getApplication(), notifyEnabled, notifyInterval)
            com.example.util.BalanceNotificationWorker.scheduleOrCancel(getApplication(), notifyEnabled, notifyInterval)
            com.example.service.ParkiaTileService.updateQuickTileState(getApplication())
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun testGeminiApiKey(apiKey: String, modelName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = com.example.util.GeminiAiParser.testApiKey(apiKey, modelName)
            onResult(res.first, res.second)
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            repository.resetAll()
            com.example.util.PhotoStorageManager.clearAllPhotos(getApplication())
            com.example.util.ReceiptStorageUtil.deleteAllReceiptFiles(getApplication())
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    // Dialog state toggles
    fun setShowRechargeCustomDialog(show: Boolean) {
        _uiState.update { it.copy(showRechargeCustomDialog = show) }
    }

    fun setShowRechargeOptionsModal(show: Boolean) {
        _uiState.update { it.copy(showRechargeOptionsModal = show) }
    }

    fun setShowRechargeHistoryModal(show: Boolean) {
        _uiState.update { it.copy(showRechargeHistoryModal = show) }
    }

    fun setViewingReceiptPath(path: String?) {
        _uiState.update { it.copy(viewingReceiptPath = path) }
    }

    fun processReceiptImage(bitmap: android.graphics.Bitmap, savedInternalPath: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isAnalyzingReceipt = true) }
            val effectivePath = savedInternalPath ?: com.example.util.ReceiptStorageUtil.saveReceiptBitmap(getApplication(), bitmap) ?: ""
            val config = _uiState.value.config
            val result = try {
                com.example.util.GeminiAiParser.processReceiptImage(
                    bitmap = bitmap,
                    customApiKey = config.customGeminiApiKey,
                    customModel = config.customGeminiModel,
                    isGeminiEnabled = config.isGeminiEnabled
                )
            } catch (t: Throwable) {
                com.example.util.ReceiptAnalysisResult(
                    totalAmount = null,
                    note = "Comprobante de Recarga",
                    rawText = "Error analizando comprobante: ${t.localizedMessage ?: t.message}",
                    usedGemini = false
                )
            }
            _uiState.update {
                it.copy(
                    isAnalyzingReceipt = false,
                    analyzedReceiptResult = result,
                    analyzedReceiptPhotoPath = effectivePath,
                    showConfirmRechargeDialog = true
                )
            }
        }
    }

    fun confirmAndApplyRecharge(
        amount: Double,
        photoPath: String,
        note: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            repository.addRechargeRecord(
                amount = amount,
                receiptPhotoPath = photoPath,
                note = note,
                timestamp = timestamp
            )
            _uiState.update {
                it.copy(
                    showConfirmRechargeDialog = false,
                    analyzedReceiptResult = null,
                    analyzedReceiptPhotoPath = null
                )
            }
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun dismissConfirmRechargeDialog(deleteAttachedPhoto: Boolean = false) {
        val currentPhotoPath = _uiState.value.analyzedReceiptPhotoPath
        if (deleteAttachedPhoto && !currentPhotoPath.isNullOrBlank()) {
            com.example.util.ReceiptStorageUtil.deleteReceiptFile(currentPhotoPath)
        }
        _uiState.update {
            it.copy(
                showConfirmRechargeDialog = false,
                analyzedReceiptResult = null,
                analyzedReceiptPhotoPath = null
            )
        }
    }

    fun requestEditRecharge(item: RechargeHistoryItem) {
        _uiState.update {
            it.copy(
                editingRechargeItem = item,
                showEditRechargeDialog = true
            )
        }
    }

    fun confirmEditRecharge(item: RechargeHistoryItem, newAmount: Double, newNote: String, newTimestamp: Long) {
        viewModelScope.launch {
            repository.updateRechargeRecord(item, newAmount, newNote, newTimestamp)
            _uiState.update {
                it.copy(
                    showEditRechargeDialog = false,
                    editingRechargeItem = null
                )
            }
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun dismissEditRecharge() {
        _uiState.update {
            it.copy(
                showEditRechargeDialog = false,
                editingRechargeItem = null
            )
        }
    }

    fun requestDeleteRecharge(item: RechargeHistoryItem) {
        _uiState.update {
            it.copy(
                deletingRechargeItem = item,
                showDeleteRechargeDialog = true
            )
        }
    }

    fun confirmDeleteRecharge(item: RechargeHistoryItem) {
        viewModelScope.launch {
            repository.deleteRechargeRecord(item)
            _uiState.update {
                it.copy(
                    showDeleteRechargeDialog = false,
                    deletingRechargeItem = null
                )
            }
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    fun dismissDeleteRecharge() {
        _uiState.update {
            it.copy(
                showDeleteRechargeDialog = false,
                deletingRechargeItem = null
            )
        }
    }

    // Full system backup & restore
    fun exportTotalBackup(destinationUri: android.net.Uri, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentConfig = repository.getOrCreateConfig()
                val parkingHistory = repository.getAllHistoryList()
                val rechargeHistory = repository.getAllRechargesList()
                val context = getApplication<Application>()
                val outputStream = context.contentResolver.openOutputStream(destinationUri)
                    ?: throw java.io.IOException("No se pudo abrir el archivo de destino para guardar el respaldo.")

                outputStream.use { os ->
                    com.example.util.BackupUtil.exportFullBackup(
                        context = context,
                        config = currentConfig,
                        parkingHistory = parkingHistory,
                        rechargeHistory = rechargeHistory,
                        outputStream = os
                    )
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(true, "¡Respaldo total generado con éxito! (${parkingHistory.size} sesiones, ${rechargeHistory.size} recargas).")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(false, "Error al generar respaldo: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    private suspend fun applyRestoredSystemState(parsed: com.example.util.ParsedParkiaBackup) {
        val app = getApplication<Application>()

        // 1. Restaurar base de datos Room completa (sesiones, recargas y configuración)
        repository.restoreAllData(
            restoredConfig = parsed.config,
            parkingHistory = parsed.parkingHistory,
            rechargeHistory = parsed.rechargeHistory
        )

        // 2. Si la sesión restaurada estaba activa, sincronizar el tiempo transcurrido
        if (parsed.config.isSessionActive) {
            repository.catchUpSession()
        }

        val effectiveConfig = repository.getOrCreateConfig()

        // 3. Restaurar y sincronizar preferencias de autenticación y seguridad
        com.example.util.SecurityManager.setQuickTileBiometricEnabled(app, effectiveConfig.quickTileBiometricEnabled)
        com.example.util.SecurityManager.setGlobalAuthEnabled(app, effectiveConfig.globalSecurityAuthEnabled)
        val restoredPin = parsed.preferences["security_pin"] as? String
        if (!restoredPin.isNullOrBlank()) {
            com.example.util.SecurityManager.savePin(app, restoredPin)
        }

        // 4. Configurar Geocerca según el estado restaurado
        if (effectiveConfig.geofenceEnabled && effectiveConfig.geofenceLatitude != 0.0 && effectiveConfig.geofenceLongitude != 0.0) {
            com.example.util.GeofenceManager.registerGeofence(
                context = app,
                latitude = effectiveConfig.geofenceLatitude,
                longitude = effectiveConfig.geofenceLongitude,
                radiusMeters = effectiveConfig.geofenceRadiusMeters
            )
        } else {
            com.example.util.GeofenceManager.removeGeofence(app)
        }

        // 5. Configurar Servicio en primer plano y alarmas/workers de notificaciones
        if (effectiveConfig.notificationsEnabled) {
            com.example.service.ParkiaForegroundService.startOrUpdate(app, forceUpdate = true)
            com.example.util.BalanceAlarmScheduler.scheduleOrCancel(app, true, effectiveConfig.notificationIntervalMinutes)
            com.example.util.BalanceNotificationWorker.scheduleOrCancel(app, true, effectiveConfig.notificationIntervalMinutes)
            postRealtimeNotification()
        } else {
            com.example.service.ParkiaForegroundService.stop(app)
            com.example.util.BalanceAlarmScheduler.scheduleOrCancel(app, false, effectiveConfig.notificationIntervalMinutes)
            com.example.util.BalanceNotificationWorker.scheduleOrCancel(app, false, effectiveConfig.notificationIntervalMinutes)
            androidx.core.app.NotificationManagerCompat.from(app).cancel(com.example.util.NotificationHelper.NOTIFICATION_ID)
        }

        // 6. Actualizar Mosaico de Ajustes Rápidos (Quick Settings Tile)
        com.example.service.ParkiaTileService.updateQuickTileState(app)

        // 7. Administrar el Ticker de sesión activa (iniciar o detener según estado)
        manageTicker(effectiveConfig)

        // 8. Actualizar estado de la interfaz de usuario de manera inmediata
        _uiState.update {
            it.copy(
                config = effectiveConfig,
                history = parsed.parkingHistory,
                rechargeHistory = parsed.rechargeHistory,
                totalSpentHistorical = parsed.parkingHistory.sumOf { h -> h.costCharged },
                totalSessionsCount = parsed.parkingHistory.size,
                showTariffSettingsDialog = false,
                isAnalyzingReceipt = false,
                analyzedReceiptResult = null,
                analyzedReceiptPhotoPath = null,
                showConfirmRechargeDialog = false,
                showEditRechargeDialog = false,
                showDeleteRechargeDialog = false,
                viewingReceiptPath = null
            )
        }
        recalculateDerivedValues()
    }

    fun restoreTotalBackup(sourceUri: android.net.Uri, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw java.io.IOException("No se pudo abrir el archivo de respaldo seleccionado.")

                val parsed = inputStream.use { stream ->
                    com.example.util.BackupUtil.restoreFullBackup(context, stream)
                }

                applyRestoredSystemState(parsed)

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(true, "¡Respaldo restaurado con éxito! Se restauraron todas las tarifas, notificaciones, credenciales de IA, seguridad y registros (${parsed.parkingHistory.size} sesiones, ${parsed.rechargeHistory.size} recargas, ${parsed.photosCount} fotos, ${parsed.receiptsCount} comprobantes).")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(false, "Error al restaurar respaldo: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun exportTotalBackup(outputStream: java.io.OutputStream, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentConfig = repository.getOrCreateConfig()
                val parkingHistory = repository.getAllHistoryList()
                val rechargeHistory = repository.getAllRechargesList()
                com.example.util.BackupUtil.exportFullBackup(
                    context = getApplication(),
                    config = currentConfig,
                    parkingHistory = parkingHistory,
                    rechargeHistory = rechargeHistory,
                    outputStream = outputStream
                )
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(true, "Respaldo total generado con éxito (${parkingHistory.size} sesiones, ${rechargeHistory.size} recargas).")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(false, "Error al generar respaldo: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun restoreTotalBackup(inputStream: java.io.InputStream, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = com.example.util.BackupUtil.restoreFullBackup(getApplication(), inputStream)
                applyRestoredSystemState(parsed)

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(true, "¡Respaldo restaurado con éxito! Se restauraron todas las tarifas, notificaciones, credenciales de IA, seguridad y registros (${parsed.parkingHistory.size} sesiones, ${parsed.rechargeHistory.size} recargas, ${parsed.photosCount} fotos, ${parsed.receiptsCount} comprobantes).")
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onDone(false, "Error al restaurar respaldo: ${e.localizedMessage ?: e.message}")
                }
            }
        }
    }

    fun setShowResetBalanceDialog(show: Boolean) {
        _uiState.update { it.copy(showResetBalanceDialog = show) }
    }

    fun setShowTariffSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showTariffSettingsDialog = show) }
    }

    fun setShowResetAllDialog(show: Boolean) {
        _uiState.update { it.copy(showResetAllDialog = show) }
    }

    fun setShowEditActiveSessionDialog(show: Boolean) {
        _uiState.update { it.copy(showEditActiveSessionDialog = show) }
    }

    fun setShowQuickTileStartModal(show: Boolean) {
        _uiState.update { it.copy(showQuickTileStartModal = show) }
    }

    fun setShowVoiceAiDialog(show: Boolean) {
        _uiState.update { it.copy(showVoiceAiDialog = show) }
    }

    fun setShowVisionAiDialog(show: Boolean) {
        _uiState.update { it.copy(showVisionAiDialog = show) }
    }

    fun setVoiceAiResult(result: com.example.util.AiRecognitionResult?) {
        _uiState.update { it.copy(voiceAiResult = result, showVoiceAiDialog = result != null) }
    }

    fun setVisionAiResult(result: com.example.util.AiRecognitionResult?, bitmap: android.graphics.Bitmap?) {
        _uiState.update { it.copy(visionAiResult = result, capturedBitmap = bitmap, showVisionAiDialog = result != null) }
    }

    fun processVoiceAi(rawText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isAiProcessing = true) }
            val config = _uiState.value.config
            val result = try {
                com.example.util.GeminiAiParser.parseSpeech(
                    rawText = rawText,
                    customApiKey = config.customGeminiApiKey,
                    customModel = config.customGeminiModel,
                    isGeminiEnabled = config.isGeminiEnabled
                )
            } catch (t: Throwable) {
                com.example.util.VoiceAiParser.parseSpeech(rawText)
            }
            _uiState.update {
                it.copy(
                    isAiProcessing = false,
                    voiceAiResult = result,
                    showVoiceAiDialog = true
                )
            }
        }
    }

    fun processVisionAi(bitmap: android.graphics.Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isAiProcessing = true) }
            // Save photo strictly inside app's private internal storage (/data/data/com.example/files/photos/)
            val savedPhotoPath = com.example.util.PhotoStorageManager.savePhotoToInternalStorage(getApplication(), bitmap)
            val config = _uiState.value.config
            val result = try {
                com.example.util.GeminiAiParser.processImage(
                    bitmap = bitmap,
                    customApiKey = config.customGeminiApiKey,
                    customModel = config.customGeminiModel,
                    isGeminiEnabled = config.isGeminiEnabled
                )
            } catch (t: Throwable) {
                android.util.Log.e("ParkiaAi", "Error en processVisionAi", t)
                com.example.util.AiRecognitionResult(
                    rawText = "Procesado con foto capturada. Detalle: ${t.localizedMessage ?: t.message ?: t.javaClass.simpleName}"
                )
            }
            _uiState.update {
                it.copy(
                    isAiProcessing = false,
                    visionAiResult = result,
                    capturedBitmap = bitmap,
                    capturedPhotoPath = savedPhotoPath,
                    showVisionAiDialog = true
                )
            }
            if (savedPhotoPath.isNotBlank()) {
                // Pre-update config with photo path
                val updatedConfig = repository.getOrCreateConfig().copy(lastSessionPhotoPath = savedPhotoPath)
                com.example.data.AppDatabase.getDatabase(getApplication()).parkingDao().saveConfig(updatedConfig)
            }
        }
    }

    fun clearCapturedPhoto() {
        _uiState.update {
            it.copy(
                capturedBitmap = null,
                capturedPhotoPath = null,
                visionAiResult = null
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val config = repository.getOrCreateConfig()
            if (!config.isSessionActive && config.lastSessionPhotoPath.isNotBlank()) {
                val updatedConfig = config.copy(lastSessionPhotoPath = "")
                com.example.data.AppDatabase.getDatabase(getApplication()).parkingDao().saveConfig(updatedConfig)
            }
        }
    }

    fun startCustomSession(
        initialBalance: Double,
        timestamp: Long = System.currentTimeMillis(),
        photoPath: String = ""
    ) {
        viewModelScope.launch {
            if (initialBalance >= 0.0) {
                repository.setBalance(initialBalance)
            }
            val configBefore = repository.getOrCreateConfig()
            val startBalance = configBefore.balance
            val effectivePhoto = if (photoPath.isNotBlank()) {
                photoPath
            } else {
                _uiState.value.capturedPhotoPath ?: configBefore.lastSessionPhotoPath
            }
            val started = repository.startSession(now = timestamp, photoPath = effectivePhoto)
            if (!started) {
                _uiState.update { it.copy(showNoFundsToStartAlert = true) }
            } else {
                com.example.util.TtsManager.announceSessionStart(getApplication(), startBalance, configBefore.ttsAnnouncementsEnabled)
                recalculateDerivedValues()
                postRealtimeNotification()
                com.example.service.ParkiaTileService.updateQuickTileState(getApplication())
            }
        }
    }

    fun dismissExhaustedAlert() {
        _uiState.update { it.copy(showExhaustedBalanceAlert = false) }
    }

    fun dismissNoFundsAlert() {
        _uiState.update { it.copy(showNoFundsToStartAlert = false) }
    }

    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    fun formatCurrency(amount: Double): String {
        return String.format(Locale.US, "$%.2f USD", amount)
    }

    override fun onCleared() {
        super.onCleared()
        com.example.util.TtsManager.shutdown()
    }
}
