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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Top-level application navigation destinations.
 */
enum class AppNavScreen(
    val id: String,
    val icon: ImageVector,
    val title: String,
) {
    HISTORY("history", Icons.Rounded.History, "History"),
    FAVOURITES("favourites", Icons.Rounded.Favorite, "Favorites"),
    EXPLORE("explore", Icons.Rounded.Explore, "Explore"),
    FEED("feed", Icons.Rounded.DynamicFeed, "Feed"),
    SETTINGS("settings", Icons.Rounded.Settings, "Settings"),
    DOWNLOADS("downloads", Icons.Rounded.Download, "Downloads");

    companion object {
        val LIBRARY = FAVOURITES
        val LATEST = EXPLORE
        val MUSIC = SETTINGS
    }
}

/**
 * Clean, modern navigation rail matching exact layout order:
 * 1. History
 * 2. Favorites
 * 3. Explore
 * (Noticeable visual gap)
 * 5. Feed (above Settings in bottom-left)
 * 4. Settings (bottom-left)
 */
@Composable
fun AppNavigationRail(
    currentScreen: AppNavScreen,
    onScreenSelect: (AppNavScreen) -> Unit,
    feedBadgeCount: Int = 0,
) {
    val tokens = LocalGlassTokens.current

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(76.dp)
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
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP NAVIGATION GROUP: 1. History, 2. Favorites, 3. Explore
            val topScreens = listOf(
                AppNavScreen.HISTORY,
                AppNavScreen.FAVOURITES,
                AppNavScreen.EXPLORE
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topScreens.forEach { screen ->
                    NavRailButton(
                        screen = screen,
                        selected = currentScreen == screen,
                        onClick = { onScreenSelect(screen) }
                    )
                }
            }

            // NOTICEABLE VISUAL GAP between main navigation and bottom actions
            Spacer(modifier = Modifier.weight(1f))

            // BOTTOM NAVIGATION GROUP: 5. Feed (above Settings), 4. Settings (at the bottom)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Feed button
                NavRailButton(
                    screen = AppNavScreen.FEED,
                    selected = currentScreen == AppNavScreen.FEED,
                    badge = if (feedBadgeCount > 0) feedBadgeCount.toString() else null,
                    onClick = { onScreenSelect(AppNavScreen.FEED) }
                )

                // Settings button (positioned at the bottom-left of screen)
                NavRailButton(
                    screen = AppNavScreen.SETTINGS,
                    selected = currentScreen == AppNavScreen.SETTINGS,
                    onClick = { onScreenSelect(AppNavScreen.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun NavRailButton(
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
        animationSpec = androidx.compose.animation.core.tween(100)
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            selected -> tokens.primary
            hovered -> tokens.textPrimary
            else -> tokens.textMuted
        },
        animationSpec = androidx.compose.animation.core.tween(100)
    )

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.04f else 1f,
        animationSpec = androidx.compose.animation.core.tween(100)
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(50.dp, 40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(13.dp))
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
                    .background(tokens.primary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badge,
                    color = tokens.onPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
