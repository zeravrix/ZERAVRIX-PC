package com.zeravrix.zeravrix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import org.jetbrains.skia.Image as SkiaImage

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
    val chromeShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)

    val logoBitmap = remember {
        runCatching {
            val bytes = object {}.javaClass.getResourceAsStream("/logo.png")?.readBytes()
            if (bytes != null && bytes.isNotEmpty()) {
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            } else null
        }.getOrNull()
    }

    WindowDraggableArea(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(chromeShape)
            .background(tokens.chromeGradient)
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
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(tokens.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = "ZERAVRIX Logo",
                            modifier = Modifier.fillMaxSize().padding(3.dp)
                        )
                    } else {
                        Text(
                            "ZX",
                            color = tokens.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "ZERAVRIX",
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tokens.primary.copy(alpha = 0.20f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "v1.6.0",
                        color = tokens.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            // Quick Search Command Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(tokens.surfaceCard)
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(15.dp))
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = tokens.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) searchQuery else "Search manhwa, manga, 1000+ sources... ($currentScreenTitle)",
                        color = if (searchQuery.isNotBlank()) tokens.textPrimary else tokens.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tokens.surfaceContainerHigh)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "Ctrl+K",
                            color = tokens.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // Reader Toggle pill if active
            if (readerActive) {
                Surface(
                    color = tokens.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable(onClick = onReaderToggle)
                        .padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = "Active Reader",
                            tint = tokens.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "READER ACTIVE",
                            color = tokens.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Window Controls (Minimize, Maximize, Close)
            ChromeIconButton(
                icon = Icons.Rounded.Remove,
                contentDescription = "Minimize",
                onClick = { windowState.isMinimized = true },
                tint = tokens.textMuted
            )
            Spacer(Modifier.width(2.dp))
            ChromeIconButton(
                icon = if (windowState.placement == WindowPlacement.Maximized) Icons.Rounded.FilterNone else Icons.Rounded.CropSquare,
                contentDescription = "Maximize / Restore",
                onClick = {
                    windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                        WindowPlacement.Floating
                    } else {
                        WindowPlacement.Maximized
                    }
                },
                tint = tokens.textMuted
            )
            Spacer(Modifier.width(2.dp))
            ChromeIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "Close",
                onClick = onClose,
                tint = tokens.textMuted,
                isDanger = true
            )
        }
    }
}

@Composable
private fun ChromeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    isDanger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed
    val tokens = LocalGlassTokens.current

    val bg by animateColorAsState(
        targetValue = when {
            isDanger && active -> Color(0xFFE11D48)
            active -> tokens.primary.copy(alpha = 0.20f)
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val fg by animateColorAsState(
        targetValue = when {
            isDanger && active -> Color.White
            active -> tokens.primary
            else -> tint
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else if (hovered) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .size(30.dp, 26.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
