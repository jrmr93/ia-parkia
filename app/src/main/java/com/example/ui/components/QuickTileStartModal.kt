package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AiRecognitionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QuickTileStartModal(
    onDismiss: () -> Unit,
    onTraditionalStart: () -> Unit,
    onCustomStart: () -> Unit,
    onVoiceStart: () -> Unit,
    onCameraStart: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Iniciar Sesión de Parqueo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Elige la modalidad para registrar el ingreso a tu parqueadero:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                QuickOptionCard(
                    title = "Sesión Tradicional",
                    description = "Iniciar con la tarifa actual y la hora del sistema.",
                    icon = Icons.Default.PlayArrow,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        onDismiss()
                        onTraditionalStart()
                    }
                )

                QuickOptionCard(
                    title = "Sesión Personalizada",
                    description = "Ingresar el saldo con el que entras y tu hora de entrada.",
                    icon = Icons.Default.Edit,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                        onDismiss()
                        onCustomStart()
                    }
                )

                QuickOptionCard(
                    title = "Comando de Voz (IA)",
                    description = "Di el saldo con el que entras (la hora será la actual).",
                    icon = Icons.Default.Mic,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = {
                        onDismiss()
                        onVoiceStart()
                    }
                )

                QuickOptionCard(
                    title = "Reconocimiento con Cámara (IA)",
                    description = "Escanea el ticket o pantalla para capturar saldo y hora (fecha actual).",
                    icon = Icons.Default.CameraAlt,
                    containerColor = Color(0xFFE8DEF8),
                    iconColor = Color(0xFF6750A4),
                    onClick = {
                        onDismiss()
                        onCameraStart()
                    }
                )
            }
        }
    )
}

@Composable
private fun QuickOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    containerColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AiEngineBadge(
    usedGemini: Boolean,
    engineName: String,
    engineDetail: String? = null
) {
    val backgroundColor = if (usedGemini) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val contentColor = if (usedGemini) Color(0xFF2E7D32) else Color(0xFFE65100)
    val icon = if (usedGemini) Icons.Default.AutoAwesome else Icons.Default.Build

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (usedGemini) "Motor: $engineName" else "Motor: $engineName",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
            if (!engineDetail.isNullOrBlank()) {
                Text(
                    text = engineDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp, start = 24.dp)
                )
            }
        }
    }
}

@Composable
fun VoiceAiResultDialog(
    result: AiRecognitionResult,
    onDismiss: () -> Unit,
    onConfirm: (balance: Double, timestamp: Long) -> Unit
) {
    val currentTimestamp = remember { System.currentTimeMillis() }
    val dateTimeFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    var balanceText by remember { mutableStateOf(result.balance?.let { String.format(Locale.US, "%.2f", it) } ?: "10.00") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Voz IA - Saldo Detectado")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiEngineBadge(
                    usedGemini = result.usedGemini,
                    engineName = result.engineName,
                    engineDetail = result.engineDetail
                )

                Text(
                    text = "Texto dictado: \"${result.rawText}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Saldo inicial ingresado ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Fecha y hora de ingreso: ${dateTimeFormat.format(Date(currentTimestamp))} (Hora actual)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bal = balanceText.replace(",", ".").toDoubleOrNull()
                    if (bal == null || bal <= 0.0) {
                        errorMessage = "Ingresa un saldo válido mayor a 0."
                        return@TextButton
                    }
                    onConfirm(bal, currentTimestamp)
                }
            ) {
                Text("Iniciar Sesión")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun VisionAiResultDialog(
    bitmap: Bitmap?,
    result: AiRecognitionResult,
    onDismiss: () -> Unit,
    onRetakePhoto: (() -> Unit)? = null,
    onConfirm: (balance: Double, timestamp: Long) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var balanceText by remember { mutableStateOf(result.balance?.let { String.format(Locale.US, "%.2f", it) } ?: "10.00") }
    var timeText by remember { mutableStateOf(timeFormat.format(Date(result.timestamp ?: System.currentTimeMillis()))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Visión IA Reconocida")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiEngineBadge(
                    usedGemini = result.usedGemini,
                    engineName = result.engineName,
                    engineDetail = result.engineDetail
                )

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captura de cámara",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }

                Text(
                    text = "Texto detectado en imagen: ${if (result.rawText.isBlank()) "(Ninguno)" else result.rawText.take(120)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Saldo capturado ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Hora de entrada capturada (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Fecha de ingreso: ${fullDateFormat.format(Date())} (Fecha actual de hoy)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val bal = balanceText.replace(",", ".").toDoubleOrNull()
                    if (bal == null || bal <= 0.0) {
                        errorMessage = "Ingresa un saldo válido mayor a 0."
                        return@TextButton
                    }
                    val parts = timeText.split(":")
                    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val seconds = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    val finalTs = com.example.util.combineTodayWithTime(hours, minutes, seconds)

                    onConfirm(bal, finalTs)
                }
            ) {
                Text("Iniciar Sesión")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRetakePhoto != null) {
                    TextButton(onClick = onRetakePhoto) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retomar Foto")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
            }
        }
    )
}
