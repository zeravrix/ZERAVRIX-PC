package com.zeravrix.zeravrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.theme.LocalGlassTokens

/** Rounded search field like Kotatsu's SearchBar. Enter calls [onSubmit]; the ✕ button clears the text. */
@Composable
fun ZSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onSubmit: () -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(28.dp)
    Surface(
        color = tokens.surfaceGlassRaised,
        shape = shape,
        modifier = modifier.height(52.dp).border(1.dp, tokens.borderGlass, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕", color = tokens.textMuted, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(hint, color = tokens.textMuted, fontSize = 16.sp)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = tokens.textPrimary, fontSize = 16.sp),
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
                Text(
                    "✕",
                    color = tokens.textMuted,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).clickable { onQueryChange("") }.padding(8.dp),
                )
            }
        }
    }
}

/** Outlined filter chip (selected = tinted). */
@Composable
fun ZChip(label: String, selected: Boolean, onClick: () -> Unit, icon: String? = null) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) tokens.primary.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) tokens.primary else tokens.borderGlass, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Text(icon, fontSize = 14.sp, color = if (selected) tokens.primary else tokens.textMuted)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) tokens.textPrimary else tokens.textMuted)
    }
}

/** Round reading-progress badge shown on a cover: "37%" or a check mark when finished. */
@Composable
fun ProgressBadge(percent: Int, modifier: Modifier = Modifier) {
    val tokens = LocalGlassTokens.current
    Box(
        modifier = modifier.size(34.dp).clip(CircleShape).background(tokens.primary.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        if (percent >= 100) {
            Text("✓", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text("$percent%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    val tokens = LocalGlassTokens.current
    Text(
        text,
        color = tokens.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Left pane = half the window up to [leftMaxWidth] (Kotatsu details/settings: 50 % max 420 dp); right pane takes the rest. */
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

/** Primary pill button with a drop-down arrow (Kotatsu's "Continue ▾"). [menu] receives a function that closes the menu. */
@Composable
fun ZSplitButton(
    label: String,
    onClick: () -> Unit,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    var open by remember { mutableStateOf(false) }
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                .background(tokens.primary)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(label, color = onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp))
                    .background(tokens.primary)
                    .clickable { open = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text("▾", color = onPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                menu { open = false }
            }
        }
    }
}
