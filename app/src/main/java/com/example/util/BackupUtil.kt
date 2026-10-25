package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.ParkingConfig
import com.example.data.ParkingHistoryItem
import com.example.data.RechargeHistoryItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ParsedParkiaBackup(
    val config: ParkingConfig,
    val parkingHistory: List<ParkingHistoryItem>,
    val rechargeHistory: List<RechargeHistoryItem>,
    val photosCount: Int,
    val receiptsCount: Int,
    val preferences: Map<String, Any?> = emptyMap()
)

object BackupUtil {
    private const val TAG = "BackupUtil"
    private const val METADATA_ENTRY_NAME = "backup_metadata.json"
    private const val PHOTOS_DIR_ENTRY = "photos/"
    private const val RECEIPTS_DIR_ENTRY = "receipts/"

    /**
     * Exporta el respaldo total del sistema (configuración, saldo, sesiones,
     * registro de recargas, preferencias de seguridad y fotos/comprobantes físicos)
     * en un archivo ZIP estándar.
     */
    fun exportFullBackup(
        context: Context,
        config: ParkingConfig,
        parkingHistory: List<ParkingHistoryItem>,
        rechargeHistory: List<RechargeHistoryItem>,
        outputStream: OutputStream
    ) {
        val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
        try {
            val root = JSONObject()
            root.put("app", "Parkia")
            root.put("version", 1)
            root.put("export_date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            root.put("parking_history_count", parkingHistory.size)
            root.put("recharge_history_count", rechargeHistory.size)

            // 1. Exportar Configuración Completa de Parkia
            val configObj = JSONObject().apply {
                put("id", config.id)
                put("balance", config.balance)
                put("rateAmount", config.rateAmount)
                put("rateMinutes", config.rateMinutes)
                put("isSessionActive", config.isSessionActive)
                put("sessionStartTimestamp", config.sessionStartTimestamp)
                put("sessionInitialBalance", config.sessionInitialBalance)
                put("lastSessionEndTimestamp", config.lastSessionEndTimestamp)
                put("lastSessionExitBalance", config.lastSessionExitBalance)
                put("lastSessionDurationSeconds", config.lastSessionDurationSeconds)
                put("lastProcessedTimestamp", config.lastProcessedTimestamp)
                put("elapsedSeconds", config.elapsedSeconds)
                put("accumulatedCost", config.accumulatedCost)
                put("notificationsEnabled", config.notificationsEnabled)
                put("notificationIntervalMinutes", config.notificationIntervalMinutes)
                put("tileLabel", config.tileLabel)
                put("customGeminiApiKey", config.customGeminiApiKey)
                put("customGeminiModel", config.customGeminiModel)
                put("isGeminiEnabled", config.isGeminiEnabled)
                put("quickTileBiometricEnabled", config.quickTileBiometricEnabled)
                put("globalSecurityAuthEnabled", config.globalSecurityAuthEnabled)
                put("ttsAnnouncementsEnabled", config.ttsAnnouncementsEnabled)
                put("geofenceEnabled", config.geofenceEnabled)
                put("geofenceLatitude", config.geofenceLatitude)
                put("geofenceLongitude", config.geofenceLongitude)
                put("geofenceRadiusMeters", config.geofenceRadiusMeters.toDouble())
                put("registeredNfcTagId", config.registeredNfcTagId)
                put("nfcStrictMatchingEnabled", config.nfcStrictMatchingEnabled)
                put("lastSessionPhotoFileName", if (config.lastSessionPhotoPath.isNotBlank()) File(config.lastSessionPhotoPath).name else "")
            }

            // 2. Exportar Preferencias de Seguridad y PIN
            val prefs = context.getSharedPreferences("parkia_security_prefs", Context.MODE_PRIVATE)
            val prefsObj = JSONObject()
            for ((key, value) in prefs.all) {
                when (value) {
                    is Boolean -> prefsObj.put(key, value)
                    is Int -> prefsObj.put(key, value)
                    is Long -> prefsObj.put(key, value)
                    is Float -> prefsObj.put(key, value.toDouble())
                    is Double -> prefsObj.put(key, value)
                    is String -> prefsObj.put(key, value)
                }
            }
            val currentPin = prefs.getString("security_pin", null)
            if (!currentPin.isNullOrBlank()) {
                prefsObj.put("security_pin", currentPin)
                configObj.put("security_pin", currentPin)
            }
            root.put("config", configObj)
            root.put("security_preferences", prefsObj)

            // 3. Exportar Historial de Sesiones de Parqueo
            val photosToPack = mutableSetOf<File>()
            val parkingArray = JSONArray()
            for (item in parkingHistory) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("startTimestamp", item.startTimestamp)
                obj.put("endTimestamp", item.endTimestamp)
                obj.put("durationSeconds", item.durationSeconds)
                obj.put("costCharged", item.costCharged)
                obj.put("reasonEnded", item.reasonEnded)
                obj.put("initialBalance", item.initialBalance)

                if (item.photoPath.isNotBlank()) {
                    val file = File(item.photoPath)
                    if (file.exists() && file.isFile) {
                        photosToPack.add(file)
                        obj.put("photoFileName", file.name)
                    } else {
                        obj.put("photoFileName", "")
                    }
                } else {
                    obj.put("photoFileName", "")
                }
                parkingArray.put(obj)
            }
            root.put("parking_history", parkingArray)

            // Si hay foto en la sesión activa, incluirla también
            if (config.lastSessionPhotoPath.isNotBlank()) {
                val activePhotoFile = File(config.lastSessionPhotoPath)
                if (activePhotoFile.exists() && activePhotoFile.isFile) {
                    photosToPack.add(activePhotoFile)
                }
            }

            // 4. Exportar Registro de Recargas de Saldo
            val receiptsToPack = mutableSetOf<File>()
            val rechargesArray = JSONArray()
            for (r in rechargeHistory) {
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("amount", r.amount)
                obj.put("timestamp", r.timestamp)
                obj.put("note", r.note)

                if (r.receiptPhotoPath.isNotBlank()) {
                    val file = File(r.receiptPhotoPath)
                    if (file.exists() && file.isFile) {
                        receiptsToPack.add(file)
                        obj.put("receiptFileName", file.name)
                    } else {
                        obj.put("receiptFileName", "")
                    }
                } else {
                    obj.put("receiptFileName", "")
                }
                rechargesArray.put(obj)
            }
            root.put("recharge_history", rechargesArray)

            // 5. Escribir entrada del archivo JSON de metadatos en el ZIP
            val metadataBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            val metadataEntry = ZipEntry(METADATA_ENTRY_NAME)
            zipOut.putNextEntry(metadataEntry)
            zipOut.write(metadataBytes)
            zipOut.closeEntry()

            // 6. Empacar todas las fotos de parqueo en photos/
            val buffer = ByteArray(16 * 1024)
            for (photoFile in photosToPack) {
                try {
                    val entry = ZipEntry(PHOTOS_DIR_ENTRY + photoFile.name)
                    zipOut.putNextEntry(entry)
                    FileInputStream(photoFile).use { fis ->
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            zipOut.write(buffer, 0, read)
                        }
                    }
                    zipOut.closeEntry()
                } catch (e: Exception) {
                    Log.e(TAG, "Error empacando foto ${photoFile.name}: ${e.message}")
                }
            }

            // 7. Empacar todos los comprobantes de recarga en receipts/
            for (receiptFile in receiptsToPack) {
                try {
                    val entry = ZipEntry(RECEIPTS_DIR_ENTRY + receiptFile.name)
                    zipOut.putNextEntry(entry)
                    FileInputStream(receiptFile).use { fis ->
                        var read: Int
                        while (fis.read(buffer).also { read = it } != -1) {
                            zipOut.write(buffer, 0, read)
                        }
                    }
                    zipOut.closeEntry()
                } catch (e: Exception) {
                    Log.e(TAG, "Error empacando comprobante ${receiptFile.name}: ${e.message}")
                }
            }

            zipOut.finish()
            Log.d(TAG, "Respaldo total exportado con éxito: ${photosToPack.size} fotos, ${receiptsToPack.size} comprobantes")
        } finally {
            try {
                zipOut.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Restaura el respaldo completo a partir de un flujo InputStream de archivo ZIP.
     * Extrae las imágenes físicas a las carpetas internas de la app y parsea los registros.
     */
    fun restoreFullBackup(context: Context, inputStream: InputStream): ParsedParkiaBackup {
        val photosDir = File(context.filesDir, "photos").apply { if (!exists()) mkdirs() }
        val receiptsDir = ReceiptStorageUtil.getReceiptsDir(context)

        val zipIn = ZipInputStream(BufferedInputStream(inputStream))
        var jsonContent: String? = null
        var extractedPhotosCount = 0
        var extractedReceiptsCount = 0

        val buffer = ByteArray(16 * 1024)
        var entry: ZipEntry? = zipIn.nextEntry

        while (entry != null) {
            val entryName = entry.name
            if (entryName == METADATA_ENTRY_NAME) {
                val baos = ByteArrayOutputStream()
                var count: Int
                while (zipIn.read(buffer).also { count = it } != -1) {
                    baos.write(buffer, 0, count)
                }
                jsonContent = baos.toString(Charsets.UTF_8.name())
            } else if (entryName.startsWith(PHOTOS_DIR_ENTRY) && !entry.isDirectory) {
                val fileName = File(entryName).name
                val targetFile = File(photosDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    var count: Int
                    while (zipIn.read(buffer).also { count = it } != -1) {
                        fos.write(buffer, 0, count)
                    }
                }
                extractedPhotosCount++
            } else if (entryName.startsWith(RECEIPTS_DIR_ENTRY) && !entry.isDirectory) {
                val fileName = File(entryName).name
                val targetFile = File(receiptsDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    var count: Int
                    while (zipIn.read(buffer).also { count = it } != -1) {
                        fos.write(buffer, 0, count)
                    }
                }
                extractedReceiptsCount++
            }
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        if (jsonContent.isNullOrBlank()) {
            throw IllegalArgumentException("El archivo ZIP no contiene un respaldo válido de Parkia (falta $METADATA_ENTRY_NAME).")
        }

        val root = JSONObject(jsonContent)
        val appName = root.optString("app", "Parkia")
        if (!appName.equals("Parkia", ignoreCase = true)) {
            Log.w(TAG, "El archivo indica app: $appName, continuando restauración.")
        }

        // Restaurar preferencias de seguridad y PIN
        val parsedPreferences = mutableMapOf<String, Any?>()
        val appPrefs = context.getSharedPreferences("parkia_security_prefs", Context.MODE_PRIVATE)
        val editor = appPrefs.edit()
        editor.clear()

        val prefsObj = root.optJSONObject("security_preferences")
        if (prefsObj != null) {
            val keys = prefsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = prefsObj.get(k)
                parsedPreferences[k] = v
                when (v) {
                    is Boolean -> editor.putBoolean(k, v)
                    is String -> editor.putString(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is Double -> editor.putFloat(k, v.toFloat())
                    is Number -> editor.putFloat(k, v.toFloat())
                }
            }
        }

        val configObj = root.optJSONObject("config") ?: JSONObject()
        val configPin = configObj.optString("security_pin", "")
        if (configPin.isNotBlank() && !parsedPreferences.containsKey("security_pin")) {
            parsedPreferences["security_pin"] = configPin
            editor.putString("security_pin", configPin)
        }
        editor.apply()

        // Restaurar Configuración
        val lastPhotoName = configObj.optString("lastSessionPhotoFileName", "")
        val restoredLastPhotoPath = if (lastPhotoName.isNotBlank()) {
            val f = File(photosDir, lastPhotoName)
            if (f.exists()) f.absolutePath else ""
        } else ""

        val restoredConfig = ParkingConfig(
            id = 1,
            balance = configObj.optDouble("balance", 0.0),
            rateAmount = configObj.optDouble("rateAmount", 0.10),
            rateMinutes = configObj.optInt("rateMinutes", 30),
            isSessionActive = configObj.optBoolean("isSessionActive", false),
            sessionStartTimestamp = configObj.optLong("sessionStartTimestamp", 0L),
            sessionInitialBalance = configObj.optDouble("sessionInitialBalance", 0.0),
            lastSessionEndTimestamp = configObj.optLong("lastSessionEndTimestamp", 0L),
            lastSessionExitBalance = configObj.optDouble("lastSessionExitBalance", 0.0),
            lastSessionDurationSeconds = configObj.optLong("lastSessionDurationSeconds", 0L),
            lastProcessedTimestamp = configObj.optLong("lastProcessedTimestamp", 0L),
            elapsedSeconds = configObj.optLong("elapsedSeconds", 0L),
            accumulatedCost = configObj.optDouble("accumulatedCost", 0.0),
            notificationsEnabled = configObj.optBoolean("notificationsEnabled", true),
            notificationIntervalMinutes = configObj.optInt("notificationIntervalMinutes", 1),
            tileLabel = configObj.optString("tileLabel", "Parkia"),
            customGeminiApiKey = configObj.optString("customGeminiApiKey", ""),
            customGeminiModel = configObj.optString("customGeminiModel", "gemini-2.0-flash"),
            isGeminiEnabled = configObj.optBoolean("isGeminiEnabled", true),
            quickTileBiometricEnabled = configObj.optBoolean("quickTileBiometricEnabled", true),
            globalSecurityAuthEnabled = configObj.optBoolean("globalSecurityAuthEnabled", true),
            ttsAnnouncementsEnabled = configObj.optBoolean("ttsAnnouncementsEnabled", true),
            geofenceEnabled = configObj.optBoolean("geofenceEnabled", false),
            geofenceLatitude = configObj.optDouble("geofenceLatitude", 0.0),
            geofenceLongitude = configObj.optDouble("geofenceLongitude", 0.0),
            geofenceRadiusMeters = configObj.optDouble("geofenceRadiusMeters", 100.0).toFloat(),
            registeredNfcTagId = configObj.optString("registeredNfcTagId", ""),
            nfcStrictMatchingEnabled = configObj.optBoolean("nfcStrictMatchingEnabled", false),
            lastSessionPhotoPath = restoredLastPhotoPath
        )

        // Restaurar Historial de Sesiones
        val parkingArray = root.optJSONArray("parking_history") ?: JSONArray()
        val parsedParkingHistory = mutableListOf<ParkingHistoryItem>()
        for (i in 0 until parkingArray.length()) {
            val obj = parkingArray.getJSONObject(i)
            val pName = obj.optString("photoFileName", "")
            val finalPhotoPath = if (pName.isNotBlank()) {
                val f = File(photosDir, pName)
                if (f.exists()) f.absolutePath else ""
            } else ""

            val item = ParkingHistoryItem(
                id = obj.optLong("id", 0L),
                startTimestamp = obj.optLong("startTimestamp", 0L),
                endTimestamp = obj.optLong("endTimestamp", 0L),
                durationSeconds = obj.optLong("durationSeconds", 0L),
                costCharged = obj.optDouble("costCharged", 0.0),
                reasonEnded = obj.optString("reasonEnded", "Finalizado por usuario"),
                photoPath = finalPhotoPath,
                initialBalance = obj.optDouble("initialBalance", 0.0)
            )
            parsedParkingHistory.add(item)
        }

        // Restaurar Registro de Recargas
        val rechargeArray = root.optJSONArray("recharge_history") ?: JSONArray()
        val parsedRecharges = mutableListOf<RechargeHistoryItem>()
        for (i in 0 until rechargeArray.length()) {
            val obj = rechargeArray.getJSONObject(i)
            val rName = obj.optString("receiptFileName", "")
            val finalReceiptPath = if (rName.isNotBlank()) {
                val f = File(receiptsDir, rName)
                if (f.exists()) f.absolutePath else ""
            } else ""

            val recharge = RechargeHistoryItem(
                id = obj.optLong("id", 0L),
                amount = obj.optDouble("amount", 0.0),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                receiptPhotoPath = finalReceiptPath,
                note = obj.optString("note", "")
            )
            parsedRecharges.add(recharge)
        }

        return ParsedParkiaBackup(
            config = restoredConfig,
            parkingHistory = parsedParkingHistory,
            rechargeHistory = parsedRecharges,
            photosCount = extractedPhotosCount,
            receiptsCount = extractedReceiptsCount,
            preferences = parsedPreferences
        )
    }
}
