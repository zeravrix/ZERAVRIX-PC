package com.zeravrix.zeravrix.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.ReaderModePreference
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.screens.RemoteCoverImage
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Track current page for progress reporting
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (mode == ReaderModePreference.VERTICAL) {
            onPageChanged(listState.firstVisibleItemIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (tokens.isAmoled) Color.Black else Color(0xFF06080E))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Escape -> {
                            onClose()
                            true
                        }
                        Key.Spacebar, Key.DirectionDown, Key.J -> {
                            if (mode == ReaderModePreference.VERTICAL) {
                                scope.launch {
                                    val current = listState.firstVisibleItemIndex
                                    listState.animateScrollToItem((current + 1).coerceAtMost(pages.lastIndex))
                                }
                            } else {
                                if (singlePageIndex < pages.lastIndex) {
                                    singlePageIndex++
                                    onPageChanged(singlePageIndex)
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
                                if (singlePageIndex > 0) {
                                    singlePageIndex--
                                    onPageChanged(singlePageIndex)
                                }
                            }
                            true
                        }
                        Key.DirectionRight, Key.D -> {
                            if (singlePageIndex < pages.lastIndex) {
                                singlePageIndex++
                                onPageChanged(singlePageIndex)
                            }
                            true
                        }
                        Key.DirectionLeft, Key.A -> {
                            if (singlePageIndex > 0) {
                                singlePageIndex--
                                onPageChanged(singlePageIndex)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable { showControls = !showControls }
    ) {
        // Reader Content Area
        when (mode) {
            ReaderModePreference.VERTICAL, ReaderModePreference.AUTO -> {
                // Continuous Seamless Webtoon Strip
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(pages, key = { index, page -> "${page.index}_${page.imageUrl}" }) { index, page ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            RemoteCoverImage(
                                url = page.imageUrl,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                        }
                    }
                }
            }
            ReaderModePreference.SINGLE -> {
                // Single Page Mode
                val currentPage = pages.getOrNull(singlePageIndex)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (currentPage != null) {
                        RemoteCoverImage(
                            url = currentPage.imageUrl,
                            modifier = Modifier.fillMaxHeight().wrapContentWidth()
                        )
                    }
                }
            }
            ReaderModePreference.DOUBLE -> {
                // Double Page Spread Mode
                val page1 = pages.getOrNull(singlePageIndex)
                val page2 = pages.getOrNull(singlePageIndex + 1)
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page1 != null) {
                        RemoteCoverImage(
                            url = page1.imageUrl,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    if (page2 != null) {
                        RemoteCoverImage(
                            url = page2.imageUrl,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }

        // Floating Glass Top Header (Auto-hide on toggle)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = tokens.surfaceGlass,
                shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = tokens.surfaceCard,
                        contentColor = tokens.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClose() }
                    ) {
                        Text(
                            "← Back",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.width(14.dp))

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
                        ReaderModeButton(
                            label = "↕ Strip",
                            selected = mode == ReaderModePreference.VERTICAL,
                            onClick = { mode = ReaderModePreference.VERTICAL }
                        )
                        ReaderModeButton(
                            label = "📄 Single",
                            selected = mode == ReaderModePreference.SINGLE,
                            onClick = { mode = ReaderModePreference.SINGLE }
                        )
                        ReaderModeButton(
                            label = "📖 Double",
                            selected = mode == ReaderModePreference.DOUBLE,
                            onClick = { mode = ReaderModePreference.DOUBLE }
                        )
                    }
                }
            }
        }

        // Floating Glass Bottom Page Slider
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = tokens.surfaceGlass,
                shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                val currentPageNumber = if (mode == ReaderModePreference.VERTICAL) {
                    listState.firstVisibleItemIndex + 1
                } else singlePageIndex + 1

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Page $currentPageNumber / ${pages.size}",
                        color = tokens.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Surface(
        color = if (selected) tokens.primary else tokens.surfaceCard,
        contentColor = if (selected) Color.Black else tokens.textPrimary,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
