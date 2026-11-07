package com.zeravrix.zeravrix.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.MangaPage as CoreMangaPage
import com.zeravrix.zeravrix.data.DesktopMangaLoaderContext
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage

enum class PcReaderMode(val label: String) {
    WEBTOON("Webtoon Strip"),
    DOUBLE_PAGE("Double Spread"),
    SINGLE_PAGE("Single Page")
}

/**
 * PC Desktop Comic & Webtoon Reader
 * Features:
 * - Ultra-smooth continuous vertical webtoon engine
 * - Keyboard navigation (WASD, Arrows, Space, M, Esc, + / -)
 * - Mouse wheel scrolling & Ctrl+Wheel zooming
 * - Double-page spread for traditional manga
 * - Floating cyber-glassmorphic status controls
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PcReaderScreen(
    loaderContext: DesktopMangaLoaderContext,
    manga: Manga,
    initialChapter: MangaChapter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    var currentChapter by remember { mutableStateOf(initialChapter) }
    var pages by remember { mutableStateOf<List<MangaPage>>(emptyList()) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var readerMode by remember { mutableStateOf(PcReaderMode.WEBTOON) }
    var showOverlay by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableStateOf(1f) }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Load pages for the active chapter
    LaunchedEffect(currentChapter.id) {
        isLoading = true
        currentPageIndex = 0
        runCatching {
            val parser = loaderContext.newParserInstance(manga.source)
            parser.getPages(currentChapter)
        }.onSuccess { loaded ->
            pages = loaded
        }
        isLoading = false
    }

    val step = if (readerMode == PcReaderMode.DOUBLE_PAGE) 2 else 1

    fun movePage(deltaSteps: Int) {
        if (pages.isEmpty()) return
        val next = (currentPageIndex + deltaSteps * step).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        currentPageIndex = next
        if (readerMode == PcReaderMode.WEBTOON) {
            scope.launch {
                listState.animateScrollToItem(next)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A10))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.D, Key.PageDown, Key.Spacebar -> {
                        movePage(1); true
                    }
                    Key.DirectionLeft, Key.A, Key.PageUp -> {
                        movePage(-1); true
                    }
                    Key.Escape -> {
                        onBack(); true
                    }
                    Key.M, Key.Enter -> {
                        showOverlay = !showOverlay; true
                    }
                    Key.Equals, Key.NumPadAdd -> {
                        zoomScale = (zoomScale * 1.15f).coerceIn(0.5f, 4f); true
                    }
                    Key.Minus, Key.NumPadSubtract -> {
                        zoomScale = (zoomScale * 0.87f).coerceIn(0.5f, 4f); true
                    }
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { pointerEvent ->
                val delta = pointerEvent.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (pointerEvent.keyboardModifiers.isCtrlPressed) {
                    zoomScale = if (delta < 0) {
                        (zoomScale * 1.12f).coerceAtMost(4f)
                    } else {
                        (zoomScale * 0.89f).coerceAtLeast(0.5f)
                    }
                } else if (readerMode != PcReaderMode.WEBTOON) {
                    if (delta > 0) movePage(1) else if (delta < 0) movePage(-1)
                }
            }
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tokens.accentGlow)
            }
        } else if (pages.isNotEmpty()) {
            when (readerMode) {
                PcReaderMode.WEBTOON -> {
                    // Seamless Webtoon continuous strip
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(pages, key = { _, p -> p.id }) { idx, page ->
                            LaunchedEffect(idx) { currentPageIndex = idx }
                            ReaderPageView(
                                page = page,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .widthIn(max = 940.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                }

                PcReaderMode.SINGLE_PAGE -> {
                    // Centered Single Page
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ReaderPageView(
                            page = pages[currentPageIndex],
                            loaderContext = loaderContext,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                        )
                    }
                }

                PcReaderMode.DOUBLE_PAGE -> {
                    // Double-Page Landscape Spread (Right to Left for traditional manga)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val page1 = pages.getOrNull(currentPageIndex)
                        val page2 = pages.getOrNull(currentPageIndex + 1)

                        if (page1 != null) {
                            ReaderPageView(
                                page = page1,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (page2 != null) {
                            ReaderPageView(
                                page = page2,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        // Floating Cyber-Glass Overlays
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = tokens.surfaceCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = tokens.textPrimary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = currentChapter.title.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = tokens.accentGlow
                            )
                        }
                    }

                    // Mode switch buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PcReaderMode.entries.forEach { mode ->
                            val isSelected = readerMode == mode
                            Surface(
                                color = if (isSelected) tokens.accentGlow.copy(alpha = 0.2f) else tokens.surfaceGlass,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(1.dp, if (isSelected) tokens.accentGlow else tokens.borderGlass, RoundedCornerShape(8.dp))
                                    .clickable { readerMode = mode }
                            ) {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tokens.accentGlow else tokens.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar
        AnimatedVisibility(
            visible = showOverlay && pages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = tokens.surfaceCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconButton(onClick = { movePage(-1) }) {
                        Icon(Icons.Default.ChevronLeft, "Previous", tint = tokens.textPrimary)
                    }

                    Text(
                        text = "${currentPageIndex + 1} / ${pages.size}",
                        fontSize = 12.sp,
                        color = tokens.accentGlow,
                        fontWeight = FontWeight.SemiBold
                    )

                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { currentPageIndex = it.toInt() },
                        valueRange = 0f..(pages.size - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = tokens.accentGlow,
                            activeTrackColor = tokens.accentGlow
                        )
                    )

                    IconButton(onClick = { movePage(1) }) {
                        Icon(Icons.Default.ChevronRight, "Next", tint = tokens.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderPageView(
    page: MangaPage,
    loaderContext: DesktopMangaLoaderContext,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    var bitmap by remember(page.id) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(page.id) { mutableStateOf(true) }

    LaunchedEffect(page.id) {
        isLoading = true
        runCatching {
            val parser = loaderContext.newParserInstance(page.source)
            val realUrl = parser.getPageUrl(page)
            bitmap = loaderContext.loadImageBitmap(realUrl, parser = parser)
        }
        isLoading = false
    }

    Box(
        modifier = modifier.defaultMinSize(minHeight = 350.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isLoading) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tokens.accentGlow, strokeWidth = 2.dp)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PcReaderScreen(
    mangaTitle: String,
    chapterTitle: String,
    pages: List<CoreMangaPage>,
    initialPage: Int = 0,
    loaderContext: DesktopMangaLoaderContext,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    var currentPageIndex by remember { mutableStateOf(initialPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    var readerMode by remember { mutableStateOf(PcReaderMode.WEBTOON) }
    var zoomScale by remember { mutableStateOf(1f) }
    var showOverlay by remember { mutableStateOf(true) }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(currentPageIndex) {
        onPageChanged(currentPageIndex)
    }

    val step = if (readerMode == PcReaderMode.DOUBLE_PAGE) 2 else 1

    fun movePage(deltaSteps: Int) {
        if (pages.isEmpty()) return
        val next = (currentPageIndex + deltaSteps * step).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        currentPageIndex = next
        if (readerMode == PcReaderMode.WEBTOON) {
            scope.launch {
                listState.animateScrollToItem(next)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070A10))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionDown, Key.DirectionRight, Key.Spacebar, Key.D, Key.PageDown -> {
                        movePage(1)
                        true
                    }
                    Key.DirectionUp, Key.DirectionLeft, Key.A, Key.PageUp -> {
                        movePage(-1)
                        true
                    }
                    Key.Plus, Key.Equals -> {
                        zoomScale = (zoomScale + 0.15f).coerceAtMost(3.0f)
                        true
                    }
                    Key.Minus -> {
                        zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.6f)
                        true
                    }
                    Key.M -> {
                        val modes = PcReaderMode.entries
                        val nextModeIndex = (modes.indexOf(readerMode) + 1) % modes.size
                        readerMode = modes[nextModeIndex]
                        true
                    }
                    Key.Escape -> {
                        onBack()
                        true
                    }
                    else -> false
                }
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (event.keyboardModifiers.isCtrlPressed) {
                    if (delta < 0) {
                        zoomScale = (zoomScale + 0.1f).coerceAtMost(3.0f)
                    } else if (delta > 0) {
                        zoomScale = (zoomScale - 0.1f).coerceAtLeast(0.6f)
                    }
                }
            }
    ) {
        // Main reading content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    showOverlay = !showOverlay
                }
        ) {
            when (readerMode) {
                PcReaderMode.WEBTOON -> {
                    // Continuous Vertical Webtoon Strip
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(pages, key = { index, _ -> index }) { _, page ->
                            CoreReaderPageView(
                                url = page.imageUrl,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }
                }

                PcReaderMode.SINGLE_PAGE -> {
                    // Single-Page View
                    val currentPage = pages.getOrNull(currentPageIndex)
                    if (currentPage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                            contentAlignment = Alignment.Center
                        ) {
                            CoreReaderPageView(
                                url = currentPage.imageUrl,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                PcReaderMode.DOUBLE_PAGE -> {
                    // Double-Page Landscape Spread
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val page1 = pages.getOrNull(currentPageIndex)
                        val page2 = pages.getOrNull(currentPageIndex + 1)

                        if (page1 != null) {
                            CoreReaderPageView(
                                url = page1.imageUrl,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                        if (page2 != null) {
                            CoreReaderPageView(
                                url = page2.imageUrl,
                                loaderContext = loaderContext,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        // Floating Cyber-Glass Overlays
        AnimatedVisibility(
            visible = showOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = tokens.surfaceCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = tokens.textPrimary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = mangaTitle,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = tokens.textPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = tokens.accentGlow
                            )
                        }
                    }

                    // Mode switch buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PcReaderMode.entries.forEach { mode ->
                            val isSelected = readerMode == mode
                            Surface(
                                color = if (isSelected) tokens.accentGlow.copy(alpha = 0.2f) else tokens.surfaceGlass,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(1.dp, if (isSelected) tokens.accentGlow else tokens.borderGlass, RoundedCornerShape(8.dp))
                                    .clickable { readerMode = mode }
                            ) {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tokens.accentGlow else tokens.textMuted
                                )
                            }
                        }
                    }

                    // Zoom reset and info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = tokens.surfaceGlass,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, tokens.borderGlass, RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = "${(zoomScale * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted
                            )
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textPrimary)
                        }
                    }
                }
            }
        }

        // Bottom Scrubber Bar & Page Indicator
        AnimatedVisibility(
            visible = showOverlay && pages.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = tokens.surfaceCard.copy(alpha = 0.92f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = { movePage(-1) }) {
                        Icon(Icons.Default.ChevronLeft, "Prev", tint = tokens.textPrimary)
                    }

                    Text(
                        text = "${currentPageIndex + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.textPrimary,
                        modifier = Modifier.width(60.dp)
                    )

                    Slider(
                        value = currentPageIndex.toFloat(),
                        onValueChange = { currentPageIndex = it.toInt() },
                        valueRange = 0f..(pages.size - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = tokens.accentGlow,
                            activeTrackColor = tokens.accentGlow
                        )
                    )

                    IconButton(onClick = { movePage(1) }) {
                        Icon(Icons.Default.ChevronRight, "Next", tint = tokens.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun CoreReaderPageView(
    url: String,
    loaderContext: DesktopMangaLoaderContext,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        isLoading = true
        bitmap = loaderContext.loadImageBitmap(url)
        isLoading = false
    }

    Box(
        modifier = modifier.defaultMinSize(minHeight = 350.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (isLoading) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tokens.accentGlow, strokeWidth = 2.dp)
            }
        }
    }
}
