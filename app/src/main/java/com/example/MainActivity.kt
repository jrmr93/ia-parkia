package com.example

import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.example.ui.ParkingMainScreen
import com.example.ui.ParkingViewModel
import com.example.ui.SecurityLockScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.NotificationHelper
import com.example.util.SecurityManager

class MainActivity : FragmentActivity() {

  private val parkingViewModel: ParkingViewModel by viewModels()
  private var isUnlocked by mutableStateOf(false)
  private var pendingTileAction by mutableStateOf(false)
  private var pendingStartModalAction by mutableStateOf(false)
  private var backgroundTimestamp = 0L
  private var nfcAdapter: NfcAdapter? = null

  companion object {
    private const val LOCK_TIMEOUT_MS = 3 * 60 * 1000L // 3 minutos
    const val EXTRA_SHOW_START_OPTIONS_MODAL = "EXTRA_SHOW_START_OPTIONS_MODAL"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    NotificationHelper.createNotificationChannel(this)

    nfcAdapter = NfcAdapter.getDefaultAdapter(this)

    checkTileIntent(intent)
    checkGeofenceNotificationIntent(intent)
    checkNfcIntent(intent)

    if (savedInstanceState != null) {
      isUnlocked = savedInstanceState.getBoolean("KEY_IS_UNLOCKED", false)
      pendingStartModalAction = savedInstanceState.getBoolean("KEY_PENDING_START_MODAL", false)
    }

    setContent {
      MyApplicationTheme {
        val isPinConfigured = SecurityManager.isPinConfigured(this@MainActivity)
        val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this@MainActivity)

        if (!isUnlocked && isGlobalAuthEnabled) {
          SecurityLockScreen(
            isInitialSetup = !isPinConfigured,
            onAuthenticated = {
              isUnlocked = true
              if (pendingTileAction) {
                pendingTileAction = false
                handleTileAction()
              }
              if (pendingStartModalAction) {
                pendingStartModalAction = false
                parkingViewModel.setShowQuickTileStartModal(true)
              }
            }
          )
        } else {
          if (!isUnlocked) {
            isUnlocked = true
          }
          ParkingMainScreen(viewModel = parkingViewModel)
        }
      }
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    checkTileIntent(intent)
    checkGeofenceNotificationIntent(intent)
    checkNfcIntent(intent)
  }

  private fun extractNfcTagId(intent: android.content.Intent?): String? {
    if (intent == null) return null
    val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    } ?: return null

    return tag.id?.joinToString(":") { String.format("%02X", it) }
  }

  private fun checkNfcIntent(intent: android.content.Intent?) {
    val action = intent?.action ?: return
    if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
        action == NfcAdapter.ACTION_TECH_DISCOVERED ||
        action == NfcAdapter.ACTION_TAG_DISCOVERED) {
      val tagId = extractNfcTagId(intent)
      intent.action = null
      handleNfcScan(tagId)
    }
  }

  private fun handleNfcScan(scannedTagId: String?) {
    vibrateFeedback()

    if (parkingViewModel.uiState.value.isScanningNfcForRegistration) {
      if (!scannedTagId.isNullOrBlank()) {
        parkingViewModel.onNfcTagScannedForRegistration(scannedTagId)
        Toast.makeText(this, "Tarjeta escaneada: $scannedTagId", Toast.LENGTH_SHORT).show()
      }
      return
    }

    val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this)
    if (isGlobalAuthEnabled && SecurityManager.isPinConfigured(this) && !isUnlocked) {
      isUnlocked = true
    }

    parkingViewModel.toggleSessionFromNfc(scannedTagId) { isSuccess, message ->
      if (!isSuccess && message.contains("no autorizada")) {
        vibrateErrorFeedback()
      }
      Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
  }

  private fun vibrateErrorFeedback() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator.vibrate(
          VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100, 80, 100), -1)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100, 80, 100), -1)
          )
        } else {
          vibrator.vibrate(longArrayOf(0, 100, 80, 100, 80, 100), -1)
        }
      }
    } catch (_: Exception) {}
  }

  private fun vibrateFeedback() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator.vibrate(
          VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          vibrator.vibrate(150)
        }
      }
    } catch (_: Exception) {}
  }

  private fun checkTileIntent(intent: android.content.Intent?) {
    if (intent?.getBooleanExtra("EXTRA_FROM_QUICK_TILE", false) == true) {
      intent.removeExtra("EXTRA_FROM_QUICK_TILE")
      val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this)
      val isBiometricRequired = SecurityManager.isQuickTileBiometricEnabled(this)
      if (isGlobalAuthEnabled && isBiometricRequired && SecurityManager.isPinConfigured(this)) {
        isUnlocked = false
        pendingTileAction = true
      } else {
        isUnlocked = true
        handleTileAction()
      }
    }
  }

  private fun checkGeofenceNotificationIntent(intent: android.content.Intent?) {
    if (intent?.getBooleanExtra(EXTRA_SHOW_START_OPTIONS_MODAL, false) == true) {
      intent.removeExtra(EXTRA_SHOW_START_OPTIONS_MODAL)
      val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this)
      if (isGlobalAuthEnabled && SecurityManager.isPinConfigured(this) && !isUnlocked) {
        pendingStartModalAction = true
      } else {
        isUnlocked = true
        parkingViewModel.setShowQuickTileStartModal(true)
      }
    }
  }

  private fun handleTileAction() {
    val uiState = parkingViewModel.uiState.value
    if (uiState.config.isSessionActive) {
      parkingViewModel.stopSession()
    } else {
      parkingViewModel.setShowQuickTileStartModal(true)
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("KEY_IS_UNLOCKED", isUnlocked)
    outState.putBoolean("KEY_PENDING_START_MODAL", pendingStartModalAction)
  }

  override fun onStop() {
    super.onStop()
    val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this)
    if (isGlobalAuthEnabled && !SecurityManager.isAuthenticating && !SecurityManager.isRequestingPermission && SecurityManager.isPinConfigured(this)) {
      backgroundTimestamp = System.currentTimeMillis()
    }
  }

  override fun onPause() {
    super.onPause()
    try {
      nfcAdapter?.disableReaderMode(this)
    } catch (_: Exception) {}
  }

  override fun onResume() {
    super.onResume()
    val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this)
    if (isGlobalAuthEnabled && backgroundTimestamp > 0L) {
      val elapsed = System.currentTimeMillis() - backgroundTimestamp
      backgroundTimestamp = 0L
      if (elapsed >= LOCK_TIMEOUT_MS && !SecurityManager.isAuthenticating && !SecurityManager.isRequestingPermission && SecurityManager.isPinConfigured(this)) {
        isUnlocked = false
      }
    } else {
      backgroundTimestamp = 0L
    }
    parkingViewModel.onAppResume()

    try {
      nfcAdapter?.enableReaderMode(
        this,
        { tag ->
          val tagId = tag?.id?.joinToString(":") { String.format("%02X", it) }
          runOnUiThread {
            handleNfcScan(tagId)
          }
        },
        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
        null
      )
    } catch (_: Exception) {}
  }
}
