package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RechargeHistoryItem
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SlateBlue
import com.example.ui.theme.SuccessEmerald
import com.example.util.ReceiptAnalysisResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RechargeOptionsDialog(
    onDismiss: () -> Unit,
    onScanWithCamera: () -> Unit,
    onUploadFromGallery: () -> Unit,
    onManualInput: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SlateBlue.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = SlateBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Recarga de Saldo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A)
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
                    text = "Selecciona cómo deseas registrar tu recarga de saldo:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Opción 1: Escanear con Cámara (Auto-captura y encuadre IA)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onScanWithCamera()
                        }
                        .testTag("btn_recharge_scan_camera"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateBlue.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, SlateBlue.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SlateBlue,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Tomar Foto con Cámara",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SlateBlue
                                ) {
                                    Text(
                                        text = "AUTO-ENCUADRE IA",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Auto-captura, encuadra y extrae el saldo total del comprobante con Gemini IA.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Opción 2: Subir Imagen de Galería
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onUploadFromGallery()
                        }
                        .testTag("btn_recharge_upload_gallery"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0F172A).copy(alpha = 0.08f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = Color(0xFF0F172A),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subir Imagen de Comprobante",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Carga una foto de tu recibo desde la galería para analizarla con Gemini IA.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Opción 3: Ingreso Manual
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onManualInput()
                        }
                        .testTag("btn_recharge_manual"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF64748B).copy(alpha = 0.1f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ingreso Manual (Sin comprobante)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Ingresa el valor directamente por teclado si no dispones de un recibo.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF64748B))
            }
        }
    )
}

@Composable
fun ConfirmRechargeFromAiDialog(
    result: ReceiptAnalysisResult,
    photoPath: String,
    onViewPhoto: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, photoPath: String, note: String) -> Unit
) {
    var amountText by remember {
        mutableStateOf(result.totalAmount?.let { String.format(Locale.US, "%.2f", it) } ?: "")
    }
    var noteText by remember {
        mutableStateOf(result.note.ifBlank { "Comprobante de Recarga" })
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val bitmap = remember(photoPath) {
        if (photoPath.isNotBlank()) {
            val file = File(photoPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = SuccessEmerald.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = SuccessEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "Confirmar Recarga de Saldo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF0F172A)
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
                AiEngineBadge(
                    usedGemini = result.usedGemini,
                    engineName = result.engineName,
                    engineDetail = result.engineDetail
                )

                // Miniatura interactiva de la foto del comprobante
                if (bitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, SlateBlue.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewPhoto(photoPath) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Comprobante",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Comprobante capturado",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Toca para ver en pantalla completa y hacer zoom",
                                    fontSize = 11.sp,
                                    color = SlateBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = "Zoom",
                                tint = SlateBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (result.totalAmount == null) {
                    Text(
                        text = "⚠️ No se detectó automáticamente el saldo total. Ingresa el valor manualmente para recargar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Saldo Total a Recargar (USD)") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_recharge_amount_input"),
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let { Text(it, color = ErrorRed) }
                    }
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota / Banco / Referencia (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("confirm_recharge_note_input")
                )

                Text(
                    text = "🔒 La foto se adjuntará permanentemente en el registro histórico de recargas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.replace(",", ".").toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "Ingresa un monto válido mayor a 0."
                    } else {
                        onConfirm(amount, photoPath, noteText.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_recharge_apply")
            ) {
                Text("Confirmar y Recargar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF64748B))
            }
        }
    )
}

@Composable
fun RechargeHistoryDialog(
    rechargeList: List<RechargeHistoryItem>,
    onDismiss: () -> Unit,
    onAddNewRecharge: () -> Unit,
    onViewReceipt: (String) -> Unit,
    onEditRecharge: (RechargeHistoryItem) -> Unit,
    onDeleteRecharge: (RechargeHistoryItem) -> Unit
) {
    val dateTimeFormat = remember { SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()) }
    val totalRecharged = remember(rechargeList) { rechargeList.sumOf { it.amount } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(min = 280.dp, max = 560.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header Row (Fixed)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SlateBlue.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = SlateBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Registro de Recargas",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF1F5F9)
                                ) {
                                    Text(
                                        text = "${rechargeList.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Historial ordenado cronológicamente",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary Row (Fixed, Outside LazyColumn)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, SuccessEmerald.copy(alpha = 0.35f)),
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
                                text = "TOTAL HISTÓRICO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = String.format(Locale.US, "$%.2f USD", totalRecharged),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D),
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = onAddNewRecharge,
                            colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_history_add_new_recharge")
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Nueva", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable List of Records (LazyColumn)
                if (rechargeList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Sin recargas registradas",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Toca '+ Nueva' para agregar una recarga manual o con foto.",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 6.dp)
                    ) {
                        items(rechargeList, key = { it.id }) { item ->
                            RechargeItemRow(
                                item = item,
                                dateTimeFormat = dateTimeFormat,
                                onViewReceipt = onViewReceipt,
                                onEdit = { onEditRecharge(item) },
                                onDelete = { onDeleteRecharge(item) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Footer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64748B))
                    ) {
                        Text("Cerrar", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RechargeItemRow(
    item: RechargeHistoryItem,
    dateTimeFormat: SimpleDateFormat,
    onViewReceipt: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val hasReceipt = item.receiptPhotoPath.isNotBlank()
    val bitmap = remember(item.receiptPhotoPath) {
        if (hasReceipt) {
            val file = File(item.receiptPhotoPath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } else null
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recharge_row_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or Photo Thumbnail
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, SlateBlue.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { onViewReceipt(item.receiptPhotoPath) }
                        .testTag("recharge_thumb_${item.id}"),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Miniatura de Comprobante",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(topStart = 4.dp),
                        modifier = Modifier.size(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SlateBlue.copy(alpha = 0.08f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            tint = SlateBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Central details: Amount, Tag, Date, and Note
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "+$%.2f USD", item.amount),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Color(0xFF15803D),
                        fontFamily = FontFamily.Monospace
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (hasReceipt) SlateBlue.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
                    ) {
                        Text(
                            text = if (hasReceipt) "📷 Con Foto" else "✏️ Manual",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasReceipt) SlateBlue else Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dateTimeFormat.format(Date(item.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                    if (item.note.isNotBlank()) {
                        Text(
                            text = " • ${item.note}",
                            fontSize = 10.sp,
                            color = Color(0xFF334155),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Action Buttons (Compact touch targets)
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_edit_recharge_${item.id}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = SlateBlue,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_delete_recharge_${item.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = ErrorRed,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditRechargeDialog(
    item: RechargeHistoryItem,
    onDismiss: () -> Unit,
    onConfirm: (newAmount: Double, newNote: String, newTimestamp: Long) -> Unit
) {
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", item.amount)) }
    var noteText by remember { mutableStateOf(item.note) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = SlateBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Editar Registro de Recarga", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ Nota: Al modificar el monto de esta recarga, el saldo disponible del sistema se ajustará automáticamente por la diferencia.",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMessage = null
                    },
                    label = { Text("Monto de Recarga (USD)") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("edit_recharge_amount_input"),
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let { Text(it, color = ErrorRed) }
                    }
                )

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Nota / Referencia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_recharge_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.replace(",", ".").toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "Ingresa un monto válido mayor a 0."
                    } else {
                        onConfirm(amount, noteText.trim(), item.timestamp)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SlateBlue),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_save_edit_recharge")
            ) {
                Text("Guardar Cambios", color = Color.White, fontWeight = FontWeight.Bold)
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
fun DeleteRechargeDialog(
    item: RechargeHistoryItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "¿Eliminar recarga de $${String.format(Locale.US, "%.2f", item.amount)} USD?",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Esta acción eliminará el registro de recarga de forma permanente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF334155)
                )
                Text(
                    text = "⚠️ Tu saldo disponible se reducirá en $${String.format(Locale.US, "%.2f", item.amount)} USD y se borrará la imagen adjunta si existe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_delete_recharge")
            ) {
                Text("Sí, Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar", color = Color(0xFF475569))
            }
        }
    )
}
