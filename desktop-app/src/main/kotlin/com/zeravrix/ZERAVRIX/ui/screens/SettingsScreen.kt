package com.zeravrix.zeravrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.zeravrix.zeravrix.theme.LocalGlassTokens

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Section: 13 Themes Studio
        Text(
            "THEME STUDIO (13 BLUEPRINT THEMES)",
            color = tokens.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )
        Text(
            "Instant live switching with hardware-accelerated Skia rendering.",
            color = tokens.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
        )

        // 13 Theme Cards Grid
        val allThemes = ColorTheme.entries
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allThemes.chunked(3).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowThemes.forEach { theme ->
                        val isSelected = settings.colorTheme == theme
                        val themePrimary = Color(theme.primaryHex)
                        val themeSecondary = Color(theme.secondaryHex)

                        Surface(
                            color = if (isSelected) tokens.primary.copy(alpha = 0.18f) else tokens.surfaceCard,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) tokens.borderGlow else tokens.borderGlass,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSettingsChange(settings.copy(colorTheme = theme))
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Swatch Circles
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(themePrimary)
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(themeSecondary)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        theme.title,
                                        color = if (isSelected) tokens.primary else tokens.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        theme.description,
                                        color = tokens.textMuted,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    // Fill remaining slots in last row if not multiple of 3
                    if (rowThemes.size < 3) {
                        repeat(3 - rowThemes.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Section: Appearance Mode & AMOLED
        Text(
            "DISPLAY MODE",
            color = tokens.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeSelectButton(
                title = "Dark Mode",
                selected = settings.theme == AppTheme.DARK && !settings.isAmoled,
                onClick = { onSettingsChange(settings.copy(theme = AppTheme.DARK, isAmoled = false)) }
            )
            ModeSelectButton(
                title = "AMOLED Pitch Black",
                selected = settings.isAmoled,
                onClick = { onSettingsChange(settings.copy(isAmoled = true)) }
            )
            ModeSelectButton(
                title = "Light Mode",
                selected = settings.theme == AppTheme.LIGHT && !settings.isAmoled,
                onClick = { onSettingsChange(settings.copy(theme = AppTheme.LIGHT, isAmoled = false)) }
            )
        }

        Spacer(Modifier.height(24.dp))

        // Section: Reader & Language
        Text(
            "READER & LANGUAGE",
            color = tokens.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeSelectButton(
                title = "Webtoon Vertical Strip (Default)",
                selected = settings.readerMode == ReaderModePreference.VERTICAL,
                onClick = { onSettingsChange(settings.copy(readerMode = ReaderModePreference.VERTICAL)) }
            )
            ModeSelectButton(
                title = "Single Page",
                selected = settings.readerMode == ReaderModePreference.SINGLE,
                onClick = { onSettingsChange(settings.copy(readerMode = ReaderModePreference.SINGLE)) }
            )
            ModeSelectButton(
                title = "Double Spread",
                selected = settings.readerMode == ReaderModePreference.DOUBLE,
                onClick = { onSettingsChange(settings.copy(readerMode = ReaderModePreference.DOUBLE)) }
            )
        }

        Spacer(Modifier.height(14.dp))

        // Language Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeSelectButton(
                title = "English",
                selected = settings.language == AppLanguage.ENGLISH,
                onClick = { onSettingsChange(settings.copy(language = AppLanguage.ENGLISH)) }
            )
            ModeSelectButton(
                title = "العربية (Arabic)",
                selected = settings.language == AppLanguage.ARABIC,
                onClick = { onSettingsChange(settings.copy(language = AppLanguage.ARABIC)) }
            )
            ModeSelectButton(
                title = "Français (French)",
                selected = settings.language == AppLanguage.FRENCH,
                onClick = { onSettingsChange(settings.copy(language = AppLanguage.FRENCH)) }
            )
        }

        Spacer(Modifier.height(28.dp))

        // Maintenance & Cache
        Surface(
            color = tokens.surfaceCard,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Disk & Memory Cache", color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Atomic disk cache and memory bounded LRU caches for webtoon chapters.", color = tokens.textMuted, fontSize = 11.sp)
                }
                Surface(
                    color = Color(0xFFDC2626).copy(alpha = 0.2f),
                    contentColor = Color(0xFFFF6B6B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClearCache() }
                ) {
                    Text("Clear Cache", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RowScope.ModeSelectButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Surface(
        color = if (selected) tokens.primary.copy(alpha = 0.2f) else tokens.surfaceCard,
        contentColor = if (selected) tokens.primary else tokens.textPrimary,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (selected) tokens.borderGlow else tokens.borderGlass,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(title, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
