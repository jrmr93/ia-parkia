package com.example

import android.os.Bundle
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
  private var backgroundTimestamp = 0L

  companion object {
    private const val LOCK_TIMEOUT_MS = 3 * 60 * 1000L // 3 minutos
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    NotificationHelper.createNotificationChannel(this)

    checkTileIntent(intent)

    if (savedInstanceState != null) {
      isUnlocked = savedInstanceState.getBoolean("KEY_IS_UNLOCKED", false)
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
  }

  override fun onStop() {
    super.onStop()
    val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(this)
    if (isGlobalAuthEnabled && !SecurityManager.isAuthenticating && !SecurityManager.isRequestingPermission && SecurityManager.isPinConfigured(this)) {
      backgroundTimestamp = System.currentTimeMillis()
    }
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
  }
}
