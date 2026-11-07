package com.zeravrix.zeravrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.theme.LocalGlassTokens

enum class AppNavScreen(val icon: String, val title: String) {
    LIBRARY("◷", "Library"),
    EXPLORE("◈", "Explore"),
    LATEST("✦", "Latest"),
    DOWNLOADS("⇩", "Downloads"),
    MUSIC("♫", "Audio Lounge"),
    SETTINGS("⚙", "Settings & 13 Themes"),
}

@Composable
fun AppNavigationRail(
    currentScreen: AppNavScreen,
    onScreenSelect: (AppNavScreen) -> Unit,
    downloadCount: Int = 0,
) {
    val tokens = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(
                        tokens.surfaceGlass,
                        tokens.background,
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(tokens.borderGlass, tokens.borderGlass.copy(alpha = 0.05f))
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        // Scope Header
        Text(
            "MANGA • MANHWA",
            color = tokens.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Navigation Items
        Text(
            "READING",
            color = tokens.textMuted.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        RailNavItem(
            screen = AppNavScreen.LIBRARY,
            selected = currentScreen == AppNavScreen.LIBRARY,
            onClick = { onScreenSelect(AppNavScreen.LIBRARY) }
        )
        RailNavItem(
            screen = AppNavScreen.LATEST,
            selected = currentScreen == AppNavScreen.LATEST,
            onClick = { onScreenSelect(AppNavScreen.LATEST) }
        )
        RailNavItem(
            screen = AppNavScreen.DOWNLOADS,
            selected = currentScreen == AppNavScreen.DOWNLOADS,
            badge = if (downloadCount > 0) downloadCount.toString() else null,
            onClick = { onScreenSelect(AppNavScreen.DOWNLOADS) }
        )

        Spacer(Modifier.height(14.dp))

        Text(
            "DISCOVER",
            color = tokens.textMuted.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        RailNavItem(
            screen = AppNavScreen.EXPLORE,
            selected = currentScreen == AppNavScreen.EXPLORE,
            onClick = { onScreenSelect(AppNavScreen.EXPLORE) }
        )
        RailNavItem(
            screen = AppNavScreen.MUSIC,
            selected = currentScreen == AppNavScreen.MUSIC,
            onClick = { onScreenSelect(AppNavScreen.MUSIC) }
        )

        Spacer(Modifier.height(14.dp))

        Text(
            "PREFERENCES",
            color = tokens.textMuted.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        RailNavItem(
            screen = AppNavScreen.SETTINGS,
            selected = currentScreen == AppNavScreen.SETTINGS,
            onClick = { onScreenSelect(AppNavScreen.SETTINGS) }
        )

        Spacer(Modifier.weight(1f))

        // Bottom Info Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.surfaceCard)
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(10.dp))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    "ZERAVRIX PC",
                    color = tokens.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "High-Speed Skia Core",
                    color = tokens.textMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun RailNavItem(
    screen: AppNavScreen,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed

    val bg = when {
        selected -> tokens.primary.copy(alpha = 0.20f)
        active -> tokens.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val borderStroke = when {
        selected -> tokens.borderGlow
        active -> tokens.borderGlass
        else -> Color.Transparent
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(1.dp, borderStroke, RoundedCornerShape(10.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                screen.icon,
                color = if (selected) tokens.primary else if (active) tokens.textPrimary else tokens.textMuted,
                fontSize = 15.sp,
                modifier = Modifier.width(22.dp)
            )
            Text(
                screen.title,
                color = if (selected) tokens.primary else if (active) tokens.textPrimary else tokens.textMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            if (badge != null) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(badge, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else if (selected) {
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.primary)
                )
            }
        }
    }
}
