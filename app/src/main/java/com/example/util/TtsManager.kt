package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsManager {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    fun init(context: Context) {
        if (tts != null) return
        val appContext = context.applicationContext
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    val localeEs = Locale.forLanguageTag("es-ES")
                    val result = tts?.setLanguage(localeEs)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    pendingText?.let { text ->
                        speakInternal(text)
                        pendingText = null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(context: Context, text: String) {
        if (tts == null) {
            init(context)
            pendingText = text
        } else if (isInitialized) {
            speakInternal(text)
        } else {
            pendingText = text
        }
    }

    private fun speakInternal(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "PARKIA_TTS_ID_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun announceSessionStart(context: Context, balance: Double, enabled: Boolean = true) {
        if (!enabled) return
        val formatted = String.format(Locale.US, "%.2f", balance)
        speak(context, "Usted ingresa con $formatted dólares")
    }

    fun announceSessionStop(context: Context, remainingBalance: Double, enabled: Boolean = true) {
        if (!enabled) return
        val formatted = String.format(Locale.US, "%.2f", remainingBalance)
        speak(context, "Usted termina con $formatted dólares")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
    }
}
