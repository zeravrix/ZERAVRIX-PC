package com.zeravrix.zeravrix.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Authentic Kotatsu Material 3 Themes + Enhanced Cyber-Glass Palettes
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
        title = "Totoro (Indigo)",
        lightPrimary = 0xFF3C6090,
        lightSecondary = 0xFF545F71,
        lightBackground = 0xFFF9F9FF,
        lightSurfaceContainer = 0xFFEDEDF4,
        darkPrimary = 0xFFA6C8FF,
        darkSecondary = 0xFFBCC7DC,
        darkBackground = 0xFF0E1116,
        darkSurfaceContainer = 0xFF171A21,
    ),
    MIKU(
        title = "Miku (Teal)",
        lightPrimary = 0xFF00696D,
        lightSecondary = 0xFF3F6566,
        lightBackground = 0xFFF5FAFA,
        lightSurfaceContainer = 0xFFEAEFEE,
        darkPrimary = 0xFF6FDDE2,
        darkSecondary = 0xFFA6CECF,
        darkBackground = 0xFF0B1213,
        darkSurfaceContainer = 0xFF141C1D,
    ),
    ASUKA(
        title = "Asuka (Crimson)",
        lightPrimary = 0xFF904A40,
        lightSecondary = 0xFF775651,
        lightBackground = 0xFFFFF8F6,
        lightSurfaceContainer = 0xFFFCEAE7,
        darkPrimary = 0xFFFFB4A8,
        darkSecondary = 0xFFE7BDB6,
        darkBackground = 0xFF160E0D,
        darkSurfaceContainer = 0xFF221715,
    ),
    MION(
        title = "Mion (Emerald)",
        lightPrimary = 0xFF3B693A,
        lightSecondary = 0xFF7B580D,
        lightBackground = 0xFFF7FBF1,
        lightSurfaceContainer = 0xFFECEFE6,
        darkPrimary = 0xFFA1D39A,
        darkSecondary = 0xFFEEBF6D,
        darkBackground = 0xFF0D140C,
        darkSurfaceContainer = 0xFF172016,
    ),
    RIKKA(
        title = "Rikka (Lavender)",
        lightPrimary = 0xFF68548D,
        lightSecondary = 0xFF635B70,
        lightBackground = 0xFFFEF7FF,
        lightSurfaceContainer = 0xFFF2ECF4,
        darkPrimary = 0xFFD3BBFD,
        darkSecondary = 0xFFCDC2DB,
        darkBackground = 0xFF120E18,
        darkSurfaceContainer = 0xFF1C1724,
    ),
    SAKURA(
        title = "Sakura (Rose)",
        lightPrimary = 0xFF8C4A60,
        lightSecondary = 0xFF74565F,
        lightBackground = 0xFFFFF8F8,
        lightSurfaceContainer = 0xFFFAEAED,
        darkPrimary = 0xFFFFB1C8,
        darkSecondary = 0xFFE3BDC6,
        darkBackground = 0xFF160E12,
        darkSurfaceContainer = 0xFF22161B,
    ),
    MAMIMI(
        title = "Mamimi (Cobalt)",
        lightPrimary = 0xFF465D91,
        lightSecondary = 0xFF575E71,
        lightBackground = 0xFFFAF8FF,
        lightSurfaceContainer = 0xFFEEEDF4,
        darkPrimary = 0xFFAFC6FF,
        darkSecondary = 0xFFBFC6DC,
        darkBackground = 0xFF0E1118,
        darkSurfaceContainer = 0xFF161B26,
    ),
    KANADE(
        title = "Kanade (Graphite)",
        lightPrimary = 0xFF353543,
        lightSecondary = 0xFF353636,
        lightBackground = 0xFFFCF8F9,
        lightSurfaceContainer = 0xFFEBE7E8,
        darkPrimary = 0xFFE2E2E6,
        darkSecondary = 0xFFDDDCDC,
        darkBackground = 0xFF101012,
        darkSurfaceContainer = 0xFF1B1B1E,
    ),
    ITSUKA(
        title = "Itsuka (Amber)",
        lightPrimary = 0xFF974800,
        lightSecondary = 0xFF825334,
        lightBackground = 0xFFFFF8F5,
        lightSurfaceContainer = 0xFFFDEAE1,
        darkPrimary = 0xFFFFBA8F,
        darkSecondary = 0xFFF7B993,
        darkBackground = 0xFF16100A,
        darkSurfaceContainer = 0xFF231A12,
    );
}

/**
 * Design tokens matching Kotatsu's Material 3 styling + Modern Cyber-Glass Acrylic Effects
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
    // Cyber-Glassmorphism layer tokens
    val accentGlow: Color get() = primary.copy(alpha = 0.85f)
    val primaryGlow: Color get() = primary.copy(alpha = 0.25f)
    val borderGlass: Color get() = if (isDark) outlineVariant.copy(alpha = 0.40f) else outlineVariant.copy(alpha = 0.55f)
    val borderGlow: Color get() = primary.copy(alpha = 0.60f)
    val borderSubtle: Color get() = if (isDark) Color(0x1FFFFFFF) else Color(0x14000000)

    // Acrylic surfaces
    val surfaceGlass: Color get() = if (isAmoled) Color(0xF20A0A0A) else surfaceContainer.copy(alpha = 0.82f)
    val surfaceCard: Color get() = if (isAmoled) Color(0xFF101010) else surfaceContainer.copy(alpha = 0.90f)
    val surfaceGlassRaised: Color get() = if (isAmoled) Color(0xFF161616) else surfaceContainerHigh.copy(alpha = 0.94f)
    val surfaceGlassUltra: Color get() = if (isDark) Color(0xEA11141A) else Color(0xFAFFFFFF)

    // Gradients
    val glassGradient: Brush get() = Brush.verticalGradient(
        listOf(
            surfaceGlassRaised,
            surfaceGlass
        )
    )

    val chromeGradient: Brush get() = Brush.horizontalGradient(
        listOf(
            surfaceGlass,
            surfaceGlassRaised,
            surfaceGlass
        )
    )
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
        isAmoled -> Color(0xFF0F1012)
        isDark -> Color(theme.darkSurfaceContainer)
        else -> Color(theme.lightSurfaceContainer)
    }

    val surfHigh = when {
        isAmoled -> Color(0xFF18191D)
        isDark -> Color(theme.darkSurfaceContainer).copy(alpha = 0.95f)
        else -> Color(theme.lightSurfaceContainer).copy(alpha = 0.95f)
    }

    val outline = if (isDark) Color(0xFF8D9199) else Color(0xFF74777F)
    val outlineVariant = if (isDark) Color(0xFF383C45) else Color(0xFFC3C6CF)
    val textPrimary = if (isDark) Color(0xFFF0F1F6) else Color(0xFF191C20)
    val textMuted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    return KotatsuTokens(
        primary = primary,
        onPrimary = if (isDark) Color(0xFF0B0F19) else Color.White,
        primaryContainer = primary.copy(alpha = 0.22f),
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
