package com.zeravrix.zeravrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.theme.KotatsuTheme
import com.zeravrix.zeravrix.theme.LocalGlassTokens

enum class SettingsSection(val title: String, val icon: String, val subtitle: String) {
    APPEARANCE("Appearance", "🎨", "Theme chooser, dark mode, AMOLED"),
    READER("Reader", "📖", "Reading mode, scale, controls"),
    SOURCES("Sources", "🌐", "Content filters, languages, pinned"),
    STORAGE("Storage & Cache", "💾", "Disk usage, image cache cleanup"),
    ABOUT("About", "ℹ️", "Version, credits, and engine info")
}

/**
 * Replicates Kotatsu's layout-w600dp-land/activity_settings.xml
 * Two-pane Master-Detail landscape settings screen
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
        // Master Column (Left 340dp)
        Column(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .background(tokens.surfaceContainer)
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
                Surface(
                    color = if (isSelected) tokens.primaryContainer else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedSection = section }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(section.icon, fontSize = 18.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                section.title,
                                color = if (isSelected) tokens.primary else tokens.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                section.subtitle,
                                color = tokens.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 1dp Divider Line (Kotatsu colorSurfaceDim)
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(tokens.outlineVariant.copy(alpha = 0.5f))
        )

        // Detail Column (Right Pane)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(28.dp)
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Kotatsu Material 3 Themes & Dark Mode",
                        color = tokens.textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    // Kotatsu Theme Chooser
                    Text(
                        "THEME PRESET",
                        color = tokens.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))

                    val kotatsuThemes = KotatsuTheme.entries
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        kotatsuThemes.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { theme ->
                                    val isSelected = when (theme) {
                                        KotatsuTheme.TOTORO -> settings.colorTheme == ColorTheme.INDIGO_NIGHT && !settings.isAmoled
                                        KotatsuTheme.MIKU -> settings.colorTheme == ColorTheme.OCEAN
                                        KotatsuTheme.ASUKA -> settings.colorTheme == ColorTheme.CRIMSON
                                        KotatsuTheme.MION -> settings.colorTheme == ColorTheme.MATCHA
                                        KotatsuTheme.RIKKA -> settings.colorTheme == ColorTheme.LAVENDER
                                        KotatsuTheme.SAKURA -> settings.colorTheme == ColorTheme.SAKURA
                                        KotatsuTheme.MAMIMI -> settings.colorTheme == ColorTheme.INDIGO_NIGHT
                                        KotatsuTheme.KANADE -> settings.colorTheme == ColorTheme.GRAPHITE
                                        KotatsuTheme.ITSUKA -> settings.colorTheme == ColorTheme.EMBER
                                    }

                                    Surface(
                                        color = if (isSelected) tokens.primaryContainer else tokens.surfaceContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(
                                                if (isSelected) 2.dp else 1.dp,
                                                if (isSelected) tokens.primary else tokens.outlineVariant.copy(alpha = 0.5f),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                val mappedColor = when (theme) {
                                                    KotatsuTheme.TOTORO -> ColorTheme.INDIGO_NIGHT
                                                    KotatsuTheme.MIKU -> ColorTheme.OCEAN
                                                    KotatsuTheme.ASUKA -> ColorTheme.CRIMSON
                                                    KotatsuTheme.MION -> ColorTheme.MATCHA
                                                    KotatsuTheme.RIKKA -> ColorTheme.LAVENDER
                                                    KotatsuTheme.SAKURA -> ColorTheme.SAKURA
                                                    KotatsuTheme.MAMIMI -> ColorTheme.INDIGO_NIGHT
                                                    KotatsuTheme.KANADE -> ColorTheme.GRAPHITE
                                                    KotatsuTheme.ITSUKA -> ColorTheme.EMBER
                                                }
                                                onSettingsChange(settings.copy(colorTheme = mappedColor, isAmoled = false))
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(theme.darkPrimary))
                                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(theme.darkSecondary))
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                theme.title,
                                                color = tokens.textPrimary,
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
                        color = tokens.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Dark Theme", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Use night color palette", color = tokens.textMuted, fontSize = 12.sp)
                                }
                                Switch(
                                    checked = settings.theme == AppTheme.DARK,
                                    onCheckedChange = { isDark ->
                                        onSettingsChange(settings.copy(theme = if (isDark) AppTheme.DARK else AppTheme.LIGHT))
                                    }
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.outlineVariant.copy(alpha = 0.3f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Pure Black (AMOLED)", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Deep #000000 background for OLED screens", color = tokens.textMuted, fontSize = 12.sp)
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Configure reading direction, scaling, and controls",
                        color = tokens.textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    Text("DEFAULT READING MODE", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))

                    val modes = listOf(
                        ReaderModePreference.VERTICAL to "Webtoon (Continuous Vertical)",
                        ReaderModePreference.SINGLE to "Single Page (Standard Left-to-Right)",
                        ReaderModePreference.DOUBLE to "Double Page Spread",
                        ReaderModePreference.AUTO to "Japanese (Right-to-Left)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { (mode, label) ->
                            val isSelected = settings.readerMode == mode
                            Surface(
                                color = if (isSelected) tokens.primaryContainer else tokens.surfaceContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) tokens.primary else tokens.outlineVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clip(RoundedCornerShape(10.dp))
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
                                    Spacer(Modifier.width(10.dp))
                                    Text(label, color = tokens.textPrimary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text("KEYBOARD SHORTCUTS", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = tokens.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("• Arrow Down / Space / Page Down: Scroll down or next page", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• Arrow Up / Page Up: Scroll up or previous page", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• Left / Right Arrow: Turn pages in horizontal mode", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• Esc: Exit reader and return to details", color = tokens.textMuted, fontSize = 12.sp)
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
                        color = tokens.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Show 18+ (NSFW) Content", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
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
                        color = tokens.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Disk Image Cache", color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                            Text("Fast Skia bitmap cache stored in ~/.zeravrix/cache", color = tokens.textMuted, fontSize = 12.sp)

                            Spacer(Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    onClearCache()
                                    cacheCleared = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.onPrimary)
                            ) {
                                Text(if (cacheCleared) "✓ Cache Cleared" else "Clear Image Cache", fontWeight = FontWeight.Bold)
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
                        color = tokens.surfaceContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("ZERAVRIX PC", color = tokens.primary, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("Version 1.6.0 (Native Desktop Release)", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Native PC manga and manhwa reader engineered with Kotatsu's parser engine and high-performance Compose Desktop Skia hardware acceleration.",
                                color = tokens.textMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("• 100+ Live parsers integrated directly from Kotatsu", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• Zero startup lag & instant page rendering with Skia", color = tokens.textMuted, fontSize = 12.sp)
                            Text("• 9 Authentic Kotatsu Material 3 landscape color themes", color = tokens.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
