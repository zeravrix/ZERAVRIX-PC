package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.net.URI

enum class SettingsSection(val title: String, val icon: ImageVector, val subtitle: String) {
    APPEARANCE("Appearance", Icons.Rounded.Palette, "Theme presets, night mode, AMOLED"),
    READER("Reader", Icons.Rounded.MenuBook, "Reading direction, mode, margins"),
    SOURCES("Sources", Icons.Rounded.Language, "Mature content filters, preferred language"),
    DOWNLOADS("Downloads", Icons.Rounded.Download, "Download threads, storage path"),
    STORAGE("Storage & Cache", Icons.Rounded.Storage, "Image cache cleanup, history"),
    LIBRARY("Library & Backup", Icons.Rounded.Bookmarks, "Auto-refresh, data backup"),
    SYSTEM("System & Performance", Icons.Rounded.Speed, "Tray minimize, GPU acceleration"),
    ABOUT("About ZERAVRIX", Icons.Rounded.Info, "Official links, website, license")
}

private fun openBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
        }
    }
}

/**
 * Professional two-pane landscape Settings Screen
 * All settings are connected to real, working functionality in AppSettings
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: (() -> Unit)? = null,
    updateInfo: AppUpdateInfo? = null,
    onDownloadUpdate: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    var selectedSection by remember { mutableStateOf(SettingsSection.APPEARANCE) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Master Column (Left 280dp)
        Column(
            modifier = Modifier
                .width(280.dp)
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
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .hoverable(interaction)
                        .clickable { selectedSection = section }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = section.icon,
                            contentDescription = null,
                            tint = if (isSelected) tokens.primary else if (hovered) tokens.textPrimary else tokens.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = section.title,
                                color = if (isSelected) tokens.primary else tokens.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = section.subtitle,
                                color = tokens.textMuted,
                                fontSize = 10.sp,
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
                .padding(28.dp)
        ) {
            when (selectedSection) {
                SettingsSection.APPEARANCE -> {
                    SectionTitle("APPEARANCE", "Color Themes & Dark Mode Palettes")

                    Text(
                        "THEME PRESET",
                        color = tokens.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    val themes = ColorTheme.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { theme ->
                                    val isSelected = settings.colorTheme == theme
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
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                onSettingsChange(
                                                    settings.copy(
                                                        colorTheme = theme,
                                                        isAmoled = (theme == ColorTheme.AMOLED_VOID)
                                                    )
                                                )
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(theme.primaryHex))
                                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = theme.title,
                                                color = if (isSelected) tokens.primary else tokens.textPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Dark Mode & AMOLED Toggles
                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingToggleRow(
                                title = "Dark Mode",
                                subtitle = "Comfortable nighttime color palette",
                                checked = settings.theme == AppTheme.DARK,
                                onCheckedChange = { isDark ->
                                    onSettingsChange(settings.copy(theme = if (isDark) AppTheme.DARK else AppTheme.LIGHT))
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.borderGlass)

                            SettingToggleRow(
                                title = "AMOLED Pitch Black",
                                subtitle = "True #000000 background for OLED displays",
                                checked = settings.isAmoled,
                                onCheckedChange = { isAmoled ->
                                    onSettingsChange(settings.copy(isAmoled = isAmoled))
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.borderGlass)

                            SettingToggleRow(
                                title = "Smooth UI Animations",
                                subtitle = "Physical spring animations across panels and buttons",
                                checked = settings.animationLevel == AnimationLevel.SMOOTH,
                                onCheckedChange = { enabled ->
                                    onSettingsChange(settings.copy(animationLevel = if (enabled) AnimationLevel.SMOOTH else AnimationLevel.OFF))
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Application Icon Personalization
                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("APPLICATION ICON", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Personalize the desktop application and window titlebar icon", color = tokens.textMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(14.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val previewBmp = remember(settings.customIconPath) {
                                    val bytes = runCatching {
                                        if (!settings.customIconPath.isNullOrBlank()) {
                                            val f = java.io.File(settings.customIconPath)
                                            if (f.exists()) f.readBytes() else null
                                        } else null
                                    }.getOrNull() ?: runCatching {
                                        object {}.javaClass.getResourceAsStream("/logo.png")?.readBytes()
                                    }.getOrNull()

                                    if (bytes != null && bytes.isNotEmpty()) {
                                        runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                                    } else null
                                }

                                if (previewBmp != null) {
                                    Image(
                                        bitmap = previewBmp,
                                        contentDescription = "App Icon",
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, tokens.borderGlass, RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(tokens.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("ZX", color = tokens.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }

                                Spacer(Modifier.width(16.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Select Custom Icon (PNG, JPG, ICO)", java.awt.FileDialog.LOAD)
                                                dialog.setFilenameFilter { _, name ->
                                                    val l = name.lowercase()
                                                    l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".ico")
                                                }
                                                dialog.isVisible = true
                                                val dir = dialog.directory
                                                val file = dialog.file
                                                if (dir != null && file != null) {
                                                    val selected = java.io.File(dir, file)
                                                    if (selected.exists()) {
                                                        onSettingsChange(settings.copy(customIconPath = selected.absolutePath))
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = tokens.primaryContainer,
                                                contentColor = tokens.primary
                                            )
                                        ) {
                                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Choose Custom Icon", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (!settings.customIconPath.isNullOrBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    onSettingsChange(settings.copy(customIconPath = null))
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, tokens.borderGlass),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.textMuted)
                                            ) {
                                                Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Reset to Default", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    Text(
                                        if (!settings.customIconPath.isNullOrBlank()) "Custom icon active: ${java.io.File(settings.customIconPath).name}"
                                        else "Default ZERAVRIX icon active",
                                        color = tokens.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSection.READER -> {
                    SectionTitle("READER PREFERENCES", "Configure reading mode, page direction, and canvas layouts")

                    Text("DEFAULT READING MODE", color = tokens.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))

                    val modes = listOf(
                        ReaderModePreference.VERTICAL to "Webtoon (Continuous Vertical)",
                        ReaderModePreference.SINGLE to "Single Page (Horizontal)",
                        ReaderModePreference.DOUBLE to "Double Page Spread",
                        ReaderModePreference.AUTO to "Japanese Manga (Right-to-Left)"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        modes.forEach { (mode, label) ->
                            val isSelected = settings.readerMode == mode
                            Surface(
                                color = if (isSelected) tokens.primaryContainer else tokens.surfaceCard,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, if (isSelected) tokens.primary else tokens.borderGlass, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSettingsChange(settings.copy(readerMode = mode)) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSettingsChange(settings.copy(readerMode = mode)) }
                                    )
                                    Spacer(Modifier.width(10.dp))
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

                    Spacer(Modifier.height(18.dp))

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingToggleRow(
                                title = "Auto-Crop Margins",
                                subtitle = "Automatically trim white page boundaries",
                                checked = settings.autoCropMargins,
                                onCheckedChange = { onSettingsChange(settings.copy(autoCropMargins = it)) }
                            )
                        }
                    }
                }

                SettingsSection.SOURCES -> {
                    SectionTitle("SOURCES & CONTENT", "Content safety, age ratings, and preferred languages")

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingToggleRow(
                                title = "Display 18+ Mature Content",
                                subtitle = "Show adult and mature manga in catalog and search",
                                checked = settings.nsfwEnabled,
                                onCheckedChange = { onSettingsChange(settings.copy(nsfwEnabled = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.borderGlass)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Preferred Source Language", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Filter source catalog defaults", color = tokens.textMuted, fontSize = 11.sp)
                                }
                                var langMenu by remember { mutableStateOf(false) }
                                Box {
                                    FilledTonalButton(
                                        onClick = { langMenu = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(settings.preferredLanguage, fontSize = 12.sp)
                                    }
                                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                                        listOf("ALL", "en", "ar", "ja", "ko", "es", "fr").forEach { l ->
                                            DropdownMenuItem(
                                                text = { Text(l.uppercase()) },
                                                onClick = {
                                                    onSettingsChange(settings.copy(preferredLanguage = l))
                                                    langMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsSection.DOWNLOADS -> {
                    SectionTitle("DOWNLOAD MANAGEMENT", "Offline chapter archive settings and concurrency")

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Concurrent Download Threads", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Simultaneous page downloads (${settings.concurrentDownloads} threads)", color = tokens.textMuted, fontSize = 11.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1, 2, 3, 4).forEach { count ->
                                        FilledTonalButton(
                                            onClick = { onSettingsChange(settings.copy(concurrentDownloads = count)) },
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = if (settings.concurrentDownloads == count) tokens.primary else tokens.surfaceContainerHigh,
                                                contentColor = if (settings.concurrentDownloads == count) tokens.onPrimary else tokens.textPrimary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.borderGlass)

                            Column {
                                Text("Download Directory", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("~/.zeravrix/downloads", color = tokens.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                SettingsSection.STORAGE -> {
                    SectionTitle("STORAGE & CACHE", "Manage local disk usage, image cache, and reading history")

                    var cacheCleared by remember { mutableStateOf(false) }
                    var historyCleared by remember { mutableStateOf(false) }

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Image & Chapter Cache", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Cached covers and chapters stored in ~/.zeravrix/cache", color = tokens.textMuted, fontSize = 11.sp)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onClearCache()
                                        cacheCleared = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.primary, contentColor = tokens.onPrimary)
                                ) {
                                    Icon(if (cacheCleared) Icons.Rounded.Check else Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (cacheCleared) "Cleared" else "Clear Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.borderGlass)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Reading History", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Clear all recorded reading progress and history entries", color = tokens.textMuted, fontSize = 11.sp)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onClearHistory?.invoke()
                                        historyCleared = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary)
                                ) {
                                    Icon(if (historyCleared) Icons.Rounded.Check else Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (historyCleared) "History Cleared" else "Clear History", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                SettingsSection.LIBRARY -> {
                    SectionTitle("LIBRARY & DATA", "Library synchronization and automatic update preferences")

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingToggleRow(
                                title = "Refresh Library on Startup",
                                subtitle = "Automatically check favorites for new chapters when launching",
                                checked = settings.refreshOnOpen,
                                onCheckedChange = { onSettingsChange(settings.copy(refreshOnOpen = it)) }
                            )
                        }
                    }
                }

                SettingsSection.SYSTEM -> {
                    SectionTitle("SYSTEM & PERFORMANCE", "Windows desktop runtime behavior and hardware pipeline")

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SettingToggleRow(
                                title = "Minimize to System Tray",
                                subtitle = "Keep application active in system tray when closed",
                                checked = settings.minimizeToTray,
                                onCheckedChange = { onSettingsChange(settings.copy(minimizeToTray = it)) }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = tokens.borderGlass)

                            Column {
                                Text("Graphics Pipeline", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Skia / Skiko Hardware Acceleration (DirectX 12 / OpenGL)", color = tokens.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                SettingsSection.ABOUT -> {
                    SectionTitle("ABOUT ZERAVRIX", "Application details and official links")

                    Surface(
                        color = tokens.surfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(22.dp)) {
                            Text("ZERAVRIX", color = tokens.primary, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "High-performance native Windows Manga, Manhua, and Manhwa reader engineered with Compose Desktop and Skia hardware acceleration.",
                                color = tokens.textMuted,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(18.dp))

                            // GitHub Release / Update Banner
                            if (updateInfo != null) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Download, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("New Update Available: v${updateInfo.version}", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("A newer version of ZERAVRIX is available for download on GitHub.", color = tokens.textMuted, fontSize = 11.sp)
                                        }
                                        FilledTonalButton(
                                            onClick = onDownloadUpdate,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                                        ) {
                                            Text("Download Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    color = tokens.surfaceCard,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, tokens.borderGlass),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("ZERAVRIX is up to date (v${AppUpdateChecker.CURRENT_VERSION})", color = tokens.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            Text("You are using the latest release", color = tokens.textMuted, fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = onCheckForUpdate,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, tokens.borderGlass)
                                        ) {
                                            Text("Check for Updates", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            Text("OFFICIAL CHANNELS", color = tokens.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { openBrowser("https://zeravrix.pages.dev") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.primary, contentColor = tokens.onPrimary)
                                ) {
                                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Official Website", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                FilledTonalButton(
                                    onClick = { openBrowser("https://github.com/zeravrix/ZERAVRIX-PC") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary)
                                ) {
                                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("GitHub Repository", fontSize = 12.sp)
                                }

                                FilledTonalButton(
                                    onClick = { openBrowser("https://discord.gg/zeravrix") },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary)
                                ) {
                                    Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Discord Server", fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider(color = tokens.borderGlass)
                            Spacer(Modifier.height(14.dp))

                            Text("License: GNU General Public License v3.0", color = tokens.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    val tokens = LocalGlassTokens.current
    Text(
        title,
        color = tokens.primary,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp
    )
    Spacer(Modifier.height(4.dp))
    Text(
        subtitle,
        color = tokens.textMuted,
        fontSize = 12.sp
    )
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = tokens.textMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
