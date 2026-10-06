package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    NotificationHelper.createNotificationChannel(this)

    setContent {
      MyApplicationTheme {
        var isUnlocked by remember { mutableStateOf(false) }
        val isPinConfigured = remember { SecurityManager.isPinConfigured(this@MainActivity) }

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

  override fun onResume() {
    super.onResume()
    parkingViewModel.onAppResume()
  }
}
