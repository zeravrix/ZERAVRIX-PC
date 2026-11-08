package com.zeravrix.zeravrix.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.ReaderModePreference
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.screens.RemoteCoverImage
import kotlinx.coroutines.launch

enum class ReaderDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT
}

enum class ReaderBgColor(val label: String, val color: Color, val textColor: Color) {
    AMOLED("AMOLED", Color(0xFF000000), Color(0xFFEEEEEE)),
    DARK("Dark", Color(0xFF12141A), Color(0xFFEEEEEE)),
    SEPIA("Sepia", Color(0xFF231C16), Color(0xFFE8D8C8)),
    LIGHT("Light", Color(0xFFF8F9FA), Color(0xFF1A1A1A))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopReaderView(
    mangaTitle: String,
    chapterTitle: String,
    pages: List<MangaPage>,
    initialPage: Int = 0,
    initialMode: ReaderModePreference = ReaderModePreference.VERTICAL,
    onClose: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(initialMode) }
    var singlePageIndex by remember { mutableIntStateOf(initialPage) }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var readerBg by remember { mutableStateOf(if (tokens.isAmoled) ReaderBgColor.AMOLED else ReaderBgColor.DARK) }
    var readingDirection by remember { mutableStateOf(ReaderDirection.LEFT_TO_RIGHT) }
    var fitWidth by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (mode == ReaderModePreference.VERTICAL) {
            onPageChanged(listState.firstVisibleItemIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(readerBg.color)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape -> {
                            if (showSettingsDialog) {
                                showSettingsDialog = false
                            } else {
                                onClose()
                            }
                            true
                        }
                        Key.Tab -> {
                            showSettingsDialog = !showSettingsDialog
                            true
                        }
                        Key.Spacebar, Key.DirectionDown, Key.J -> {
                            if (mode == ReaderModePreference.VERTICAL) {
                                scope.launch {
                                    val current = listState.firstVisibleItemIndex
                                    listState.animateScrollToItem((current + 1).coerceAtMost(pages.lastIndex))
                                }
                            } else {
                                if (readingDirection == ReaderDirection.LEFT_TO_RIGHT) {
                                    if (singlePageIndex < pages.lastIndex) {
                                        singlePageIndex++
                                        onPageChanged(singlePageIndex)
                                    }
                                } else {
                                    if (singlePageIndex > 0) {
                                        singlePageIndex--
                                        onPageChanged(singlePageIndex)
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionUp, Key.K -> {
                            if (mode == ReaderModePreference.VERTICAL) {
                                scope.launch {
                                    val current = listState.firstVisibleItemIndex
                                    listState.animateScrollToItem((current - 1).coerceAtLeast(0))
                                }
                            } else {
                                if (readingDirection == ReaderDirection.LEFT_TO_RIGHT) {
                                    if (singlePageIndex > 0) {
                                        singlePageIndex--
                                        onPageChanged(singlePageIndex)
                                    }
                                } else {
                                    if (singlePageIndex < pages.lastIndex) {
                                        singlePageIndex++
                                        onPageChanged(singlePageIndex)
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionRight, Key.D -> {
                            if (readingDirection == ReaderDirection.LEFT_TO_RIGHT) {
                                if (singlePageIndex < pages.lastIndex) {
                                    singlePageIndex++
                                    onPageChanged(singlePageIndex)
                                }
                            } else {
                                if (singlePageIndex > 0) {
                                    singlePageIndex--
                                    onPageChanged(singlePageIndex)
                                }
                            }
                            true
                        }
                        Key.DirectionLeft, Key.A -> {
                            if (readingDirection == ReaderDirection.LEFT_TO_RIGHT) {
                                if (singlePageIndex > 0) {
                                    singlePageIndex--
                                    onPageChanged(singlePageIndex)
                                }
                            } else {
                                if (singlePageIndex < pages.lastIndex) {
                                    singlePageIndex++
                                    onPageChanged(singlePageIndex)
                                }
                            }
                            true
                        }
                        Key.Plus, Key.Equals, Key.NumPadAdd -> {
                            zoomScale = (zoomScale + 0.15f).coerceAtMost(3.0f)
                            true
                        }
                        Key.Minus, Key.NumPadSubtract -> {
                            zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.4f)
                            true
                        }
                        Key.Zero, Key.NumPad0 -> {
                            zoomScale = 1.0f
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                if (event.keyboardModifiers.isCtrlPressed) {
                    val delta = change.scrollDelta.y
                    if (delta < 0) {
                        zoomScale = (zoomScale + 0.10f).coerceAtMost(3.0f)
                    } else if (delta > 0) {
                        zoomScale = (zoomScale - 0.10f).coerceAtLeast(0.4f)
                    }
                    change.consume()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showSettingsDialog) {
                    showSettingsDialog = false
                } else {
                    showControls = !showControls
                }
            }
    ) {
        // Reader Content Area with Native Width Scaling (Keeps edge gaps, removes bottom gap, crystal-sharp Skia rendering)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (mode) {
                ReaderModePreference.VERTICAL, ReaderModePreference.AUTO -> {
                    val widthFraction = ((if (fitWidth) 1.0f else 0.85f) * zoomScale.coerceIn(0.30f, 1.0f)).coerceIn(0.20f, 1.0f)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(widthFraction),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(pages, key = { _, page -> "${page.index}_${page.imageUrl}" }) { _, page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                RemoteCoverImage(
                                    url = page.imageUrl,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentHeight()
                                )
                            }
                        }
                    }
                }

                ReaderModePreference.SINGLE -> {
                    val currentPage = pages.getOrNull(singlePageIndex)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (currentPage != null) {
                            RemoteCoverImage(
                                url = currentPage.imageUrl,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxHeight(zoomScale.coerceIn(0.35f, 1.0f))
                                    .wrapContentWidth()
                            )
                        }
                    }
                }

                ReaderModePreference.DOUBLE -> {
                    val idx1 = if (readingDirection == ReaderDirection.LEFT_TO_RIGHT) singlePageIndex else singlePageIndex + 1
                    val idx2 = if (readingDirection == ReaderDirection.LEFT_TO_RIGHT) singlePageIndex + 1 else singlePageIndex

                    val page1 = pages.getOrNull(idx1)
                    val page2 = pages.getOrNull(idx2)

                    Row(
                        Modifier
                            .fillMaxHeight(zoomScale.coerceIn(0.35f, 1.0f))
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (page1 != null) {
                            RemoteCoverImage(
                                url = page1.imageUrl,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (page2 != null) {
                            RemoteCoverImage(
                                url = page2.imageUrl,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        // Floating Top Header
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = tokens.surfaceContainerHigh.copy(alpha = 0.94f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onClose,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = tokens.surfaceContainer,
                            contentColor = tokens.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            mangaTitle,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            chapterTitle,
                            color = tokens.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    // Mode toggles
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModernReaderModeButton(
                            label = "Strip",
                            icon = Icons.Rounded.ViewDay,
                            selected = mode == ReaderModePreference.VERTICAL,
                            onClick = { mode = ReaderModePreference.VERTICAL }
                        )
                        ModernReaderModeButton(
                            label = "Single",
                            icon = Icons.Rounded.CropPortrait,
                            selected = mode == ReaderModePreference.SINGLE,
                            onClick = { mode = ReaderModePreference.SINGLE }
                        )
                        ModernReaderModeButton(
                            label = "Double",
                            icon = Icons.Rounded.AutoStories,
                            selected = mode == ReaderModePreference.DOUBLE,
                            onClick = { mode = ReaderModePreference.DOUBLE }
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    // Zoom Controls Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(tokens.surfaceContainer, RoundedCornerShape(10.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.4f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp), tint = tokens.textPrimary)
                        }
                        Text(
                            text = "${(zoomScale * 100).toInt()}%",
                            color = tokens.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { zoomScale = 1.0f }
                                .padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(3.0f) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Zoom In", modifier = Modifier.size(16.dp), tint = tokens.textPrimary)
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    IconButton(
                        onClick = { showSettingsDialog = !showSettingsDialog },
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (showSettingsDialog) tokens.primaryContainer else tokens.surfaceContainer, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = "Reader Settings",
                            tint = if (showSettingsDialog) tokens.primary else tokens.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Floating Bottom Page Slider
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = tokens.surfaceContainerHigh.copy(alpha = 0.94f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                val currentPageNumber = if (mode == ReaderModePreference.VERTICAL) {
                    listState.firstVisibleItemIndex + 1
                } else singlePageIndex + 1

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Page $currentPageNumber of ${pages.size}",
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Press TAB for Reader Settings",
                            color = tokens.textMuted,
                            fontSize = 11.sp
                        )
                        Surface(
                            color = tokens.surfaceContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                "TAB",
                                color = tokens.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Kotatsu-Style Reader Settings Modal (Triggered by TAB or Tune button)
        AnimatedVisibility(
            visible = showSettingsDialog,
            enter = fadeIn() + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
        ) {
            KotatsuReaderSettingsCard(
                mode = mode,
                onModeChange = { mode = it },
                direction = readingDirection,
                onDirectionChange = { readingDirection = it },
                bgColor = readerBg,
                onBgColorChange = { readerBg = it },
                fitWidth = fitWidth,
                onFitWidthChange = { fitWidth = it },
                onClose = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun KotatsuReaderSettingsCard(
    mode: ReaderModePreference,
    onModeChange: (ReaderModePreference) -> Unit,
    direction: ReaderDirection,
    onDirectionChange: (ReaderDirection) -> Unit,
    bgColor: ReaderBgColor,
    onBgColorChange: (ReaderBgColor) -> Unit,
    fitWidth: Boolean,
    onFitWidthChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalGlassTokens.current

    Surface(
        color = tokens.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 16.dp,
        modifier = Modifier
            .width(360.dp)
            .border(1.dp, tokens.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = tokens.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "READER SETTINGS",
                        color = tokens.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Reading Layout
            Text(
                "READING MODE",
                color = tokens.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KotatsuSettingChip(
                    label = "Webtoon",
                    icon = Icons.Rounded.ViewDay,
                    selected = mode == ReaderModePreference.VERTICAL,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(ReaderModePreference.VERTICAL) }
                )
                KotatsuSettingChip(
                    label = "Single",
                    icon = Icons.Rounded.CropPortrait,
                    selected = mode == ReaderModePreference.SINGLE,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(ReaderModePreference.SINGLE) }
                )
                KotatsuSettingChip(
                    label = "Double",
                    icon = Icons.Rounded.AutoStories,
                    selected = mode == ReaderModePreference.DOUBLE,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(ReaderModePreference.DOUBLE) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Direction
            Text(
                "DIRECTION",
                color = tokens.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KotatsuSettingChip(
                    label = "Left to Right",
                    icon = Icons.Rounded.ArrowForward,
                    selected = direction == ReaderDirection.LEFT_TO_RIGHT,
                    modifier = Modifier.weight(1f),
                    onClick = { onDirectionChange(ReaderDirection.LEFT_TO_RIGHT) }
                )
                KotatsuSettingChip(
                    label = "Right to Left",
                    icon = Icons.Rounded.ArrowBack,
                    selected = direction == ReaderDirection.RIGHT_TO_LEFT,
                    modifier = Modifier.weight(1f),
                    onClick = { onDirectionChange(ReaderDirection.RIGHT_TO_LEFT) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Background Color
            Text(
                "BACKGROUND COLOR",
                color = tokens.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReaderBgColor.entries.forEach { bgOption ->
                    val isSelected = bgColor == bgOption
                    Surface(
                        color = bgOption.color,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) tokens.primary else Color.Gray.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onBgColorChange(bgOption) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = bgOption.label,
                                color = bgOption.textColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Fit width toggle (for webtoon mode)
            if (mode == ReaderModePreference.VERTICAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Fit to Window Width", color = tokens.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("Stretches webtoon strip horizontally", color = tokens.textMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = fitWidth,
                        onCheckedChange = onFitWidthChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = tokens.primary,
                            checkedTrackColor = tokens.primaryContainer
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // Keyboard hints
            Surface(
                color = tokens.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "KEYBOARD SHORTCUTS",
                        color = tokens.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("• Space / J / Down : Next page / scroll", color = tokens.textPrimary, fontSize = 11.sp)
                    Text("• K / Up : Previous page / scroll", color = tokens.textPrimary, fontSize = 11.sp)
                    Text("• TAB : Toggle this settings HUD", color = tokens.textPrimary, fontSize = 11.sp)
                    Text("• ESC : Close reader", color = tokens.textPrimary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ModernReaderModeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Surface(
        color = if (selected) tokens.primary else tokens.surfaceContainer,
        contentColor = if (selected) Color.Black else tokens.textPrimary,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun KotatsuSettingChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = LocalGlassTokens.current
    Surface(
        color = if (selected) tokens.primaryContainer else tokens.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) tokens.primary else tokens.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) tokens.primary else tokens.textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = if (selected) tokens.primary else tokens.textPrimary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
