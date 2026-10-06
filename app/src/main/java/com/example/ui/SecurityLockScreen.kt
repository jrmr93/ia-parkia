package com.example.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import com.example.util.SecurityManager

@Composable
fun SecurityLockScreen(
    isInitialSetup: Boolean,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmingStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val canUseBiometrics = remember {
        SecurityManager.canAuthenticateBiometrics(context)
    }

    // Auto-launch biometric prompt if unlocking and biometrics available
    LaunchedEffect(Unit) {
        if (!isInitialSetup && canUseBiometrics && activity != null) {
            SecurityManager.launchBiometricPrompt(
                activity = activity,
                onSuccess = onAuthenticated,
                onError = { /* handled gracefully; user can use PIN or click button */ }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("security_screen_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = SlateBlue.copy(alpha = 0.12f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isInitialSetup) Icons.Default.Pin else Icons.Default.Lock,
                            contentDescription = "Seguridad Parkia",
                            tint = SlateBlue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isInitialSetup) {
                        if (isConfirmingStep) "Confirmar PIN de Acceso" else "Crear PIN de Seguridad"
                    } else {
                        "Acceso Seguro a Parkia"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isInitialSetup) {
                        if (isConfirmingStep) "Vuelve a ingresar el PIN de 4 dígitos para confirmar."
                        else "Ingresa un PIN de 4 dígitos para proteger tu cuenta."
                    } else {
                        "Ingresa tu PIN o utiliza la autenticación biométrica del sistema."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots Display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentLength = if (isConfirmingStep) confirmPin.length else enteredPin.length
                    for (i in 0 until 4) {
                        val isFilled = i < currentLength
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(
                                    if (isFilled) SlateBlue else Color(0xFFE2E8F0),
                                    shape = CircleShape
                                )
                                .testTag("pin_dot_$i")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error Message
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

                Spacer(modifier = Modifier.height(12.dp))

                // Keypad (1 to 9, Biometrics/Empty, 0, Backspace)
                val onDigitClick: (String) -> Unit = { digit ->
                    errorMessage = null
                    if (isInitialSetup) {
                        if (!isConfirmingStep) {
                            if (enteredPin.length < 4) {
                                val next = enteredPin + digit
                                enteredPin = next
                                if (next.length == 4) {
                                    isConfirmingStep = true
                                }
                            }
                        } else {
                            if (confirmPin.length < 4) {
                                val next = confirmPin + digit
                                confirmPin = next
                                if (next.length == 4) {
                                    if (next == enteredPin) {
                                        SecurityManager.savePin(context, enteredPin)
                                        onAuthenticated()
                                    } else {
                                        errorMessage = "Los PINs no coinciden. Intenta de nuevo."
                                        confirmPin = ""
                                        enteredPin = ""
                                        isConfirmingStep = false
                                    }
                                }
                            }
                        }
                    } else {
                        if (enteredPin.length < 4) {
                            val next = enteredPin + digit
                            enteredPin = next
                            if (next.length == 4) {
                                if (SecurityManager.verifyPin(context, next)) {
                                    onAuthenticated()
                                } else {
                                    errorMessage = "PIN incorrecto. Intenta de nuevo."
                                    enteredPin = ""
                                }
                            }
                        }
                    }
                }

                val onBackspaceClick: () -> Unit = {
                    errorMessage = null
                    if (isInitialSetup && isConfirmingStep) {
                        if (confirmPin.isNotEmpty()) {
                            confirmPin = confirmPin.dropLast(1)
                        } else {
                            isConfirmingStep = false
                            enteredPin = ""
                        }
                    } else {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9")
                    )

                    rows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            row.forEach { digit ->
                                KeypadButton(
                                    text = digit,
                                    onClick = { onDigitClick(digit) }
                                )
                            }
                        }
                    }

                    // Bottom Row: Biometric icon / 0 / Backspace
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Key: Biometric Button (if not setup and supported)
                        if (!isInitialSetup && canUseBiometrics && activity != null) {
                            Surface(
                                shape = CircleShape,
                                color = SlateBlue.copy(alpha = 0.1f),
                                modifier = Modifier.size(62.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("launch_biometrics_button")
                                ) {
                                    Button(
                                        onClick = {
                                            SecurityManager.launchBiometricPrompt(
                                                activity = activity,
                                                onSuccess = onAuthenticated,
                                                onError = { err -> errorMessage = err }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = SlateBlue
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.Default.Fingerprint,
                                            contentDescription = "Autenticación Biométrica",
                                            modifier = Modifier.size(28.dp),
                                            tint = SlateBlue
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.size(62.dp))
                        }

                        // Middle Key: 0
                        KeypadButton(
                            text = "0",
                            onClick = { onDigitClick("0") }
                        )

                        // Right Key: Backspace
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF1F5F9),
                            modifier = Modifier.size(62.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("keypad_backspace")
                            ) {
                                Button(
                                    onClick = onBackspaceClick,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color(0xFF475569)
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Borrar",
                                        modifier = Modifier.size(22.dp),
                                        tint = Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }

                if (!isInitialSetup && canUseBiometrics && activity != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            SecurityManager.launchBiometricPrompt(
                                activity = activity,
                                onSuccess = onAuthenticated,
                                onError = { err -> errorMessage = err }
                            )
                        },
                        border = BorderStroke(1.dp, SlateBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("use_fingerprint_action_button")
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = SlateBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Usar Huella Digital",
                            color = SlateBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.size(62.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .testTag("keypad_digit_$text")
        ) {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF0F172A)
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}
