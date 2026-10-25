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
}
