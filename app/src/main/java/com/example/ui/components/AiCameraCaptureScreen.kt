package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

@Composable
fun AiCameraCaptureScreen(
    onDismiss: () -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onFallbackToSystemCamera: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var currentZoom by remember { mutableFloatStateOf(5.0f) }
    var minZoom by remember { mutableFloatStateOf(1.0f) }
    var maxZoom by remember { mutableFloatStateOf(10.0f) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Initialize CameraX and auto-zoom to 5x
    LaunchedEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                camera = cam

                // Observe hardware zoom capabilities
                cam.cameraInfo.zoomState.observe(lifecycleOwner) { state ->
                    state?.let {
                        minZoom = it.minZoomRatio
                        maxZoom = it.maxZoomRatio
                    }
                }

                // Automatically set zoom to 5x by default
                val initialZoomState = cam.cameraInfo.zoomState.value
                val maxZ = initialZoomState?.maxZoomRatio ?: 10.0f
                val minZ = initialZoomState?.minZoomRatio ?: 1.0f
                val targetZoom = 5.0f.coerceIn(minZ, maxZ)
                cam.cameraControl.setZoomRatio(targetZoom)
                currentZoom = targetZoom
            } catch (e: Exception) {
                Log.e("AiCameraCapture", "Error al iniciar CameraX", e)
                Toast.makeText(context, "Error al iniciar la cámara con IA", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("ai_camera_capture_screen")
        ) {
            // Camera Preview with Pinch-to-Zoom gesture
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera, minZoom, maxZoom) {
                        detectTransformGestures { _, _, zoom, _ ->
                            camera?.let { cam ->
                                val newZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                                currentZoom = newZoom
                                cam.cameraControl.setZoomRatio(newZoom)
                            }
                        }
                    }
            )

            // Target Overlay with transparent center cutout
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxWidth = 280.dp.toPx()
                val boxHeight = 180.dp.toPx()
                val left = (size.width - boxWidth) / 2f
                val top = (size.height - boxHeight) / 2.3f

                val holePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, left + boxWidth, top + boxHeight),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                        )
                    )
                }

                // Semi-transparent scrim outside target box
                clipPath(holePath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.55f))
                }

                // Corner bracket accents
                val strokeW = 4.dp.toPx()
                val cornerLen = 28.dp.toPx()
                val accentColor = Color.White

                // Top-Left
                drawLine(accentColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                drawLine(accentColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)
                // Top-Right
                drawLine(accentColor, Offset(left + boxWidth, top), Offset(left + boxWidth - cornerLen, top), strokeW)
                drawLine(accentColor, Offset(left + boxWidth, top), Offset(left + boxWidth, top + cornerLen), strokeW)
                // Bottom-Left
                drawLine(accentColor, Offset(left, top + boxHeight), Offset(left + cornerLen, top + boxHeight), strokeW)
                drawLine(accentColor, Offset(left, top + boxHeight), Offset(left, top + boxHeight - cornerLen), strokeW)
                // Bottom-Right
                drawLine(accentColor, Offset(left + boxWidth, top + boxHeight), Offset(left + boxWidth - cornerLen, top + boxHeight), strokeW)
                drawLine(accentColor, Offset(left + boxWidth, top + boxHeight), Offset(left + boxWidth, top + boxHeight - cornerLen), strokeW)
            }

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_ai_camera")) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar Cámara",
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Zoom 5x Automático (IA)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(
                        onClick = {
                            camera?.let { cam ->
                                val nextFlash = !isFlashOn
                                isFlashOn = nextFlash
                                cam.cameraControl.enableTorch(nextFlash)
                            }
                        },
                        modifier = Modifier.testTag("btn_toggle_flash_ai_camera")
                    ) {
                        Icon(
                            if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Linterna",
                            tint = if (isFlashOn) Color(0xFFFACC15) else Color.White
                        )
                    }
                }
            }

            // Guide text under framing box
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 220.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Apunta al saldo o ticket de entrada",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Bottom Controls Section (Zoom Presets, Shutter Button, Fallback)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom Selector Presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    val presets = listOf(1.0f, 2.0f, 5.0f, 8.0f)
                    presets.forEach { preset ->
                        val isSelected = abs(currentZoom - preset) < 0.3f
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF4F46E5) else Color.Black.copy(alpha = 0.55f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color.White else Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    camera?.let { cam ->
                                        val target = preset.coerceIn(minZoom, maxZoom)
                                        currentZoom = target
                                        cam.cameraControl.setZoomRatio(target)
                                    }
                                }
                                .testTag("btn_zoom_${preset.toInt()}x")
                        ) {
                            Text(
                                text = if (preset == 5.0f) "5x ★" else "${preset.toInt()}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Shutter Button
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.9f else 1.0f, label = "shutter_scale")

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(scale)
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = !isCapturing
                        ) {
                            if (isCapturing) return@clickable
                            isCapturing = true
                            val capture = imageCapture
                            if (capture == null) {
                                isCapturing = false
                                Toast.makeText(context, "Cámara no lista", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }

                            val executor = ContextCompat.getMainExecutor(context)
                            capture.takePicture(
                                executor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        try {
                                            val rawBitmap = imageProxy.toBitmap()
                                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                            val finalBitmap = if (rotationDegrees != 0) {
                                                val matrix = Matrix().apply {
                                                    postRotate(rotationDegrees.toFloat())
                                                }
                                                Bitmap.createBitmap(
                                                    rawBitmap,
                                                    0,
                                                    0,
                                                    rawBitmap.width,
                                                    rawBitmap.height,
                                                    matrix,
                                                    true
                                                )
                                            } else {
                                                rawBitmap
                                            }
                                            onPhotoCaptured(finalBitmap)
                                        } catch (t: Throwable) {
                                            Log.e("AiCameraCapture", "Error procesando foto", t)
                                            Toast.makeText(context, "Error procesando foto", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            imageProxy.close()
                                            isCapturing = false
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
                                        Log.e("AiCameraCapture", "Error al capturar imagen", exception)
                                        Toast.makeText(context, "Error al capturar foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        .testTag("btn_shutter_ai_camera")
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = Color(0xFF4F46E5),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Tomar Foto",
                            tint = Color(0xFF1E293B),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Fallback option to system camera
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.clickable { onFallbackToSystemCamera() }
                ) {
                    Text(
                        text = "Usar cámara del sistema",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
