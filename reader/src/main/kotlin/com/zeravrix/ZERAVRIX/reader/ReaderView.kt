package com.zeravrix.zeravrix.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.MusicTrack
import com.zeravrix.zeravrix.core.MusicPlaylist
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow

@Composable
fun ReaderWindow(title: String, imagePaths: List<Path>, onClose: () -> Unit) {
    if (imagePaths.isEmpty()) return
    val pages = remember(imagePaths) { imagePaths.mapIndexed { i, p -> MangaPage(i, p.toUri().toString()) } }
    ReaderWindow(title, pages, Path.of(System.getProperty("user.home"), ".zeravrix", "cache", "pages"), onClose)
}

@Composable
fun ReaderWindow(
    title: String,
    pages: List<MangaPage>,
    cacheRoot: Path,
    onClose: () -> Unit,
    initialLayout: PageLayout = PageLayout.AUTO,
    nextChapterLoader: (suspend () -> List<MangaPage>)? = null,
) {
    if (pages.isEmpty()) return
    val windowState = rememberWindowState(width = 1500.dp, height = 950.dp, placement = WindowPlacement.Maximized)
    Window(onCloseRequest = onClose, title = "ZERAVRIX Reader • $title", state = windowState) {
        ReaderSurface(title, pages, cacheRoot, onClose, initialLayout, 0, nextChapterLoader, 0, {}, windowState)
    }
}

/** Embedded reader surface hosted by the main themed ZERAVRIX window. */
@Composable
fun ReaderContent(
    title: String,
    pages: List<MangaPage>,
    cacheRoot: Path,
    onClose: () -> Unit,
    hostWindowState: WindowState,
    language: ReaderLanguage = ReaderLanguage.ENGLISH,
    initialLayout: PageLayout = PageLayout.AUTO,
    initialPageIndex: Int = 0,
    nextChapterLoader: (suspend () -> List<MangaPage>)? = null,
    toggleControlsSignal: Int = 0,
    onPageProgress: (Int) -> Unit = {},
    musicTracks: List<MusicTrack> = emptyList(),
    currentMusic: MusicTrack? = null,
    onSelectMusic: (MusicTrack?) -> Unit = {},
    loopMusic: Boolean = false,
    shuffleMusic: Boolean = false,
    onToggleLoop: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    musicPlaylists: List<MusicPlaylist> = emptyList(),
    musicPlaylistName: String? = null,
    onSelectMusicPlaylist: (String?) -> Unit = {},
    onMusicPrevious: () -> Unit = {},
    onMusicNext: () -> Unit = {},
) {
    if (pages.isEmpty()) return
    ReaderSurface(title, pages, cacheRoot, onClose, initialLayout, initialPageIndex, nextChapterLoader, toggleControlsSignal, onPageProgress, hostWindowState, musicTracks, currentMusic, onSelectMusic, loopMusic, shuffleMusic, onToggleLoop, onToggleShuffle, language, musicPlaylists, musicPlaylistName, onSelectMusicPlaylist, onMusicPrevious, onMusicNext)
}

@Composable
private fun ReaderSurface(
    title: String,
    pages: List<MangaPage>,
    cacheRoot: Path,
    onClose: () -> Unit,
    initialLayout: PageLayout,
    initialPageIndex: Int,
    nextChapterLoader: (suspend () -> List<MangaPage>)?,
    toggleControlsSignal: Int,
    onPageProgress: (Int) -> Unit,
    windowState: WindowState,
    musicTracks: List<MusicTrack> = emptyList(),
    currentMusic: MusicTrack? = null,
    onSelectMusic: (MusicTrack?) -> Unit = {},
    loopMusic: Boolean = false,
    shuffleMusic: Boolean = false,
    onToggleLoop: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    language: ReaderLanguage = ReaderLanguage.ENGLISH,
    musicPlaylists: List<MusicPlaylist> = emptyList(),
    musicPlaylistName: String? = null,
    onSelectMusicPlaylist: (String?) -> Unit = {},
    onMusicPrevious: () -> Unit = {},
    onMusicNext: () -> Unit = {},
) {
    var state by remember(pages.firstOrNull()?.imageUrl, initialLayout, initialPageIndex) { mutableStateOf(ReaderState(pageIndex = initialPageIndex.coerceAtLeast(0), layout = initialLayout, zoom = .8f)) }
    var controlsVisible by remember(pages.firstOrNull()?.imageUrl) { mutableStateOf(false) }
    var lastToggleControlsSignal by remember { mutableIntStateOf(toggleControlsSignal) }
    var translationMode by remember { mutableStateOf(false) }
    var selectedPageIndex by remember { mutableStateOf<Int?>(null) }
    var selectedRegion by remember { mutableStateOf<SelectionRegion?>(null) }
    var translationDialogVisible by remember { mutableStateOf(false) }
    var translationError by remember { mutableStateOf<String?>(null) }
    val translator = remember { MangaTranslationEngine() }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val readerFocusRequester = remember { FocusRequester() }
    var loadedPages by remember(pages) { mutableStateOf(pages) }
    var cached by remember(pages) { mutableStateOf<Map<Int, Path>>(emptyMap()) }
    var cacheErrors by remember(pages) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var decodedBitmaps by remember(pages) { mutableStateOf<Map<Int, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    var decodeErrors by remember(pages) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var loadingNextChapter by remember { mutableStateOf(false) }
    var nextChapterFinished by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val sessionKey = remember(pages) {
        MessageDigest.getInstance("SHA-256").digest(pages.firstOrNull()?.imageUrl.orEmpty().toByteArray())
            .joinToString("") { "%02x".format(it) }.take(20)
    }
    val sessionCacheRoot = remember(cacheRoot, sessionKey) { cacheRoot.resolve("reader-session-$sessionKey") }
    val cache = remember(sessionCacheRoot) { PageCache(sessionCacheRoot) }
    val pageLoader = remember(cache) { ReaderPageLoader(cache, maxConcurrentDownloads = 3) }
    val bitmapCache = remember(pages) { ImageBitmapLru(maxEntries = 16) }
    val pageLoadScope = rememberCoroutineScope()
    var ctrlPressed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        readerFocusRequester.requestFocus()
    }
    LaunchedEffect(toggleControlsSignal) {
        if (toggleControlsSignal > lastToggleControlsSignal) {
            controlsVisible = !controlsVisible
            readerFocusRequester.requestFocus()
        }
        lastToggleControlsSignal = toggleControlsSignal
    }
    DisposableEffect(pageLoader) {
        onDispose { pageLoader.cancelAll() }
    }

    suspend fun decodePage(index: Int, path: Path) {
        bitmapCache.get(index)?.let {
            decodedBitmaps = bitmapCache.snapshot()
            return
        }
        try {
            val bitmap = withContext(Dispatchers.Default) {
                SkiaImage.makeFromEncoded(Files.readAllBytes(path)).toComposeImageBitmap()
            }
            bitmapCache.put(index, bitmap)
            decodedBitmaps = bitmapCache.snapshot()
            decodeErrors = decodeErrors - index
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            decodeErrors = decodeErrors + (index to (failure.message ?: "Image decode failed"))
        }
    }

    suspend fun requestPage(index: Int) {
        val page = loadedPages.getOrNull(index) ?: return
        if (cacheErrors.containsKey(index)) return
        val path = cached[index] ?: try {
            pageLoader.load(page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            cacheErrors = cacheErrors + (index to (failure.message ?: "Page download failed"))
            return
        }
        cached = cached + (index to path)
        decodePage(index, path)
    }

    fun prefetchWindow(center: Int) {
        val indices = buildList {
            add(center.coerceIn(0, loadedPages.lastIndex.coerceAtLeast(0)))
            for (offset in 1..6) {
                add(center + offset)
                if (center - offset >= 0) add(center - offset)
            }
        }.distinct().filter { it in loadedPages.indices }
        pageLoadScope.launch {
            coroutineScope {
                indices.map { index -> async { requestPage(index) } }.awaitAll()
            }
        }
    }

    LaunchedEffect(selectedPageIndex, selectedRegion) {
        translationError = null
    }

    // Load only the current page and a bounded nearby window. The loader limits network
    // concurrency to three and deduplicates visible/prefetch requests.
    LaunchedEffect(loadedPages, state.pageIndex, state.layout) {
        if (state.layout != PageLayout.VERTICAL) prefetchWindow(state.pageIndex)
    }
    LaunchedEffect(loadedPages, state.layout, lazyListState) {
        if (state.layout == PageLayout.VERTICAL) {
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { visible -> visible.firstOrNull()?.let(::prefetchWindow) }
        }
    }

    fun loadNextChapterNow() {
        if (nextChapterLoader == null || nextChapterFinished || loadingNextChapter) return
        loadingNextChapter = true
        pageLoadScope.launch {
            val next = try {
                nextChapterLoader.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
            if (next.isNotEmpty()) {
                loadedPages = loadedPages + next.mapIndexed { index, page -> page.copy(index = loadedPages.size + index) }
                nextChapterFinished = false
            } else {
                nextChapterFinished = true
            }
            loadingNextChapter = false
        }
    }

    LaunchedEffect(lazyListState, loadedPages.size, nextChapterLoader, nextChapterFinished, state.layout, state.pageIndex) {
        snapshotFlow {
            Triple(
                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index,
                lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                lazyListState.layoutInfo.totalItemsCount,
            )
        }.distinctUntilChanged().collect { (firstVisible, lastVisible, totalItems) ->
            if (state.layout == PageLayout.VERTICAL && totalItems > 0) {
                onPageProgress((firstVisible ?: 0).coerceIn(0, loadedPages.lastIndex))
                if (nextChapterLoader != null && !nextChapterFinished &&
                    (lastVisible ?: 0) >= (totalItems - 4).coerceAtLeast(0) && !loadingNextChapter) {
                    loadNextChapterNow()
                }
            } else {
                onPageProgress(state.pageIndex.coerceIn(0, loadedPages.lastIndex))
            }
        }
    }

    fun zoomTo(value: Float) {
        state = state.copy(zoom = value.coerceIn(.25f, 4f))
        if (state.zoom <= 1f) pan = Offset.Zero
    }

    fun nextPage(direction: Int) {
        val step = if (state.layout == PageLayout.DOUBLE) 2 else 1
        state = state.copy(pageIndex = (state.pageIndex + direction * step).coerceIn(0, loadedPages.lastIndex))
        pan = Offset.Zero
    }

    fun toggleLayout() {
        state = state.copy(
            layout = when (state.layout) {
                PageLayout.SINGLE -> PageLayout.DOUBLE
                PageLayout.DOUBLE -> PageLayout.AUTO
                PageLayout.AUTO -> PageLayout.VERTICAL
                PageLayout.VERTICAL -> PageLayout.SINGLE
            },
        )
        pan = Offset.Zero
    }

            Box(
            Modifier.fillMaxSize()
                .focusRequester(readerFocusRequester)
                .focusable()
                .background(MaterialTheme.colorScheme.background)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.CtrlLeft || event.key == Key.CtrlRight) {
                        ctrlPressed = event.type == KeyEventType.KeyDown
                        return@onPreviewKeyEvent true
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.PageDown, Key.Spacebar -> {
                            nextPage(if (state.direction == ReadingDirection.LTR) 1 else -1); true
                        }
                        Key.DirectionLeft, Key.PageUp -> {
                            nextPage(if (state.direction == ReadingDirection.LTR) -1 else 1); true
                        }
                        Key.Plus -> { zoomTo(state.zoom + .1f); true }
                        Key.Minus -> { zoomTo(state.zoom - .1f); true }
                        Key.Zero -> { zoomTo(1f); true }
                        Key.F -> { toggleLayout(); true }
                        Key.Tab -> {
                            controlsVisible = !controlsVisible
                            readerFocusRequester.requestFocus()
                            true
                        }
                        Key.F11 -> {
                            windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Maximized else WindowPlacement.Fullscreen
                            true
                        }
                        Key.R -> {
                            state = state.copy(direction = if (state.direction == ReadingDirection.LTR) ReadingDirection.RTL else ReadingDirection.LTR)
                            true
                        }
                        Key.Escape -> {
                            when {
                                windowState.placement == WindowPlacement.Fullscreen -> windowState.placement = WindowPlacement.Maximized
                                state.zoom > 1f -> zoomTo(1f)
                                else -> controlsVisible = !controlsVisible
                            }
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(state.zoom) {
                    detectTransformGestures { _, delta, scale, _ ->
                        pan += delta
                        zoomTo(state.zoom * scale)
                    }
                }
                .pointerInput(state.zoom) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll && ctrlPressed) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (delta != 0f) zoomTo(state.zoom * (1f - delta * .08f))
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(state.zoom, translationMode) {
                    if (!translationMode) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            if (state.zoom > 1f) pan += drag
                        }
                    }
                },
        ) {
            ReaderPages(
                pages = loadedPages,
                cached = cached,
                cacheErrors = cacheErrors,
                decodedBitmaps = decodedBitmaps,
                decodeErrors = decodeErrors,
                state = state,
                pan = pan,
                lazyListState = lazyListState,
                loadingNextChapter = loadingNextChapter,
                translationMode = translationMode,
                selectedPageIndex = selectedPageIndex,
                selectedRegion = selectedRegion,
                onSelection = { index, region ->
                    selectedPageIndex = index
                    selectedRegion = region
                    translationDialogVisible = true
                    controlsVisible = true
                },
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(animationSpec = tween(140)) + slideInVertically(animationSpec = tween(180), initialOffsetY = { -it / 2 }),
                exit = fadeOut(animationSpec = tween(110)) + slideOutVertically(animationSpec = tween(140), targetOffsetY = { -it / 2 }),
            ) {
                TopReaderBar(
                    title = title,
                    state = state,
                    total = loadedPages.size,
                    onPrev = { nextPage(if (state.direction == ReadingDirection.LTR) -1 else 1) },
                    onNext = { nextPage(if (state.direction == ReadingDirection.LTR) 1 else -1) },
                    hasNextChapter = nextChapterLoader != null && !nextChapterFinished,
                    onNextChapter = ::loadNextChapterNow,
                    onOut = { zoomTo(.8f) },
                    onIn = { zoomTo(1f) },
                    onFit = { zoomTo(1f) },
                    onZoom = ::zoomTo,
                    musicTracks = musicTracks,
                    currentMusic = currentMusic,
                    onSelectMusic = onSelectMusic,
                    loopMusic = loopMusic,
                    shuffleMusic = shuffleMusic,
                    onToggleLoop = onToggleLoop,
                    onToggleShuffle = onToggleShuffle,
                    musicPlaylists = musicPlaylists,
                    musicPlaylistName = musicPlaylistName,
                    onSelectMusicPlaylist = onSelectMusicPlaylist,
                    onMusicPrevious = onMusicPrevious,
                    onMusicNext = onMusicNext,
                    onLayout = ::toggleLayout,
                    language = language,
                    onDir = { state = state.copy(direction = if (state.direction == ReadingDirection.LTR) ReadingDirection.RTL else ReadingDirection.LTR) },
                    saturation = state.saturation,
                    onSaturation = { state = state.copy(saturation = it.coerceIn(0f, 2f)) },
                    translationMode = translationMode,
                    onTranslationMode = {
                        translationMode = !translationMode
                        selectedPageIndex = null
                        selectedRegion = null
                        translationDialogVisible = false
                        controlsVisible = true
                    },
                    onHide = { controlsVisible = false },
                )
            }
            if (translationMode && !controlsVisible) {
                Surface(color = Color(0xE61B4D86), shape = RoundedCornerShape(14.dp), modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                    Text("Translation mode • drag over text • Tab for controls", color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
            if (translationDialogVisible) {
                TranslationDialog(
                    page = selectedPageIndex?.let { cached[it] },
                    region = selectedRegion,
                    translator = translator,
                    onDismiss = { translationDialogVisible = false },
                )
            }
        }
}

@Composable
private fun TopReaderBar(
    title: String,
    state: ReaderState,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    hasNextChapter: Boolean,
    onNextChapter: () -> Unit,
    onOut: () -> Unit,
    onIn: () -> Unit,
    onFit: () -> Unit,
    onZoom: (Float) -> Unit,
    musicTracks: List<MusicTrack>,
    currentMusic: MusicTrack?,
    onSelectMusic: (MusicTrack?) -> Unit,
    loopMusic: Boolean,
    shuffleMusic: Boolean,
    onToggleLoop: () -> Unit,
    onToggleShuffle: () -> Unit,
    musicPlaylists: List<MusicPlaylist>,
    musicPlaylistName: String?,
    onSelectMusicPlaylist: (String?) -> Unit,
    onMusicPrevious: () -> Unit,
    onMusicNext: () -> Unit,
    onLayout: () -> Unit,
    onDir: () -> Unit,
    language: ReaderLanguage,
    saturation: Float,
    onSaturation: (Float) -> Unit,
    translationMode: Boolean,
    onTranslationMode: () -> Unit,
    onHide: () -> Unit,
) {
    var zoomMenuExpanded by remember { mutableStateOf(false) }
    var musicMenuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().height(94.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .32f)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onPrev, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)) { Text(readerText(language, "← Prev", "← السابق", "← Précédent")) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onNext, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp)) { Text(readerText(language, "Next →", "التالي →", "Suivant →")) }
                if (hasNextChapter) {
                    Button(onClick = onNextChapter, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) { Text(readerText(language, "Next chapter", "الفصل التالي", "Chapitre suivant")) }
                }
                Spacer(Modifier.width(14.dp))
                Text(title, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                    Text("${state.pageIndex + 1} / $total", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(readerText(language, "Zoom", "التكبير", "Zoom"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = onOut) { Text("80%", color = MaterialTheme.colorScheme.primary) }
                Box {
                    TextButton(onClick = { zoomMenuExpanded = true }) { Text("${(state.zoom * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurface) }
                    DropdownMenu(expanded = zoomMenuExpanded, onDismissRequest = { zoomMenuExpanded = false }) {
                        listOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f).forEach { preset ->
                            DropdownMenuItem(text = { Text("${(preset * 100).toInt()}%") }, onClick = { onZoom(preset); zoomMenuExpanded = false })
                        }
                    }
                }
                TextButton(onClick = onIn) { Text("100%", color = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = onFit) { Text(readerText(language, "Fit", "ملاءمة", "Ajuster"), color = MaterialTheme.colorScheme.primary) }
                HorizontalDivider(Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onLayout) {
                    Text(when (state.layout) { PageLayout.SINGLE -> "1 Page"; PageLayout.DOUBLE -> "2 Pages"; PageLayout.AUTO -> "Auto"; PageLayout.VERTICAL -> "Webtoon" }, color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDir) { Text(if (state.direction == ReadingDirection.LTR) "LTR" else "RTL", color = MaterialTheme.colorScheme.primary) }
                TextButton(onClick = onTranslationMode) { Text(if (translationMode) "Translate: ON" else "Translate", color = if (translationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                var saturationMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { saturationMenuExpanded = true }) { Text("Color ${(saturation * 100).toInt()}%", color = MaterialTheme.colorScheme.primary) }
                    DropdownMenu(expanded = saturationMenuExpanded, onDismissRequest = { saturationMenuExpanded = false }) {
                        Text(readerText(language, "Saturation", "التشبع", "Saturation"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Slider(value = saturation, onValueChange = onSaturation, valueRange = 0f..2f, steps = 7, modifier = Modifier.width(190.dp).padding(horizontal = 12.dp))
                        DropdownMenuItem(text = { Text(readerText(language, "Reset colors", "إعادة ضبط الألوان", "Réinitialiser les couleurs")) }, onClick = { onSaturation(1f); saturationMenuExpanded = false })
                    }
                }
                Box {
                    TextButton(onClick = { musicMenuExpanded = true }) {
                        Text(if (currentMusic == null) readerText(language, "Music", "الموسيقى", "Musique") else "♫ ${currentMusic.title.take(12)}", color = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = musicMenuExpanded, onDismissRequest = { musicMenuExpanded = false }) {
                        Text(readerText(language, "Playback", "التشغيل", "Lecture"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = onMusicPrevious, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("⏮") }
                            OutlinedButton(onClick = onMusicNext, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("⏭") }
                            OutlinedButton(onClick = { onSelectMusic(null); musicMenuExpanded = false }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(readerText(language, "Stop", "إيقاف", "Stop")) }
                        }
                        Text(readerText(language, "Music source", "مصدر الموسيقى", "Source musicale"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        DropdownMenuItem(
                            text = { Text(if (musicPlaylistName == null) "✓ ${readerText(language, "All tracks", "كل المقاطع", "Tous les morceaux")}" else readerText(language, "All tracks", "كل المقاطع", "Tous les morceaux")) },
                            onClick = { onSelectMusicPlaylist(null); musicMenuExpanded = false },
                        )
                        musicPlaylists.forEach { playlist ->
                            DropdownMenuItem(
                                text = { Text(if (playlist.name == musicPlaylistName) "✓ ${playlist.name}" else "${playlist.name} • ${playlist.trackIds.size}") },
                                onClick = { onSelectMusicPlaylist(playlist.name); musicMenuExpanded = false },
                            )
                        }
                        HorizontalDivider()
                        if (musicTracks.isEmpty()) Text(readerText(language, "Add tracks in My Music", "أضف المقاطع من موسيقاي", "Ajoutez des morceaux dans Ma musique"), modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        musicTracks.take(14).forEach { track ->
                            DropdownMenuItem(text = { Text(if (track.id == currentMusic?.id) "▶ ${track.title}" else track.title) }, onClick = { onSelectMusic(track); musicMenuExpanded = false })
                        }
                        DropdownMenuItem(text = { Text(if (loopMusic) "Loop: ON" else "Loop: OFF") }, onClick = onToggleLoop)
                        DropdownMenuItem(text = { Text(if (shuffleMusic) "Shuffle: ON" else "Shuffle: OFF") }, onClick = onToggleShuffle)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), shape = RoundedCornerShape(10.dp)) {
                    TextButton(onClick = onHide) { Text(readerText(language, "Hide menu", "إخفاء القائمة", "Masquer le menu"), color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

enum class ReaderLanguage { ENGLISH, ARABIC, FRENCH }

private fun readerText(language: ReaderLanguage, english: String, arabic: String, french: String): String = when (language) {
    ReaderLanguage.ENGLISH -> english
    ReaderLanguage.ARABIC -> arabic
    ReaderLanguage.FRENCH -> french
}

@Composable
private fun ReaderPages(
    pages: List<MangaPage>,
    cached: Map<Int, Path>,
    cacheErrors: Map<Int, String>,
    decodedBitmaps: Map<Int, androidx.compose.ui.graphics.ImageBitmap>,
    decodeErrors: Map<Int, String>,
    state: ReaderState,
    pan: Offset,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    loadingNextChapter: Boolean,
    translationMode: Boolean,
    selectedPageIndex: Int?,
    selectedRegion: SelectionRegion?,
    onSelection: (Int, SelectionRegion) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier) {
        if (state.layout == PageLayout.VERTICAL) {
            val horizontalScrollState = rememberScrollState()
            val viewportWidth = maxWidth
            val contentWidth = viewportWidth * state.zoom
            Box(Modifier.fillMaxSize().horizontalScroll(horizontalScrollState)) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.width(maxOf(viewportWidth, contentWidth)).fillMaxHeight().align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(
                        items = pages,
                        key = { index, page -> "${page.chapterLabel.orEmpty()}:$index:${page.imageUrl}" },
                        contentType = { _, _ -> "reader-page" },
                    ) { index, page ->
                        page.chapterLabel?.let { label -> ChapterSeparator(label) }
                        ReaderPageImage(
                            index = index,
                            path = cached[index],
                            error = cacheErrors[index],
                            bitmap = decodedBitmaps[index],
                            decodeError = decodeErrors[index],
                            modifier = Modifier.width(contentWidth),
                            state = state,
                            pan = pan,
                            vertical = true,
                            translationMode = translationMode,
                            selected = if (selectedPageIndex == index) selectedRegion else null,
                            onSelection = { onSelection(index, it) },
                            saturation = state.saturation,
                        )
                    }
                    if (loadingNextChapter) item(key = "loading-next-chapter") {
                        Text("Loading next chapter…", color = Color.Gray, modifier = Modifier.padding(18.dp))
                    }
                }
            }
            return@BoxWithConstraints
        }

        val containerWidth = maxWidth
        val layout = when (state.layout) {
            PageLayout.SINGLE -> PageLayout.SINGLE
            PageLayout.DOUBLE -> PageLayout.DOUBLE
            PageLayout.AUTO -> if (containerWidth >= 1250.dp) PageLayout.DOUBLE else PageLayout.SINGLE
            PageLayout.VERTICAL -> PageLayout.VERTICAL
        }
        val start = state.pageIndex.coerceIn(pages.indices)
        val indices = if (layout == PageLayout.DOUBLE) {
            if (state.direction == ReadingDirection.LTR) listOf(start, start + 1).filter { it in pages.indices }
            else listOf(start, start - 1).filter { it in pages.indices }
        } else listOf(start)

        Row(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            indices.forEach { index ->
                ReaderPageImage(
                    index = index,
                    path = cached[index],
                    error = cacheErrors[index],
                    bitmap = decodedBitmaps[index],
                    decodeError = decodeErrors[index],
                    modifier = Modifier.weight(1f, fill = false).fillMaxHeight().widthIn(max = if (layout == PageLayout.DOUBLE) containerWidth / 2 else containerWidth).padding(4.dp),
                    state = state,
                    pan = pan,
                    vertical = false,
                    translationMode = translationMode,
                    selected = if (selectedPageIndex == index) selectedRegion else null,
                    onSelection = { onSelection(index, it) },
                    saturation = state.saturation,
                )
            }
        }
    }
}

@Composable
private fun ReaderPageImage(
    index: Int,
    path: Path?,
    error: String?,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    decodeError: String?,
    modifier: Modifier,
    state: ReaderState,
    pan: Offset,
    vertical: Boolean,
    translationMode: Boolean,
    selected: SelectionRegion?,
    onSelection: (SelectionRegion) -> Unit,
    saturation: Float,
) {
    var dragStart by remember(index) { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember(index) { mutableStateOf<Offset?>(null) }
    var canvasSize by remember(index) { mutableStateOf(IntSize.Zero) }
    val dragSelection = remember(dragStart, dragCurrent, canvasSize) {
        val start = dragStart
        val current = dragCurrent
        if (start == null || current == null || canvasSize.width <= 0 || canvasSize.height <= 0) null
        else SelectionRegion(
            start.x / canvasSize.width,
            start.y / canvasSize.height,
            current.x / canvasSize.width,
            current.y / canvasSize.height,
        ).normalized()
    }
    Box(
        modifier
            .onSizeChanged { canvasSize = it }
            .then(if (translationMode) Modifier.pointerInput(index) {
                detectDragGestures(
                    onDragStart = { point -> dragStart = point; dragCurrent = point },
                    onDragCancel = { dragStart = null; dragCurrent = null },
                    onDragEnd = {
                        val region = dragSelection
                        dragStart = null
                        dragCurrent = null
                        if (region != null && region.isUseful()) onSelection(region)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragCurrent = change.position
                    },
                )
            } else Modifier)
            .drawWithContent {
                drawContent()
                val region = dragSelection ?: selected
                if (translationMode && region != null) {
                    val r = region.normalized()
                    drawRect(
                        color = Color(0x663B9CFF),
                        topLeft = Offset(size.width * r.left, size.height * r.top),
                        size = androidx.compose.ui.geometry.Size(size.width * (r.right - r.left), size.height * (r.bottom - r.top)),
                    )
                    drawRect(
                        color = Color(0xFF5CB6FF),
                        topLeft = Offset(size.width * r.left, size.height * r.top),
                        size = androidx.compose.ui.geometry.Size(size.width * (r.right - r.left), size.height * (r.bottom - r.top)),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (error != null) {
            Text("Page ${index + 1} failed: $error", color = Color(0xFFB3261E), modifier = Modifier.padding(18.dp))
        } else if (path == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Loading page ${index + 1}…", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
        } else if (decodeError != null) {
            Text("Page ${index + 1} decode failed: $decodeError", color = Color(0xFFB3261E), modifier = Modifier.padding(18.dp))
        } else if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text("Decoding page ${index + 1}…", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
            }
        } else {
            val decoded = bitmap
            Image(
                decoded,
                "Page ${index + 1}",
                if (vertical) {
                        Modifier.fillMaxWidth().aspectRatio(decoded.width.toFloat() / decoded.height.toFloat(), matchHeightConstraintsFirst = false)
                } else {
                    Modifier.fillMaxSize().graphicsLayer(
                        scaleX = state.zoom,
                        scaleY = state.zoom,
                        translationX = pan.x,
                        translationY = pan.y,
                    )
                },
                contentScale = ContentScale.Fit,
                colorFilter = if (saturation == 1f) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }),
            )
        }
    }
}

@Composable
private fun ChapterSeparator(label: String) {
    Surface(
        color = Color(0xFF080A0E),
        modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp, max = 58.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFFE6EAF2), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}


@Composable
private fun TranslationDialog(
    page: Path?,
    region: SelectionRegion?,
    translator: MangaTranslationEngine,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sourceLanguage by remember { mutableStateOf(TranslationLanguage.AUTO) }
    var targetLanguage by remember { mutableStateOf(TranslationLanguage.ARABIC) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(readClipboardText()) }
    var translatedText by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun runOcr() {
        if (page == null || region == null) {
            error = "Select a visible page region first."
            return
        }
        val executable = MangaOcrBridge.executable()
        if (executable == null) {
            error = "Local OCR is not installed. Paste the selected text below, or set ZERAVRIX_TESSERACT to a Tesseract executable."
            return
        }
        busy = true
        error = null
        selectedText = withContext(Dispatchers.IO) { MangaOcrBridge.recognize(page, region, sourceLanguage) }
        if (selectedText.isBlank()) error = "OCR did not find readable text in this selection. Try a larger or tighter region."
        busy = false
    }

    LaunchedEffect(page, region) {
        if (page != null && region != null) {
            if (MangaOcrBridge.executable() != null) runOcr()
            else if (selectedText.isBlank()) error = "Local OCR is not installed. Paste the selected text below, or set ZERAVRIX_TESSERACT to a Tesseract executable."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 620.dp, max = 820.dp),
        title = { Text("Translate selected text", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Translation is active only for the page region you selected. Your normal reader remains unchanged.", color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(onClick = { sourceMenuExpanded = true }) { Text("From: ${sourceLanguage.label}") }
                        DropdownMenu(expanded = sourceMenuExpanded, onDismissRequest = { sourceMenuExpanded = false }) {
                            TranslationLanguage.values().forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { sourceLanguage = option; sourceMenuExpanded = false })
                            }
                        }
                    }
                    Text("→", color = Color.Gray)
                    Box {
                        OutlinedButton(onClick = { targetMenuExpanded = true }) { Text("To: ${targetLanguage.label}") }
                        DropdownMenu(expanded = targetMenuExpanded, onDismissRequest = { targetMenuExpanded = false }) {
                            listOf(TranslationLanguage.ARABIC, TranslationLanguage.ENGLISH).forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = { targetLanguage = option; targetMenuExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(enabled = !busy, onClick = { scope.launch { runOcr() } }) { Text("OCR selection") }
                }
                OutlinedTextField(
                    value = selectedText,
                    onValueChange = { selectedText = it; translatedText = ""; error = null },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 190.dp),
                    label = { Text("Selected text") },
                    placeholder = { Text("OCR appears here; you can correct it before translating") },
                )
                error?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall) }
                if (translatedText.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Translation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(translatedText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                            if (provider.isNotBlank()) Text(provider, color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && selectedText.isNotBlank() && targetLanguage != TranslationLanguage.AUTO,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching { withContext(Dispatchers.IO) { translator.translate(selectedText, sourceLanguage, targetLanguage) } }
                            .onSuccess { result -> translatedText = result.translatedText; provider = result.provider }
                            .onFailure { error = it.message ?: "Translation failed." }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Working…" else "Translate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun readClipboardText(): String = runCatching {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) ""
    else clipboard.getData(DataFlavor.stringFlavor)?.toString().orEmpty().trim()
}.getOrDefault("")
