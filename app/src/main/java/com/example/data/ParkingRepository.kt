package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.max

class ParkingRepository(private val dao: ParkingDao) {

    val configFlow: Flow<ParkingConfig?> = dao.getConfigFlow()
    val historyFlow: Flow<List<ParkingHistoryItem>> = dao.getAllHistory()

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
    fun calculateAvailableTime(balance: Double, rateAmount: Double, rateMinutes: Int): String {
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
        if (!config.isSessionActive || config.rateAmount <= 0.0) return null
        val totalBlocksAffordable = Math.floor(config.sessionInitialBalance / config.rateAmount + 0.0001).toLong()
        if (totalBlocksAffordable <= 0L) return null
        val affordableSeconds = totalBlocksAffordable * (config.rateMinutes * 60L)
        return config.sessionStartTimestamp + (affordableSeconds * 1000L)
    }

    /**
     * Catch-up session logic for background/suspend state.
     * Returns true if session ended due to balance exhaustion.
     */
    suspend fun catchUpSession(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) {
            return@withContext false
        }

        val elapsed = max(0L, (now - config.sessionStartTimestamp) / 1000L)
        val blockDuration = (config.rateMinutes * 60L).coerceAtLeast(60L)
        val maxBlocks = Math.floor(config.sessionInitialBalance / config.rateAmount + 0.0001).toLong()
        val maxDurationSeconds = maxBlocks * blockDuration

        if (maxBlocks <= 0 || elapsed >= maxDurationSeconds) {
            // Balance depleted!
            val actualEndTimestamp = config.sessionStartTimestamp + (maxDurationSeconds * 1000L)
            val costCharged = maxBlocks * config.rateAmount
            val remainingBalance = max(0.0, config.sessionInitialBalance - costCharged)

            dao.insertHistory(
                ParkingHistoryItem(
                    startTimestamp = config.sessionStartTimestamp,
                    endTimestamp = actualEndTimestamp,
                    durationSeconds = max(1L, maxDurationSeconds),
                    costCharged = costCharged,
                    reasonEnded = "Saldo Agotado"
                )
            )

            dao.saveConfig(
                config.copy(
                    balance = remainingBalance,
                    isSessionActive = false,
                    lastProcessedTimestamp = now,
                    elapsedSeconds = 0L,
                    accumulatedCost = 0.0
                )
            )
            return@withContext true
        } else {
            val blocks = calculateBlocks(elapsed, config.rateMinutes)
            val currentCost = blocks * config.rateAmount
            val currentBalance = max(0.0, config.sessionInitialBalance - currentCost)

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
    }

    /**
     * Advances time by 1 second during active session.
     */
    suspend fun advanceOneSecond(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) return@withContext false

        val elapsed = max(0L, (now - config.sessionStartTimestamp) / 1000L)
        val blockDuration = (config.rateMinutes * 60L).coerceAtLeast(60L)
        val maxBlocks = Math.floor(config.sessionInitialBalance / config.rateAmount + 0.0001).toLong()
        val maxDurationSeconds = maxBlocks * blockDuration

        if (maxBlocks <= 0 || elapsed >= maxDurationSeconds) {
            val actualEndTimestamp = config.sessionStartTimestamp + (maxDurationSeconds * 1000L)
            val costCharged = maxBlocks * config.rateAmount
            val remainingBalance = max(0.0, config.sessionInitialBalance - costCharged)

            dao.insertHistory(
                ParkingHistoryItem(
                    startTimestamp = config.sessionStartTimestamp,
                    endTimestamp = actualEndTimestamp,
                    durationSeconds = max(1L, maxDurationSeconds),
                    costCharged = costCharged,
                    reasonEnded = "Saldo Agotado"
                )
            )

            dao.saveConfig(
                config.copy(
                    balance = remainingBalance,
                    isSessionActive = false,
                    lastProcessedTimestamp = now,
                    elapsedSeconds = 0L,
                    accumulatedCost = 0.0
                )
            )
            return@withContext true
        } else {
            val blocks = calculateBlocks(elapsed, config.rateMinutes)
            val currentCost = blocks * config.rateAmount
            val currentBalance = max(0.0, config.sessionInitialBalance - currentCost)

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
    }

    /**
     * Starts parking session.
     * Charges first block immediately ($rateAmount).
     */
    suspend fun startSession(now: Long = System.currentTimeMillis()): Boolean = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        // Must have at least enough balance for 1 full block
        if (config.balance < config.rateAmount || config.rateAmount <= 0.0) {
            return@withContext false
        }

        val initialBalance = config.balance
        val firstBlockCost = config.rateAmount
        val remainingBalance = max(0.0, initialBalance - firstBlockCost)

        dao.saveConfig(
            config.copy(
                isSessionActive = true,
                sessionStartTimestamp = now,
                sessionInitialBalance = initialBalance,
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
     */
    suspend fun stopSession(now: Long = System.currentTimeMillis(), reason: String = "Finalizado por usuario") = withContext(Dispatchers.IO) {
        val config = getOrCreateConfig()
        if (!config.isSessionActive) return@withContext

        val elapsed = max(0L, (now - config.sessionStartTimestamp) / 1000L)
        val blocks = calculateBlocks(elapsed, config.rateMinutes)
        val finalCost = blocks * config.rateAmount
        val remainingBalance = max(0.0, config.sessionInitialBalance - finalCost)

        dao.insertHistory(
            ParkingHistoryItem(
                startTimestamp = config.sessionStartTimestamp,
                endTimestamp = now,
                durationSeconds = max(1L, elapsed),
                costCharged = finalCost,
                reasonEnded = reason
            )
        )

        dao.saveConfig(
            config.copy(
                balance = remainingBalance,
                isSessionActive = false,
                sessionStartTimestamp = 0L,
                sessionInitialBalance = 0.0,
                elapsedSeconds = 0L,
                accumulatedCost = 0.0,
                lastProcessedTimestamp = now
            )
        )
    }

    /**
     * Modifies the active session's entry timestamp AND initial balance,
     * automatically recalculating all costs, elapsed time and remaining balance.
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
        val blockDuration = (config.rateMinutes * 60L).coerceAtLeast(60L)
        val maxBlocks = Math.floor(newInitialBalance / config.rateAmount + 0.0001).toLong()
        val maxDurationSeconds = maxBlocks * blockDuration

        if (maxBlocks <= 0 || elapsed >= maxDurationSeconds) {
            // Depleted with the new initial balance / time
            val actualEndTimestamp = validStart + (maxDurationSeconds * 1000L)
            val costCharged = maxBlocks * config.rateAmount
            val remainingBalance = max(0.0, newInitialBalance - costCharged)

            dao.insertHistory(
                ParkingHistoryItem(
                    startTimestamp = validStart,
                    endTimestamp = actualEndTimestamp,
                    durationSeconds = max(1L, maxDurationSeconds),
                    costCharged = costCharged,
                    reasonEnded = "Saldo Agotado"
                )
            )

            dao.saveConfig(
                config.copy(
                    balance = remainingBalance,
                    isSessionActive = false,
                    sessionStartTimestamp = 0L,
                    sessionInitialBalance = 0.0,
                    lastProcessedTimestamp = now,
                    elapsedSeconds = 0L,
                    accumulatedCost = 0.0
                )
            )
            return@withContext true
        } else {
            val blocks = calculateBlocks(elapsed, config.rateMinutes)
            val cost = blocks * config.rateAmount
            val newRemainingBalance = max(0.0, newInitialBalance - cost)

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
    }

    suspend fun rechargeBalance(amount: Double) = withContext(Dispatchers.IO) {
        if (amount <= 0.0) return@withContext
        val config = getOrCreateConfig()
        val newBalance = config.balance + amount
        val newInitial = if (config.isSessionActive) config.sessionInitialBalance + amount else config.sessionInitialBalance
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
     * Resets everything: clears all history records AND sets balance to $0.00.
     */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
        val config = getOrCreateConfig()
        dao.saveConfig(
            config.copy(
                balance = 0.0,
                isSessionActive = false,
                sessionStartTimestamp = 0L,
                sessionInitialBalance = 0.0,
                elapsedSeconds = 0L,
                accumulatedCost = 0.0
            )
        )
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
        geofenceRadiusMeters: Float = 100f
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
                geofenceRadiusMeters = geofenceRadiusMeters
            )
        )
    }
}
