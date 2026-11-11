package com.fitifysync.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = SurfaceLowestDark,
    primaryContainer = BrandPrimary,
    onPrimaryContainer = SurfaceLowestLight,
    secondary = BrandSecondary,
    onSecondary = SurfaceLowestDark,
    secondaryContainer = SurfaceHighDark,
    onSecondaryContainer = BrandSecondaryLight,
    tertiary = BrandTertiary,
    onTertiary = SurfaceLowestDark,
    background = SurfaceLowestDark,
    onBackground = SurfaceLowestLight,
    surface = SurfaceLowDark,
    onSurface = SurfaceLowestLight,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = SurfaceHighLight
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = SurfaceLowestLight,
    primaryContainer = SurfaceContainerLight,
    onPrimaryContainer = BrandPrimary,
    secondary = BrandSecondary,
    onSecondary = SurfaceLowestLight,
    secondaryContainer = SurfaceHighLight,
    onSecondaryContainer = SurfaceLowestDark,
    tertiary = BrandTertiary,
    onTertiary = SurfaceLowestLight,
    background = SurfaceLowLight,
    onBackground = SurfaceLowestDark,
    surface = SurfaceLowestLight,
    onSurface = SurfaceLowestDark,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = SurfaceLowestDark
)

@Composable
fun FitifySyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for vibrant M3 Expressive branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
