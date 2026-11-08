package com.zeravrix.zeravrix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.theme.LocalGlassTokens

/**
 * Modern Cyber-Glass Search Field with Vector Icons & Instant Clear
 */
@Composable
fun ZSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(12.dp)

    Surface(
        color = tokens.surfaceCard,
        shape = shape,
        modifier = modifier
            .height(38.dp)
            .border(1.dp, tokens.borderGlass, shape)
            .shadow(elevation = 2.dp, shape = shape, spotColor = tokens.primaryGlow),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = tokens.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(hint, color = tokens.textMuted, fontSize = 13.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(tokens.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                onSubmit()
                                true
                            } else {
                                false
                            }
                        },
                )
            }
            if (query.isNotEmpty()) {
                Surface(
                    color = tokens.surfaceContainerHigh,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = tokens.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated Glass Filter Chip with Vector Icon support & spring transitions
 */
@Composable
fun ZChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg by animateColorAsState(
        targetValue = when {
            selected -> tokens.primaryContainer
            hovered -> tokens.surfaceContainerHigh
            else -> tokens.surfaceCard
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> tokens.primary
            hovered -> tokens.borderGlow
            else -> tokens.borderGlass
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) tokens.primary else if (hovered) tokens.textPrimary else tokens.textMuted,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Surface(
        color = bg,
        shape = shape,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * Backward compatibility overload for String emoji icons
 */
@Composable
fun ZChip(label: String, selected: Boolean, onClick: () -> Unit, icon: String?) {
    val vectorIcon = when (icon) {
        "✦", "⭐" -> Icons.Rounded.AutoAwesome
        "🔞" -> Icons.Rounded.Explicit
        "✎" -> Icons.Rounded.Edit
        "▣" -> Icons.Rounded.Devices
        "▶" -> Icons.Rounded.PlayArrow
        "✓" -> Icons.Rounded.Check
        "♡" -> Icons.Rounded.Favorite
        "⊘" -> Icons.Rounded.Block
        else -> null
    }
    ZChip(label = label, selected = selected, onClick = onClick, icon = vectorIcon)
}

/**
 * Elegant circular reading-progress badge
 */
@Composable
fun ProgressBadge(percent: Int, modifier: Modifier = Modifier) {
    val tokens = LocalGlassTokens.current
    Surface(
        color = tokens.primary,
        shape = CircleShape,
        modifier = modifier
            .size(32.dp)
            .shadow(elevation = 4.dp, shape = CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (percent >= 100) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Finished",
                    tint = tokens.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(
                    text = "$percent%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.onPrimary
                )
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    val tokens = LocalGlassTokens.current
    Text(
        text = text,
        color = tokens.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
    )
}

/**
 * Left pane = responsive split up to [leftMaxWidth] (Kotatsu details/settings: 50% max 420dp)
 */
@Composable
fun ZTwoPane(
    modifier: Modifier = Modifier,
    leftMaxWidth: Dp = 420.dp,
    leftFraction: Float = 0.5f,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val leftWidth = minOf(maxWidth * leftFraction, leftMaxWidth)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(leftWidth).fillMaxHeight()) { left() }
            Box(Modifier.weight(1f).fillMaxHeight()) { right() }
        }
    }
}

/**
 * Primary action button with split drop-down menu
 */
@Composable
fun ZSplitButton(
    label: String,
    onClick: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    var open by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = tokens.primary,
            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = tokens.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = tokens.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Box {
            Surface(
                color = tokens.primary,
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp),
                modifier = Modifier.clickable { open = true }
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Options",
                        tint = tokens.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false }
            ) {
                menu { open = false }
            }
        }
    }
}
