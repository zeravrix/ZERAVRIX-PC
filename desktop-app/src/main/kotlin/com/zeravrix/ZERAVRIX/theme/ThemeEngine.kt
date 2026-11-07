package com.zeravrix.zeravrix.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.zeravrix.zeravrix.core.AppTheme
import com.zeravrix.zeravrix.core.ColorTheme

/**
 * Three-layer design tokens for ZERAVRIX Glassmorphism UI
 */
data class GlassTokens(
    val background: Color,
    val surfaceGlass: Color,
    val surfaceGlassRaised: Color,
    val surfaceCard: Color,
    val borderGlass: Color,
    val borderGlow: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val isDark: Boolean,
    val isAmoled: Boolean,
) {
    val accentGlow: Color get() = accent.copy(alpha = 0.65f)
}

val LocalGlassTokens = compositionLocalOf {
    createGlassTokens(ColorTheme.INDIGO_NIGHT, AppTheme.DARK, false)
}

fun createGlassTokens(theme: ColorTheme, appTheme: AppTheme, isAmoled: Boolean): GlassTokens {
    val isDark = appTheme == AppTheme.DARK || isAmoled
    val primary = Color(theme.primaryHex)
    val secondary = Color(theme.secondaryHex)
    val accent = Color(theme.accentHex)

    val bg = when {
        isAmoled -> Color(0xFF000000)
        isDark -> Color(0xFF080B12)
        else -> Color(0xFFF6F8FC)
    }

    val surfaceGlass = when {
        isAmoled -> Color(0xEE080808)
        isDark -> Color(0xDD0D1322)
        else -> Color(0xDDFFFFFF)
    }

    val surfaceRaised = when {
        isAmoled -> Color(0xFF121212)
        isDark -> Color(0xEE131A2B)
        else -> Color(0xFFEEF2F9)
    }

    val surfaceCard = when {
        isAmoled -> Color(0xFF181818)
        isDark -> Color(0x99182238)
        else -> Color(0xF5FFFFFF)
    }

    val borderGlass = when {
        isAmoled -> primary.copy(alpha = 0.28f)
        isDark -> primary.copy(alpha = 0.22f)
        else -> primary.copy(alpha = 0.16f)
    }

    val borderGlow = primary.copy(alpha = 0.65f)

    val textPrimary = if (isDark) Color(0xFFF1F4F9) else Color(0xFF0F172A)
    val textMuted = if (isDark) Color(0xFF8E9BB0) else Color(0xFF64748B)

    return GlassTokens(
        background = bg,
        surfaceGlass = surfaceGlass,
        surfaceGlassRaised = surfaceRaised,
        surfaceCard = surfaceCard,
        borderGlass = borderGlass,
        borderGlow = borderGlow,
        textPrimary = textPrimary,
        textMuted = textMuted,
        primary = primary,
        secondary = secondary,
        accent = accent,
        isDark = isDark,
        isAmoled = isAmoled,
    )
}

fun createMaterialColorScheme(tokens: GlassTokens): ColorScheme {
    return if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = if (tokens.isAmoled) Color.Black else Color(0xFF041021),
            secondary = tokens.secondary,
            onSecondary = Color.White,
            tertiary = tokens.accent,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surfaceGlass,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceGlassRaised,
            onSurfaceVariant = tokens.textMuted,
            error = Color(0xFFFF4D6D),
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = tokens.primary,
            onPrimary = Color.White,
            secondary = tokens.secondary,
            onSecondary = Color.White,
            tertiary = tokens.accent,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surfaceGlass,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceGlassRaised,
            onSurfaceVariant = tokens.textMuted,
            error = Color(0xFFDC2626),
            onError = Color.White,
        )
    }
}
