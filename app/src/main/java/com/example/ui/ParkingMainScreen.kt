package com.example.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ConfirmStartSessionDialog
import com.example.ui.components.ConfirmStopSessionDialog
import com.example.ui.components.EditActiveSessionDialog
import com.example.ui.components.ExhaustedBalanceAlertDialog
import com.example.ui.components.FooterCard
import com.example.ui.components.HistoryCard
import com.example.ui.components.NoFundsAlertDialog
import com.example.ui.components.ParkingBayCard
import com.example.ui.components.ParkingTopBar
import com.example.ui.components.RechargeCustomDialog
import com.example.ui.components.ResetAllDialog
import com.example.ui.components.ResetBalanceDialog
import com.example.ui.components.TariffSettingsDialog
import com.example.ui.components.WalletCard

@Composable
fun ParkingMainScreen(
    viewModel: ParkingViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Request notification permission on Android 13+ only if not granted
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val activity = context as? androidx.fragment.app.FragmentActivity
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        var hasCheckedPermission by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(hasPermission) }

        if (!hasCheckedPermission && activity != null) {
            LaunchedEffect(Unit) {
                hasCheckedPermission = true
                com.example.util.SecurityManager.isRequestingPermission = true
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                kotlinx.coroutines.delay(1000)
                com.example.util.SecurityManager.isRequestingPermission = false
            }
        }
    }


    // Register lifecycle observer to trigger catch-up on ON_RESUME
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val appBackgroundColor = Color(0xFFF1F5F9)

    Scaffold(
        topBar = {
            ParkingTopBar(
                onOpenTariffSettings = { viewModel.setShowTariffSettingsDialog(true) }
            )
        },
        containerColor = appBackgroundColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(appBackgroundColor),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                // Digital Wallet Card (includes estimated exhaustion when session is active)
                item {
                    WalletCard(
                        balance = uiState.config.balance,
                        availableTimeFormatted = uiState.availableTimeFormatted,
                        isLowBalance = uiState.isLowBalance,
                        estimatedExhaustion = uiState.estimatedExhaustionFormatted,
                        isSessionActive = uiState.config.isSessionActive,
                        onQuickRecharge = { viewModel.rechargeBalance(it) },
                        onOpenCustomRecharge = { viewModel.setShowRechargeCustomDialog(true) },
                        onOpenResetBalance = { viewModel.setShowResetBalanceDialog(true) }
                    )
                }

                // Parking Bay and Session Control Card (includes Modify button for active session)
                item {
                    ParkingBayCard(
                        isSessionActive = uiState.config.isSessionActive,
                        sessionStartTimestamp = uiState.config.sessionStartTimestamp,
                        sessionInitialBalance = uiState.config.sessionInitialBalance,
                        elapsedSeconds = uiState.config.elapsedSeconds,
                        accumulatedCost = uiState.config.accumulatedCost,
                        rateAmount = uiState.config.rateAmount,
                        rateMinutes = uiState.config.rateMinutes,
                        onRequestStartSession = { viewModel.requestStartSession() },
                        onRequestStopSession = { viewModel.requestStopSession() },
                        onEditActiveSessionClick = { viewModel.setShowEditActiveSessionDialog(true) }
                    )
                }

                // History Card (contains active session row and Resetear button)
                item {
                    HistoryCard(
                        historyList = uiState.history,
                        isSessionActive = uiState.config.isSessionActive,
                        sessionStartTimestamp = uiState.config.sessionStartTimestamp,
                        sessionInitialBalance = uiState.config.sessionInitialBalance,
                        elapsedSeconds = uiState.config.elapsedSeconds,
                        accumulatedCost = uiState.config.accumulatedCost,
                        totalSpentHistorical = uiState.totalSpentHistorical,
                        totalSessionsCount = uiState.totalSessionsCount,
                        onResetAllClick = { viewModel.setShowResetAllDialog(true) }
                    )
                }

                // Footer with Creator Attribution
                item {
                    FooterCard()
                }
            }
        }
    }

    // Modal Confirmation: Start Session
    if (uiState.showConfirmStartDialog) {
        ConfirmStartSessionDialog(
            rateAmount = uiState.config.rateAmount,
            rateMinutes = uiState.config.rateMinutes,
            currentBalance = uiState.config.balance,
            onDismiss = { viewModel.dismissConfirmStartDialog() },
            onConfirm = { viewModel.confirmStartSession() }
        )
    }

    // Modal Confirmation: Stop Session
    if (uiState.showConfirmStopDialog) {
        ConfirmStopSessionDialog(
            elapsedSeconds = uiState.config.elapsedSeconds,
            accumulatedCost = uiState.config.accumulatedCost,
            onDismiss = { viewModel.dismissConfirmStopDialog() },
            onConfirm = { viewModel.confirmStopSession() }
        )
    }

    // Modal Dialogs
    if (uiState.showRechargeCustomDialog) {
        RechargeCustomDialog(
            onDismiss = { viewModel.setShowRechargeCustomDialog(false) },
            onConfirm = { amount ->
                viewModel.rechargeBalance(amount)
                viewModel.setShowRechargeCustomDialog(false)
            }
        )
    }

    if (uiState.showResetBalanceDialog) {
        ResetBalanceDialog(
            onDismiss = { viewModel.setShowResetBalanceDialog(false) },
            onConfirm = {
                viewModel.resetBalance()
                viewModel.setShowResetBalanceDialog(false)
            }
        )
    }

    if (uiState.showTariffSettingsDialog) {
        TariffSettingsDialog(
            currentAmount = uiState.config.rateAmount,
            currentMinutes = uiState.config.rateMinutes,
            onDismiss = { viewModel.setShowTariffSettingsDialog(false) },
            onConfirm = { amount, minutes ->
                viewModel.updateTariff(amount, minutes)
                viewModel.setShowTariffSettingsDialog(false)
            }
        )
    }

    if (uiState.showResetAllDialog) {
        ResetAllDialog(
            onDismiss = { viewModel.setShowResetAllDialog(false) },
            onConfirm = {
                viewModel.resetAll()
                viewModel.setShowResetAllDialog(false)
            }
        )
    }

    if (uiState.showEditActiveSessionDialog) {
        EditActiveSessionDialog(
            currentStartTimestamp = uiState.config.sessionStartTimestamp,
            currentInitialBalance = uiState.config.sessionInitialBalance,
            onDismiss = { viewModel.setShowEditActiveSessionDialog(false) },
            onConfirm = { newTimestamp, newInitialBalance ->
                viewModel.updateActiveSession(newTimestamp, newInitialBalance)
                viewModel.setShowEditActiveSessionDialog(false)
            }
        )
    }

    if (uiState.showExhaustedBalanceAlert) {
        ExhaustedBalanceAlertDialog(
            onDismiss = { viewModel.dismissExhaustedAlert() },
            onRechargeClick = { viewModel.setShowRechargeCustomDialog(true) }
        )
    }

    if (uiState.showNoFundsToStartAlert) {
        NoFundsAlertDialog(
            onDismiss = { viewModel.dismissNoFundsAlert() },
            onRechargeClick = { viewModel.setShowRechargeCustomDialog(true) }
        )
    }
}
