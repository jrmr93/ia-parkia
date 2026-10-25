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

    @Test
    fun testDeleteHistoryRecordRemovesRecordAndAssociatedPhoto(): Unit = runBlocking {
        // 1. Crear foto física en la carpeta de fotos
        val photosDir = java.io.File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val testPhotoFile = java.io.File(photosDir, "test_session_photo_delete.jpg")
        testPhotoFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertTrue(testPhotoFile.exists())

        // 2. Insertar sesión de parqueo con la foto
        val item = ParkingHistoryItem(
            startTimestamp = 1000L,
            endTimestamp = 3000L,
            durationSeconds = 2000L,
            costCharged = 5.0,
            reasonEnded = "Finalizado por usuario",
            photoPath = testPhotoFile.absolutePath,
            initialBalance = 10.0
        )
        db.parkingDao().insertHistory(item)
        val savedList = repository.getAllHistoryList()
        assertEquals(1, savedList.size)
        val savedItem = savedList[0]

        // 3. Eliminar el registro
        repository.deleteHistoryRecord(savedItem)

        // 4. Verificar que se borró de la base de datos y que el archivo físico fue eliminado
        val remainingList = repository.getAllHistoryList()
        assertTrue(remainingList.isEmpty())
        assertTrue(!testPhotoFile.exists())
    }

    @Test
    fun testFullBackupAndRestoreWithPhysicalPhotosAndReceipts(): Unit = runBlocking {
        // 1. Crear una foto física de sesión
        val photosDir = java.io.File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val sessionPhoto = java.io.File(photosDir, "session_entry_sample.jpg")
        sessionPhoto.writeBytes("FakeSessionPhotoContent".toByteArray())

        // 2. Crear un comprobante físico de recarga
        val receiptsDir = com.example.util.ReceiptStorageUtil.getReceiptsDir(context)
        val receiptPhoto = java.io.File(receiptsDir, "receipt_ticket_sample.jpg")
        receiptPhoto.writeBytes("FakeReceiptPhotoContent".toByteArray())

        // 3. Crear registros en la BD
        val sessionItem = ParkingHistoryItem(
            startTimestamp = 5000L,
            endTimestamp = 8600L,
            durationSeconds = 3600L,
            costCharged = 2.50,
            reasonEnded = "Finalizado por usuario",
            photoPath = sessionPhoto.absolutePath,
            initialBalance = 20.0
        )
        db.parkingDao().insertHistory(sessionItem)

        val rechargeItem = RechargeHistoryItem(
            amount = 30.0,
            timestamp = 4000L,
            receiptPhotoPath = receiptPhoto.absolutePath,
            note = "Recarga con ticket"
        )
        db.parkingDao().insertRecharge(rechargeItem)

        val config = repository.getOrCreateConfig().copy(balance = 27.50)
        db.parkingDao().saveConfig(config)

        // 4. Exportar respaldo completo
        val outStream = ByteArrayOutputStream()
        BackupUtil.exportFullBackup(
            context = context,
            config = config,
            parkingHistory = repository.getAllHistoryList(),
            rechargeHistory = repository.getAllRechargesList(),
            outputStream = outStream
        )
        assertTrue(outStream.size() > 0)

        // 5. Borrar archivos físicos y limpiar la base de datos para simular nuevo dispositivo
        sessionPhoto.delete()
        receiptPhoto.delete()
        assertTrue(!sessionPhoto.exists())
        assertTrue(!receiptPhoto.exists())

        db.parkingDao().clearAllHistory()
        db.parkingDao().clearAllRecharges()
        repository.resetAll()
        assertTrue(repository.getAllHistoryList().isEmpty())
        assertTrue(repository.getAllRechargesList().isEmpty())

        // 6. Restaurar desde el flujo ZIP
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val parsed = BackupUtil.restoreFullBackup(context, inStream)

        assertEquals(1, parsed.photosCount)
        assertEquals(1, parsed.receiptsCount)

        repository.restoreAllData(
            restoredConfig = parsed.config,
            parkingHistory = parsed.parkingHistory,
            rechargeHistory = parsed.rechargeHistory
        )

        // 7. Verificar datos y archivos restaurados
        val restoredSessions = repository.getAllHistoryList()
        assertEquals(1, restoredSessions.size)
        assertEquals(2.50, restoredSessions[0].costCharged, 0.001)
        val restoredPhotoFile = java.io.File(restoredSessions[0].photoPath)
        assertTrue(restoredPhotoFile.exists())
        assertEquals("FakeSessionPhotoContent", restoredPhotoFile.readText())

        val restoredRecharges = repository.getAllRechargesList()
        assertEquals(1, restoredRecharges.size)
        assertEquals(30.0, restoredRecharges[0].amount, 0.001)
        val restoredReceiptFile = java.io.File(restoredRecharges[0].receiptPhotoPath)
        assertTrue(restoredReceiptFile.exists())
        assertEquals("FakeReceiptPhotoContent", restoredReceiptFile.readText())

        val restoredConfig = repository.getOrCreateConfig()
        assertEquals(27.50, restoredConfig.balance, 0.001)

        // Limpieza de archivos de prueba
        restoredPhotoFile.delete()
        restoredReceiptFile.delete()
    }

    @Test
    fun testDeleteSessionRecordDoesNotAffectSubsequentBackupAndRestore(): Unit = runBlocking {
        // Insertar 2 sesiones
        val session1 = ParkingHistoryItem(
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            durationSeconds = 1000L,
            costCharged = 3.0,
            reasonEnded = "Finalizado por usuario",
            initialBalance = 10.0
        )
        val session2 = ParkingHistoryItem(
            startTimestamp = 3000L,
            endTimestamp = 5000L,
            durationSeconds = 2000L,
            costCharged = 4.0,
            reasonEnded = "Saldo Agotado",
            initialBalance = 7.0
        )
        db.parkingDao().insertHistory(session1)
        db.parkingDao().insertHistory(session2)

        val listBefore = repository.getAllHistoryList()
        assertEquals(2, listBefore.size)

        // Eliminar session1
        val itemToDelete = listBefore.first { it.costCharged == 3.0 }
        repository.deleteHistoryRecord(itemToDelete)

        val listAfter = repository.getAllHistoryList()
        assertEquals(1, listAfter.size)
        assertEquals(4.0, listAfter[0].costCharged, 0.001)

        // Exportar y restaurar
        val config = repository.getOrCreateConfig().copy(balance = 3.0)
        val outStream = ByteArrayOutputStream()
        BackupUtil.exportFullBackup(
            context = context,
            config = config,
            parkingHistory = listAfter,
            rechargeHistory = emptyList(),
            outputStream = outStream
        )

        // Limpiar DB
        db.parkingDao().clearAllHistory()
        assertTrue(repository.getAllHistoryList().isEmpty())

        // Restaurar
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val parsed = BackupUtil.restoreFullBackup(context, inStream)
        repository.restoreAllData(parsed.config, parsed.parkingHistory, parsed.rechargeHistory)

        val finalSessions = repository.getAllHistoryList()
        assertEquals(1, finalSessions.size)
        assertEquals(4.0, finalSessions[0].costCharged, 0.001)
        assertEquals("Saldo Agotado", finalSessions[0].reasonEnded)
    }
}
