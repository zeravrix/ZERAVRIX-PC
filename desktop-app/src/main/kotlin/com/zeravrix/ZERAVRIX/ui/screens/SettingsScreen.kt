package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.theme.KotatsuTheme
import com.zeravrix.zeravrix.theme.LocalGlassTokens

enum class SettingsSection(val title: String, val icon: ImageVector, val subtitle: String) {
    APPEARANCE("Appearance", Icons.Rounded.Palette, "Theme presets, night mode, AMOLED"),
    READER("Reader", Icons.Rounded.MenuBook, "Reading direction, mode, hotkeys"),
    SOURCES("Sources", Icons.Rounded.Language, "Mature content filters, active registries"),
    STORAGE("Storage & Cache", Icons.Rounded.Storage, "Local cache size, image cleanup"),
    ABOUT("About", Icons.Rounded.Info, "Engine info, build version, credits")
}

/**
 * Replicates Kotatsu's layout-w600dp-land/activity_settings.xml
 * High-grade two-pane Master-Detail landscape settings screen
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    var selectedSection by remember { mutableStateOf(SettingsSection.APPEARANCE) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Master Column (Left 320dp)
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .background(tokens.surfaceGlass)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "SETTINGS",
                color = tokens.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(14.dp))

            SettingsSection.entries.forEach { section ->
                val isSelected = selectedSection == section
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()

                val bg by animateColorAsState(
                    targetValue = when {
                        isSelected -> tokens.primaryContainer
                        hovered -> tokens.surfaceContainerHigh
                        else -> Color.Transparent
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )

                Surface(
                    color = bg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .hoverable(interaction)
                        .clickable { selectedSection = section }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = if (isSelected) tokens.primary else if (hovered) tokens.textPrimary else tokens.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = section.title,
                                color = if (isSelected) tokens.primary else tokens.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = section.subtitle,
                                color = tokens.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 1dp Divider Line
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(tokens.borderGlass)
        )

        // Detail Column (Right Pane)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            when (selectedSection) {
                SettingsSection.APPEARANCE -> {
                    Text(
                        "APPEARANCE",
                        color = tokens.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Kotatsu Material 3 Themes & Dark Mode Palettes",
                        color = tokens.textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    Text(
                        "THEME PRESET",
                        color = tokens.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    val kotatsuThemes = KotatsuTheme.entries
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        kotatsuThemes.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { theme ->
                                    val isSelected = when (theme) {
                                        KotatsuTheme.TOTORO -> settings.colorTheme == ColorTheme.TOTORO
                                        KotatsuTheme.MONET -> settings.colorTheme == ColorTheme.MONET
                                        KotatsuTheme.EXPRESSIVE -> settings.colorTheme == ColorTheme.EXPRESSIVE
                                        KotatsuTheme.MIKU -> settings.colorTheme == ColorTheme.MIKU
                                        KotatsuTheme.ASUKA -> settings.colorTheme == ColorTheme.ASUKA
                                        KotatsuTheme.MION -> settings.colorTheme == ColorTheme.MION
                                        KotatsuTheme.RIKKA -> settings.colorTheme == ColorTheme.RIKKA
                                        KotatsuTheme.SAKURA -> settings.colorTheme == ColorTheme.SAKURA
                                        KotatsuTheme.MAMIMI -> settings.colorTheme == ColorTheme.MAMIMI
                                        KotatsuTheme.KANADE -> settings.colorTheme == ColorTheme.KANADE
                                        KotatsuTheme.ITSUKA -> settings.colorTheme == ColorTheme.ITSUKA
                                        KotatsuTheme.AMOLED_VOID -> settings.colorTheme == ColorTheme.AMOLED_VOID
                                        KotatsuTheme.HIGH_CONTRAST -> settings.colorTheme == ColorTheme.HIGH_CONTRAST
                                    }

                                    Surface(
                                        color = if (isSelected) tokens.primaryContainer else tokens.surfaceCard,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) tokens.primary else tokens.borderGlass,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .shadow(
                                                elevation = if (isSelected) 6.dp else 1.dp,
                                                shape = RoundedCornerShape(12.dp),
                                                spotColor = tokens.primary
                                            )
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val mappedColor = when (theme) {
                                                    KotatsuTheme.TOTORO -> ColorTheme.TOTORO
                                                    KotatsuTheme.MONET -> ColorTheme.MONET
                                                    KotatsuTheme.EXPRESSIVE -> ColorTheme.EXPRESSIVE
                                                    KotatsuTheme.MIKU -> ColorTheme.MIKU
                                                    KotatsuTheme.ASUKA -> ColorTheme.ASUKA
                                                    KotatsuTheme.MION -> ColorTheme.MION
                                                    KotatsuTheme.RIKKA -> ColorTheme.RIKKA
                                                    KotatsuTheme.SAKURA -> ColorTheme.SAKURA
                                                    KotatsuTheme.MAMIMI -> ColorTheme.MAMIMI
                                                    KotatsuTheme.KANADE -> ColorTheme.KANADE
                                                    KotatsuTheme.ITSUKA -> ColorTheme.ITSUKA
                                                    KotatsuTheme.AMOLED_VOID -> ColorTheme.AMOLED_VOID
                                                    KotatsuTheme.HIGH_CONTRAST -> ColorTheme.HIGH_CONTRAST
                                                }
                                                onSettingsChange(settings.copy(colorTheme = mappedColor, isAmoled = (theme == KotatsuTheme.AMOLED_VOID)))
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(theme.darkPrimary))
                                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = theme.title,
                                                color = if (isSelected) tokens.primary else tokens.textPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Dark Mode & AMOLED Toggles
                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Dark Theme", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Use comfortable night color palette", color = tokens.textMuted, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = settings.theme == AppTheme.DARK,
                                    onCheckedChange = { isDark ->
                                        onSettingsChange(settings.copy(theme = if (isDark) AppTheme.DARK else AppTheme.LIGHT))
                                    }
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 14.dp), color = tokens.borderGlass)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Pure Black (AMOLED)", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Deep #000000 background for OLED / high contrast screens", color = tokens.textMuted, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = settings.isAmoled,
                                    onCheckedChange = { isAmoled ->
                                        onSettingsChange(settings.copy(isAmoled = isAmoled))
                                    }
                                )
                            }
                        }
                    }
                }

                SettingsSection.READER -> {
                    Text(
                        "READER SETTINGS",
                        color = tokens.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Configure reading direction, scaling, and navigation shortcuts",
                        color = tokens.textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    Text("DEFAULT READING MODE", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))

                    val modes = listOf(
                        ReaderModePreference.VERTICAL to "Webtoon (Continuous Vertical)",
                        ReaderModePreference.SINGLE to "Single Page (Standard Left-to-Right)",
                        ReaderModePreference.DOUBLE to "Double Page Spread",
                        ReaderModePreference.AUTO to "Japanese Manga (Right-to-Left)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { (mode, label) ->
                            val isSelected = settings.readerMode == mode
                            Surface(
                                color = if (isSelected) tokens.primaryContainer else tokens.surfaceCard,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) tokens.primary else tokens.borderGlass,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSettingsChange(settings.copy(readerMode = mode)) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSettingsChange(settings.copy(readerMode = mode)) }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        color = tokens.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(22.dp))

                    Text("KEYBOARD SHORTCUTS", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("• Space / Down Arrow / Page Down: Scroll down or advance page", color = tokens.textPrimary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("• Up Arrow / Page Up: Scroll up or retreat page", color = tokens.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("• Left / Right Arrow: Turn pages in horizontal modes", color = tokens.textMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("• Esc: Close active reader session", color = tokens.textMuted, fontSize = 12.sp)
                        }
                    }
                }

                SettingsSection.SOURCES -> {
                    Text(
                        "SOURCES & CONTENT",
                        color = tokens.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Show 18+ (Mature) Content", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Include adult / mature manga in catalog and search", color = tokens.textMuted, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = settings.nsfwEnabled,
                                    onCheckedChange = { nsfw ->
                                        onSettingsChange(settings.copy(nsfwEnabled = nsfw))
                                    }
                                )
                            }
                        }
                    }
                }

                SettingsSection.STORAGE -> {
                    Text(
                        "STORAGE & CACHE",
                        color = tokens.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    var cacheCleared by remember { mutableStateOf(false) }

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Disk Image Cache", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Fast Skia bitmap cache stored in ~/.zeravrix/cache (covers & chapters)", color = tokens.textMuted, fontSize = 12.sp)

                            Spacer(Modifier.height(16.dp))

                            FilledTonalButton(
                                onClick = {
                                    onClearCache()
                                    cacheCleared = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.primary, contentColor = tokens.onPrimary)
                            ) {
                                Icon(
                                    imageVector = if (cacheCleared) Icons.Rounded.Check else Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (cacheCleared) "Cache Cleared" else "Clear Image Cache",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                SettingsSection.ABOUT -> {
                    Text(
                        "ABOUT ZERAVRIX PC",
                        color = tokens.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(22.dp)) {
                            Text("ZERAVRIX PC", color = tokens.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("Version 1.6.0 (Native Desktop Edition)", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Native PC manga and manhwa reader engineered with Kotatsu's parser engine and high-performance Compose Desktop Skia hardware acceleration.",
                                color = tokens.textMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("• 1000+ Live parsers integrated directly from Kotatsu", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• Instant page rendering with Skia/Skiko GPU pipeline", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• 9 Authentic Kotatsu Material 3 color themes + AMOLED Black", color = tokens.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
