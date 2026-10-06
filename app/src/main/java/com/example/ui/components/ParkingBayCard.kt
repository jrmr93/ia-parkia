package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
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
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import com.example.ui.theme.SuccessEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParkingBayCard(
    isSessionActive: Boolean,
    sessionStartTimestamp: Long,
    sessionInitialBalance: Double,
    elapsedSeconds: Long,
    accumulatedCost: Double,
    rateAmount: Double,
    rateMinutes: Int,
    onRequestStartSession: () -> Unit,
    onRequestStopSession: () -> Unit,
    onEditActiveSessionClick: () -> Unit
) {
    val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val bayStatus = if (isSessionActive) "EN SESIÓN ACTIVA" else "LIBRE"
    val statusColor = if (isSessionActive) SlateBlue else SuccessEmerald

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("parking_bay_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.5.dp,
            if (isSessionActive) statusColor.copy(alpha = pulseAlpha) else Color(0xFFE2E8F0)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row: Bay Title & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = statusColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.LocalParking,
                                contentDescription = "Bahía de Estacionamiento",
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Bahía de Estacionamiento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Tarifa: $${String.format(Locale.US, "%.2f", rateAmount)} USD por bloque de ${rateMinutes} min",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // State Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor),
                    modifier = Modifier.testTag("bay_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                                .then(
                                    if (isSessionActive) Modifier.alpha(pulseAlpha) else Modifier
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bayStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Visual Parking Slot Graphic
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Parking Bay Lines and Car
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(
                                BorderStroke(
                                    2.dp,
                                    if (isSessionActive) statusColor else Color(0xFFCBD5E1)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSessionActive) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = "Vehículo Estacionado",
                                    tint = SlateBlue,
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "VEHÍCULO ESTACIONADO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SlateBlue,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "BAHÍA 01 • EN SERVICIO",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF0F172A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Default.LocalParking,
                                    contentDescription = "Bahía Libre",
                                    tint = SuccessEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "BAHÍA DISPONIBLE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = SuccessEmerald,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Lista para iniciar sesión",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Timer and Cost Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timer
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "TIEMPO TRANSCURRIDO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val hours = elapsedSeconds / 3600
                            val minutes = (elapsedSeconds % 3600) / 60
                            val seconds = elapsedSeconds % 60
                            val formattedTimer = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                            Text(
                                text = formattedTimer,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("session_timer_display")
                            )
                        }

                        // Cost
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "COSTO DE BLOQUE(S)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.US, "$%.2f USD", accumulatedCost),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSessionActive) statusColor else Color(0xFF0F172A),
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("session_cost_display")
                            )
                        }
                    }

                    // Active session info + REQUIRED: Modify button back in active session panel
                    if (isSessionActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Entrada: ${timeFormat.format(Date(sessionStartTimestamp))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "Saldo con el que entró: $${String.format(Locale.US, "%.2f", sessionInitialBalance)} USD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }

                                // REQUIRED: Botón de modificar en el panel de sesión activa
                                OutlinedButton(
                                    onClick = onEditActiveSessionClick,
                                    border = BorderStroke(1.dp, SlateBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("edit_active_session_bay_button")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Modificar Sesión Activa",
                                        tint = SlateBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Modificar",
                                        fontSize = 12.sp,
                                        color = SlateBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons with Confirmation
            if (!isSessionActive) {
                Button(
                    onClick = onRequestStartSession,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SlateBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_session_button")
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Iniciar Sesión",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Iniciar Sesión de Parqueo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Button(
                    onClick = onRequestStopSession,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("stop_session_button")
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Finalizar y Cobrar",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Finalizar y Cobrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
