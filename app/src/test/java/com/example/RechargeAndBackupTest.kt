package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ParkingHistoryItem
import com.example.data.ParkingRepository
import com.example.data.RechargeHistoryItem
import com.example.util.BackupUtil
import com.example.util.VisionAiParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RechargeAndBackupTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ParkingRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ParkingRepository(db.parkingDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAddRechargeUpdatesBalanceAndCreatesHistoryItem() = runBlocking {
        val initialConfig = repository.getOrCreateConfig()
        assertEquals(0.0, initialConfig.balance, 0.001)

        val rechargeId = repository.addRechargeRecord(
            amount = 25.0,
            receiptPhotoPath = "receipt_test.jpg",
            note = "Recarga con comprobante"
        )
        assertTrue(rechargeId > 0)

        val updatedConfig = repository.getOrCreateConfig()
        assertEquals(25.0, updatedConfig.balance, 0.001)

        val list = repository.rechargeHistoryFlow.first()
        assertEquals(1, list.size)
        assertEquals(25.0, list[0].amount, 0.001)
        assertEquals("receipt_test.jpg", list[0].receiptPhotoPath)
        assertEquals("Recarga con comprobante", list[0].note)
    }

    @Test
    fun testEditRechargeAdjustsBalanceByDifference() = runBlocking {
        repository.addRechargeRecord(amount = 20.0, receiptPhotoPath = "", note = "Original")
        var config = repository.getOrCreateConfig()
        assertEquals(20.0, config.balance, 0.001)

        val item = repository.rechargeHistoryFlow.first().first()
        repository.updateRechargeRecord(item, newAmount = 35.0, newNote = "Editada a 35", newTimestamp = item.timestamp)

        config = repository.getOrCreateConfig()
        assertEquals(35.0, config.balance, 0.001)

        val updatedList = repository.rechargeHistoryFlow.first()
        assertEquals(1, updatedList.size)
        assertEquals(35.0, updatedList[0].amount, 0.001)
        assertEquals("Editada a 35", updatedList[0].note)
    }

    @Test
    fun testDeleteRechargeDeductsAmountFromBalance() = runBlocking {
        repository.addRechargeRecord(amount = 50.0, receiptPhotoPath = "", note = "Para borrar")
        var config = repository.getOrCreateConfig()
        assertEquals(50.0, config.balance, 0.001)

        val item = repository.rechargeHistoryFlow.first().first()
        repository.deleteRechargeRecord(item)

        config = repository.getOrCreateConfig()
        assertEquals(0.0, config.balance, 0.001)

        val list = repository.rechargeHistoryFlow.first()
        assertTrue(list.isEmpty())
    }

    @Test
    fun testBackupExportAndRestore() = runBlocking {
        val config = repository.getOrCreateConfig().copy(
            balance = 42.50,
            rateAmount = 3.50,
            rateMinutes = 30
        )
        db.parkingDao().saveConfig(config)

        val session = ParkingHistoryItem(
            startTimestamp = 1000L,
            endTimestamp = 2800L,
            durationSeconds = 1800L,
            costCharged = 7.0,
            reasonEnded = "Finalizado por usuario",
            photoPath = "",
            initialBalance = 50.0
        )
        db.parkingDao().insertHistory(session)

        val recharge = RechargeHistoryItem(
            amount = 50.0,
            timestamp = 900L,
            receiptPhotoPath = "",
            note = "Recarga backup"
        )
        db.parkingDao().insertRecharge(recharge)

        val outStream = ByteArrayOutputStream()
        BackupUtil.exportFullBackup(
            context = context,
            config = config,
            parkingHistory = repository.getAllHistoryList(),
            rechargeHistory = repository.getAllRechargesList(),
            outputStream = outStream
        )
        assertTrue(outStream.size() > 0)

        // Restore into an empty database
        db.parkingDao().clearAllHistory()
        db.parkingDao().clearAllRecharges()
        repository.resetAll()

        assertEquals(0.0, repository.getOrCreateConfig().balance, 0.001)
        assertTrue(repository.getAllHistoryList().isEmpty())
        assertTrue(repository.getAllRechargesList().isEmpty())

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val parsed = BackupUtil.restoreFullBackup(context, inStream)

        repository.restoreAllData(
            restoredConfig = parsed.config,
            parkingHistory = parsed.parkingHistory,
            rechargeHistory = parsed.rechargeHistory
        )

        val restoredConfig = repository.getOrCreateConfig()
        assertEquals(42.50, restoredConfig.balance, 0.001)
        assertEquals(3.50, restoredConfig.rateAmount, 0.001)
        assertEquals(30, restoredConfig.rateMinutes)

        val restoredSessions = repository.getAllHistoryList()
        assertEquals(1, restoredSessions.size)
        assertEquals(7.0, restoredSessions[0].costCharged, 0.001)

        val restoredRecharges = repository.getAllRechargesList()
        assertEquals(1, restoredRecharges.size)
        assertEquals(50.0, restoredRecharges[0].amount, 0.001)
    }

    @Test
    fun testReceiptRegexAmountExtraction() {
        val rawOcrSample1 = "BANCO DE PAGO\nFECHA: 24/10/2026\nTOTAL A PAGAR: $ 18.75 USD\nGRACIAS"
        val rawOcrSample2 = "ESTACIONAMIENTOS PARKIA\nIMPORTE TOTAL: $ 120.50\nTRANSACCION EXITOSA"

        val res1 = VisionAiParser.parseExtractedText(rawOcrSample1)
        assertEquals(18.75, res1.balance ?: 0.0, 0.01)

        val res2 = VisionAiParser.parseExtractedText(rawOcrSample2)
        assertEquals(120.50, res2.balance ?: 0.0, 0.01)
    }

    @Test
    fun testBackupExportAndRestoreAllConfigurationParameters() = runBlocking {
        // Guardar PIN y configuraciones de seguridad previas
        com.example.util.SecurityManager.savePin(context, "8921")
        com.example.util.SecurityManager.setQuickTileBiometricEnabled(context, false)
        com.example.util.SecurityManager.setGlobalAuthEnabled(context, true)

        val fullConfig = repository.getOrCreateConfig().copy(
            balance = 15.75,
            rateAmount = 0.50,
            rateMinutes = 45,
            notificationsEnabled = false,
            notificationIntervalMinutes = 15,
            tileLabel = "Mi Cochera",
            customGeminiApiKey = "AIzaSySecretApiKey12345",
            customGeminiModel = "gemini-2.0-flash",
            isGeminiEnabled = true,
            quickTileBiometricEnabled = false,
            globalSecurityAuthEnabled = true,
            ttsAnnouncementsEnabled = false,
            geofenceEnabled = true,
            geofenceLatitude = -2.189412,
            geofenceLongitude = -79.889064,
            geofenceRadiusMeters = 250f,
            registeredNfcTagId = "04:D3:55:A1",
            nfcStrictMatchingEnabled = true
        )
        db.parkingDao().saveConfig(fullConfig)

        val outStream = ByteArrayOutputStream()
        BackupUtil.exportFullBackup(
            context = context,
            config = fullConfig,
            parkingHistory = emptyList(),
            rechargeHistory = emptyList(),
            outputStream = outStream
        )
        assertTrue(outStream.size() > 0)

        // Limpiar todas las configuraciones actuales y PIN
        db.parkingDao().clearAllHistory()
        db.parkingDao().clearAllRecharges()
        db.parkingDao().saveConfig(com.example.data.ParkingConfig(id = 1))
        context.getSharedPreferences("parkia_security_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        // Verificar que quedó reseteado a valores por defecto
        val blankConfig = repository.getOrCreateConfig()
        assertEquals(0.10, blankConfig.rateAmount, 0.001)
        assertEquals(30, blankConfig.rateMinutes)
        assertEquals("", blankConfig.customGeminiApiKey)
        assertTrue(com.example.util.SecurityManager.verifyPin(context, "8921") == false)

        // Restaurar respaldo completo
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val parsed = BackupUtil.restoreFullBackup(context, inStream)

        repository.restoreAllData(
            restoredConfig = parsed.config,
            parkingHistory = parsed.parkingHistory,
            rechargeHistory = parsed.rechargeHistory
        )

        // Verificar que absolutamente todos los parámetros de configuración se restauraron
        val restored = repository.getOrCreateConfig()
        assertEquals(15.75, restored.balance, 0.001)
        assertEquals(0.50, restored.rateAmount, 0.001)
        assertEquals(45, restored.rateMinutes)
        assertEquals(false, restored.notificationsEnabled)
        assertEquals(15, restored.notificationIntervalMinutes)
        assertEquals("Mi Cochera", restored.tileLabel)
        assertEquals("AIzaSySecretApiKey12345", restored.customGeminiApiKey)
        assertEquals("gemini-2.0-flash", restored.customGeminiModel)
        assertEquals(true, restored.isGeminiEnabled)
        assertEquals(false, restored.quickTileBiometricEnabled)
        assertEquals(true, restored.globalSecurityAuthEnabled)
        assertEquals(false, restored.ttsAnnouncementsEnabled)
        assertEquals(true, restored.geofenceEnabled)
        assertEquals(-2.189412, restored.geofenceLatitude, 0.000001)
        assertEquals(-79.889064, restored.geofenceLongitude, 0.000001)
        assertEquals(250f, restored.geofenceRadiusMeters, 0.01f)
        assertEquals("04:D3:55:A1", restored.registeredNfcTagId)
        assertEquals(true, restored.nfcStrictMatchingEnabled)

        // Verificar que las credenciales de seguridad y PIN también se restauraron
        assertTrue(com.example.util.SecurityManager.isPinConfigured(context))
        assertTrue(com.example.util.SecurityManager.verifyPin(context, "8921"))
        assertEquals(false, com.example.util.SecurityManager.isQuickTileBiometricEnabled(context))
        assertEquals(true, com.example.util.SecurityManager.isGlobalAuthEnabled(context))
    }
}
