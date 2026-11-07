package com.zeravrix.zeravrix.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Authentic Kotatsu Material 3 Themes
 */
enum class KotatsuTheme(
    val title: String,
    val lightPrimary: Long,
    val lightSecondary: Long,
    val lightBackground: Long,
    val lightSurfaceContainer: Long,
    val darkPrimary: Long,
    val darkSecondary: Long,
    val darkBackground: Long,
    val darkSurfaceContainer: Long,
) {
    TOTORO(
        title = "Totoro",
        lightPrimary = 0xFF3C6090,
        lightSecondary = 0xFF545F71,
        lightBackground = 0xFFF9F9FF,
        lightSurfaceContainer = 0xFFEDEDF4,
        darkPrimary = 0xFFA6C8FF,
        darkSecondary = 0xFFBCC7DC,
        darkBackground = 0xFF111318,
        darkSurfaceContainer = 0xFF1D2024,
    ),
    MIKU(
        title = "Miku",
        lightPrimary = 0xFF00696D,
        lightSecondary = 0xFF3F6566,
        lightBackground = 0xFFF5FAFA,
        lightSurfaceContainer = 0xFFEAEFEE,
        darkPrimary = 0xFF6FDDE2,
        darkSecondary = 0xFFA6CECF,
        darkBackground = 0xFF0F1415,
        darkSurfaceContainer = 0xFF1B2121,
    ),
    ASUKA(
        title = "Asuka",
        lightPrimary = 0xFF904A40,
        lightSecondary = 0xFF775651,
        lightBackground = 0xFFFFF8F6,
        lightSurfaceContainer = 0xFFFCEAE7,
        darkPrimary = 0xFFFFB4A8,
        darkSecondary = 0xFFE7BDB6,
        darkBackground = 0xFF1A1110,
        darkSurfaceContainer = 0xFF271D1C,
    ),
    MION(
        title = "Mion",
        lightPrimary = 0xFF3B693A,
        lightSecondary = 0xFF7B580D,
        lightBackground = 0xFFF7FBF1,
        lightSurfaceContainer = 0xFFECEFE6,
        darkPrimary = 0xFFA1D39A,
        darkSecondary = 0xFFEEBF6D,
        darkBackground = 0xFF10140F,
        darkSurfaceContainer = 0xFF1D211B,
    ),
    RIKKA(
        title = "Rikka",
        lightPrimary = 0xFF68548D,
        lightSecondary = 0xFF635B70,
        lightBackground = 0xFFFEF7FF,
        lightSurfaceContainer = 0xFFF2ECF4,
        darkPrimary = 0xFFD3BBFD,
        darkSecondary = 0xFFCDC2DB,
        darkBackground = 0xFF151218,
        darkSurfaceContainer = 0xFF211F24,
    ),
    SAKURA(
        title = "Sakura",
        lightPrimary = 0xFF8C4A60,
        lightSecondary = 0xFF74565F,
        lightBackground = 0xFFFFF8F8,
        lightSurfaceContainer = 0xFFFAEAED,
        darkPrimary = 0xFFFFB1C8,
        darkSecondary = 0xFFE3BDC6,
        darkBackground = 0xFF191113,
        darkSurfaceContainer = 0xFF261D20,
    ),
    MAMIMI(
        title = "Mamimi",
        lightPrimary = 0xFF465D91,
        lightSecondary = 0xFF575E71,
        lightBackground = 0xFFFAF8FF,
        lightSurfaceContainer = 0xFFEEEDF4,
        darkPrimary = 0xFFAFC6FF,
        darkSecondary = 0xFFBFC6DC,
        darkBackground = 0xFF121318,
        darkSurfaceContainer = 0xFF1E1F25,
    ),
    KANADE(
        title = "Kanade",
        lightPrimary = 0xFF353543,
        lightSecondary = 0xFF353636,
        lightBackground = 0xFFFCF8F9,
        lightSurfaceContainer = 0xFFEBE7E8,
        darkPrimary = 0xFFE2E2E6,
        darkSecondary = 0xFFDDDCDC,
        darkBackground = 0xFF141314,
        darkSurfaceContainer = 0xFF282829,
    ),
    ITSUKA(
        title = "Itsuka",
        lightPrimary = 0xFF974800,
        lightSecondary = 0xFF825334,
        lightBackground = 0xFFFFF8F5,
        lightSurfaceContainer = 0xFFFDEAE1,
        darkPrimary = 0xFFFFBA8F,
        darkSecondary = 0xFFF7B993,
        darkBackground = 0xFF1A110C,
        darkSurfaceContainer = 0xFF271E18,
    );
}

/**
 * Design tokens matching Kotatsu's Material 3 styling
 */
data class KotatsuTokens(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val outline: Color,
    val outlineVariant: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val isDark: Boolean,
    val isAmoled: Boolean,
) {
    val accentGlow: Color get() = primary.copy(alpha = 0.85f)
    val borderGlass: Color get() = outlineVariant.copy(alpha = 0.5f)
    val borderGlow: Color get() = primary.copy(alpha = 0.65f)
    val surfaceGlass: Color get() = surfaceContainer
    val surfaceCard: Color get() = surfaceContainer
    val surfaceGlassRaised: Color get() = surfaceContainerHigh
}

val LocalKotatsuTokens = compositionLocalOf {
    createKotatsuTokens(KotatsuTheme.TOTORO, isDark = true, isAmoled = false)
}

// Backward-compatible alias for LocalGlassTokens
val LocalGlassTokens = LocalKotatsuTokens

fun createGlassTokens(
    colorTheme: com.zeravrix.zeravrix.core.ColorTheme,
    appTheme: com.zeravrix.zeravrix.core.AppTheme,
    isAmoled: Boolean,
): KotatsuTokens {
    val isDark = appTheme == com.zeravrix.zeravrix.core.AppTheme.DARK || isAmoled
    val kotatsuTheme = when (colorTheme) {
        com.zeravrix.zeravrix.core.ColorTheme.SAKURA -> KotatsuTheme.SAKURA
        com.zeravrix.zeravrix.core.ColorTheme.MATCHA -> KotatsuTheme.MION
        com.zeravrix.zeravrix.core.ColorTheme.EMBER -> KotatsuTheme.ITSUKA
        com.zeravrix.zeravrix.core.ColorTheme.OCEAN -> KotatsuTheme.MIKU
        com.zeravrix.zeravrix.core.ColorTheme.LAVENDER -> KotatsuTheme.RIKKA
        com.zeravrix.zeravrix.core.ColorTheme.CRIMSON -> KotatsuTheme.ASUKA
        com.zeravrix.zeravrix.core.ColorTheme.GRAPHITE -> KotatsuTheme.KANADE
        com.zeravrix.zeravrix.core.ColorTheme.AMOLED_VOID -> KotatsuTheme.TOTORO
        else -> KotatsuTheme.TOTORO
    }
    return createKotatsuTokens(kotatsuTheme, isDark, isAmoled || colorTheme == com.zeravrix.zeravrix.core.ColorTheme.AMOLED_VOID)
}

fun createKotatsuTokens(
    theme: KotatsuTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): KotatsuTokens {
    val primary = Color(if (isDark) theme.darkPrimary else theme.lightPrimary)
    val secondary = Color(if (isDark) theme.darkSecondary else theme.lightSecondary)

    val bg = when {
        isAmoled -> Color(0xFF000000)
        isDark -> Color(theme.darkBackground)
        else -> Color(theme.lightBackground)
    }

    val surf = when {
        isAmoled -> Color(0xFF080808)
        isDark -> Color(theme.darkBackground)
        else -> Color(theme.lightBackground)
    }

    val surfContainer = when {
        isAmoled -> Color(0xFF121212)
        isDark -> Color(theme.darkSurfaceContainer)
        else -> Color(theme.lightSurfaceContainer)
    }

    val surfHigh = when {
        isAmoled -> Color(0xFF1A1A1A)
        isDark -> Color(theme.darkSurfaceContainer).copy(alpha = 0.9f)
        else -> Color(theme.lightSurfaceContainer).copy(alpha = 0.9f)
    }

    val outline = if (isDark) Color(0xFF8D9199) else Color(0xFF74777F)
    val outlineVariant = if (isDark) Color(0xFF43474E) else Color(0xFFC3C6CF)
    val textPrimary = if (isDark) Color(0xFFE1E2E9) else Color(0xFF191C20)
    val textMuted = if (isDark) Color(0xFF8D9199) else Color(0xFF74777F)

    return KotatsuTokens(
        primary = primary,
        onPrimary = if (isDark) Color.Black else Color.White,
        primaryContainer = primary.copy(alpha = 0.25f),
        secondary = secondary,
        background = bg,
        surface = surf,
        surfaceContainer = surfContainer,
        surfaceContainerHigh = surfHigh,
        outline = outline,
        outlineVariant = outlineVariant,
        textPrimary = textPrimary,
        textMuted = textMuted,
        isDark = isDark,
        isAmoled = isAmoled,
    )
}

fun createMaterialColorScheme(tokens: KotatsuTokens): ColorScheme {
    return if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            primaryContainer = tokens.primaryContainer,
            secondary = tokens.secondary,
            background = tokens.background,
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceContainer,
            outline = tokens.outline,
            outlineVariant = tokens.outlineVariant,
        )
    } else {
        lightColorScheme(
            primary = tokens.primary,
            onPrimary = tokens.onPrimary,
            primaryContainer = tokens.primaryContainer,
            secondary = tokens.secondary,
            background = tokens.background,
            surface = tokens.surface,
            surfaceVariant = tokens.surfaceContainer,
            outline = tokens.outline,
            outlineVariant = tokens.outlineVariant,
        )
    }
}
