package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.max

class ParkingRepository(private val dao: ParkingDao) {

    val configFlow: Flow<ParkingConfig?> = dao.getConfigFlow()
    val historyFlow: Flow<List<ParkingHistoryItem>> = dao.getAllHistory()
    val rechargeHistoryFlow: Flow<List<RechargeHistoryItem>> = dao.getAllRecharges()

    suspend fun getAllHistoryList(): List<ParkingHistoryItem> = withContext(Dispatchers.IO) {
        dao.getAllHistoryList()
    }

    suspend fun getAllRechargesList(): List<RechargeHistoryItem> = withContext(Dispatchers.IO) {
        dao.getAllRechargesList()
    }

    suspend fun getLastHistoryItem(): ParkingHistoryItem? = withContext(Dispatchers.IO) {
        dao.getLastHistoryItem()
    }

    suspend fun getOrCreateConfig(): ParkingConfig = withContext(Dispatchers.IO) {
        val existing = dao.getConfig()
        if (existing != null) {
            existing
        } else {
            val defaultConfig = ParkingConfig()
            dao.saveConfig(defaultConfig)
            defaultConfig
        }
    }

    /**
     * Calculates the number of blocks consumed by elapsed seconds.
     * Minimum 1 full block as soon as session starts.
     * If session stops before block finishes, full block is charged.
     */
    fun calculateBlocks(elapsedSeconds: Long, rateMinutes: Int): Long {
        val blockSec = (rateMinutes * 60).toLong().coerceAtLeast(60L)
        if (elapsedSeconds <= 0L) return 1L
        return (elapsedSeconds + blockSec - 1) / blockSec
    }

    /**
     * Formats available time based on current balance and rate.
     * Calculated in blocks of continuous time:
     * e.g. "2d 4h 0m", "1h 30m", "45m", "0m"
     */
    /**
     * Formats available time based on current balance and rate.
     * Calculated in blocks of continuous time:
     * e.g. "2d 4h 0m", "1h 30m", "45m", "0m"
     */
    fun calculateAvailableTime(balance: Double, rateAmount: Double, rateMinutes: Int): String {
        if (balance < -0.0001) {
            val debt = -balance
            return "0m (Deuda: $${String.format(java.util.Locale.US, "%.2f", debt)} USD)"
        }
        if (balance < rateAmount || rateAmount <= 0.0) {
            return "0m (Sin saldo para 1 bloque)"
        }
        val fullBlocks = Math.floor(balance / rateAmount + 0.0001).toLong()
        val totalSeconds = fullBlocks * (rateMinutes * 60L)

        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            append("${minutes}m")
        }.trim()
    }

    /**
     * Checks if remaining time is under 30 minutes.
     */
    fun isLowBalance(balance: Double, rateAmount: Double, rateMinutes: Int): Boolean {
        if (balance < rateAmount || rateAmount <= 0.0) return true
        val fullBlocks = Math.floor(balance / rateAmount + 0.0001).toLong()
        val totalSeconds = fullBlocks * (rateMinutes * 60L)
        return totalSeconds < (30 * 60L)
    }

    /**
     * Calculates estimated exhaustion timestamp in milliseconds.
     * ONLY relevant when session is active!
     */
    fun calculateEstimatedExhaustionMs(config: ParkingConfig): Long? {
        if (!config.isSessionActive || config.rateAmount <= 0.0 || config.sessionInitialBalance <= 0.0) return null
        val totalBlocksAffordable = Math.floor(config.sessionInitialBalance / config.rateAmount + 0.0001).toLong()
        if (totalBlocksAffordable <= 0L) return null
        val affordableSeconds = totalBlocksAffordable * (config.rateMinutes * 60L)
        return config.sessionStartTimestamp + (affordableSeconds * 1000L)
    }

    /**
     * Catch-up session logic for background/suspend state.
     * Keeps session running continuously into negative balance (debt) without stopping.
     */
    suspend fun catchUpSession(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) {
            return@withContext false
        }

        val elapsed = max(0L, (now - config.sessionStartTimestamp) / 1000L)
        val blocks = calculateBlocks(elapsed, config.rateMinutes)
        val currentCost = blocks * config.rateAmount
        val currentBalance = config.sessionInitialBalance - currentCost

        dao.saveConfig(
            config.copy(
                balance = currentBalance,
                elapsedSeconds = elapsed,
                accumulatedCost = currentCost,
                lastProcessedTimestamp = now
            )
        )
        return@withContext false
    }

    /**
     * Advances time by 1 second during active session.
     * Keeps session running continuously into negative balance (debt) without stopping.
     */
    suspend fun advanceOneSecond(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) return@withContext false

        val elapsed = max(0L, (now - config.sessionStartTimestamp) / 1000L)
        val blocks = calculateBlocks(elapsed, config.rateMinutes)
        val currentCost = blocks * config.rateAmount
        val currentBalance = config.sessionInitialBalance - currentCost

        dao.saveConfig(
            config.copy(
                balance = currentBalance,
                elapsedSeconds = elapsed,
                accumulatedCost = currentCost,
                lastProcessedTimestamp = now
            )
        )
        return@withContext false
    }

    /**
     * Starts parking session.
     * Charges first block immediately ($rateAmount).
     */
    suspend fun startSession(
        now: Long = System.currentTimeMillis(),
        photoPath: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        // Must have at least enough balance for 1 full block
        if (config.balance < config.rateAmount || config.rateAmount <= 0.0) {
            return@withContext false
        }

        val initialBalance = config.balance
        val firstBlockCost = config.rateAmount
        val remainingBalance = initialBalance - firstBlockCost

        val updatedPhotoPath = if (photoPath.isNotBlank()) photoPath else config.lastSessionPhotoPath

        dao.saveConfig(
            config.copy(
                isSessionActive = true,
                sessionStartTimestamp = now,
                sessionInitialBalance = initialBalance,
                lastSessionPhotoPath = updatedPhotoPath,
                lastProcessedTimestamp = now,
                elapsedSeconds = 0L,
                accumulatedCost = firstBlockCost,
                balance = remainingBalance
            )
        )
        return@withContext true
    }

    /**
     * Stops session and charges full blocks.
     * Does not clamp to 0; remaining balance reflects negative debt if initial balance was exceeded.
     */
    suspend fun stopSession(now: Long = System.currentTimeMillis(), reason: String = "Finalizado por usuario") = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) return@withContext

        val elapsed = max(0L, (now - config.sessionStartTimestamp) / 1000L)
        val blocks = calculateBlocks(elapsed, config.rateMinutes)
        val finalCost = blocks * config.rateAmount
        val remainingBalance = config.sessionInitialBalance - finalCost

        dao.insertHistory(
            ParkingHistoryItem(
                startTimestamp = config.sessionStartTimestamp,
                endTimestamp = now,
                durationSeconds = max(1L, elapsed),
                costCharged = finalCost,
                reasonEnded = reason,
                photoPath = config.lastSessionPhotoPath,
                initialBalance = config.sessionInitialBalance
            )
        )

        dao.saveConfig(
            config.copy(
                balance = remainingBalance,
                isSessionActive = false,
                sessionStartTimestamp = 0L,
                sessionInitialBalance = 0.0,
                lastSessionEndTimestamp = now,
                lastSessionExitBalance = remainingBalance,
                lastSessionDurationSeconds = max(1L, elapsed),
                elapsedSeconds = 0L,
                accumulatedCost = 0.0,
                lastProcessedTimestamp = now,
                lastSessionPhotoPath = ""
            )
        )
    }

    /**
     * Resumes a finished session from history, restoring its start timestamp,
     * initial balance, and photo into the active session config.
     * Recalculates elapsed time, blocks, and current balance from entry time to now.
     * Deletes the resumed entry from history so it continues as the active session.
     */
    suspend fun resumeSessionFromHistory(
        item: ParkingHistoryItem,
        now: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()

        // If a session is currently active, stop and save it first
        if (config.isSessionActive) {
            stopSession(now = now, reason = "Finalizado para reanudar sesión anterior")
        }

        val baseConfig = getOrCreateConfig()
        dao.deleteHistoryItem(item.id)

        val effectiveStart = item.startTimestamp
        val effectiveInitialBalance = if (item.initialBalance > 0.0) {
            item.initialBalance
        } else {
            maxOf(item.costCharged, baseConfig.rateAmount)
        }
        val effectivePhotoPath = item.photoPath

        val elapsed = max(0L, (now - effectiveStart) / 1000L)
        val blocks = calculateBlocks(elapsed, baseConfig.rateMinutes)
        val currentCost = blocks * baseConfig.rateAmount
        val currentBalance = effectiveInitialBalance - currentCost

        dao.saveConfig(
            baseConfig.copy(
                isSessionActive = true,
                sessionStartTimestamp = effectiveStart,
                sessionInitialBalance = effectiveInitialBalance,
                lastSessionPhotoPath = effectivePhotoPath,
                elapsedSeconds = elapsed,
                accumulatedCost = currentCost,
                balance = currentBalance,
                lastProcessedTimestamp = now
            )
        )
        return@withContext true
    }

    /**
     * Deletes a session record permanently from parking history and deletes its
     * attached photo file if one exists.
     */
    suspend fun deleteHistoryRecord(item: ParkingHistoryItem) = withContext(Dispatchers.IO) {
        dao.deleteHistoryItem(item.id)
        if (item.photoPath.isNotBlank()) {
            com.example.util.PhotoStorageManager.deletePhoto(item.photoPath)
        }
    }

    /**
     * Modifies the active session's entry timestamp AND initial balance,
     * automatically recalculating all costs, elapsed time and remaining balance without stopping.
     */
    suspend fun updateActiveSession(
        newStartTimestamp: Long,
        newInitialBalance: Double,
        now: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) return@withContext false

        val validStart = if (newStartTimestamp > now) now else newStartTimestamp
        val elapsed = max(0L, (now - validStart) / 1000L)
        val blocks = calculateBlocks(elapsed, config.rateMinutes)
        val cost = blocks * config.rateAmount
        val newRemainingBalance = newInitialBalance - cost

        dao.saveConfig(
            config.copy(
                balance = newRemainingBalance,
                sessionStartTimestamp = validStart,
                sessionInitialBalance = newInitialBalance,
                elapsedSeconds = elapsed,
                accumulatedCost = cost,
                lastProcessedTimestamp = now
            )
        )
        return@withContext false
    }

    suspend fun rechargeBalance(amount: Double, note: String = "Recarga manual") = withContext(Dispatchers.IO) {
        if (amount <= 0.0) return@withContext
        addRechargeRecord(amount = amount, receiptPhotoPath = "", note = note)
    }

    suspend fun addRechargeRecord(
        amount: Double,
        receiptPhotoPath: String = "",
        note: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        if (amount <= 0.0) return@withContext -1L
        val item = RechargeHistoryItem(
            amount = amount,
            timestamp = timestamp,
            receiptPhotoPath = receiptPhotoPath,
            note = note.trim()
        )
        val id = dao.insertRecharge(item)
        val config = getOrCreateConfig()
        val newBalance = config.balance + amount
        val newInitial = if (config.isSessionActive) config.sessionInitialBalance + amount else config.sessionInitialBalance
        dao.saveConfig(
            config.copy(
                balance = newBalance,
                sessionInitialBalance = newInitial
            )
        )
        id
    }

    suspend fun updateRechargeRecord(
        item: RechargeHistoryItem,
        newAmount: Double,
        newNote: String,
        newTimestamp: Long
    ) = withContext(Dispatchers.IO) {
        val diff = newAmount - item.amount
        val updatedItem = item.copy(
            amount = newAmount,
            note = newNote.trim(),
            timestamp = newTimestamp
        )
        dao.updateRecharge(updatedItem)

        val config = getOrCreateConfig()
        val newBalance = config.balance + diff
        val newInitial = if (config.isSessionActive) config.sessionInitialBalance + diff else config.sessionInitialBalance
        dao.saveConfig(
            config.copy(
                balance = newBalance,
                sessionInitialBalance = newInitial
            )
        )
    }

    suspend fun deleteRechargeRecord(item: RechargeHistoryItem) = withContext(Dispatchers.IO) {
        dao.deleteRecharge(item.id)
        if (item.receiptPhotoPath.isNotBlank()) {
            com.example.util.ReceiptStorageUtil.deleteReceiptFile(item.receiptPhotoPath)
        }
        val config = getOrCreateConfig()
        val newBalance = config.balance - item.amount
        val newInitial = if (config.isSessionActive) config.sessionInitialBalance - item.amount else config.sessionInitialBalance
        dao.saveConfig(
            config.copy(
                balance = newBalance,
                sessionInitialBalance = newInitial
            )
        )
    }

    suspend fun setBalance(amount: Double) = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        val validBalance = amount.coerceAtLeast(0.0)
        dao.saveConfig(
            config.copy(
                balance = validBalance,
                sessionInitialBalance = validBalance
            )
        )
    }

    suspend fun resetBalance() = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (config.isSessionActive) {
            stopSession(reason = "Reinicio de saldo")
        }
        val refreshed = getOrCreateConfig()
        dao.saveConfig(
            refreshed.copy(
                balance = 0.0,
                sessionInitialBalance = 0.0
            )
        )
    }

    /**
     * Resets everything: clears all history records AND recharge records, and sets balance to $0.00.
     */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
        dao.clearAllRecharges()
        val config = getOrCreateConfig()
        dao.saveConfig(
            config.copy(
                balance = 0.0,
                isSessionActive = false,
                sessionStartTimestamp = 0L,
                sessionInitialBalance = 0.0,
                elapsedSeconds = 0L,
                accumulatedCost = 0.0,
                lastSessionPhotoPath = ""
            )
        )
    }

    suspend fun restoreAllData(
        restoredConfig: ParkingConfig,
        parkingHistory: List<ParkingHistoryItem>,
        rechargeHistory: List<RechargeHistoryItem>
    ) = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
        dao.clearAllRecharges()

        for (item in parkingHistory) {
            dao.insertHistory(item)
        }
        for (recharge in rechargeHistory) {
            dao.insertRecharge(recharge)
        }
        dao.saveConfig(restoredConfig)
    }

    suspend fun updateTariff(amount: Double, minutes: Int) = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        dao.saveConfig(
            config.copy(
                rateAmount = amount,
                rateMinutes = max(1, minutes)
            )
        )
    }

    suspend fun updateNotificationSettings(enabled: Boolean, intervalMinutes: Int) = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        dao.saveConfig(
            config.copy(
                notificationsEnabled = enabled,
                notificationIntervalMinutes = max(1, intervalMinutes)
            )
        )
    }

    suspend fun updateTileLabel(label: String) = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        val cleanLabel = if (label.isBlank()) "Parkia" else label.trim()
        dao.saveConfig(
            config.copy(
                tileLabel = cleanLabel
            )
        )
    }

    suspend fun updateTariffAndNotifications(
        amount: Double,
        minutes: Int,
        notifyEnabled: Boolean,
        notifyInterval: Int,
        tileLabel: String,
        geminiApiKey: String,
        geminiModel: String,
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
    ) = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        val cleanLabel = if (tileLabel.isBlank()) "Parkia" else tileLabel.trim()
        dao.saveConfig(
            config.copy(
                rateAmount = amount,
                rateMinutes = max(1, minutes),
                notificationsEnabled = notifyEnabled,
                notificationIntervalMinutes = max(1, notifyInterval),
                tileLabel = cleanLabel,
                customGeminiApiKey = geminiApiKey.trim(),
                customGeminiModel = if (geminiModel.isBlank()) "gemini-2.0-flash" else geminiModel.trim(),
                isGeminiEnabled = isGeminiEnabled,
                quickTileBiometricEnabled = quickTileBiometricEnabled,
                globalSecurityAuthEnabled = globalSecurityAuthEnabled,
                ttsAnnouncementsEnabled = ttsAnnouncementsEnabled,
                geofenceEnabled = geofenceEnabled,
                geofenceLatitude = geofenceLatitude,
                geofenceLongitude = geofenceLongitude,
                geofenceRadiusMeters = geofenceRadiusMeters,
                registeredNfcTagId = registeredNfcTagId.trim(),
                nfcStrictMatchingEnabled = nfcStrictMatchingEnabled
            )
        )
    }
}
