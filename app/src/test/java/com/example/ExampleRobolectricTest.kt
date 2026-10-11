package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ParkingHistoryItem
import com.example.data.ParkingRepository
import com.example.util.NotificationHelper
import com.example.util.SecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ParkingRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
    fun `verifies brand identity Parkia and attribution strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val appSubtitle = context.getString(R.string.app_subtitle)
        val appCreator = context.getString(R.string.app_creator)

        assertEquals("Parkia", appName)
        assertEquals("Control de Parqueo Inteligente", appSubtitle)
        assertEquals("Creado por Justo Maldonado Ruiz", appCreator)
    }

    @Test
    fun `security manager pin setup and verification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Save PIN
        SecurityManager.savePin(context, "1234")
        assertTrue(SecurityManager.isPinConfigured(context))
        assertTrue(SecurityManager.verifyPin(context, "1234"))
        assertFalse(SecurityManager.verifyPin(context, "9999"))
        assertFalse(SecurityManager.verifyPin(context, "123"))
    }

    @Test
    fun `notification channel creates without error`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.showBalanceNotification(context, 5.0, false, "06/10/2026 12:00:00")
    }

    @Test
    fun `block calculation requires full block minimum`() {
        // Rate: 30 minutes block (1800 seconds)
        // 0 seconds -> 1 block
        assertEquals(1L, repository.calculateBlocks(0L, 30))
        // 10 seconds -> 1 block
        assertEquals(1L, repository.calculateBlocks(10L, 30))
        // 1800 seconds (30m) -> 1 block
        assertEquals(1L, repository.calculateBlocks(1800L, 30))
        // 1801 seconds (30m 1s) -> 2 blocks
        assertEquals(2L, repository.calculateBlocks(1801L, 30))
    }

    @Test
    fun `session stopped before block finishes still charges full block`() = runBlocking {
        // Balance = $1.00, rate = $0.10 for 30 min
        repository.rechargeBalance(1.00)
        val startTime = 1000000L
        repository.startSession(startTime)

        // Session stops after only 5 minutes (300 seconds)
        val stopTime = startTime + (300 * 1000L)
        repository.stopSession(stopTime, "Finalizado por usuario")

        val history = repository.historyFlow.first()
        assertEquals(1, history.size)
        // Full block cost ($0.10) must be charged
        assertEquals(0.10, history[0].costCharged, 0.001)
        assertEquals(300L, history[0].durationSeconds)

        // Remaining balance: 1.00 - 0.10 = 0.90
        val config = repository.getOrCreateConfig()
        assertFalse(config.isSessionActive)
        assertEquals(0.90, config.balance, 0.001)
    }

    @Test
    fun `editing active session with initial balance and entry time recalculates correctly`() = runBlocking {
        // Start session with $0.50 balance at time 'now'
        repository.rechargeBalance(0.50)
        val now = 10000000L
        repository.startSession(now)

        // Edit session: entered 45 minutes ago with $2.00 initial balance
        // 45 minutes = 2 blocks of 30 min (cost = $0.20)
        val entered45MinAgo = now - (45 * 60 * 1000L)
        val depleted = repository.updateActiveSession(entered45MinAgo, 2.00, now)

        assertFalse("Balance should not be depleted", depleted)
        val config = repository.getOrCreateConfig()
        assertTrue(config.isSessionActive)
        assertEquals(2.00, config.sessionInitialBalance, 0.001)
        assertEquals(0.20, config.accumulatedCost, 0.001)
        assertEquals(1.80, config.balance, 0.001)
        assertEquals(2700L, config.elapsedSeconds)
    }

    @Test
    fun `resetAll clears history and sets balance to zero`() = runBlocking {
        repository.rechargeBalance(10.00)
        repository.startSession()
        repository.stopSession()

        val historyBefore = repository.historyFlow.first()
        assertEquals(1, historyBefore.size)
        assertTrue(repository.getOrCreateConfig().balance > 0.0)

        // ResetAll
        repository.resetAll()

        val historyAfter = repository.historyFlow.first()
        assertEquals(0, historyAfter.size)
        val configAfter = repository.getOrCreateConfig()
        assertEquals(0.00, configAfter.balance, 0.001)
        assertFalse(configAfter.isSessionActive)
    }

    @Test
    fun `cannot start session with balance less than one block`() = runBlocking {
        // Rate is $0.10 for 30m, balance is only $0.05
        repository.rechargeBalance(0.05)
        val started = repository.startSession()
        assertFalse("Should not start session without enough balance for 1 block", started)
    }

    @Test
    fun `estimated exhaustion calculated only when session is active`() = runBlocking {
        repository.rechargeBalance(0.30)
        val now = 1000000L

        // Inactive -> null
        val configInactive = repository.getOrCreateConfig()
        assertNull(repository.calculateEstimatedExhaustionMs(configInactive))

        // Active: $0.30 / $0.10 = 3 blocks = 90 minutes (5400 seconds)
        repository.startSession(now)
        val configActive = repository.getOrCreateConfig()
        val estimatedMs = repository.calculateEstimatedExhaustionMs(configActive)
        assertNotNull(estimatedMs)
        val expected = now + (3 * 30 * 60 * 1000L)
        assertEquals(expected, estimatedMs)
    }

    @Test
    fun `continuous parking equivalence updates when tariff changes`() = runBlocking {
        val balance = 1.00

        // Tariff 1: $0.10 for 30m -> 10 blocks -> 300 minutes -> 5h 0m
        val time1 = repository.calculateAvailableTime(balance, 0.10, 30)
        assertEquals("5h 0m", time1)

        // Tariff 2: $0.50 for 60m -> 2 blocks -> 120 minutes -> 2h 0m
        val time2 = repository.calculateAvailableTime(balance, 0.50, 60)
        assertEquals("2h 0m", time2)
    }

    @Test
    fun `gemini api switch disables remote api calls and uses local fallback`() = runBlocking {
        val defaultConfig = repository.getOrCreateConfig()
        assertTrue(defaultConfig.isGeminiEnabled)

        // Disable Gemini API
        repository.updateTariffAndNotifications(
            amount = 0.10,
            minutes = 30,
            notifyEnabled = true,
            notifyInterval = 1,
            tileLabel = "Parkia",
            geminiApiKey = "",
            geminiModel = "gemini-2.0-flash",
            isGeminiEnabled = false,
            quickTileBiometricEnabled = true
        )

        val updated = repository.getOrCreateConfig()
        assertFalse(updated.isGeminiEnabled)

        val result = com.example.util.GeminiAiParser.parseSpeech("saldo 25.50", isGeminiEnabled = false)
        assertFalse(result.usedGemini)
        assertEquals(25.50, result.balance!!, 0.001)
        assertTrue(result.engineDetail?.contains("desactivada") == true)
    }

    @Test
    fun `quick tile biometric setting toggles security manager preference`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // Initial state is true
        assertTrue(SecurityManager.isQuickTileBiometricEnabled(context))

        // Save setting as false
        SecurityManager.setQuickTileBiometricEnabled(context, false)
        assertFalse(SecurityManager.isQuickTileBiometricEnabled(context))

        // Save setting as true
        SecurityManager.setQuickTileBiometricEnabled(context, true)
        assertTrue(SecurityManager.isQuickTileBiometricEnabled(context))
    }

    @Test
    fun `vision ai parser extracts balance below Efectivo word`() {
        val rawText = """
            PARQUEADERO CENTRAL
            FECHA: 07/10/2026 14:30
            Efectivo
            15.50
        """.trimIndent()

        val result = com.example.util.VisionAiParser.parseExtractedText(rawText)
        assertNotNull(result.balance)
        assertEquals(15.50, result.balance!!, 0.001)
    }

    @Test
    fun `vision ai parser converts 12h AM PM time to 24h format`() {
        val rawTextPM = """
            COMPROBANTE DE PAGO
            FECHA: 07/10/2026 7:49 PM
            Efectivo $ 20.00
        """.trimIndent()

        val resultPM = com.example.util.VisionAiParser.parseExtractedText(rawTextPM)
        assertNotNull(resultPM.timestamp)
        val calPM = java.util.Calendar.getInstance().apply { timeInMillis = resultPM.timestamp!! }
        assertEquals(19, calPM.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(49, calPM.get(java.util.Calendar.MINUTE))

        val rawTextAM = """
            COMPROBANTE DE PAGO
            HORA: 7:49 AM
            SALDO: 10.00
        """.trimIndent()

        val resultAM = com.example.util.VisionAiParser.parseExtractedText(rawTextAM)
        assertNotNull(resultAM.timestamp)
        val calAM = java.util.Calendar.getInstance().apply { timeInMillis = resultAM.timestamp!! }
        assertEquals(7, calAM.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(49, calAM.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `voice ai parser parses natural spoken spanish balance phrases`() {
        val res1 = com.example.util.VoiceAiParser.parseSpeech("Un dolar con cincuenta y cuatro centavos")
        assertEquals(1.54, res1.balance!!, 0.001)

        val res2 = com.example.util.VoiceAiParser.parseSpeech("uno punto cincuenta y cuatro dolares")
        assertEquals(1.54, res2.balance!!, 0.001)

        val res3 = com.example.util.VoiceAiParser.parseSpeech("cinco dolares con cinco centavos")
        assertEquals(5.05, res3.balance!!, 0.001)

        val res4 = com.example.util.VoiceAiParser.parseSpeech("dos dolares con treinta centavos")
        assertEquals(2.30, res4.balance!!, 0.001)
    }

    @Test
    fun `global security auth switch toggles preference in security manager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default is true
        assertTrue(SecurityManager.isGlobalAuthEnabled(context))

        // Disable global auth
        SecurityManager.setGlobalAuthEnabled(context, false)
        assertFalse(SecurityManager.isGlobalAuthEnabled(context))

        // Re-enable global auth
        SecurityManager.setGlobalAuthEnabled(context, true)
        assertTrue(SecurityManager.isGlobalAuthEnabled(context))
    }

    @Test
    fun `tts manager announces session start and stop without errors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.util.TtsManager.announceSessionStart(context, 15.50, enabled = true)
        com.example.util.TtsManager.announceSessionStop(context, 12.30, enabled = true)

        // Should return silently when disabled
        com.example.util.TtsManager.announceSessionStart(context, 15.50, enabled = false)
        com.example.util.TtsManager.announceSessionStop(context, 12.30, enabled = false)
    }

    @Test
    fun `geofence notification displays and saves settings correctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.updateTariffAndNotifications(
            amount = 0.10,
            minutes = 30,
            notifyEnabled = true,
            notifyInterval = 1,
            tileLabel = "Parkia",
            geminiApiKey = "",
            geminiModel = "gemini-2.0-flash",
            isGeminiEnabled = true,
            quickTileBiometricEnabled = true,
            globalSecurityAuthEnabled = true,
            ttsAnnouncementsEnabled = true,
            geofenceEnabled = true,
            geofenceLatitude = -0.180653,
            geofenceLongitude = -78.467838,
            geofenceRadiusMeters = 150f
        )

        val config = repository.configFlow.first()
        assertNotNull(config)
        assertTrue(config!!.geofenceEnabled)
        assertEquals(-0.180653, config.geofenceLatitude, 0.0001)
        assertEquals(-78.467838, config.geofenceLongitude, 0.0001)
        assertEquals(150f, config.geofenceRadiusMeters, 0.1f)

        // Show geofence notification without throwing exception
        NotificationHelper.showGeofenceNotification(context, isSessionActive = false)
        NotificationHelper.showGeofenceNotification(context, isSessionActive = true)
    }

    @Test
    fun `session keeps running into negative balance when initial balance runs out and does not auto-terminate`() = runBlocking {
        // Start session with $0.10 (only 1 block of 30 min at $0.10)
        repository.rechargeBalance(0.10)
        val startTime = 10000000L
        val started = repository.startSession(startTime)
        assertTrue(started)

        // Advance to 65 minutes (3900 seconds -> 3 blocks of 30 min = $0.30 cost)
        val currentTime = startTime + (3900 * 1000L)
        val depleted = repository.catchUpSession(currentTime)

        // Must NOT be depleted/stopped!
        assertFalse("Session should NOT stop when balance runs out", depleted)

        val config = repository.getOrCreateConfig()
        assertTrue("Session must remain active", config.isSessionActive)
        assertEquals(3900L, config.elapsedSeconds)
        assertEquals(0.30, config.accumulatedCost, 0.001)
        // Initial balance $0.10 - cost $0.30 = -$0.20 (debt of $0.20)
        assertEquals(-0.20, config.balance, 0.001)

        // Available time should indicate debt
        val availableTime = repository.calculateAvailableTime(config.balance, config.rateAmount, config.rateMinutes)
        assertTrue("Available time should show debt", availableTime.contains("Deuda: $0.20 USD"))
    }

    @Test
    fun `stopping session in negative balance retains debt and does not clamp to zero`() = runBlocking {
        repository.rechargeBalance(0.10)
        val startTime = 10000000L
        repository.startSession(startTime)

        // Stop session after 65 minutes (3 blocks = $0.30 cost)
        val stopTime = startTime + (3900 * 1000L)
        repository.stopSession(stopTime, "Finalizado por usuario")

        val config = repository.getOrCreateConfig()
        assertFalse(config.isSessionActive)
        // Balance remains negative debt: 0.10 - 0.30 = -0.20
        assertEquals(-0.20, config.balance, 0.001)
        assertEquals(-0.20, config.lastSessionExitBalance, 0.001)

        val history = repository.historyFlow.first()
        assertEquals(1, history.size)
        assertEquals(0.30, history[0].costCharged, 0.001)
        assertEquals(3900L, history[0].durationSeconds)
        assertEquals("Finalizado por usuario", history[0].reasonEnded)

        // Recharging $1.00 clears debt: -0.20 + 1.00 = +0.80
        repository.rechargeBalance(1.00)
        val configAfterRecharge = repository.getOrCreateConfig()
        assertEquals(0.80, configAfterRecharge.balance, 0.001)
    }

    @Test
    fun `notification shows debt and negative balance properly in active and resting states`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.createNotificationChannel(context)

        // 1. Active session in debt
        val activeDebtNotification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = -0.75,
            isSessionActive = true,
            formattedDateTime = "11/10/2026 12:30:00"
        )
        val activeTitle = activeDebtNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val activeText = activeDebtNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()

        assertNotNull(activeTitle)
        assertTrue("Title should mention debt", activeTitle!!.contains("Deuda"))
        assertTrue("Title should include negative balance amount", activeTitle.contains("-$0.75"))
        assertNotNull(activeText)
        assertTrue("Content should indicate active debt session", activeText!!.contains("En Sesión Activa (En Deuda)"))

        // 2. Resting state with pending debt
        val restingDebtNotification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = -2.50,
            isSessionActive = false,
            formattedDateTime = "11/10/2026 13:00:00"
        )
        val restingTitle = restingDebtNotification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val restingText = restingDebtNotification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()

        assertNotNull(restingTitle)
        assertTrue("Title should mention debt", restingTitle!!.contains("Deuda"))
        assertTrue("Title should include negative balance amount", restingTitle.contains("-$2.50"))
        assertNotNull(restingText)
        assertTrue("Content should indicate resting pending debt", restingText!!.contains("En Reposo (Deuda Pendiente)"))
    }

    @Test
    fun `balance alarm receiver executes catch-up into debt and triggers notification`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Initialize config with 1 block, start session
        repository.rechargeBalance(0.10)
        val startTime = 10000000L
        repository.startSession(startTime)

        // Advance repository in DB so next tick catches up past balance
        val futureTime = startTime + (3600 * 1000L) // 1 hour -> 2 blocks = $0.20 -> -$0.10 debt
        val depleted = repository.catchUpSession(futureTime)
        assertFalse(depleted)

        val config = repository.getOrCreateConfig()
        assertTrue("Session remains active", config.isSessionActive)
        assertEquals(-0.10, config.balance, 0.001)

        // Broadcast to BalanceAlarmReceiver
        val receiver = com.example.receiver.BalanceAlarmReceiver()
        val intent = android.content.Intent(com.example.receiver.BalanceAlarmReceiver.ACTION_BALANCE_ALARM_TICK)
        receiver.onReceive(context, intent)

        // Verify alarm scheduler can be scheduled/canceled without error
        com.example.util.BalanceAlarmScheduler.scheduleOrCancel(context, true, config.notificationIntervalMinutes)
        com.example.util.BalanceAlarmScheduler.scheduleNext(context, config.notificationIntervalMinutes)
        com.example.util.BalanceAlarmScheduler.scheduleOrCancel(context, false, config.notificationIntervalMinutes)
    }

    @Test
    fun `session started with photo stores photo in active config and persists into history`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.rechargeBalance(2.00)

        // Save dummy photo
        val bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val photoPath = com.example.util.PhotoStorageManager.savePhotoToInternalStorage(context, bitmap)
        assertTrue("Photo should be saved to internal storage", photoPath.isNotBlank())

        val now = 10000000L
        val started = repository.startSession(now = now, photoPath = photoPath)
        assertTrue(started)

        // 1. Active session must immediately have the photoPath in config
        val activeConfig = repository.getOrCreateConfig()
        assertTrue("Session must be active", activeConfig.isSessionActive)
        assertEquals("Active session must display captured photo path", photoPath, activeConfig.lastSessionPhotoPath)

        // 2. Stop session
        repository.stopSession(now = now + 1800000L, reason = "Finalizado por usuario")

        // 3. History record must have the photoPath
        val history = repository.historyFlow.first()
        assertEquals(1, history.size)
        assertEquals(photoPath, history[0].photoPath)

        // 4. Config lastSessionPhotoPath must now be cleared for future sessions
        val finishedConfig = repository.getOrCreateConfig()
        assertFalse("Session is stopped", finishedConfig.isSessionActive)
        assertEquals("lastSessionPhotoPath should be cleared on session stop", "", finishedConfig.lastSessionPhotoPath)

        // 5. Next session started without photo should NOT have the old photo
        repository.startSession(now = now + 2000000L, photoPath = "")
        val nextActiveConfig = repository.getOrCreateConfig()
        assertTrue(nextActiveConfig.isSessionActive)
        assertEquals("Subsequent session without photo must be blank", "", nextActiveConfig.lastSessionPhotoPath)

        // Clean up
        com.example.util.PhotoStorageManager.deletePhoto(photoPath)
    }

    @Test
    fun `resetAll clears lastSessionPhotoPath`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.rechargeBalance(2.00)
        val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
        val photoPath = com.example.util.PhotoStorageManager.savePhotoToInternalStorage(context, bitmap)

        repository.startSession(photoPath = photoPath)
        assertEquals(photoPath, repository.getOrCreateConfig().lastSessionPhotoPath)

        repository.resetAll()
        val resetConfig = repository.getOrCreateConfig()
        assertEquals("", resetConfig.lastSessionPhotoPath)
        assertFalse(resetConfig.isSessionActive)

        com.example.util.PhotoStorageManager.deletePhoto(photoPath)
    }

    @Test
    fun `resumeSessionFromHistory restores entry timestamp initial balance and photo and removes item from history`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.rechargeBalance(5.00)

        // Create a photo
        val bitmap = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
        val photoPath = com.example.util.PhotoStorageManager.savePhotoToInternalStorage(context, bitmap)

        val t0 = 10000000L
        repository.startSession(now = t0, photoPath = photoPath)

        // Session stops after 15 minutes (900 seconds) -> 1 block of 30 min charged = $0.10
        val t1 = t0 + 900_000L
        repository.stopSession(now = t1, reason = "Finalizado por usuario")

        // Check history item
        val historyBefore = repository.historyFlow.first()
        assertEquals(1, historyBefore.size)
        val itemToResume = historyBefore[0]
        assertEquals(t0, itemToResume.startTimestamp)
        assertEquals(5.00, itemToResume.initialBalance, 0.001)
        assertEquals(photoPath, itemToResume.photoPath)
        assertEquals(0.10, itemToResume.costCharged, 0.001)

        // Current config has inactive session
        val inactiveConfig = repository.getOrCreateConfig()
        assertFalse(inactiveConfig.isSessionActive)

        // Resume session at t0 + 60 minutes (3600 seconds)
        // At 60 min with 30m block rate: 2 blocks = $0.20 charged from initial $5.00 -> balance = $4.80
        val t2 = t0 + 3600_000L
        val resumed = repository.resumeSessionFromHistory(item = itemToResume, now = t2)
        assertTrue("Session must be resumed successfully", resumed)

        // 1. History item must be removed from history list
        val historyAfterResume = repository.historyFlow.first()
        assertEquals("Resumed item must be removed from history list", 0, historyAfterResume.size)

        // 2. Active config must restore original entry timestamp, initial balance, and photo
        val resumedConfig = repository.getOrCreateConfig()
        assertTrue("Session must now be active", resumedConfig.isSessionActive)
        assertEquals("Must restore original start timestamp", t0, resumedConfig.sessionStartTimestamp)
        assertEquals("Must restore original initial balance", 5.00, resumedConfig.sessionInitialBalance, 0.001)
        assertEquals("Must restore original photo path", photoPath, resumedConfig.lastSessionPhotoPath)
        assertEquals("Elapsed seconds must be 3600", 3600L, resumedConfig.elapsedSeconds)
        assertEquals("Accumulated cost for 2 blocks must be 0.20", 0.20, resumedConfig.accumulatedCost, 0.001)
        assertEquals("Current balance must be 4.80", 4.80, resumedConfig.balance, 0.001)

        // 3. Stop the resumed session at t0 + 65 minutes (3900 seconds -> 3 blocks = $0.30)
        val t3 = t0 + 3900_000L
        repository.stopSession(now = t3, reason = "Finalizado por usuario tras reanudar")

        val historyFinal = repository.historyFlow.first()
        assertEquals(1, historyFinal.size)
        val finalItem = historyFinal[0]
        assertEquals(t0, finalItem.startTimestamp)
        assertEquals(t3, finalItem.endTimestamp)
        assertEquals(5.00, finalItem.initialBalance, 0.001)
        assertEquals(0.30, finalItem.costCharged, 0.001)
        assertEquals(photoPath, finalItem.photoPath)
        assertEquals(4.70, repository.getOrCreateConfig().balance, 0.001)

        com.example.util.PhotoStorageManager.deletePhoto(photoPath)
    }

    @Test
    fun `resumeSessionFromHistory stops and archives currently active session before resuming`(): Unit = runBlocking {
        repository.rechargeBalance(10.00)

        // Session 1: Started at t0, stopped at t1
        val t0 = 10000000L
        repository.startSession(now = t0)
        val t1 = t0 + 600_000L // 10 min
        repository.stopSession(now = t1, reason = "Sesion 1 finalizada")

        val historyAfterS1 = repository.historyFlow.first()
        assertEquals(1, historyAfterS1.size)
        val session1HistoryItem = historyAfterS1[0]

        // Session 2: Started at t2
        val t2 = t1 + 300_000L
        repository.startSession(now = t2)
        assertTrue(repository.getOrCreateConfig().isSessionActive)

        // While Session 2 is active, resume Session 1 at t3
        val t3 = t2 + 900_000L
        repository.resumeSessionFromHistory(item = session1HistoryItem, now = t3)

        // Session 2 should have been saved to history, and Session 1 removed from history
        val historyAfterResume = repository.historyFlow.first()
        assertEquals(1, historyAfterResume.size)
        val archivedSession2 = historyAfterResume[0]
        assertEquals("Session 2 was archived", t2, archivedSession2.startTimestamp)
        assertEquals("Finalizado para reanudar sesión anterior", archivedSession2.reasonEnded)

        // Active session is Session 1
        val activeConfig = repository.getOrCreateConfig()
        assertTrue(activeConfig.isSessionActive)
        assertEquals(t0, activeConfig.sessionStartTimestamp)
    }
}
