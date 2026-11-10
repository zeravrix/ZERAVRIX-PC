package com.zeravrix.zeravrix.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.ReaderModePreference
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.screens.RemoteCoverImage
import com.zeravrix.zeravrix.ui.screens.preloadPageImage
import com.zeravrix.zeravrix.ui.screens.pruneMemoryExcept
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReaderBgColor(val label: String, val color: Color, val textColor: Color) {
    AMOLED("AMOLED", Color(0xFF000000), Color(0xFFEEEEEE)),
    DARK("Dark", Color(0xFF12141A), Color(0xFFEEEEEE)),
    SEPIA("Sepia", Color(0xFF231C16), Color(0xFFE8D8C8)),
    LIGHT("Light", Color(0xFFF8F9FA), Color(0xFF1A1A1A))
}

data class LoadedChapter(
    val chapter: Chapter,
    val pages: List<MangaPage>
)

sealed interface ContinuousReaderItem {
    data class ChapterDivider(val chapter: Chapter, val chapterIndex: Int, val totalChapters: Int) : ContinuousReaderItem
    data class Page(val chapter: Chapter, val page: MangaPage, val pageIndex: Int, val totalPages: Int) : ContinuousReaderItem
    data class EndOfManga(val mangaTitle: String) : ContinuousReaderItem
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopReaderView(
    mangaTitle: String,
    initialChapter: Chapter,
    allChapters: List<Chapter> = emptyList(),
    initialPages: List<MangaPage>,
    initialPage: Int = 0,
    initialMode: ReaderModePreference = ReaderModePreference.VERTICAL,
    onFetchPages: suspend (Chapter) -> List<MangaPage> = { emptyList() },
    onClose: () -> Unit,
    onToggleFullscreen: () -> Unit = {},
    onPageChanged: (chapter: Chapter, pageIndex: Int) -> Unit = { _, _ -> },
) {
    val tokens = LocalGlassTokens.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(initialMode) }
    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var readerBg by remember { mutableStateOf(if (tokens.isAmoled) ReaderBgColor.AMOLED else ReaderBgColor.DARK) }
    var fitWidth by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    // Image Adjustment Settings (hardware-accelerated Skia ColorFilter)
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }
    var brightness by remember { mutableFloatStateOf(1.0f) }
    var invertColors by remember { mutableStateOf(false) }

    val imageColorFilter = remember(contrast, saturation, brightness, invertColors) {
        if (contrast == 1f && saturation == 1f && brightness == 1f && !invertColors) {
            null
        } else {
            val cm = ColorMatrix()
            cm.setToSaturation(saturation)
            if (contrast != 1f || brightness != 1f) {
                val scale = contrast * brightness
                val offset = (1f - contrast) * 128f * 0.5f
                val cm2 = ColorMatrix(floatArrayOf(
                    scale, 0f, 0f, 0f, offset,
                    0f, scale, 0f, 0f, offset,
                    0f, 0f, scale, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.timesAssign(cm2)
            }
            if (invertColors) {
                val inv = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.timesAssign(inv)
            }
            ColorFilter.colorMatrix(cm)
        }
    }

    // Multi-chapter continuous loading state
    val loadedChapters = remember { mutableStateListOf(LoadedChapter(initialChapter, initialPages)) }
    var isLoadingNextChapter by remember { mutableStateOf(false) }

    var currentActiveChapter by remember { mutableStateOf(initialChapter) }
    var currentPageInChapter by remember { mutableIntStateOf(initialPage) }
    var totalPagesInCurrentChapter by remember { mutableIntStateOf(initialPages.size) }

    // Single Page / Double Page state
    var currentSingleChapterIndex by remember {
        mutableIntStateOf(allChapters.indexOfFirst { it.id == initialChapter.id }.coerceAtLeast(0))
    }
    var currentSinglePages by remember { mutableStateOf(initialPages) }
    var singlePageIndex by remember { mutableIntStateOf(initialPage) }

    val feedItems by remember {
        derivedStateOf {
            val items = mutableListOf<ContinuousReaderItem>()
            val totalChapters = allChapters.size.coerceAtLeast(1)
            loadedChapters.forEachIndexed { chapIdx, loaded ->
                if (chapIdx > 0) {
                    val realIdx = allChapters.indexOfFirst { it.id == loaded.chapter.id }
                    items.add(
                        ContinuousReaderItem.ChapterDivider(
                            chapter = loaded.chapter,
                            chapterIndex = (if (realIdx >= 0) realIdx else chapIdx) + 1,
                            totalChapters = totalChapters
                        )
                    )
                }
                loaded.pages.forEachIndexed { pIdx, p ->
                    items.add(ContinuousReaderItem.Page(loaded.chapter, p, pIdx, loaded.pages.size))
                }
            }
            val lastLoaded = loadedChapters.lastOrNull()?.chapter
            val lastIndex = if (lastLoaded != null) allChapters.indexOfFirst { it.id == lastLoaded.id } else -1
            if (allChapters.isNotEmpty() && lastIndex != -1 && lastIndex >= allChapters.size - 1) {
                items.add(ContinuousReaderItem.EndOfManga(mangaTitle))
            }
            items
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Intelligent Preload & Memory Window for Continuous Vertical (Webtoon) mode:
    // Keeps exactly 3 pages behind and preloads 5 pages ahead. When near chapter end, preloads next 5 pages in next chapter.
    // Automatically releases all memory for pages outside the window.
    LaunchedEffect(listState.firstVisibleItemIndex, loadedChapters.size, mode) {
        if (mode == ReaderModePreference.VERTICAL || mode == ReaderModePreference.AUTO) {
            val total = feedItems.size
            if (total == 0) return@LaunchedEffect
            val curIdx = listState.firstVisibleItemIndex
            val fromIdx = (curIdx - 3).coerceAtLeast(0)
            val toIdx = (curIdx + 5).coerceAtMost(total - 1)

            val activeUrls = mutableSetOf<String>()
            for (i in fromIdx..toIdx) {
                val item = feedItems.getOrNull(i)
                if (item is ContinuousReaderItem.Page) {
                    activeUrls.add(item.page.imageUrl)
                }
            }

            // Preload 5 pages ahead
            for (i in curIdx..toIdx) {
                val item = feedItems.getOrNull(i)
                if (item is ContinuousReaderItem.Page) {
                    scope.launch { preloadPageImage(item.page.imageUrl) }
                }
            }

            // Check if near the end of the chapter (<= 5 pages left)
            if (currentPageInChapter >= totalPagesInCurrentChapter - 5 && allChapters.isNotEmpty()) {
                val lastLoaded = loadedChapters.lastOrNull()?.chapter
                if (lastLoaded != null) {
                    val curChapIdx = allChapters.indexOfFirst { it.id == lastLoaded.id }
                    if (curChapIdx != -1 && curChapIdx + 1 < allChapters.size && !isLoadingNextChapter) {
                        val nextChapter = allChapters[curChapIdx + 1]
                        isLoadingNextChapter = true
                        scope.launch {
                            val nextPages = withContext(Dispatchers.IO) { onFetchPages(nextChapter) }
                            if (nextPages.isNotEmpty()) {
                                loadedChapters.add(LoadedChapter(nextChapter, nextPages))
                                // Preload first 5 pages of the next chapter
                                nextPages.take(5).forEach { p ->
                                    preloadPageImage(p.imageUrl)
                                }
                            }
                            isLoadingNextChapter = false
                        }
                    }
                }
            }

            // Strictly prune bitmaps in RAM outside [curIdx - 3 .. curIdx + 5]
            if (activeUrls.isNotEmpty()) {
                pruneMemoryExcept(activeUrls)
            }
        }
    }

    // Active Chapter & Reading Progress Tracking for Continuous Mode
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (mode == ReaderModePreference.VERTICAL || mode == ReaderModePreference.AUTO) {
            val item = feedItems.getOrNull(listState.firstVisibleItemIndex)
            if (item is ContinuousReaderItem.Page) {
                currentActiveChapter = item.chapter
                currentPageInChapter = item.pageIndex
                totalPagesInCurrentChapter = item.totalPages
                onPageChanged(item.chapter, item.pageIndex)
            }
        }
    }

    // Intelligent Preload & Memory Window for Single/Double Page Mode
    LaunchedEffect(singlePageIndex, currentSinglePages, mode) {
        if (mode == ReaderModePreference.SINGLE || mode == ReaderModePreference.DOUBLE) {
            val cur = singlePageIndex
            val from = (cur - 3).coerceAtLeast(0)
            val to = (cur + 5).coerceAtMost(currentSinglePages.lastIndex)
            val activeUrls = mutableSetOf<String>()
            for (i in from..to) {
                val p = currentSinglePages.getOrNull(i)
                if (p != null) activeUrls.add(p.imageUrl)
            }

            // Preload 5 pages ahead
            for (i in cur..to) {
                val p = currentSinglePages.getOrNull(i)
                if (p != null) {
                    scope.launch { preloadPageImage(p.imageUrl) }
                }
            }

            // Near end of chapter preload next chapter
            if (cur >= currentSinglePages.size - 5 && allChapters.isNotEmpty() && currentSingleChapterIndex + 1 < allChapters.size) {
                val nextChap = allChapters[currentSingleChapterIndex + 1]
                scope.launch {
                    val nextPages = withContext(Dispatchers.IO) { onFetchPages(nextChap) }
                    nextPages.take(5).forEach { preloadPageImage(it.imageUrl) }
                }
            }

            if (activeUrls.isNotEmpty()) {
                pruneMemoryExcept(activeUrls)
            }
        }
    }

    fun loadNextChapterForSingle() {
        if (allChapters.isNotEmpty() && currentSingleChapterIndex + 1 < allChapters.size && !isLoadingNextChapter) {
            val nextChap = allChapters[currentSingleChapterIndex + 1]
            isLoadingNextChapter = true
            scope.launch {
                val pages = withContext(Dispatchers.IO) { onFetchPages(nextChap) }
                if (pages.isNotEmpty()) {
                    currentSingleChapterIndex++
                    currentSinglePages = pages
                    singlePageIndex = 0
                    currentActiveChapter = nextChap
                    currentPageInChapter = 0
                    totalPagesInCurrentChapter = pages.size
                    onPageChanged(nextChap, 0)
                }
                isLoadingNextChapter = false
            }
        }
    }

    fun loadPrevChapterForSingle() {
        if (currentSingleChapterIndex > 0 && !isLoadingNextChapter) {
            val prevChap = allChapters[currentSingleChapterIndex - 1]
            isLoadingNextChapter = true
            scope.launch {
                val pages = withContext(Dispatchers.IO) { onFetchPages(prevChap) }
                if (pages.isNotEmpty()) {
                    currentSingleChapterIndex--
                    currentSinglePages = pages
                    singlePageIndex = pages.lastIndex
                    currentActiveChapter = prevChap
                    currentPageInChapter = pages.lastIndex
                    totalPagesInCurrentChapter = pages.size
                    onPageChanged(prevChap, pages.lastIndex)
                }
                isLoadingNextChapter = false
            }
        }
    }

    fun advancePage() {
        if (mode == ReaderModePreference.DOUBLE) {
            if (singlePageIndex + 2 <= currentSinglePages.lastIndex) {
                singlePageIndex += 2
                currentPageInChapter = singlePageIndex
                onPageChanged(currentActiveChapter, singlePageIndex)
            } else if (singlePageIndex < currentSinglePages.lastIndex) {
                singlePageIndex = currentSinglePages.lastIndex
                currentPageInChapter = singlePageIndex
                onPageChanged(currentActiveChapter, singlePageIndex)
            } else {
                loadNextChapterForSingle()
            }
        } else {
            if (singlePageIndex < currentSinglePages.lastIndex) {
                singlePageIndex++
                currentPageInChapter = singlePageIndex
                onPageChanged(currentActiveChapter, singlePageIndex)
            } else {
                loadNextChapterForSingle()
            }
        }
    }

    fun previousPage() {
        if (mode == ReaderModePreference.DOUBLE) {
            if (singlePageIndex >= 2) {
                singlePageIndex -= 2
                currentPageInChapter = singlePageIndex
                onPageChanged(currentActiveChapter, singlePageIndex)
            } else if (singlePageIndex > 0) {
                singlePageIndex = 0
                currentPageInChapter = 0
                onPageChanged(currentActiveChapter, 0)
            } else {
                loadPrevChapterForSingle()
            }
        } else {
            if (singlePageIndex > 0) {
                singlePageIndex--
                currentPageInChapter = singlePageIndex
                onPageChanged(currentActiveChapter, singlePageIndex)
            } else {
                loadPrevChapterForSingle()
            }
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
                        Key.F11 -> {
                            onToggleFullscreen()
                            true
                        }
                        Key.Tab -> {
                            showSettingsDialog = !showSettingsDialog
                            true
                        }
                        Key.Spacebar, Key.DirectionDown, Key.J -> {
                            if (mode == ReaderModePreference.VERTICAL || mode == ReaderModePreference.AUTO) {
                                scope.launch {
                                    val current = listState.firstVisibleItemIndex
                                    listState.animateScrollToItem((current + 1).coerceAtMost(feedItems.lastIndex.coerceAtLeast(0)))
                                }
                            } else {
                                advancePage()
                            }
                            true
                        }
                        Key.DirectionUp, Key.K -> {
                            if (mode == ReaderModePreference.VERTICAL || mode == ReaderModePreference.AUTO) {
                                scope.launch {
                                    val current = listState.firstVisibleItemIndex
                                    listState.animateScrollToItem((current - 1).coerceAtLeast(0))
                                }
                            } else {
                                previousPage()
                            }
                            true
                        }
                        Key.DirectionRight, Key.D -> {
                            advancePage()
                            true
                        }
                        Key.DirectionLeft, Key.A -> {
                            previousPage()
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
                } else {
                    // Smooth wheel scrolling across the entire reader canvas, including gaps and letterboxes
                    val delta = change.scrollDelta.y
                    if (mode == ReaderModePreference.VERTICAL || mode == ReaderModePreference.AUTO) {
                        scope.launch {
                            listState.scrollBy(delta * 120f)
                        }
                    } else {
                        if (delta > 0) advancePage() else if (delta < 0) previousPage()
                    }
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
        // Reader Content Area with Native Width Scaling
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (mode) {
                ReaderModePreference.VERTICAL, ReaderModePreference.AUTO -> {
                    val widthFraction = ((if (fitWidth) 1.0f else 0.85f) * zoomScale.coerceIn(0.30f, 1.0f)).coerceIn(0.20f, 1.0f)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(
                            items = feedItems,
                            key = { _, item ->
                                when (item) {
                                    is ContinuousReaderItem.ChapterDivider -> "ch_divider_${item.chapter.id}"
                                    is ContinuousReaderItem.Page -> "p_${item.chapter.id}_${item.page.index}_${item.page.imageUrl}"
                                    is ContinuousReaderItem.EndOfManga -> "end_of_manga"
                                }
                            }
                        ) { _, item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(widthFraction)
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                when (item) {
                                    is ContinuousReaderItem.ChapterDivider -> {
                                        Surface(
                                            color = tokens.surfaceContainerHigh.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, tokens.primary.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp, vertical = 28.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    "CHAPTER ${item.chapterIndex} OF ${item.totalChapters}",
                                                    color = tokens.primary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    letterSpacing = 1.2.sp
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    item.chapter.title,
                                                    color = tokens.textPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    is ContinuousReaderItem.Page -> {
                                        RemoteCoverImage(
                                            url = item.page.imageUrl,
                                            contentScale = ContentScale.FillWidth,
                                            colorFilter = imageColorFilter,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight()
                                        )
                                    }
                                    is ContinuousReaderItem.EndOfManga -> {
                                        Surface(
                                            color = tokens.surfaceContainerHigh.copy(alpha = 0.9f),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, tokens.borderGlass),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 32.dp, vertical = 40.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = tokens.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                                Spacer(Modifier.height(10.dp))
                                                Text(
                                                    "You're all caught up!",
                                                    color = tokens.textPrimary,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    "You've reached the latest page of the latest chapter.",
                                                    color = tokens.textMuted,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(Modifier.height(16.dp))
                                                FilledTonalButton(
                                                    onClick = onClose,
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text("Close Reader", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isLoadingNextChapter) {
                            item(key = "loading_indicator") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = tokens.primary,
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                    }
                }

                ReaderModePreference.SINGLE -> {
                    val currentPage = currentSinglePages.getOrNull(singlePageIndex)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (currentPage != null) {
                            RemoteCoverImage(
                                url = currentPage.imageUrl,
                                contentScale = ContentScale.Fit,
                                colorFilter = imageColorFilter,
                                modifier = Modifier
                                    .fillMaxHeight(zoomScale.coerceIn(0.5f, 1.0f))
                                    .wrapContentWidth()
                            )
                        }
                    }
                }

                ReaderModePreference.DOUBLE -> {
                    val idx1 = singlePageIndex
                    val idx2 = singlePageIndex + 1

                    val page1 = currentSinglePages.getOrNull(idx1)
                    val page2 = currentSinglePages.getOrNull(idx2)

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight(zoomScale.coerceIn(0.5f, 1.0f))
                                .wrapContentWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (page1 != null) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    RemoteCoverImage(
                                        url = page1.imageUrl,
                                        contentScale = ContentScale.Fit,
                                        colorFilter = imageColorFilter,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .wrapContentWidth()
                                    )
                                }
                            }
                            if (page2 != null) {
                                Spacer(Modifier.width(8.dp)) // Book spine / center gutter
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    RemoteCoverImage(
                                        url = page2.imageUrl,
                                        contentScale = ContentScale.Fit,
                                        colorFilter = imageColorFilter,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .wrapContentWidth()
                                    )
                                }
                            }
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            currentActiveChapter.title,
                            color = tokens.primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Progress indicator
                    Surface(
                        color = tokens.surfaceContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        val progressText = if (mode == ReaderModePreference.DOUBLE && currentPageInChapter + 1 < totalPagesInCurrentChapter) {
                            "Pages ${currentPageInChapter + 1}-${currentPageInChapter + 2} / $totalPagesInCurrentChapter"
                        } else {
                            "Page ${currentPageInChapter + 1} / $totalPagesInCurrentChapter"
                        }
                        Text(
                            progressText,
                            color = tokens.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // Fullscreen Button
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Fullscreen,
                            contentDescription = "Toggle Fullscreen (F11)",
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    // Settings Dialog Toggle
                    FilledTonalButton(
                        onClick = { showSettingsDialog = !showSettingsDialog },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (showSettingsDialog) tokens.primaryContainer else tokens.surfaceContainer,
                            contentColor = if (showSettingsDialog) tokens.primary else tokens.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Floating Bottom Controls for Single/Double Page Mode
        if (mode != ReaderModePreference.VERTICAL && mode != ReaderModePreference.AUTO) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    color = tokens.surfaceContainerHigh.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, tokens.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { previousPage() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Prev", fontSize = 12.sp)
                        }

                        val curPageLabel = if (mode == ReaderModePreference.DOUBLE && singlePageIndex + 1 < currentSinglePages.size) {
                            "${singlePageIndex + 1}-${singlePageIndex + 2} / ${currentSinglePages.size}"
                        } else {
                            "${singlePageIndex + 1} / ${currentSinglePages.size}"
                        }
                        Text(
                            curPageLabel,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )

                        FilledTonalButton(
                            onClick = { advancePage() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Next", fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Settings Dialog Card
        AnimatedVisibility(
            visible = showSettingsDialog,
            enter = fadeIn() + scaleIn(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 70.dp, end = 24.dp)
        ) {
            KotatsuReaderSettingsCard(
                mode = mode,
                onModeChange = { mode = it },
                bgColor = readerBg,
                onBgColorChange = { readerBg = it },
                fitWidth = fitWidth,
                onFitWidthChange = { fitWidth = it },
                contrast = contrast,
                onContrastChange = { contrast = it },
                saturation = saturation,
                onSaturationChange = { saturation = it },
                brightness = brightness,
                onBrightnessChange = { brightness = it },
                invertColors = invertColors,
                onInvertColorsChange = { invertColors = it },
                onResetAdjustments = {
                    contrast = 1.0f
                    saturation = 1.0f
                    brightness = 1.0f
                    invertColors = false
                },
                onClose = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun KotatsuReaderSettingsCard(
    mode: ReaderModePreference,
    onModeChange: (ReaderModePreference) -> Unit,
    bgColor: ReaderBgColor,
    onBgColorChange: (ReaderBgColor) -> Unit,
    fitWidth: Boolean,
    onFitWidthChange: (Boolean) -> Unit,
    contrast: Float,
    onContrastChange: (Float) -> Unit,
    saturation: Float,
    onSaturationChange: (Float) -> Unit,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    invertColors: Boolean,
    onInvertColorsChange: (Boolean) -> Unit,
    onResetAdjustments: () -> Unit,
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
        Column(modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState())) {
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

            // Image Adjustments Section
            Text(
                "IMAGE ADJUSTMENTS",
                color = tokens.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))

            // Contrast Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Contrast", color = tokens.textPrimary, fontSize = 12.sp)
                Text("${(contrast * 100).toInt()}%", color = tokens.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(thumbColor = tokens.primary, activeTrackColor = tokens.primary)
            )

            // Saturation Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saturation", color = tokens.textPrimary, fontSize = 12.sp)
                Text("${(saturation * 100).toInt()}%", color = tokens.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(thumbColor = tokens.primary, activeTrackColor = tokens.primary)
            )

            // Brightness Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Brightness", color = tokens.textPrimary, fontSize = 12.sp)
                Text("${(brightness * 100).toInt()}%", color = tokens.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.5f..1.5f,
                colors = SliderDefaults.colors(thumbColor = tokens.primary, activeTrackColor = tokens.primary)
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onInvertColorsChange(!invertColors) }
                        .padding(vertical = 4.dp)
                ) {
                    Switch(
                        checked = invertColors,
                        onCheckedChange = onInvertColorsChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = tokens.primary, checkedTrackColor = tokens.primaryContainer)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Invert Colors", color = tokens.textPrimary, fontSize = 11.sp)
                }

                TextButton(
                    onClick = onResetAdjustments,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Reset", color = tokens.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
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
            if (mode == ReaderModePreference.VERTICAL || mode == ReaderModePreference.AUTO) {
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
                    Text("• F11 : Toggle Fullscreen", color = tokens.textPrimary, fontSize = 11.sp)
                    Text("• TAB : Toggle this settings HUD", color = tokens.textPrimary, fontSize = 11.sp)
                    Text("• ESC : Close reader", color = tokens.textPrimary, fontSize = 11.sp)
                }
            }
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
