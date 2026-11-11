package com.fitifysync.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Material 3 Expressive Tonal Tokens - Vibrant Fitness Palette
val BrandPrimary = Color(0xFF0D59F2)       // Electric Royal Blue
val BrandPrimaryLight = Color(0xFF4C8DFF)
val BrandSecondary = Color(0xFF00C896)     // Energetic Emerald Mint
val BrandSecondaryLight = Color(0xFF5FFAC6)
val BrandTertiary = Color(0xFFFF6B4A)      // Sunset Coral Flame
val BrandTertiaryLight = Color(0xFFFF9B85)
val BrandAccent = Color(0xFF8B5CF6)        // Cosmic Purple

// Expressive Gradients
val ExpressivePrimaryGradient = Brush.horizontalGradient(
    colors = listOf(BrandPrimary, Color(0xFF00B4D8))
)
val ExpressiveAccentGradient = Brush.horizontalGradient(
    colors = listOf(BrandTertiary, Color(0xFFFF9E00))
)
val ExpressiveMintGradient = Brush.horizontalGradient(
    colors = listOf(BrandSecondary, Color(0xFF00E5FF))
)
val ExpressiveCardioGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF5722), Color(0xFFFF9800))
)

// Neutral & Surface Containers (Light Mode)
val SurfaceLowestLight = Color(0xFFFFFFFF)
val SurfaceLowLight = Color(0xFFF7F9FC)
val SurfaceContainerLight = Color(0xFFEFF3F8)
val SurfaceHighLight = Color(0xFFE5EBF2)
val SurfaceHighestLight = Color(0xFFDCE3EB)

// Neutral & Surface Containers (Dark Mode)
val SurfaceLowestDark = Color(0xFF0B101E)
val SurfaceLowDark = Color(0xFF11182B)
val SurfaceContainerDark = Color(0xFF18223C)
val SurfaceHighDark = Color(0xFF212E50)
val SurfaceHighestDark = Color(0xFF2C3C66)

// Semantic State Colors
val SuccessGreen = Color(0xFF00B87C)
val SuccessGreenBg = Color(0xFFE6F8F2)
val SuccessGreenBgDark = Color(0xFF0F382B)

val WarningOrange = Color(0xFFFF8800)
val WarningOrangeBg = Color(0xFFFFF3E0)
val WarningOrangeBgDark = Color(0xFF3E280C)

val HeartRed = Color(0xFFFF2A55)           // Vibrant Heart Rate Crimson
val HeartRedBg = Color(0xFFFFE8ED)
val HeartRedBgDark = Color(0xFF3E111B)

val RestGray = Color(0xFF64748B)
val RestGrayBg = Color(0xFFF1F5F9)
val RestGrayBgDark = Color(0xFF1E293B)

// Dark/Light aware semantic color getters
data class SemanticBadgeColors(val background: Color, val content: Color)

@Composable
fun getStatusBadgeColors(isSynced: Boolean): SemanticBadgeColors {
    val isDark = isSystemInDarkTheme()
    return if (isSynced) {
        SemanticBadgeColors(
            background = if (isDark) SuccessGreenBgDark else SuccessGreenBg,
            content = if (isDark) Color(0xFF69F0AE) else SuccessGreen
        )
    } else {
        SemanticBadgeColors(
            background = if (isDark) WarningOrangeBgDark else WarningOrangeBg,
            content = if (isDark) Color(0xFFFFB74D) else WarningOrange
        )
    }
}

@Composable
fun getHeartRateBadgeColors(): SemanticBadgeColors {
    val isDark = isSystemInDarkTheme()
    return SemanticBadgeColors(
        background = if (isDark) HeartRedBgDark else HeartRedBg,
        content = if (isDark) Color(0xFFFF80AB) else Color(0xFFC2185B)
    )
}

@Composable
fun getRestSegmentColors(): SemanticBadgeColors {
    val isDark = isSystemInDarkTheme()
    return SemanticBadgeColors(
        background = if (isDark) RestGrayBgDark else RestGrayBg,
        content = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    )
}
