package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ParkingConfig
import com.example.data.ParkingHistoryItem
import com.example.data.ParkingRepository
import com.example.util.NotificationHelper
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
    val showConfirmStopDialog: Boolean = false
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
            postRealtimeNotification()
        }

        // Collect config updates reactively from Room
        viewModelScope.launch {
            repository.configFlow.collect { updatedConfig ->
                if (updatedConfig != null) {
                    _uiState.update { it.copy(config = updatedConfig) }
                    recalculateDerivedValues()
                    manageTicker(updatedConfig)
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

        // Background periodic loop: notifies in real time EVERY MINUTE
        viewModelScope.launch {
            while (isActive) {
                delay(60000L) // every 1 minute
                recalculateDerivedValues()
                postRealtimeNotification()
            }
        }
    }

    /**
     * Posts a notification with current balance, update timestamp, and session state.
     */
    private fun postRealtimeNotification() {
        val config = _uiState.value.config
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
            val started = repository.startSession()
            if (!started) {
                _uiState.update { it.copy(showNoFundsToStartAlert = true) }
            } else {
                recalculateDerivedValues()
                postRealtimeNotification()
            }
        }
    }

    fun stopSession() {
        viewModelScope.launch {
            repository.stopSession(reason = "Finalizado por usuario")
            recalculateDerivedValues()
            postRealtimeNotification()
        }
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

    fun resetAll() {
        viewModelScope.launch {
            repository.resetAll()
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
}
