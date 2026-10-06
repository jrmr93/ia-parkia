package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = SlateBlue,
  onPrimary = Color.White,
  primaryContainer = NavyCard,
  onPrimaryContainer = Color.White,
  secondary = AccentCyan,
  onSecondary = Color.White,
  secondaryContainer = NavySurface,
  onSecondaryContainer = Color.White,
  tertiary = SlateLight,
  onTertiary = DeepNavy,
  background = DeepNavy,
  onBackground = Color.White,
  surface = NavyCard,
  onSurface = Color.White,
  surfaceVariant = NavySurface,
  onSurfaceVariant = SlateLight,
  outline = SlateBorder,
  error = ErrorRed,
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = SlateBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDBEAFE),
  onPrimaryContainer = Color(0xFF1E3A8A),
  secondary = AccentCyan,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0F2FE),
  onSecondaryContainer = Color(0xFF0369A1),
  tertiary = SlateLight,
  onTertiary = DeepNavy,
  background = Color(0xFFF1F5F9),
  onBackground = Color(0xFF0F172A),
  surface = Color.White,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = Color(0xFFF8FAFC),
  onSurfaceVariant = Color(0xFF475569),
  outline = Color(0xFFCBD5E1),
  error = ErrorRed,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Always enforce crisp white panels and black text as requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}

