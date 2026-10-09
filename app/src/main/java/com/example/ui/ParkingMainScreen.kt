package com.example.ui

import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
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
import com.example.ui.components.PinAuthenticationDialog
import com.example.ui.components.RechargeCustomDialog
import com.example.ui.components.ResetAllDialog
import com.example.ui.components.ResetBalanceDialog
import com.example.ui.components.TariffSettingsDialog
import com.example.ui.components.WalletCard
import com.example.util.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ParkingMainScreen(
    viewModel: ParkingViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Request notification permission on Android 13+ only if not granted
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        var hasCheckedPermission by remember { mutableStateOf(hasPermission) }

        if (!hasCheckedPermission && activity != null) {
            LaunchedEffect(Unit) {
                hasCheckedPermission = true
                SecurityManager.isRequestingPermission = true
                androidx.core.app.ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
                kotlinx.coroutines.delay(1000)
                SecurityManager.isRequestingPermission = false
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

    var showPinAuthDialog by remember { mutableStateOf(false) }
    var pendingAuthenticatedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Helper for authenticating via PIN/Biometrics before performing sensitive reset actions
    fun authenticateBeforeAction(onSuccess: () -> Unit) {
        val isGlobalAuthEnabled = SecurityManager.isGlobalAuthEnabled(context)
        if (activity != null && isGlobalAuthEnabled && SecurityManager.isPinConfigured(context)) {
            if (SecurityManager.canAuthenticateBiometrics(context)) {
                SecurityManager.launchBiometricPrompt(
                    activity = activity,
                    onSuccess = onSuccess,
                    onPinRequired = {
                        pendingAuthenticatedAction = onSuccess
                        showPinAuthDialog = true
                    },
                    onError = {
                        pendingAuthenticatedAction = onSuccess
                        showPinAuthDialog = true
                    }
                )
            } else {
                pendingAuthenticatedAction = onSuccess
                showPinAuthDialog = true
            }
        } else {
            onSuccess()
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
                // Digital Wallet Card (Panel 1: keyboard recharge only & authenticated balance reset)
                item {
                    WalletCard(
                        balance = uiState.config.balance,
                        availableTimeFormatted = uiState.availableTimeFormatted,
                        isLowBalance = uiState.isLowBalance,
                        estimatedExhaustion = uiState.estimatedExhaustionFormatted,
                        isSessionActive = uiState.config.isSessionActive,
                        onOpenCustomRecharge = { viewModel.setShowRechargeCustomDialog(true) },
                        onOpenResetBalance = {
                            authenticateBeforeAction {
                                viewModel.setShowResetBalanceDialog(true)
                            }
                        }
                    )
                }

                // Parking Bay and Session Control Card (Panel 2: active session & full-text Modify button)
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

                // History Card (Panel 3: active session row and authenticated Resetear todo button)
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
                        onResetAllClick = {
                            authenticateBeforeAction {
                                viewModel.setShowResetAllDialog(true)
                            }
                        }
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
            notificationsEnabled = uiState.config.notificationsEnabled,
            notificationIntervalMinutes = uiState.config.notificationIntervalMinutes,
            currentTileLabel = uiState.config.tileLabel,
            currentGeminiApiKey = uiState.config.customGeminiApiKey,
            currentGeminiModel = uiState.config.customGeminiModel,
            isGeminiEnabled = uiState.config.isGeminiEnabled,
            quickTileBiometricEnabled = uiState.config.quickTileBiometricEnabled,
            globalSecurityAuthEnabled = uiState.config.globalSecurityAuthEnabled,
            ttsAnnouncementsEnabled = uiState.config.ttsAnnouncementsEnabled,
            geofenceEnabled = uiState.config.geofenceEnabled,
            geofenceLatitude = uiState.config.geofenceLatitude,
            geofenceLongitude = uiState.config.geofenceLongitude,
            geofenceRadiusMeters = uiState.config.geofenceRadiusMeters,
            registeredNfcTagId = uiState.config.registeredNfcTagId,
            nfcStrictMatchingEnabled = uiState.config.nfcStrictMatchingEnabled,
            isScanningNfc = uiState.isScanningNfcForRegistration,
            scannedNfcTagId = uiState.scannedNfcTagId,
            onStartNfcScan = { viewModel.setIsScanningNfcForRegistration(true) },
            onStopNfcScan = { viewModel.setIsScanningNfcForRegistration(false) },
            onClearNfcTag = { viewModel.clearScannedNfcTagId() },
            isSessionActive = uiState.config.isSessionActive,
            onTestGeminiKey = { apiKey, modelName, onResult ->
                viewModel.testGeminiApiKey(apiKey, modelName, onResult)
            },
            onDismiss = {
                viewModel.setIsScanningNfcForRegistration(false)
                viewModel.setShowTariffSettingsDialog(false)
            },
            onConfirm = { amount, minutes, notifyEnabled, notifyInterval, tileLabel, apiKey, modelName, isGeminiEnabled, quickTileBiometric, globalAuth, ttsAnnounce, geoEnabled, geoLat, geoLng, geoRad, regNfcTagId, nfcStrict ->
                viewModel.updateTariffAndNotifications(
                    amount = amount,
                    minutes = minutes,
                    notifyEnabled = notifyEnabled,
                    notifyInterval = notifyInterval,
                    tileLabel = tileLabel,
                    geminiApiKey = apiKey,
                    geminiModel = modelName,
                    isGeminiEnabled = isGeminiEnabled,
                    quickTileBiometricEnabled = quickTileBiometric,
                    globalSecurityAuthEnabled = globalAuth,
                    ttsAnnouncementsEnabled = ttsAnnounce,
                    geofenceEnabled = geoEnabled,
                    geofenceLatitude = geoLat,
                    geofenceLongitude = geoLng,
                    geofenceRadiusMeters = geoRad,
                    registeredNfcTagId = regNfcTagId,
                    nfcStrictMatchingEnabled = nfcStrict
                )
                viewModel.setIsScanningNfcForRegistration(false)
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

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun createTempImageUri(): android.net.Uri {
        val file = java.io.File(context.cacheDir, "parkia_camera_photo.jpg")
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    val speechLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        SecurityManager.isRequestingPermission = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                viewModel.processVoiceAi(spokenText)
            }
        }
    }

    fun decodeAndScaleBitmap(uri: android.net.Uri, maxDimension: Int = 1024): android.graphics.Bitmap? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }

            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val scaleOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }

            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, scaleOptions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        SecurityManager.isRequestingPermission = false
        if (success && tempPhotoUri != null) {
            val bitmap = decodeAndScaleBitmap(tempPhotoUri!!)
            if (bitmap != null) {
                viewModel.processVisionAi(bitmap)
            }
        }
    }

    fun launchCameraIntent() {
        SecurityManager.isRequestingPermission = true
        try {
            val uri = createTempImageUri()
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            SecurityManager.isRequestingPermission = false
            android.widget.Toast.makeText(context, "No se pudo abrir la cámara.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSpeechIntent() {
        SecurityManager.isRequestingPermission = true
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Di tu saldo inicial...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            SecurityManager.isRequestingPermission = false
            android.widget.Toast.makeText(context, "No se pudo abrir el dictado por voz.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        SecurityManager.isRequestingPermission = false
        if (isGranted) {
            launchSpeechIntent()
        } else {
            android.widget.Toast.makeText(context, "Se requiere permiso de micrófono.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        SecurityManager.isRequestingPermission = false
        if (isGranted) {
            launchCameraIntent()
        } else {
            android.widget.Toast.makeText(context, "Se requiere permiso de cámara.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    if (uiState.showEditActiveSessionDialog) {
        EditActiveSessionDialog(
            currentStartTimestamp = if (uiState.config.isSessionActive) uiState.config.sessionStartTimestamp else System.currentTimeMillis(),
            currentInitialBalance = if (uiState.config.isSessionActive) uiState.config.sessionInitialBalance else uiState.config.balance,
            onDismiss = { viewModel.setShowEditActiveSessionDialog(false) },
            onConfirm = { newTimestamp, newInitialBalance ->
                if (uiState.config.isSessionActive) {
                    viewModel.updateActiveSession(newTimestamp, newInitialBalance)
                } else {
                    viewModel.startCustomSession(newInitialBalance, newTimestamp)
                }
                viewModel.setShowEditActiveSessionDialog(false)
            }
        )
    }

    if (uiState.showQuickTileStartModal) {
        com.example.ui.components.QuickTileStartModal(
            onDismiss = { viewModel.setShowQuickTileStartModal(false) },
            onTraditionalStart = {
                viewModel.setShowQuickTileStartModal(false)
                viewModel.startSession()
            },
            onCustomStart = {
                viewModel.setShowQuickTileStartModal(false)
                viewModel.setShowEditActiveSessionDialog(true)
            },
            onVoiceStart = {
                viewModel.setShowQuickTileStartModal(false)
                val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasAudio) {
                    launchSpeechIntent()
                } else {
                    SecurityManager.isRequestingPermission = true
                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            },
            onCameraStart = {
                viewModel.setShowQuickTileStartModal(false)
                val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasCamera) {
                    launchCameraIntent()
                } else {
                    SecurityManager.isRequestingPermission = true
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }
        )
    }

    if (uiState.showVoiceAiDialog && uiState.voiceAiResult != null) {
        com.example.ui.components.VoiceAiResultDialog(
            result = uiState.voiceAiResult!!,
            onDismiss = { viewModel.setShowVoiceAiDialog(false) },
            onConfirm = { balance, timestamp ->
                viewModel.setShowVoiceAiDialog(false)
                viewModel.startCustomSession(balance, timestamp)
            }
        )
    }

    if (uiState.showVisionAiDialog && uiState.visionAiResult != null) {
        com.example.ui.components.VisionAiResultDialog(
            bitmap = uiState.capturedBitmap,
            result = uiState.visionAiResult!!,
            onDismiss = { viewModel.setShowVisionAiDialog(false) },
            onRetakePhoto = {
                viewModel.setShowVisionAiDialog(false)
                launchCameraIntent()
            },
            onConfirm = { balance, timestamp ->
                viewModel.setShowVisionAiDialog(false)
                viewModel.startCustomSession(balance, timestamp)
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

    if (showPinAuthDialog) {
        PinAuthenticationDialog(
            onDismiss = {
                showPinAuthDialog = false
                pendingAuthenticatedAction = null
            },
            onSuccess = {
                val action = pendingAuthenticatedAction
                showPinAuthDialog = false
                pendingAuthenticatedAction = null
                action?.invoke()
            }
        )
    }
}
