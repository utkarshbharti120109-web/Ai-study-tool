package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ElegantColorScheme = darkColorScheme(
  primary = ElegantPrimary,
  onPrimary = ElegantOnPrimary,
  primaryContainer = ElegantPrimaryContainer,
  onPrimaryContainer = ElegantOnPrimaryContainer,
  secondary = ElegantSecondary,
  onSecondary = ElegantOnSecondary,
  secondaryContainer = ElegantSecondaryContainer,
  onSecondaryContainer = ElegantOnSecondaryContainer,
  background = ElegantBackground,
  onBackground = ElegantOnSurface,
  surface = ElegantSurface,
  onSurface = ElegantOnSurface,
  surfaceVariant = ElegantSurface,
  onSurfaceVariant = ElegantOnSurfaceVariant,
  outline = ElegantOutline,
  error = ElegantError,
  onError = ElegantOnError
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  // Dynamic color is overridden to force our premium Elegant Dark styling
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = ElegantColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
