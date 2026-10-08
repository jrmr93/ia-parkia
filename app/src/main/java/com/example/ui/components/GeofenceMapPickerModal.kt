package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.sinh
import kotlin.math.tan

@SuppressLint("MissingPermission")
fun fetchBestDeviceLocation(context: Context, onResult: (Double, Double, String) -> Unit) {
    try {
        val finePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (finePerm != PackageManager.PERMISSION_GRANTED && coarsePerm != PackageManager.PERMISSION_GRANTED) {
            onResult(0.0, 0.0, "Se requiere permiso de ubicación.")
            return
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        if (!isGpsEnabled) {
            onResult(0.0, 0.0, "El servicio de ubicación (GPS) está desactivado en el teléfono.")
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onResult(loc.latitude, loc.longitude, "Ubicación GPS obtenida con éxito")
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            onResult(last.latitude, last.longitude, "Ubicación obtenida (última posición)")
                        } else {
                            onResult(0.0, 0.0, "Buscando señal GPS... Reintenta en unos segundos.")
                        }
                    }.addOnFailureListener {
                        onResult(0.0, 0.0, "Error al consultar ubicación")
                    }
                }
            }
            .addOnFailureListener {
                onResult(0.0, 0.0, "Error al consultar GPS: ${it.localizedMessage}")
            }
    } catch (e: Exception) {
        onResult(0.0, 0.0, "Excepción al obtener ubicación: ${e.localizedMessage}")
    }
}

@SuppressLint("MissingPermission")
fun getBestDeviceLocation(context: Context, onLocationFound: (Double, Double) -> Unit) {
    fetchBestDeviceLocation(context) { lat, lng, _ ->
        if (lat != 0.0 && lng != 0.0) {
            onLocationFound(lat, lng)
        }
    }
}

private fun lonToTileX(lon: Double, zoom: Int): Double {
    return (lon + 180.0) / 360.0 * (1 shl zoom)
}

private fun latToTileY(lat: Double, zoom: Int): Double {
    val latRad = Math.toRadians(lat)
    return (1.0 - log(tan(latRad) + 1.0 / cos(latRad), E_BASE) / PI) / 2.0 * (1 shl zoom)
}

private val E_BASE = kotlin.math.E

private fun tileXToLon(x: Double, zoom: Int): Double {
    return x / (1 shl zoom) * 360.0 - 180.0
}

private fun tileYToLat(y: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * y / (1 shl zoom)
    return Math.toDegrees(atan(sinh(n)))
}

@Composable
fun NativeOpenStreetMap(
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
    onLocationChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var zoom by remember { mutableIntStateOf(17) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val lat = if (latitude != 0.0) latitude else -0.180653
    val lng = if (longitude != 0.0) longitude else -78.467838

    val tileDpVal = (256f / density)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE2E8F0))
            .onGloballyPositioned { containerSize = it.size }
            .pointerInput(lat, lng, zoom, containerSize, density) {
                detectTapGestures { tapOffset ->
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        val centerX = containerSize.width / 2f
                        val centerY = containerSize.height / 2f

                        val dxPx = (tapOffset.x - centerX).toDouble()
                        val dyPx = (tapOffset.y - centerY).toDouble()

                        val cx = lonToTileX(lng, zoom)
                        val cy = latToTileY(lat, zoom)

                        val newCx = cx + (dxPx / 256.0)
                        val newCy = cy + (dyPx / 256.0)

                        val nLng = tileXToLon(newCx, zoom)
                        val nLat = tileYToLat(newCy, zoom)

                        onLocationChange(nLat, nLng)
                    }
                }
            }
            .pointerInput(lat, lng, zoom, containerSize, density) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (containerSize.width > 0 && containerSize.height > 0) {
                        val cx = lonToTileX(lng, zoom)
                        val cy = latToTileY(lat, zoom)

                        val newCx = cx - (dragAmount.x / 256.0)
                        val newCy = cy - (dragAmount.y / 256.0)

                        val nLng = tileXToLon(newCx, zoom)
                        val nLat = tileYToLat(newCy, zoom)

                        onLocationChange(nLat, nLng)
                    }
                }
            }
    ) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            val centerXDp = (containerSize.width / 2f) / density
            val centerYDp = (containerSize.height / 2f) / density

            val cx = lonToTileX(lng, zoom)
            val cy = latToTileY(lat, zoom)

            val centerTileX = floor(cx).toInt()
            val centerTileY = floor(cy).toInt()

            val maxTiles = 1 shl zoom
            val subdomains = arrayOf("a", "b", "c")

            for (dx in -3..3) {
                for (dy in -3..3) {
                    val tileX = (centerTileX + dx).let { (it % maxTiles + maxTiles) % maxTiles }
                    val tileY = centerTileY + dy

                    if (tileY in 0 until maxTiles) {
                        val leftDp = (centerXDp + (centerTileX + dx - cx) * tileDpVal).dp
                        val topDp = (centerYDp + (centerTileY + dy - cy) * tileDpVal).dp

                        val sub = subdomains[Math.abs(tileX + tileY) % 3]
                        val tileUrl = "https://$sub.tile.openstreetmap.org/$zoom/$tileX/$tileY.png"

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(tileUrl)
                                .setHeader("User-Agent", "Parkia-AndroidApp/1.0")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset(x = leftDp, y = topDp)
                                .size(tileDpVal.dp)
                        )
                    }
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val latRad = Math.toRadians(lat)
                val metersPerPixel = (156543.03392 * cos(latRad)) / (1 shl zoom)
                val radiusPx = (radiusMeters / metersPerPixel).toFloat().coerceIn(20f, size.width / 1.5f)

                drawCircle(
                    color = Color(0x334F46E5),
                    center = Offset(centerX, centerY),
                    radius = radiusPx
                )
                drawCircle(
                    color = Color(0xFF4F46E5),
                    center = Offset(centerX, centerY),
                    radius = radiusPx,
                    style = Stroke(
                        width = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )

                drawCircle(
                    color = Color(0x44EF4444),
                    center = Offset(centerX, centerY),
                    radius = 24f
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    center = Offset(centerX, centerY),
                    radius = 8f
                )
            }

            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
                    .offset(y = (-20).dp)
            )
        }

        // Map Zoom Controls & Badge (Max Zoom Level 19 for OpenStreetMap tiles)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = { if (zoom < 19) zoom += 1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = Color(0xFF1E293B))
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                IconButton(
                    onClick = { if (zoom > 12) zoom -= 1 },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = Color(0xFF1E293B))
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color(0xCC0F172A)
        ) {
            Text(
                text = "OpenStreetMap • Zoom $zoom (Máx 19)",
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
fun GeofenceInteractiveMapCard(
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
    onLocationChange: (Double, Double) -> Unit,
    onRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted = permissions.values.any { it }
        if (isGranted) {
            Toast.makeText(context, "Buscando señal GPS...", Toast.LENGTH_SHORT).show()
            fetchBestDeviceLocation(context) { nLat, nLng, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                if (nLat != 0.0 && nLng != 0.0) {
                    onLocationChange(nLat, nLng)
                }
            }
        } else {
            Toast.makeText(context, "Permiso de ubicación denegado en el dispositivo", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header bar inside card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Visualización Geocerca",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF334155)
                    )
                }

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("GPS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Map View Box (Expands in weight)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
            ) {
                NativeOpenStreetMap(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                    onLocationChange = onLocationChange,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Preset Radius Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Radio:",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
                listOf(50f, 100f, 200f, 500f, 1000f).forEach { preset ->
                    val isSelected = (radiusMeters == preset)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onRadiusChange(preset) }
                    ) {
                        Text(
                            text = "${preset.toInt()}m",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Radius Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cobertura Geocerca:",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "${radiusMeters.toInt()} m",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4F46E5)
                )
            }

            Slider(
                value = radiusMeters,
                onValueChange = { onRadiusChange(it) },
                valueRange = 50f..1000f,
                steps = 18,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF4F46E5),
                    activeTrackColor = Color(0xFF4F46E5)
                ),
                modifier = Modifier
                    .height(24.dp)
                    .testTag("embedded_geofence_radius_slider")
            )

            // Coordinates text readout
            Text(
                text = String.format(Locale.US, "Coords: %.6f, %.6f", latitude, longitude),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun GeofenceMapPickerModal(
    initialLatitude: Double,
    initialLongitude: Double,
    initialRadiusMeters: Float,
    onDismiss: () -> Unit,
    onConfirm: (latitude: Double, longitude: Double, radiusMeters: Float) -> Unit
) {
    var lat by remember { mutableStateOf(if (initialLatitude != 0.0) initialLatitude else -0.180653) }
    var lng by remember { mutableStateOf(if (initialLongitude != 0.0) initialLongitude else -78.467838) }
    var rad by remember { mutableFloatStateOf(if (initialRadiusMeters > 0f) initialRadiusMeters else 100f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(6.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Modal Header with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Seleccionar Ubicación en Mapa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Toca o arrastra para ubicar el parqueadero",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Interactive Map View container expanding flexibly
                Box(modifier = Modifier.weight(1f)) {
                    GeofenceInteractiveMapCard(
                        latitude = lat,
                        longitude = lng,
                        radiusMeters = rad,
                        onLocationChange = { nLat, nLng ->
                            lat = nLat
                            lng = nLng
                        },
                        onRadiusChange = { nRad ->
                            rad = nRad
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sticky Action Buttons at bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("cancel_map_modal_button")
                    ) {
                        Text("Cancelar", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { onConfirm(lat, lng, rad) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .testTag("confirm_map_modal_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirmar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
