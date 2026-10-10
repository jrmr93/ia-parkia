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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParkingUiState(
    val config: ParkingConfig = ParkingConfig(),
    val history: List<ParkingHistoryItem> = emptyList(),
    val availableTimeFormatted: String = "0m",
    val isLowBalance: Boolean = true,
    val estimatedExhaustionFormatted: String? = null,
    val totalSpentHistorical: Double = 0.0,
    val totalSessionsCount: Int = 0,
    val showRechargeCustomDialog: Boolean = false,
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
            val depleted = repository.catchUpSession()
            if (depleted) {
                _uiState.update { it.copy(showExhaustedBalanceAlert = true) }
            }
            recalculateDerivedValues()
            if (initial.notificationsEnabled) {
                com.example.service.ParkiaForegroundService.startOrUpdate(getApplication(), forceUpdate = false)
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
    }

    /**
     * Called when the app returns to foreground.
     */
    fun onAppResume() {
        viewModelScope.launch {
            val depleted = repository.catchUpSession()
            if (depleted) {
                _uiState.update { it.copy(showExhaustedBalanceAlert = true) }
            }
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
                val depleted = repository.advanceOneSecond()
                if (depleted) {
                    _uiState.update { it.copy(showExhaustedBalanceAlert = true) }
                    postRealtimeNotification()
                    break
                }
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
            exhaustionMs?.let { friendlyDateFormat.format(Date(it)) }
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
    fun startSession() {
        viewModelScope.launch {
            val configBefore = repository.getOrCreateConfig()
            val startBalance = configBefore.balance
            val started = repository.startSession()
            if (!started) {
                _uiState.update { it.copy(showNoFundsToStartAlert = true) }
            } else {
                com.example.util.TtsManager.announceSessionStart(getApplication(), startBalance, configBefore.ttsAnnouncementsEnabled)
                recalculateDerivedValues()
                postRealtimeNotification()
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
            recalculateDerivedValues()
            postRealtimeNotification()
        }
    }

    // Dialog state toggles
    fun setShowRechargeCustomDialog(show: Boolean) {
        _uiState.update { it.copy(showRechargeCustomDialog = show) }
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

    fun startCustomSession(initialBalance: Double, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            if (initialBalance >= 0.0) {
                repository.setBalance(initialBalance)
            }
            val configBefore = repository.getOrCreateConfig()
            val startBalance = configBefore.balance
            val started = repository.startSession(now = timestamp)
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
