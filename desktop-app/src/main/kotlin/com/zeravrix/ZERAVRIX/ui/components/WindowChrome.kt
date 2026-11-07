package com.zeravrix.zeravrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.zeravrix.zeravrix.theme.LocalGlassTokens

@Composable
fun WindowScope.WindowChrome(
    windowState: WindowState,
    currentScreenTitle: String,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onClose: () -> Unit,
    readerActive: Boolean = false,
    onReaderToggle: () -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    val chromeShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

    WindowDraggableArea(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(chromeShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        tokens.surfaceGlass,
                        tokens.surfaceGlassRaised,
                        tokens.surfaceGlass,
                    )
                )
            )
            .border(1.dp, tokens.borderGlass, chromeShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Branding & Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tokens.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ZX",
                        color = tokens.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "ZERAVRIX",
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tokens.primary.copy(alpha = 0.18f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "v1.6.0",
                        color = tokens.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Quick Search Command Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tokens.surfaceCard)
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", color = tokens.primary, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (searchQuery.isNotBlank()) searchQuery else "Search manhwa, manhua, manga... ($currentScreenTitle)",
                        color = if (searchQuery.isNotBlank()) tokens.textPrimary else tokens.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Reader Toggle pill if active
            if (readerActive) {
                ChromeButton(
                    label = "📖 READER",
                    onClick = onReaderToggle,
                    color = tokens.primary,
                    width = 80.dp
                )
                Spacer(Modifier.width(6.dp))
            }

            // Window Controls (Minimize, Maximize, Close)
            ChromeButton(
                label = "—",
                onClick = { windowState.isMinimized = true },
                color = tokens.textMuted
            )
            Spacer(Modifier.width(4.dp))
            ChromeButton(
                label = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                onClick = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                color = tokens.textMuted
            )
            Spacer(Modifier.width(4.dp))
            ChromeButton(
                label = "✕",
                onClick = onClose,
                color = tokens.textMuted,
                isDanger = true
            )
        }
    }
}

@Composable
private fun ChromeButton(
    label: String,
    onClick: () -> Unit,
    color: Color,
    width: androidx.compose.ui.unit.Dp = 30.dp,
    isDanger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed
    val tokens = LocalGlassTokens.current

    val bg = when {
        isDanger && active -> Color(0xFFDC2626)
        active -> tokens.primary.copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    val fg = when {
        isDanger && active -> Color.White
        active -> tokens.primary
        else -> color
    }

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .width(width)
            .height(28.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer(scaleX = if (active) 1.05f else 1f, scaleY = if (active) 1.05f else 1f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
