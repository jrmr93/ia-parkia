package com.example

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.NotificationHelper
import org.json.JSONObject
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
class HyperIslandTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        NotificationHelper.createNotificationChannel(context)
        NotificationHelper.isHyperIslandForcedForTesting = null
    }

    @After
    fun tearDown() {
        NotificationHelper.isHyperIslandForcedForTesting = null
    }

    @Test
    fun `formatHyperIslandBalance formats positive, zero, and debt amounts correctly without extras`() {
        assertEquals("$25.50", NotificationHelper.formatHyperIslandBalance(25.50))
        assertEquals("$25.50", NotificationHelper.formatHyperIslandBalance(25.5))
        assertEquals("$0.00", NotificationHelper.formatHyperIslandBalance(0.0))
        assertEquals("-$0.75", NotificationHelper.formatHyperIslandBalance(-0.75))
        assertEquals("-$25.50", NotificationHelper.formatHyperIslandBalance(-25.50))
    }

    @Test
    fun `incompatible devices retain conventional notification without hyperisland extras`() {
        NotificationHelper.isHyperIslandForcedForTesting = false

        val notification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = 25.50,
            isSessionActive = false,
            formattedDateTime = "25/10/2026 14:00:00"
        )

        // Conventional notification fields must be intact
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        assertNotNull(title)
        assertTrue("Conventional title should contain balance with currency", title!!.contains("Parkia - Saldo: $25.50 USD"))
        assertNotNull(text)
        assertTrue("Conventional text should contain update timestamp and state", text!!.contains("Actualizado: 25/10/2026 14:00:00 • En Reposo"))

        // Must not inject miui.focus.param on incompatible devices
        val focusParam = notification.extras.getString("miui.focus.param")
        assertNull("miui.focus.param should be null for incompatible devices", focusParam)
        assertFalse(notification.extras.getBoolean("miui.enableFloat", false))
    }

    @Test
    fun `compatible Xiaomi HyperOS devices display strictly user balance in HyperIsland`() {
        NotificationHelper.isHyperIslandForcedForTesting = true

        val notification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = 25.50,
            isSessionActive = false,
            formattedDateTime = "25/10/2026 14:00:00"
        )

        // Conventional notification is still preserved as alternative/fallback
        val conventionalTitle = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertNotNull(conventionalTitle)
        assertTrue(conventionalTitle!!.contains("Parkia - Saldo: $25.50 USD"))

        // Xiaomi HyperIsland integration extras
        val ticker = notification.extras.getString("miui.focus.ticker")
        assertEquals("$25.50", ticker)
        assertTrue(notification.extras.getBoolean("miui.enableFloat", false))

        val focusParamJson = notification.extras.getString("miui.focus.param")
        assertNotNull("miui.focus.param must be populated", focusParamJson)

        val rootJson = JSONObject(focusParamJson!!)
        val paramV2 = rootJson.getJSONObject("param_v2")

        // In HyperIsland, ticker, baseInfo, and bigIsland must show strictly the user's balance
        assertEquals("$25.50", paramV2.getString("ticker"))

        val baseInfo = paramV2.getJSONObject("baseInfo")
        assertEquals("$25.50", baseInfo.getString("title"))
        assertEquals("Saldo disponible", baseInfo.getString("content"))

        val paramIsland = paramV2.getJSONObject("param_island")

        // Small Island Area (Status bar pill/capsule around punch hole)
        val smallIsland = paramIsland.getJSONObject("smallIslandArea")
        val smallPic = smallIsland.getJSONObject("picInfo")
        assertEquals("miui.focus.pic_car_icon", smallPic.getString("pic"))

        // Big Island Area (Expanded island card attached to island with image on left and balance on right)
        val bigIsland = paramIsland.getJSONObject("bigIslandArea")
        val imageTextLeft = bigIsland.getJSONObject("imageTextInfoLeft")
        assertEquals("miui.focus.pic_car_icon", imageTextLeft.getJSONObject("picInfo").getString("pic"))
        val textInfo = imageTextLeft.getJSONObject("textInfo")
        assertEquals("$25.50", textInfo.getString("title"))
        assertEquals("Saldo disponible", textInfo.getString("content"))

        // Picture parcelables exist in extras
        assertTrue("Extras should contain pic_car_icon", notification.extras.containsKey("miui.focus.pic_car_icon"))
        assertTrue("Extras should contain pic_ticker", notification.extras.containsKey("miui.focus.pic_ticker"))

        // Verify no music playback simulation
        assertFalse("Must not simulate music player", paramV2.has("mediaInfo"))
        assertFalse("Must not simulate music player", paramIsland.has("mediaArea"))
        assertFalse("Must not contain song playback actions", rootJson.has("playAction"))
    }

    @Test
    fun `debt is correctly reflected in HyperIsland as negative amount only`() {
        NotificationHelper.isHyperIslandForcedForTesting = true

        val notification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = -12.30,
            isSessionActive = true,
            formattedDateTime = "25/10/2026 15:30:00"
        )

        // HyperIsland must show strictly "-$12.30"
        val ticker = notification.extras.getString("miui.focus.ticker")
        assertEquals("-$12.30", ticker)

        val focusParamJson = notification.extras.getString("miui.focus.param")
        assertNotNull(focusParamJson)
        val paramV2 = JSONObject(focusParamJson!!).getJSONObject("param_v2")
        assertEquals("-$12.30", paramV2.getString("ticker"))
        assertEquals("-$12.30", paramV2.getJSONObject("baseInfo").getString("title"))
        assertEquals("Deuda pendiente", paramV2.getJSONObject("baseInfo").getString("content"))

        val bigIsland = paramV2.getJSONObject("param_island").getJSONObject("bigIslandArea")
        val textInfo = bigIsland.getJSONObject("imageTextInfoLeft").getJSONObject("textInfo")
        assertEquals("-$12.30", textInfo.getString("title"))
        assertEquals("Deuda pendiente", textInfo.getString("content"))

        // Conventional notification alternative shows full details
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertTrue(title!!.contains("Deuda: -$12.30 USD"))
        assertTrue(text!!.contains("En Sesión Activa (En Deuda)"))
    }

    @Test
    fun `updates amount whenever balance changes`() {
        NotificationHelper.isHyperIslandForcedForTesting = true

        val initialNotification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = 25.50,
            isSessionActive = false,
            formattedDateTime = "25/10/2026 14:00:00"
        )
        assertEquals("$25.50", initialNotification.extras.getString("miui.focus.ticker"))

        // Balance recharged or updated to $50.00
        val updatedNotification = NotificationHelper.buildBalanceNotification(
            context = context,
            balance = 50.00,
            isSessionActive = false,
            formattedDateTime = "25/10/2026 14:05:00"
        )
        assertEquals("$50.00", updatedNotification.extras.getString("miui.focus.ticker"))
        val paramV2 = JSONObject(updatedNotification.extras.getString("miui.focus.param")!!).getJSONObject("param_v2")
        assertEquals("$50.00", paramV2.getString("ticker"))
        assertEquals("$50.00", paramV2.getJSONObject("baseInfo").getString("title"))
    }
}
