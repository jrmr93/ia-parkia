package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import com.example.ui.theme.WarningAmber
import com.example.util.SecurityManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun RechargeCustomDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.AccountBalanceWallet,
                contentDescription = "Recargar Saldo",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Recargar Saldo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column {
                Text(
                    "Ingresa el valor en USD que deseas agregar al monedero digital:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        amountText = input
                        errorMessage = null
                    },
                    label = { Text("Monto en USD (ej. 5.50)") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let {
                            Text(it, color = ErrorRed)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_recharge_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = amountText.replace(',', '.').toDoubleOrNull()
                    if (value == null || value <= 0.0) {
                        errorMessage = "Ingresa un monto válido mayor a 0."
                    } else {
                        onConfirm(value)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("confirm_custom_recharge_button")
            ) {
                Text("Confirmar Recarga", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_custom_recharge_button")
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun ResetBalanceDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.RestartAlt,
                contentDescription = "Reiniciar Saldo",
                tint = ErrorRed
            )
        },
        title = {
            Text(
                "¿Reiniciar Saldo a $0.00?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Text(
                "Esta acción pondrá tu monedero digital en $0.00 USD. Si hay una sesión activa, se detendrá inmediatamente.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF334155)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                modifier = Modifier.testTag("confirm_reset_balance_button")
            ) {
                Text("Sí, Reiniciar", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_reset_balance_button")
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun TariffSettingsDialog(
    currentAmount: Double,
    currentMinutes: Int,
    notificationsEnabled: Boolean,
    notificationIntervalMinutes: Int,
    currentTileLabel: String = "Parkia",
    currentGeminiApiKey: String = "",
    currentGeminiModel: String = "gemini-2.0-flash",
    onTestGeminiKey: ((apiKey: String, modelName: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        amount: Double,
        minutes: Int,
        notifyEnabled: Boolean,
        notifyInterval: Int,
        tileLabel: String,
        geminiApiKey: String,
        geminiModel: String
    ) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentAmount)) }
    var minutesText by remember { mutableStateOf(currentMinutes.toString()) }
    var notifyEnabled by remember { mutableStateOf(notificationsEnabled) }
    var intervalText by remember { mutableStateOf(notificationIntervalMinutes.toString()) }
    var tileLabelText by remember { mutableStateOf(currentTileLabel) }
    var geminiApiKeyText by remember { mutableStateOf(currentGeminiApiKey) }
    var geminiModelSelected by remember { mutableStateOf(currentGeminiModel.ifBlank { "gemini-2.0-flash" }) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isModelMenuExpanded by remember { mutableStateOf(false) }
    var isTestingKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusInfoMessage by remember { mutableStateOf<String?>(null) }
    var showChangePinModal by remember { mutableStateOf(false) }
    var showQuickTileInstructions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val modelOptions = remember {
        listOf(
            "Auto (Búsqueda automática)",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro",
            "gemini-2.0-pro-exp",
            "gemini-pro"
        )
    }

    if (showChangePinModal) {
        ChangePinDialog(onDismiss = { showChangePinModal = false })
    }

    if (showQuickTileInstructions) {
        QuickTileInstructionsDialog(onDismiss = { showQuickTileInstructions = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Configuración App",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Ajustes de Tarifa y Notificaciones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Tarifa por bloque de parqueo:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Tarifa en USD") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tariff_amount_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = {
                        minutesText = it
                        errorMessage = null
                    },
                    label = { Text("Minutos por bloque") },
                    suffix = { Text("min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tariff_minutes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Notificaciones de Saldo:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = SlateBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (notifyEnabled) "Notificaciones Activadas" else "Notificaciones Desactivadas",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                    }
                    Switch(
                        checked = notifyEnabled,
                        onCheckedChange = { notifyEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SlateBlue
                        ),
                        modifier = Modifier.testTag("toggle_notifications_switch")
                    )
                }

                if (notifyEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = {
                            intervalText = it
                            errorMessage = null
                        },
                        label = { Text("Frecuencia de notificación") },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notification_interval_input")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Ajustes Rápidos (Panel de Notificaciones):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = tileLabelText,
                    onValueChange = {
                        tileLabelText = it
                        errorMessage = null
                    },
                    label = { Text("Nombre del Acceso Rápido") },
                    placeholder = { Text("Ej. Parkia") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tile_label_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            com.example.service.ParkiaTileService.setTileComponentEnabled(context, true)
                            statusInfoMessage = "Acceso rápido activado."
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                try {
                                    val statusBarManager = context.getSystemService(android.content.Context.STATUS_BAR_SERVICE) as? android.app.StatusBarManager
                                    val componentName = android.content.ComponentName(context, com.example.service.ParkiaTileService::class.java)
                                    val cleanLabel = if (tileLabelText.isBlank()) "Parkia" else tileLabelText.trim()
                                    statusBarManager?.requestAddTileService(
                                        componentName,
                                        cleanLabel,
                                        android.graphics.drawable.Icon.createWithResource(context, com.example.R.drawable.ic_car),
                                        androidx.core.content.ContextCompat.getMainExecutor(context)
                                    ) { _ -> }
                                } catch (_: Exception) {}
                            }
                            showQuickTileInstructions = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SlateBlue),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_quick_tile_button")
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = SlateBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Crear / Agregar",
                            color = SlateBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            com.example.service.ParkiaTileService.setTileComponentEnabled(context, false)
                            statusInfoMessage = "Acceso rápido desactivado y eliminado del panel."
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ErrorRed),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("remove_quick_tile_button")
                    ) {
                        Text(
                            text = "Quitar / Eliminar",
                            color = ErrorRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                statusInfoMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = SlateBlue, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        tint = SlateBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Configuración de Google Gemini IA:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = geminiApiKeyText,
                    onValueChange = {
                        geminiApiKeyText = it
                        testResult = null
                    },
                    label = { Text("API Key de Gemini") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isApiKeyVisible) "Ocultar clave" else "Mostrar clave",
                                tint = Color(0xFF64748B)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input")
                )
                Text(
                    text = "Dejar en blanco para usar la clave por defecto (.env)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Modelo de IA Gemini:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = geminiModelSelected,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Modelo de IA") },
                        trailingIcon = {
                            IconButton(onClick = { isModelMenuExpanded = !isModelMenuExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar Modelo", tint = SlateBlue)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_model_selector")
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Transparent)
                            .testTag("gemini_model_selector_click")
                            .clickable { isModelMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = isModelMenuExpanded,
                        onDismissRequest = { isModelMenuExpanded = false }
                    ) {
                        modelOptions.forEach { modelName ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = modelName,
                                        fontWeight = if (modelName == geminiModelSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (modelName == geminiModelSelected) SlateBlue else Color(0xFF0F172A)
                                    )
                                },
                                onClick = {
                                    geminiModelSelected = modelName
                                    isModelMenuExpanded = false
                                    testResult = null
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        if (onTestGeminiKey != null) {
                            isTestingKey = true
                            testResult = null
                            onTestGeminiKey(geminiApiKeyText, geminiModelSelected) { success, message ->
                                isTestingKey = false
                                testResult = Pair(success, message)
                            }
                        }
                    },
                    enabled = !isTestingKey,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_gemini_key_button")
                ) {
                    if (isTestingKey) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = SlateBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probando conexión...", fontSize = 12.sp, color = SlateBlue)
                    } else {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = SlateBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probar API Key y Modelo", fontSize = 12.sp, color = SlateBlue, fontWeight = FontWeight.Bold)
                    }
                }

                testResult?.let { (success, message) ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (success) Color(0xFF16A34A) else ErrorRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message,
                                color = if (success) Color(0xFF15803D) else Color(0xFF991B1B),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Seguridad y PIN de Acceso:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showChangePinModal = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SlateBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pin_button")
                ) {
                    Icon(
                        Icons.Default.Pin,
                        contentDescription = null,
                        tint = SlateBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cambiar PIN de la App",
                        color = SlateBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull()
                    val minutes = minutesText.toIntOrNull()
                    val interval = intervalText.toIntOrNull()
                    val cleanTileLabel = if (tileLabelText.isBlank()) "Parkia" else tileLabelText.trim()
                    if (amount == null || amount <= 0.0 || minutes == null || minutes <= 0) {
                        errorMessage = "Ingresa valores numéricos válidos para la tarifa."
                    } else if (notifyEnabled && (interval == null || interval <= 0)) {
                        errorMessage = "Ingresa un intervalo de notificación válido mayor a 0 min."
                    } else {
                        onConfirm(
                            amount,
                            minutes,
                            notifyEnabled,
                            interval ?: 1,
                            cleanTileLabel,
                            geminiApiKeyText.trim(),
                            geminiModelSelected.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("confirm_tariff_settings_button")
            ) {
                Text("Guardar Ajustes", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_tariff_settings_button")
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

/**
 * Dialog to Reset History AND Balance to $0.00
 */
@Composable
fun ResetAllDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.RestartAlt,
                contentDescription = "Resetear Todo",
                tint = ErrorRed
            )
        },
        title = {
            Text(
                "¿Resetear Sesiones y Saldo?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Text(
                "Esta acción borrará todas las sesiones del historial y reiniciará el saldo del monedero a $0.00 USD.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF334155)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                modifier = Modifier.testTag("confirm_reset_all_button")
            ) {
                Text("Sí, Resetear Todo", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_reset_all_button")
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun ExhaustedBalanceAlertDialog(
    onDismiss: () -> Unit,
    onRechargeClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = "Saldo Agotado",
                tint = ErrorRed
            )
        },
        title = {
            Text(
                "¡Saldo Agotado!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )
        },
        text = {
            Column {
                Text(
                    "La sesión de estacionamiento se ha detenido automáticamente por falta de saldo para cubrir el siguiente bloque.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "La sesión ha sido guardada en el historial. Por favor recarga tu monedero para iniciar una nueva sesión de parqueo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onRechargeClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("recharge_from_exhausted_alert_button")
            ) {
                Text("Recargar Ahora", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_exhausted_alert_button")
            ) {
                Text("Entendido", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun NoFundsAlertDialog(
    onDismiss: () -> Unit,
    onRechargeClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Sin Saldo",
                tint = WarningAmber
            )
        },
        title = {
            Text(
                "Saldo Insuficiente",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Text(
                "No tienes saldo suficiente para cubrir al menos 1 bloque de parqueo. Recarga tu monedero antes de iniciar.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF334155)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onRechargeClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("recharge_from_nofunds_alert_button")
            ) {
                Text("Recargar Saldo", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_nofunds_alert_button")
            ) {
                Text("Cerrar", color = Color(0xFF475569))
            }
        }
    )
}

/**
 * Dialog to modify the active parking session entry date/time AND initial entry balance.
 */
@Composable
fun EditActiveSessionDialog(
    currentStartTimestamp: Long,
    currentInitialBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (newTimestamp: Long, newInitialBalance: Double) -> Unit
) {
    val context = LocalContext.current
    val fullFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    var selectedTimestamp by remember { mutableStateOf(currentStartTimestamp) }
    var initialBalanceText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", currentInitialBalance))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun openDateTimePicker() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = selectedTimestamp

        val datePicker = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                val timePicker = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        selectedTimestamp = cal.timeInMillis
                        errorMessage = null
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                )
                timePicker.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Edit,
                contentDescription = "Modificar Sesión",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Modificar Sesión Actual",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column {
                Text(
                    "Modifica el saldo inicial y selecciona la fecha y hora de entrada de la sesión activa:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Initial Balance input
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = {
                        initialBalanceText = it
                        errorMessage = null
                    },
                    label = { Text("Saldo inicial con el que entró") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_initial_balance_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Date & Time picker graphic button
                Text(
                    text = "Fecha y Hora de Entrada (Selección gráfica):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { openDateTimePicker() },
                    border = BorderStroke(1.dp, SlateBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_datetime_picker_button")
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = "Seleccionar fecha y hora",
                        tint = SlateBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = fullFormat.format(Date(selectedTimestamp)),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balanceVal = initialBalanceText.replace(',', '.').toDoubleOrNull()
                    if (balanceVal == null || balanceVal < 0.0) {
                        errorMessage = "Ingresa un saldo inicial válido."
                        return@Button
                    }
                    val now = System.currentTimeMillis()
                    if (selectedTimestamp > now) {
                        errorMessage = "La hora de entrada no puede ser en el futuro."
                        return@Button
                    }
                    onConfirm(selectedTimestamp, balanceVal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("confirm_edit_entry_time_button")
            ) {
                Text("Guardar Cambios", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_edit_entry_time_button")
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun ConfirmStartSessionDialog(
    rateAmount: Double,
    rateMinutes: Int,
    currentBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Iniciar Parqueo",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "¿Iniciar Sesión de Parqueo?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column {
                Text(
                    "Al iniciar se cobrará el valor del primer bloque de parqueo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "• Tarifa: $${String.format(Locale.US, "%.2f", rateAmount)} USD cada ${rateMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "• Saldo disponible: $${String.format(Locale.US, "%.2f", currentBalance)} USD",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("confirm_start_session_dialog_button")
            ) {
                Text("Iniciar Parqueo", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_start_session_dialog_button")
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun ConfirmStopSessionDialog(
    elapsedSeconds: Long,
    accumulatedCost: Double,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = "Finalizar Parqueo",
                tint = ErrorRed
            )
        },
        title = {
            Text(
                "¿Finalizar Sesión de Parqueo?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column {
                Text(
                    "Se cerrará la sesión activa, se registrará en el historial y se aplicará el cobro final:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "• Tiempo transcurrido: $timeFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Cobro total: $${String.format(Locale.US, "%.2f", accumulatedCost)} USD",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                modifier = Modifier.testTag("confirm_stop_session_dialog_button")
            ) {
                Text("Finalizar y Cobrar", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_stop_session_dialog_button")
            ) {
                Text("Continuar Parqueando", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun PinAuthenticationDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Autenticación PIN",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Autenticación de Seguridad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Ingresa tu PIN de 4 dígitos para autorizar esta acción:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // PIN Dots Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (isFilled) SlateBlue else Color(0xFFE2E8F0),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Keypad
                val onDigitClick: (String) -> Unit = { digit ->
                    errorMessage = null
                    if (enteredPin.length < 4) {
                        val next = enteredPin + digit
                        enteredPin = next
                        if (next.length == 4) {
                            if (SecurityManager.verifyPin(context, next)) {
                                onSuccess()
                            } else {
                                errorMessage = "PIN incorrecto. Intenta de nuevo."
                                enteredPin = ""
                            }
                        }
                    }
                }

                val onBackspaceClick: () -> Unit = {
                    errorMessage = null
                    if (enteredPin.isNotEmpty()) {
                        enteredPin = enteredPin.dropLast(1)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { digit ->
                                KeypadDialogButton(text = digit, onClick = { onDigitClick(digit) })
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(56.dp))
                        KeypadDialogButton(text = "0", onClick = { onDigitClick("0") })
                        Surface(
                            onClick = onBackspaceClick,
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Borrar",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasExistingPin = remember { SecurityManager.isPinConfigured(context) }

    // Step 0 = enter current pin, 1 = enter new pin, 2 = confirm new pin
    var step by remember { mutableStateOf(if (hasExistingPin) 0 else 1) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Pin,
                contentDescription = "Cambiar PIN",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Cambiar PIN de la App",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val instructionText = when (step) {
                    0 -> "Ingresa tu PIN actual:"
                    1 -> "Ingresa tu NUEVO PIN de 4 dígitos:"
                    else -> "Confirma tu NUEVO PIN de 4 dígitos:"
                }

                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                val activePin = when (step) {
                    0 -> currentPin
                    1 -> newPin
                    else -> confirmPin
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < activePin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    if (isFilled) SlateBlue else Color(0xFFE2E8F0),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                } else if (successMessage != null) {
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF16A34A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Keypad
                val onDigitClick: (String) -> Unit = { digit ->
                    errorMessage = null
                    when (step) {
                        0 -> {
                            if (currentPin.length < 4) {
                                val next = currentPin + digit
                                currentPin = next
                                if (next.length == 4) {
                                    if (SecurityManager.verifyPin(context, next)) {
                                        step = 1
                                    } else {
                                        errorMessage = "PIN actual incorrecto. Intenta de nuevo."
                                        currentPin = ""
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (newPin.length < 4) {
                                val next = newPin + digit
                                newPin = next
                                if (next.length == 4) {
                                    step = 2
                                }
                            }
                        }
                        2 -> {
                            if (confirmPin.length < 4) {
                                val next = confirmPin + digit
                                confirmPin = next
                                if (next.length == 4) {
                                    if (next == newPin) {
                                        SecurityManager.savePin(context, newPin)
                                        successMessage = "¡PIN actualizado correctamente!"
                                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                            onDismiss()
                                        }, 1200)
                                    } else {
                                        errorMessage = "Los PINs no coinciden. Intenta de nuevo."
                                        confirmPin = ""
                                        newPin = ""
                                        step = 1
                                    }
                                }
                            }
                        }
                    }
                }

                val onBackspaceClick: () -> Unit = {
                    errorMessage = null
                    when (step) {
                        0 -> if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1)
                        1 -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                        2 -> {
                            if (confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                            } else {
                                step = 1
                                newPin = ""
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { digit ->
                                KeypadDialogButton(text = digit, onClick = { onDigitClick(digit) })
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(56.dp))
                        KeypadDialogButton(text = "0", onClick = { onDigitClick("0") })
                        Surface(
                            onClick = onBackspaceClick,
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Borrar",
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}

@Composable
private fun KeypadDialogButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
fun QuickTileInstructionsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = "Acceso Rápido",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Ajustes Rápidos (Panel de Notificaciones)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                Text(
                    "Puedes agregar el acceso rápido 'Abrir Parkia' al panel superior de tu teléfono:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "1. Desliza la barra de estado hacia abajo para abrir los Ajustes Rápidos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "2. Presiona el botón del lápiz ✏️ o 'Editar'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "3. Busca el azulejo 'Abrir Parkia' 🚗 y arrástralo a tus accesos principales.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            val statusBarManager = context.getSystemService(android.content.Context.STATUS_BAR_SERVICE) as? android.app.StatusBarManager
                            val componentName = android.content.ComponentName(context, com.example.service.ParkiaTileService::class.java)
                            statusBarManager?.requestAddTileService(
                                componentName,
                                "Abrir Parkia",
                                android.graphics.drawable.Icon.createWithResource(context, com.example.R.drawable.ic_car),
                                androidx.core.content.ContextCompat.getMainExecutor(context)
                            ) { _ -> }
                        } catch (_: Exception) {}
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) "Agregar al Panel de Notificaciones" else "Entendido",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar", color = Color(0xFF475569))
            }
        }
    )
}
