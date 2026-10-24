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

private val GoogleLightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = GoogleOnPrimary,
    primaryContainer = GooglePrimaryContainer,
    onPrimaryContainer = GoogleOnPrimaryContainer,
    secondary = HealthConnectTeal,
    onSecondary = GoogleOnSecondary,
    secondaryContainer = GoogleSecondaryContainer,
    onSecondaryContainer = GoogleOnSecondaryContainer,
    tertiary = GoogleVitalsPurple,
    onTertiary = GoogleOnTertiary,
    tertiaryContainer = GoogleTertiaryContainer,
    onTertiaryContainer = GoogleOnTertiaryContainer,
    background = GoogleSurfaceLight,
    onBackground = GoogleOnSurfaceLight,
    surface = GoogleSurfaceLight,
    onSurface = GoogleOnSurfaceLight,
    surfaceVariant = GoogleSurfaceContainer,
    onSurfaceVariant = GoogleOnSurfaceVariantLight,
    outline = GoogleOutlineLight,
    outlineVariant = GoogleOutlineVariantLight,
    surfaceContainerLowest = GoogleSurfaceContainerLowest,
    surfaceContainerLow = GoogleSurfaceContainerLow,
    surfaceContainer = GoogleSurfaceContainer,
    surfaceContainerHigh = GoogleSurfaceContainerHigh
)

private val GoogleDarkColorScheme = darkColorScheme(
    primary = GoogleBlueDark,
    onPrimary = GoogleOnPrimaryDark,
    primaryContainer = GooglePrimaryContainerDark,
    onPrimaryContainer = GoogleOnPrimaryContainerDark,
    secondary = HealthConnectTealDark,
    onSecondary = GoogleOnSecondaryDark,
    secondaryContainer = GoogleSecondaryContainerDark,
    onSecondaryContainer = GoogleOnSecondaryContainerDark,
    tertiary = GoogleVitalsPurpleDark,
    onTertiary = GoogleOnTertiaryDark,
    tertiaryContainer = GoogleTertiaryContainerDark,
    onTertiaryContainer = GoogleOnTertiaryContainerDark,
    background = GoogleSurfaceDark,
    onBackground = GoogleOnSurfaceDark,
    surface = GoogleSurfaceDark,
    onSurface = GoogleOnSurfaceDark,
    surfaceVariant = GoogleSurfaceContainerDark,
    onSurfaceVariant = GoogleOnSurfaceVariantDark,
    outline = GoogleOutlineDark,
    outlineVariant = GoogleOutlineVariantDark,
    surfaceContainerLowest = GoogleSurfaceContainerLowestDark,
    surfaceContainerLow = GoogleSurfaceContainerLowDark,
    surfaceContainer = GoogleSurfaceContainerDark,
    surfaceContainerHigh = GoogleSurfaceContainerHighDark
)

@Composable
fun HealthSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GoogleDarkColorScheme
        else -> GoogleLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
