package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parking_config")
data class ParkingConfig(
    @PrimaryKey
    val id: Int = 1,
    val balance: Double = 0.0,
    val rateAmount: Double = 0.10, // $0.10 USD
    val rateMinutes: Int = 30,     // por cada 30 minutos
    val isSessionActive: Boolean = false,
    val sessionStartTimestamp: Long = 0L,
    val sessionInitialBalance: Double = 0.0, // Saldo al momento de iniciar la sesión
    val lastProcessedTimestamp: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val accumulatedCost: Double = 0.0,
    val notificationsEnabled: Boolean = true,
    val notificationIntervalMinutes: Int = 1,
    val tileLabel: String = "Parkia",
    val customGeminiApiKey: String = "",
    val customGeminiModel: String = "gemini-2.0-flash",
    val isGeminiEnabled: Boolean = true,
    val quickTileBiometricEnabled: Boolean = true,
    val globalSecurityAuthEnabled: Boolean = true,
    val ttsAnnouncementsEnabled: Boolean = true,
    val geofenceEnabled: Boolean = false,
    val geofenceLatitude: Double = 0.0,
    val geofenceLongitude: Double = 0.0,
    val geofenceRadiusMeters: Float = 100f,
    val registeredNfcTagId: String = "",
    val nfcStrictMatchingEnabled: Boolean = false
)

@Entity(tableName = "parking_history")
data class ParkingHistoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationSeconds: Long,
    val costCharged: Double,
    val reasonEnded: String // "Finalizado por usuario" o "Saldo Agotado"
)
