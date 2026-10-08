package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object SecurityManager {

    private const val PREFS_NAME = "parkia_security_prefs"
    private const val KEY_PIN = "security_pin"
    private const val KEY_QUICK_TILE_BIOMETRIC = "quick_tile_biometric_enabled"

    @Volatile
    var isAuthenticating: Boolean = false

    @Volatile
    var isRequestingPermission: Boolean = false

    fun isQuickTileBiometricEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_QUICK_TILE_BIOMETRIC, true)
    }

    fun setQuickTileBiometricEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_QUICK_TILE_BIOMETRIC, enabled).apply()
    }

    fun isPinConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN, null) != null
    }

    fun savePin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PIN, pin).apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PIN, null)
        return saved != null && saved == pin
    }

    fun canAuthenticateBiometrics(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun launchBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onPinRequired: (() -> Unit)? = null,
        onError: (String) -> Unit
    ) {
        if (activity.supportFragmentManager.isStateSaved ||
            !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        ) {
            return
        }

        isAuthenticating = true
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticating = false
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onSuccess()
                    }, 250)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isAuthenticating = false
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        if (onPinRequired != null) {
                            onPinRequired()
                        } else {
                            onError("Ingresa tu PIN de la app para continuar.")
                        }
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    isAuthenticating = false
                    onError("Autenticación biométrica no reconocida. Intenta de nuevo o ingresa tu PIN.")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Parkia")
            .setSubtitle("Confirma tu identidad para ingresar")
            .setNegativeButtonText("Usar PIN de la app")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (ex: Exception) {
            isAuthenticating = false
            if (onPinRequired != null) {
                onPinRequired()
            } else {
                onError("Ingresa tu PIN de 4 dígitos para acceder.")
            }
        }
    }
}



