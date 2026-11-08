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
    EXPLORE("explore", Icons.Rounded.Explore, "Explore"),
    FAVOURITES("favourites", Icons.Rounded.Favorite, "Favourites"),
    HISTORY("history", Icons.Rounded.History, "History"),
    DOWNLOADS("downloads", Icons.Rounded.Download, "Downloads"),
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
            .width(80.dp)
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
                    .size(44.dp)
                    .border(1.dp, tokens.borderGlow.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(14.dp), spotColor = tokens.primary)
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

            Spacer(Modifier.height(14.dp))

            // Settings button explicitly placed at TOP above other buttons as requested
            val isSettingsSelected = currentScreen == AppNavScreen.SETTINGS
            val settingsInteraction = remember { MutableInteractionSource() }
            val settingsHovered by settingsInteraction.collectIsHoveredAsState()

            Surface(
                color = when {
                    isSettingsSelected -> tokens.primaryContainer
                    settingsHovered -> tokens.surfaceContainerHigh
                    else -> tokens.surfaceContainer.copy(alpha = 0.6f)
                },
                contentColor = if (isSettingsSelected) tokens.primary else tokens.textPrimary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .size(46.dp)
                    .hoverable(settingsInteraction)
                    .clickable(interactionSource = settingsInteraction, indication = null) {
                        onScreenSelect(AppNavScreen.SETTINGS)
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = if (isSettingsSelected) tokens.primary else if (settingsHovered) tokens.textPrimary else tokens.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            HorizontalDivider(
                modifier = Modifier.width(40.dp),
                thickness = 1.dp,
                color = tokens.outlineVariant.copy(alpha = 0.35f)
            )

            Spacer(Modifier.height(12.dp))

            // Kotatsu Navigation Rail FAB (Resume Reading)
            if (hasResumeTarget && onResumeReading != null) {
                FloatingActionButton(
                    onClick = onResumeReading,
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Resume Reading",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // Main Content Navigation Items (Exclude SETTINGS since it is placed at TOP above other buttons)
            val contentScreens = listOf(
                AppNavScreen.EXPLORE,
                AppNavScreen.FAVOURITES,
                AppNavScreen.HISTORY,
                AppNavScreen.DOWNLOADS,
                AppNavScreen.FEED
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                contentScreens.forEach { screen ->
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
        targetValue = if (hovered) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp, 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.title,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 6.dp)
                    .clip(CircleShape)
                    .background(tokens.primary)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(badge, color = tokens.onPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
