package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
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
                "Recargar Saldo Personalizado",
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
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, minutes: Int) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", currentAmount)) }
    var minutesText by remember { mutableStateOf(currentMinutes.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Configurar Tarifa",
                tint = SlateBlue
            )
        },
        title = {
            Text(
                "Configurar Tarifa por Bloque",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column {
                Text(
                    "Define el valor cobrado por bloque de tiempo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull()
                    val minutes = minutesText.toIntOrNull()
                    if (amount == null || amount <= 0.0 || minutes == null || minutes <= 0) {
                        errorMessage = "Ingresa valores numéricos válidos mayores a 0."
                    } else {
                        onConfirm(amount, minutes)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                modifier = Modifier.testTag("confirm_tariff_settings_button")
            ) {
                Text("Guardar Tarifa", color = Color.White)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditActiveSessionDialog(
    currentStartTimestamp: Long,
    currentInitialBalance: Double,
    onDismiss: () -> Unit,
    onConfirm: (newTimestamp: Long, newInitialBalance: Double) -> Unit
) {
    val fullFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    var dateTimeText by remember {
        mutableStateOf(fullFormat.format(Date(currentStartTimestamp)))
    }
    var initialBalanceText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", currentInitialBalance))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                    "Modifica la fecha y hora de entrada y el saldo inicial con el que inició la sesión. El costo de bloques y saldo restante se recalcularán automáticamente.",
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

                Spacer(modifier = Modifier.height(10.dp))

                // Date/time input
                OutlinedTextField(
                    value = dateTimeText,
                    onValueChange = {
                        dateTimeText = it
                        errorMessage = null
                    },
                    label = { Text("Fecha y Hora de Entrada") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let {
                            Text(it, color = ErrorRed)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_entry_datetime_input")
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ajustes rápidos de entrada:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Hace 15m" to (15 * 60L * 1000L),
                        "Hace 30m" to (30 * 60L * 1000L),
                        "Hace 1h" to (60 * 60L * 1000L),
                        "Hace 2h" to (120 * 60L * 1000L),
                        "Ahora mismo" to 0L
                    )
                    presets.forEach { (label, offsetMs) ->
                        OutlinedButton(
                            onClick = {
                                val newTime = System.currentTimeMillis() - offsetMs
                                dateTimeText = fullFormat.format(Date(newTime))
                                errorMessage = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.testTag("preset_${label.replace(" ", "_")}_button")
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = SlateBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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

                    try {
                        val parsed = fullFormat.parse(dateTimeText)
                        if (parsed == null) {
                            errorMessage = "Formato de fecha inválido. Usa dd/MM/yyyy HH:mm:ss"
                        } else {
                            val now = System.currentTimeMillis()
                            if (parsed.time > now) {
                                errorMessage = "La hora de entrada no puede ser en el futuro."
                            } else {
                                onConfirm(parsed.time, balanceVal)
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error al interpretar la fecha. Usa dd/MM/yyyy HH:mm:ss"
                    }
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
