package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletCard(
    balance: Double,
    availableTimeFormatted: String,
    isLowBalance: Boolean,
    estimatedExhaustion: String?,
    isSessionActive: Boolean,
    onQuickRecharge: (Double) -> Unit,
    onOpenCustomRecharge: () -> Unit,
    onOpenResetBalance: () -> Unit
) {
    val isExhausted = balance <= 0.0001

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("wallet_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            when {
                isExhausted -> ErrorRed.copy(alpha = 0.5f)
                isLowBalance -> WarningAmber.copy(alpha = 0.5f)
                else -> Color(0xFFE2E8F0)
            }
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row: Wallet title & Reset button
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
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = "Monedero Digital",
                                tint = SlateBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Monedero Digital",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Fondos para estacionamiento",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // Reset balance button
                OutlinedButton(
                    onClick = onOpenResetBalance,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorRed
                    ),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reset_balance_button")
                ) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Reiniciar Saldo",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reiniciar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Balance Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "SALDO DISPONIBLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "$%.2f", balance),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isExhausted) ErrorRed else Color(0xFF0F172A),
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isExhausted -> ErrorRed.copy(alpha = 0.12f)
                        isLowBalance -> WarningAmber.copy(alpha = 0.12f)
                        else -> SuccessEmerald.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isExhausted -> ErrorRed.copy(alpha = 0.6f)
                            isLowBalance -> WarningAmber.copy(alpha = 0.6f)
                            else -> SuccessEmerald.copy(alpha = 0.6f)
                        }
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isExhausted -> Icons.Default.ErrorOutline
                                isLowBalance -> Icons.Default.WarningAmber
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = when {
                                isExhausted -> ErrorRed
                                isLowBalance -> WarningAmber
                                else -> SuccessEmerald
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isExhausted -> "Saldo Agotado"
                                isLowBalance -> "Saldo Bajo"
                                else -> "Saldo Óptimo"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isExhausted -> ErrorRed
                                isLowBalance -> WarningAmber
                                else -> SuccessEmerald
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time Equivalence Banner (Always auto-calculated)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = "Equivalencia de tiempo",
                        tint = if (isExhausted) ErrorRed else SlateBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Equivalencia para parqueo continuo:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                        Text(
                            text = availableTimeFormatted,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isExhausted) ErrorRed else Color(0xFF0F172A)
                        )
                    }
                }
            }

            // REQUIRED: Estimated exhaustion date and time in Digital Wallet panel
            // Displayed ONLY when parking session is active
            AnimatedVisibility(
                visible = isSessionActive && estimatedExhaustion != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7), // Warm Amber Light
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wallet_estimated_exhaustion_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.HourglassBottom,
                                contentDescription = "Agotamiento estimado",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Fecha y hora estimada de agotamiento:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF78350F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = estimatedExhaustion ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                }
            }

            // Warning note if low or exhausted
            if (isExhausted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Tu saldo es $0.00 USD. Recarga saldo para poder estacionar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    fontSize = 12.sp
                )
            } else if (isLowBalance) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ Saldo bajo: te quedan menos de 30 minutos disponibles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Recharge Buttons
            Text(
                text = "Recarga Rápida de Saldo:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val quickAmounts = listOf(1.0, 2.0, 5.0, 10.0, 20.0)
                quickAmounts.forEach { amount ->
                    Button(
                        onClick = { onQuickRecharge(amount) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SlateBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("quick_recharge_${amount.toInt()}_button")
                    ) {
                        Text(
                            text = "+$${amount.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Custom amount button
                OutlinedButton(
                    onClick = onOpenCustomRecharge,
                    border = BorderStroke(1.dp, SlateBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("custom_recharge_button")
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SlateBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Otro monto...",
                        color = SlateBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
