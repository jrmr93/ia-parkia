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
  private var shouldReLockOnResume = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    NotificationHelper.createNotificationChannel(this)

    if (savedInstanceState != null) {
      isUnlocked = savedInstanceState.getBoolean("KEY_IS_UNLOCKED", false)
    }

    setContent {
      MyApplicationTheme {
        val isPinConfigured = SecurityManager.isPinConfigured(this@MainActivity)

        if (!isUnlocked) {
          SecurityLockScreen(
            isInitialSetup = !isPinConfigured,
            onAuthenticated = {
              isUnlocked = true
            }
          )
        } else {
          ParkingMainScreen(viewModel = parkingViewModel)
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean("KEY_IS_UNLOCKED", isUnlocked)
  }

  override fun onStop() {
    super.onStop()
    if (!SecurityManager.isAuthenticating && !SecurityManager.isRequestingPermission && SecurityManager.isPinConfigured(this)) {
      shouldReLockOnResume = true
    }
  }

  override fun onResume() {
    super.onResume()
    if (shouldReLockOnResume) {
      shouldReLockOnResume = false
      if (!SecurityManager.isAuthenticating && !SecurityManager.isRequestingPermission && SecurityManager.isPinConfigured(this)) {
        isUnlocked = false
      }
    }
    parkingViewModel.onAppResume()
  }
}
