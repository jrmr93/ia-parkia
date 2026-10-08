package com.example.util

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AiRecognitionResult(
    val balance: Double? = null,
    val timestamp: Long? = null,
    val rawText: String = "",
    val usedGemini: Boolean = false,
    val engineName: String = "Fallback Local",
    val engineDetail: String? = null
)

object GeminiAiParser {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val availableModels = listOf(
        "gemini-3.5-flash",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest",
        "gemini-1.5-pro",
        "gemini-2.0-pro-exp",
        "gemini-pro"
    )

    private fun getModelsToTry(preferredModel: String?): List<String> {
        val list = ArrayList<String>()
        if (!preferredModel.isNullOrBlank() && !preferredModel.contains("Auto")) {
            list.add(preferredModel.trim())
        }
        availableModels.forEach { m ->
            if (!list.contains(m)) list.add(m)
        }
        return list
    }

    suspend fun testApiKey(apiKey: String, selectedModel: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val keyToUse = if (cleanKey.isBlank() || cleanKey == "DEFAULT_KEY") BuildConfig.GEMINI_API_KEY.trim() else cleanKey
        if (keyToUse.isBlank() || keyToUse == "DEFAULT_KEY") {
            return@withContext Pair(false, "Ingresa una API Key válida de Google AI Studio.")
        }
        val modelsToTry = getModelsToTry(selectedModel)
        var lastErr = ""
        for (modelToUse in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelToUse:generateContent?key=$keyToUse"
                val jsonPayload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", "Responde ÚNICAMENTE la palabra: OK") })
                            })
                        })
                    })
                }

                val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(respBody)
                        val text = extractTextFromJson(json)
                        if (text.isNotBlank()) {
                            return@withContext Pair(true, "¡Conexión Exitosa con $modelToUse!")
                        }
                    } else {
                        val errMsg = extractErrorMessageFromJson(respBody) ?: "HTTP ${response.code}"
                        lastErr = "($modelToUse): $errMsg"
                    }
                }
            } catch (t: Throwable) {
                lastErr = "($modelToUse): ${t.localizedMessage ?: t.message}"
            }
        }
        return@withContext Pair(false, "Error: $lastErr")
    }

    private fun extractTextFromJson(json: JSONObject): String {
        try {
            val candidates = json.optJSONArray("candidates") ?: return ""
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content") ?: return ""
                val parts = content.optJSONArray("parts") ?: return ""
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "")
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun extractErrorMessageFromJson(jsonStr: String): String? {
        try {
            val json = JSONObject(jsonStr)
            if (json.has("error")) {
                val errObj = json.getJSONObject("error")
                val msg = errObj.optString("message", "")
                val code = errObj.optInt("code", 0)
                if (msg.isNotBlank()) return "[$code] $msg"
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun parseSpeech(
        rawText: String,
        customApiKey: String? = null,
        customModel: String? = null,
        isGeminiEnabled: Boolean = true
    ): AiRecognitionResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!isGeminiEnabled) {
            return@withContext VoiceAiParser.parseSpeech(rawText).copy(
                usedGemini = false,
                engineName = "Fallback Local (Dictado Android)",
                engineDetail = "La API de Gemini IA está desactivada en la configuración de la app."
            )
        }
        val cleanKey = customApiKey?.trim()
        val apiKey = if (!cleanKey.isNullOrBlank() && cleanKey != "DEFAULT_KEY") cleanKey else BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank() || apiKey == "DEFAULT_KEY") {
            return@withContext VoiceAiParser.parseSpeech(rawText).copy(
                usedGemini = false,
                engineName = "Fallback Local (Dictado Android)",
                engineDetail = "GEMINI_API_KEY no configurada en .env ni en ajustes"
            )
        }

        val prompt = """
            Analiza el siguiente texto dictado por un usuario de estacionamiento.
            Extrae ÚNICAMENTE el saldo o monto numérico expresado (ej: 25.50).
            Texto: "$rawText"
            Responde ÚNICAMENTE un objeto JSON válido con la forma:
            {
              "balance": 25.50
            }
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
        }

        val modelsToTry = getModelsToTry(customModel)
        var lastError: String? = null

        for (modelName in modelsToTry) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder().url(url).post(requestBody).build()

                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(respBody)
                        val responseText = extractTextFromJson(json)
                        val jsonStart = responseText.indexOf("{")
                        val jsonEnd = responseText.lastIndexOf("}")
                        if (jsonStart != -1 && jsonEnd != -1) {
                            val jsonString = responseText.substring(jsonStart, jsonEnd + 1)
                            val parsedJson = JSONObject(jsonString)
                            val balance = if (parsedJson.has("balance") && !parsedJson.isNull("balance")) parsedJson.getDouble("balance") else null
                            return@withContext AiRecognitionResult(
                                balance = balance,
                                timestamp = System.currentTimeMillis(),
                                rawText = rawText,
                                usedGemini = true,
                                engineName = "Gemini IA ($modelName)"
                            )
                        } else if (responseText.isNotBlank()) {
                            val parsed = VoiceAiParser.parseSpeech(rawText)
                            return@withContext parsed.copy(
                                usedGemini = true,
                                engineName = "Gemini IA ($modelName)"
                            )
                        }
                    } else {
                        val err = extractErrorMessageFromJson(respBody) ?: "HTTP ${response.code}"
                        lastError = "($modelName): $err"
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                lastError = "($modelName): ${t.localizedMessage ?: t.message ?: t.javaClass.simpleName}"
            }
        }

        return@withContext VoiceAiParser.parseSpeech(rawText).copy(
            usedGemini = false,
            engineName = "Fallback Local (Dictado Android)",
            engineDetail = lastError ?: "Error al conectar con Gemini"
        )
    }

    suspend fun processImage(
        bitmap: Bitmap,
        customApiKey: String? = null,
        customModel: String? = null,
        isGeminiEnabled: Boolean = true
    ): AiRecognitionResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!isGeminiEnabled) {
            val localResult = processImageLocal(bitmap)
            return@withContext localResult.copy(
                usedGemini = false,
                engineName = "Fallback Local (ML Kit OCR)",
                engineDetail = "La API de Gemini IA está desactivada en la configuración de la app."
            )
        }
        val cleanKey = customApiKey?.trim()
        val apiKey = if (!cleanKey.isNullOrBlank() && cleanKey != "DEFAULT_KEY") cleanKey else BuildConfig.GEMINI_API_KEY.trim()
        var lastError: String? = if (apiKey.isBlank() || apiKey == "DEFAULT_KEY") "GEMINI_API_KEY no configurada" else null

        if (!apiKey.isBlank() && apiKey != "DEFAULT_KEY") {
            val base64Image = bitmapToBase64(bitmap)
            val promptText = """
                Analiza esta imagen de una pantalla o comprobante de estacionamiento.
                Extrae ÚNICAMENTE el saldo/monto a pagar y la HORA de entrada (ejemplo "14:30").
                Responde ÚNICAMENTE en formato JSON con la siguiente estructura:
                {
                  "balance": 15.00,
                  "hour": 14,
                  "minute": 30,
                  "rawText": "Texto detectado en la pantalla"
                }
            """.trimIndent()

            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", promptText) })
                            put(JSONObject().apply {
                                put("inline_data", JSONObject().apply {
                                    put("mime_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
            }

            val modelsToTry = getModelsToTry(customModel)
            for (modelName in modelsToTry) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                    val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    val request = Request.Builder().url(url).post(requestBody).build()

                    httpClient.newCall(request).execute().use { response ->
                        val respBody = response.body?.string() ?: ""
                        if (response.isSuccessful) {
                            val json = JSONObject(respBody)
                            val responseText = extractTextFromJson(json)
                            val jsonStart = responseText.indexOf("{")
                            val jsonEnd = responseText.lastIndexOf("}")
                            if (jsonStart != -1 && jsonEnd != -1) {
                                val jsonString = responseText.substring(jsonStart, jsonEnd + 1)
                                val parsedJson = JSONObject(jsonString)
                                val balance = if (parsedJson.has("balance") && !parsedJson.isNull("balance")) parsedJson.getDouble("balance") else null
                                val detectedHour = if (parsedJson.has("hour") && !parsedJson.isNull("hour")) parsedJson.getInt("hour") else null
                                val detectedMinute = if (parsedJson.has("minute") && !parsedJson.isNull("minute")) parsedJson.getInt("minute") else null
                                val detectedText = if (parsedJson.has("rawText")) parsedJson.getString("rawText") else responseText

                                val timestamp = if (detectedHour != null && detectedMinute != null) {
                                    combineTodayWithTime(detectedHour, detectedMinute)
                                } else {
                                    System.currentTimeMillis()
                                }

                                return@withContext AiRecognitionResult(
                                    balance = balance,
                                    timestamp = timestamp,
                                    rawText = detectedText,
                                    usedGemini = true,
                                    engineName = "Gemini IA ($modelName)"
                                )
                            } else if (responseText.isNotBlank()) {
                                val parsed = VisionAiParser.parseExtractedText(responseText)
                                return@withContext parsed.copy(
                                    rawText = responseText,
                                    usedGemini = true,
                                    engineName = "Gemini IA ($modelName)"
                                )
                            }
                        } else {
                            val err = extractErrorMessageFromJson(respBody) ?: "HTTP ${response.code}"
                            lastError = "($modelName): $err"
                        }
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    lastError = "($modelName): ${t.localizedMessage ?: t.message ?: t.javaClass.simpleName}"
                }
            }
        }

        // Fallback to local ML Kit OCR synchronously with Tasks.await
        val localResult = processImageLocal(bitmap)

        return@withContext localResult.copy(
            usedGemini = false,
            engineName = "Fallback Local (ML Kit OCR)",
            engineDetail = lastError
        )
    }

    suspend fun processImageLocal(bitmap: Bitmap): AiRecognitionResult {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val visionText = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                val fullText = visionText.text
                VisionAiParser.parseExtractedText(fullText)
            } catch (t: Throwable) {
                t.printStackTrace()
                VisionAiParser.parseExtractedText("").copy(
                    rawText = "Foto capturada sin texto detectado (${t.localizedMessage ?: t.message ?: t.javaClass.simpleName})"
                )
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

fun combineTodayWithTime(detectedHour: Int, detectedMinute: Int, detectedSecond: Int = 0): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, detectedHour)
    cal.set(Calendar.MINUTE, detectedMinute)
    cal.set(Calendar.SECOND, detectedSecond)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

object VoiceAiParser {

    fun parseSpeech(rawText: String): AiRecognitionResult {
        var normalized = rawText.lowercase(Locale.getDefault())

        normalized = normalized
            .replace("veinticinco", "25")
            .replace("veinte", "20")
            .replace("quince", "15")
            .replace("cincuenta", "50")
            .replace("treinta", "30")
            .replace("diez", "10")
            .replace("cinco", "5")
            .replace("cien", "100")
            .replace("dólares", "dolares")

        var parsedBalance: Double? = null

        val numberMatches = ArrayList<Double>()
        val numberPattern = Pattern.compile("(\\d+(?:[\\.,]\\d{1,2})?)")
        val numberMatcher = numberPattern.matcher(normalized)
        while (numberMatcher.find()) {
            val numStr = numberMatcher.group(1)?.replace(",", ".") ?: continue
            val num = numStr.toDoubleOrNull()
            if (num != null && num > 0.0 && num < 10000.0) {
                numberMatches.add(num)
            }
        }

        if (numberMatches.isNotEmpty()) {
            parsedBalance = numberMatches.first()
        }

        return AiRecognitionResult(
            balance = parsedBalance,
            timestamp = System.currentTimeMillis(),
            rawText = rawText
        )
    }
}

object VisionAiParser {

    fun parseExtractedText(rawText: String): AiRecognitionResult {
        var parsedBalance: Double? = null
        var parsedTimestamp: Long? = null

        val lines = rawText.split("\n")

        val moneyKeywordRegex = Pattern.compile("(?i)(?:total|saldo|monto|pago|ingreso|val|usd|\\$|s\\/)\\s*[:=]?\\s*(\\d+(?:[\\.,]\\d{1,2})?)")
        for (line in lines) {
            val matcher = moneyKeywordRegex.matcher(line)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", ".")
                parsedBalance = amountStr?.toDoubleOrNull()
                if (parsedBalance != null && parsedBalance > 0.0) break
            }
        }

        if (parsedBalance == null) {
            val genericDecimalPattern = Pattern.compile("(\\d+(?:[\\.,]\\d{1,2})?)")
            for (line in lines) {
                val matcher = genericDecimalPattern.matcher(line)
                while (matcher.find()) {
                    val amountStr = matcher.group(1)?.replace(",", ".")
                    val candidate = amountStr?.toDoubleOrNull()
                    if (candidate != null && candidate > 0.0 && candidate < 10000.0) {
                        parsedBalance = candidate
                        break
                    }
                }
                if (parsedBalance != null) break
            }
        }

        val timeRegex = Pattern.compile("(\\d{1,2}[:\\.]\\d{2}(?:[:\\.]\\d{2})?)")
        for (line in lines) {
            val matcher = timeRegex.matcher(line)
            if (matcher.find()) {
                val timeStr = matcher.group(1)
                val parts = timeStr?.replace(".", ":")?.split(":")
                if (parts != null && parts.size >= 2) {
                    val hours = parts[0].toIntOrNull() ?: 0
                    val minutes = parts[1].toIntOrNull() ?: 0
                    if (hours in 0..23 && minutes in 0..59) {
                        parsedTimestamp = combineTodayWithTime(hours, minutes)
                        break
                    }
                }
            }
        }

        if (parsedTimestamp == null) {
            parsedTimestamp = System.currentTimeMillis()
        }

        return AiRecognitionResult(
            balance = parsedBalance,
            timestamp = parsedTimestamp,
            rawText = rawText.ifBlank { "Foto procesada (pantalla/comprobante)" }
        )
    }
}
