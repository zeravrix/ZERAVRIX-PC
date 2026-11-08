package com.zeravrix.zeravrix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import org.jetbrains.skia.Image as SkiaImage

/**
 * Authentic Kotatsu Landscape Navigation Items with High-Quality Material Icons
 */
enum class AppNavScreen(
    val id: String,
    val icon: ImageVector,
    val title: String,
) {
    HISTORY("history", Icons.Rounded.History, "History"),
    FAVOURITES("favourites", Icons.Rounded.Favorite, "Favourites"),
    DOWNLOADS("downloads", Icons.Rounded.Download, "Downloads"),
    EXPLORE("explore", Icons.Rounded.Explore, "Explore"),
    FEED("feed", Icons.Rounded.DynamicFeed, "Feed"),
    SETTINGS("settings", Icons.Rounded.Settings, "Settings");

    // Backward compatibility aliases
    companion object {
        val LIBRARY = FAVOURITES
        val LATEST = EXPLORE
        val MUSIC = SETTINGS
    }
}

/**
 * Replicates Kotatsu's layout-w600dp-land/activity_main.xml navigation rail
 * Enhanced with modern Glassmorphism, smooth animations & vector typography
 */
@Composable
fun AppNavigationRail(
    currentScreen: AppNavScreen,
    onScreenSelect: (AppNavScreen) -> Unit,
    onResumeReading: (() -> Unit)? = null,
    hasResumeTarget: Boolean = false,
    feedBadgeCount: Int = 0,
) {
    val tokens = LocalGlassTokens.current

    val logoBitmap = remember {
        runCatching {
            val bytes = object {}.javaClass.getResourceAsStream("/logo.png")?.readBytes()
            if (bytes != null && bytes.isNotEmpty()) {
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            } else null
        }.getOrNull()
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(88.dp)
            .border(
                width = 1.dp,
                color = tokens.borderGlass,
                shape = RoundedCornerShape(0.dp)
            ),
        color = tokens.surfaceGlass,
        contentColor = tokens.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Branding Mark
            Surface(
                color = tokens.primaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(46.dp)
                    .border(1.dp, tokens.borderGlow.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), spotColor = tokens.primary)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(6.dp)) {
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = "ZERAVRIX",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            "ZX",
                            color = tokens.primary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Kotatsu Navigation Rail FAB (Resume Reading)
            if (hasResumeTarget && onResumeReading != null) {
                FloatingActionButton(
                    onClick = onResumeReading,
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Resume Reading",
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Navigation Destination Items
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AppNavScreen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    val badge = if (screen == AppNavScreen.FEED && feedBadgeCount > 0) feedBadgeCount.toString() else null

                    KotatsuNavRailItem(
                        screen = screen,
                        selected = selected,
                        badge = badge,
                        onClick = { onScreenSelect(screen) }
                    )
                }
            }

            // Bottom Brand Text
            Text(
                "ZERAVRIX",
                color = tokens.textMuted.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun KotatsuNavRailItem(
    screen: AppNavScreen,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    // Smooth hover / active animations
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else if (hovered) 48.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> tokens.primaryContainer
            hovered -> tokens.surfaceContainerHigh
            else -> Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            selected -> tokens.primary
            hovered -> tokens.textPrimary
            else -> tokens.textMuted
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 6.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        // M3 Indicator Pill
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor)
        ) {
            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )

            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 4.dp)
                        .clip(CircleShape)
                        .background(tokens.primary)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(badge, color = tokens.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = screen.title,
            color = iconColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
