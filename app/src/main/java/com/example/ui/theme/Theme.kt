package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimary,
    secondary = SecondaryTeal,
    onSecondary = OnPrimary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = TertiaryAmber,
    onTertiary = OnPrimary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundLight,
    onBackground = OnSurfaceDark,
    surface = BackgroundLight,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerLow,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineVariant,
    surfaceContainer = SurfaceContainer,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFB4C5FF),
    onPrimary = Color(0xFF00297A),
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = Color(0xFFDBE1FF),
    secondary = Color(0xFF4FDBC8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = SecondaryTeal,
    onSecondaryContainer = Color(0xFF71F8E4),
    background = Color(0xFF0B1C30),
    onBackground = Color(0xFFEAF1FF),
    surface = Color(0xFF0B1C30),
    onSurface = Color(0xFFEAF1FF),
    surfaceVariant = Color(0xFF1E2F46),
    onSurfaceVariant = Color(0xFFC3C6D7),
    outline = Color(0xFF8D91A0),
  )

@Composable
fun HealthSyncTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

