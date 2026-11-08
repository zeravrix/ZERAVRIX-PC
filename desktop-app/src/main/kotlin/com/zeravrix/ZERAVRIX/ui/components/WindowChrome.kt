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
            // Refined Minimalist App Branding
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Surface(
                    color = tokens.primary.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            "Z",
                            color = tokens.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    "ZERAVRIX",
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.4.sp
                )
            }

            Spacer(Modifier.weight(1f))

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
        animationSpec = androidx.compose.animation.core.tween(80)
    )

    val fg by animateColorAsState(
        targetValue = when {
            isDanger && active -> Color.White
            active -> tokens.primary
            else -> tint
        },
        animationSpec = androidx.compose.animation.core.tween(80)
    )

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else if (hovered) 1.03f else 1f,
        animationSpec = androidx.compose.animation.core.tween(80)
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
