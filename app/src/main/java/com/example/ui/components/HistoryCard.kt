package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ParkingHistoryItem
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryCard(
    historyList: List<ParkingHistoryItem>,
    isSessionActive: Boolean,
    sessionStartTimestamp: Long,
    sessionInitialBalance: Double,
    elapsedSeconds: Long,
    accumulatedCost: Double,
    totalSpentHistorical: Double,
    totalSessionsCount: Int,
    onResetAllClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    val infiniteTransition = rememberInfiniteTransition(label = "history_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_row_pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("history_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header: Title and Resetear Button (Renamed from Limpiar, resets history + balance to $0.00)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = SlateBlue.copy(alpha = 0.1f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Historial",
                                tint = SlateBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Registro de Sesiones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Historial y actividad de parqueo",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Botón "Resetear" (borra las sesiones y pone el saldo a $0.00)
                OutlinedButton(
                    onClick = onResetAllClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorRed
                    ),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reset_all_button")
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Resetear",
                        modifier = Modifier.size(16.dp),
                        tint = ErrorRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Resetear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Total Gastado & Sesiones Registradas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric 1: Total Gastado
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SlateBlue.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    contentDescription = null,
                                    tint = SlateBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "TOTAL GASTADO",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            val grandTotal = totalSpentHistorical + (if (isSessionActive) accumulatedCost else 0.0)
                            Text(
                                text = String.format(Locale.US, "$%.2f USD", grandTotal),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Metric 2: Número de Sesiones
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0F172A).copy(alpha = 0.08f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Numbers,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SESIONES REGISTRADAS",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            val totalCount = totalSessionsCount + (if (isSessionActive) 1 else 0)
                            Text(
                                text = "$totalCount sesión(es)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sesión de parqueo activa mostrada en el registro de sesiones (sin botón modificar aquí)
            if (isSessionActive) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEFF6FF), // Light Blue tint
                    border = BorderStroke(1.5.dp, SlateBlue.copy(alpha = pulseAlpha)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_session_history_item")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        // Header: Date and LIVE Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SlateBlue)
                                        .alpha(pulseAlpha)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateFormat.format(Date(sessionStartTimestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E3A8A)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SlateBlue.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "SESIÓN EN CURSO",
                                    color = SlateBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Time row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val entryTime = timeFormat.format(Date(sessionStartTimestamp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = SlateBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Entrada: $entryTime",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = String.format(Locale.US, "$%.2f USD", accumulatedCost),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = SlateBlue,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val hours = elapsedSeconds / 3600
                        val minutes = (elapsedSeconds % 3600) / 60
                        val seconds = elapsedSeconds % 60

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Duración en curso: ${String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Saldo inicial: $${String.format(Locale.US, "%.2f", sessionInitialBalance)} USD",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (historyList.isEmpty() && !isSessionActive) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aún no hay sesiones registradas",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Las sesiones finalizadas se guardarán aquí automáticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    historyList.forEach { item ->
                        HistoryItemRow(item, timeFormat, dateFormat)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: ParkingHistoryItem,
    timeFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat
) {
    val isExhausted = item.reasonEnded.contains("Agotado", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(
            1.dp,
            if (isExhausted) ErrorRed.copy(alpha = 0.4f) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Row 1: Date & Reason badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(item.startTimestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isExhausted) ErrorRed.copy(alpha = 0.12f) else SlateBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = item.reasonEnded,
                        color = if (isExhausted) ErrorRed else SlateBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Entry and Exit time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val entryTime = timeFormat.format(Date(item.startTimestamp))
                val exitTime = timeFormat.format(Date(item.endTimestamp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$entryTime  ➔  $exitTime",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Cost Charged
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = if (isExhausted) ErrorRed else SlateBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = String.format(Locale.US, "$%.2f USD", item.costCharged),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isExhausted) ErrorRed else Color(0xFF0F172A),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Duration
            val hours = item.durationSeconds / 3600
            val minutes = (item.durationSeconds % 3600) / 60
            val seconds = item.durationSeconds % 60
            val durationText = buildString {
                if (hours > 0) append("${hours}h ")
                if (minutes > 0 || hours > 0) append("${minutes}m ")
                append("${seconds}s")
            }

            Text(
                text = "Duración total: $durationText (${String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)})",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}
