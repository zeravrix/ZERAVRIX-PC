package com.zeravrix.zeravrix.ui.components

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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.theme.LocalGlassTokens

/**
 * Authentic Kotatsu Landscape Navigation Items
 */
enum class AppNavScreen(val id: String, val icon: String, val title: String) {
    HISTORY("history", "🕒", "History"),
    FAVOURITES("favourites", "♥", "Favourites"),
    EXPLORE("explore", "🧭", "Explore"),
    FEED("feed", "📰", "Feed"),
    SETTINGS("settings", "⚙", "Settings");

    // Backward compatibility aliases
    companion object {
        val LIBRARY = FAVOURITES
        val LATEST = EXPLORE
        val DOWNLOADS = EXPLORE
        val MUSIC = SETTINGS
    }
}

/**
 * Replicates Kotatsu's layout-w600dp-land/activity_main.xml navigation rail
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

    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .width(92.dp),
        containerColor = tokens.surfaceContainer,
        contentColor = tokens.textPrimary,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
            ) {
                // App Logo Badge
                Surface(
                    color = tokens.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(42.dp)
                        .border(1.dp, tokens.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "ZX",
                            color = tokens.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Kotatsu Navigation Rail FAB (Resume Reading)
                if (hasResumeTarget && onResumeReading != null) {
                    FloatingActionButton(
                        onClick = onResumeReading,
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text("▶", fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
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

            Spacer(Modifier.weight(1f))

            // Bottom Brand Text
            Text(
                "ZERAVRIX",
                color = tokens.textMuted.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
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

    val containerColor = when {
        selected -> tokens.primaryContainer
        hovered -> tokens.surfaceContainerHigh
        else -> Color.Transparent
    }

    val contentColor = when {
        selected -> tokens.primary
        hovered -> tokens.textPrimary
        else -> tokens.textMuted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 6.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        // M3 Indicator Pill
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor)
        ) {
            Text(
                text = screen.icon,
                color = contentColor,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
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

        Spacer(Modifier.height(4.dp))

        Text(
            text = screen.title,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
