package com.example.util

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object TtsManager {
    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    private val pendingQueue = ConcurrentLinkedQueue<String>()

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
                    // Flush pending texts
                    while (!pendingQueue.isEmpty()) {
                        val text = pendingQueue.poll()
                        if (!text.isNullOrBlank()) {
                            speakInternal(text)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        init(context)
        if (isInitialized) {
            speakInternal(text)
        } else {
            pendingQueue.add(text)
        }
    }

    private fun speakInternal(text: String) {
        try {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "PARKIA_TTS_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun announceSessionStart(context: Context, balance: Double, enabled: Boolean = true) {
        val formatted = String.format(Locale.US, "%.2f", balance)
        val text = "Usted ingresa con $formatted dólares"
        speak(context, text)
    }

    fun announceSessionStop(context: Context, remainingBalance: Double, enabled: Boolean = true) {
        val formatted = String.format(Locale.US, "%.2f", remainingBalance)
        val text = "Usted termina con $formatted dólares"
        speak(context, text)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
        pendingQueue.clear()
    }
}
