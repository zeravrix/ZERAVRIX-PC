package com.zeravrix.zeravrix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.data.DesktopMangaLoaderContext
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.theme.createGlassTokens
import com.zeravrix.zeravrix.theme.createMaterialColorScheme
import com.zeravrix.zeravrix.ui.components.AppNavScreen
import com.zeravrix.zeravrix.ui.components.AppNavigationRail
import com.zeravrix.zeravrix.ui.components.WindowChrome
import com.zeravrix.zeravrix.ui.reader.DesktopReaderView
import com.zeravrix.zeravrix.ui.screens.DetailsScreen
import com.zeravrix.zeravrix.ui.screens.ExploreScreen
import com.zeravrix.zeravrix.ui.screens.FeedScreen
import com.zeravrix.zeravrix.ui.screens.MangaShelfScreen
import com.zeravrix.zeravrix.ui.screens.SettingsScreen
import com.zeravrix.zeravrix.ui.screens.ShelfKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*

private data class ActiveReaderSession(
    val manga: Manga,
    val chapter: Chapter,
    val pages: List<MangaPage>,
    val initialPage: Int = 0,
)

private fun setupCrashDiagnostics(appDir: Path) {
    runCatching {
        Files.createDirectories(appDir)
        val crashLog = appDir.resolve("crash.log")
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val msg = buildString {
                appendLine("[$timestamp] Uncaught exception in thread '${thread.name}':")
                appendLine(throwable.stackTraceToString())
            }
            System.err.println(msg)
            runCatching {
                Files.writeString(
                    crashLog,
                    msg,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
                )
            }
        }
    }
}

private fun loadAppIconPainter(): BitmapPainter {
    val bytes = runCatching {
        object {}.javaClass.getResourceAsStream("/logo.png")?.readBytes()
    }.getOrNull()

    val bmp = if (bytes != null && bytes.isNotEmpty()) {
        runCatching {
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    } else null

    return BitmapPainter(bmp ?: ImageBitmap(1, 1))
}

fun main() = application {
    val appDir = remember { Path.of(System.getProperty("user.home"), ".zeravrix") }
    remember { setupCrashDiagnostics(appDir) }

    val loaderContext = remember { DesktopMangaLoaderContext(appDir) }
    val settingsStore = remember { SettingsStore(appDir) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    val libraryStore = remember { LibraryStore(appDir) }
    val sourceRegistry = remember { LiveSourceRegistry(appDir, loaderContext) }
    val downloader = remember { DesktopDownloader(appDir) }

    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(AppNavScreen.EXPLORE) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMangaForDetail by remember { mutableStateOf<Manga?>(null) }
    var activeReader by remember { mutableStateOf<ActiveReaderSession?>(null) }
    var shelfRefresh by remember { mutableIntStateOf(0) }
    var latestReadPair by remember { mutableStateOf<Pair<Manga, ReadingProgress>?>(null) }

    LaunchedEffect(shelfRefresh, activeReader) {
        withContext(Dispatchers.IO) {
            val allHistory = libraryStore.listHistory()
            val latest = allHistory.firstNotNullOfOrNull { m ->
                libraryStore.latestProgress(m.id)?.let { m to it }
            }
            latestReadPair = latest
        }
    }

    // Fast Startup Loading Screen states
    var isAppReady by remember { mutableStateOf(false) }
    var startupProgress by remember { mutableFloatStateOf(0.2f) }
    var startupStatus by remember { mutableStateOf("Initializing ZERAVRIX engine...") }

    val appIcon = remember { loadAppIconPainter() }
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = DpSize(1280.dp, 820.dp)
    )

    val tokens = remember(settings.colorTheme, settings.theme, settings.isAmoled) {
        createGlassTokens(settings.colorTheme, settings.theme, settings.isAmoled)
    }

    val isRtl = settings.language == AppLanguage.ARABIC

    // Background warmup to prevent UI freeze and animate progress
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            startupStatus = "Loading library & reading history..."
            startupProgress = 0.45f
            libraryStore.list()
            libraryStore.listHistory()

            startupStatus = "Connecting live manga & manhwa sources..."
            startupProgress = 0.8f
            sourceRegistry.getAllKotatsuParserSources()

            startupStatus = "Ready"
            startupProgress = 1.0f
            delay(180)
            isAppReady = true
        }
    }

    // Helper: Open chapter with CBZ detection
    fun openChapter(manga: Manga, chapter: Chapter, startPage: Int) {
        scope.launch {
            withContext(Dispatchers.IO) {
                libraryStore.recordHistory(manga)
            }
            val downloaded = downloader.downloadedChapters(manga.id).firstOrNull { it.chapterId == chapter.id }
            val pages = if (downloaded != null) {
                downloader.localPages(downloaded)
            } else {
                sourceRegistry.fetchPages(chapter)
            }
            if (pages.isNotEmpty()) {
                activeReader = ActiveReaderSession(
                    manga = manga,
                    chapter = chapter,
                    pages = pages,
                    initialPage = startPage.coerceIn(0, pages.lastIndex)
                )
            }
        }
    }

    // Helper: Resume reading from exact saved position
    fun resumeLastRead() {
        scope.launch {
            val pair = latestReadPair ?: withContext(Dispatchers.IO) {
                val allHistory = libraryStore.listHistory()
                allHistory.firstNotNullOfOrNull { m ->
                    libraryStore.latestProgress(m.id)?.let { m to it }
                }
            }
            if (pair != null) {
                val (manga, progress) = pair
                val chapters = sourceRegistry.fetchChapters(manga)
                val targetChapter = chapters.firstOrNull { it.id == progress.chapterId } ?: chapters.firstOrNull()
                if (targetChapter != null) {
                    val safePage = progress.pageIndex.coerceAtLeast(0)
                    openChapter(manga, targetChapter, safePage)
                }
            }
        }
    }

    val clearCache: () -> Unit = {
        runCatching {
            val cacheDir = appDir.resolve("cache")
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir).use { walk ->
                    walk.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
                }
            }
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "ZERAVRIX • Native Windows Manga & Manhwa Reader",
        icon = appIcon,
        undecorated = true,
        resizable = true,
    ) {
        CompositionLocalProvider(
            LocalGlassTokens provides tokens,
            LocalLayoutDirection provides (if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
        ) {
            MaterialTheme(colorScheme = createMaterialColorScheme(tokens)) {
                val isMaximized = windowState.placement == WindowPlacement.Maximized
                val outerCorner = if (isMaximized) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tokens.background)
                        .border(1.dp, tokens.borderGlass, outerCorner)
                        .clip(outerCorner)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Window Top Chrome Bar
                        WindowChrome(
                            windowState = windowState,
                            currentScreenTitle = currentScreen.title,
                            searchQuery = searchQuery,
                            onSearchClick = {
                                activeMangaForDetail = null
                                currentScreen = AppNavScreen.EXPLORE
                            },
                            onClose = ::exitApplication,
                            readerActive = activeReader != null,
                            onReaderToggle = { /* focus reader */ }
                        )

                        // Main Workspace, Split-Pane Details, or Native PC Reader
                        if (activeReader != null) {
                            val session = activeReader!!
                            DesktopReaderView(
                                mangaTitle = session.manga.title,
                                chapterTitle = session.chapter.title,
                                pages = session.pages,
                                initialPage = session.initialPage,
                                initialMode = settings.readerMode,
                                onClose = {
                                    activeReader = null
                                    shelfRefresh++
                                },
                                onPageChanged = { pageIndex ->
                                    libraryStore.saveProgress(
                                        ReadingProgress(
                                            mangaId = session.manga.id,
                                            chapterId = session.chapter.id,
                                            pageIndex = pageIndex,
                                            chapterNumber = session.chapter.number,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            )
                        } else if (activeMangaForDetail != null) {
                            val detailManga = activeMangaForDetail!!
                            DetailsScreen(
                                manga = detailManga,
                                sourceRegistry = sourceRegistry,
                                libraryStore = libraryStore,
                                downloader = downloader,
                                onBack = {
                                    activeMangaForDetail = null
                                    shelfRefresh++
                                },
                                onRead = { chapter, startPage -> openChapter(detailManga, chapter, startPage) }
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Left Navigation Rail
                                AppNavigationRail(
                                    currentScreen = currentScreen,
                                    onScreenSelect = { screen ->
                                        activeMangaForDetail = null
                                        currentScreen = screen
                                    }
                                )

                                // Screen Content View
                                Box(modifier = Modifier.fillMaxSize()) {
                                    when (currentScreen) {
                                        AppNavScreen.HISTORY -> MangaShelfScreen(
                                            kind = ShelfKind.HISTORY,
                                            libraryStore = libraryStore,
                                            downloader = downloader,
                                            onOpenManga = { manga -> activeMangaForDetail = manga },
                                            onExploreClick = { currentScreen = AppNavScreen.EXPLORE },
                                            refreshKey = shelfRefresh
                                        )

                                        AppNavScreen.FAVOURITES -> MangaShelfScreen(
                                            kind = ShelfKind.FAVOURITES,
                                            libraryStore = libraryStore,
                                            downloader = downloader,
                                            onOpenManga = { manga -> activeMangaForDetail = manga },
                                            onExploreClick = { currentScreen = AppNavScreen.EXPLORE },
                                            refreshKey = shelfRefresh
                                        )

                                        AppNavScreen.DOWNLOADS -> MangaShelfScreen(
                                            kind = ShelfKind.DOWNLOADS,
                                            libraryStore = libraryStore,
                                            downloader = downloader,
                                            onOpenManga = { manga -> activeMangaForDetail = manga },
                                            onExploreClick = { currentScreen = AppNavScreen.EXPLORE },
                                            refreshKey = shelfRefresh
                                        )

                                        AppNavScreen.EXPLORE -> ExploreScreen(
                                            sourceRegistry = sourceRegistry,
                                            searchQuery = searchQuery,
                                            onSearchChange = { searchQuery = it },
                                            onOpenManga = { manga -> activeMangaForDetail = manga },
                                            onNavigateBookmarks = { currentScreen = AppNavScreen.FAVOURITES },
                                            onNavigateDownloads = { currentScreen = AppNavScreen.DOWNLOADS },
                                            onNavigateLocal = { currentScreen = AppNavScreen.HISTORY }
                                        )

                                        AppNavScreen.FEED -> FeedScreen(
                                            libraryStore = libraryStore,
                                            sourceRegistry = sourceRegistry,
                                            onOpenManga = { manga -> activeMangaForDetail = manga },
                                            onReadChapter = { manga, chapter -> openChapter(manga, chapter, 0) },
                                            onExploreClick = { currentScreen = AppNavScreen.EXPLORE }
                                        )

                                        AppNavScreen.SETTINGS -> SettingsScreen(
                                            settings = settings,
                                            onSettingsChange = {
                                                settings = it
                                                settingsStore.save(it)
                                            },
                                            onClearCache = clearCache,
                                            onClearHistory = {
                                                libraryStore.clearHistory()
                                                shelfRefresh++
                                            }
                                        )
                                    }

                                    // Prominent Floating Continue Reading Button at Bottom-Right
                                    if (latestReadPair != null && currentScreen != AppNavScreen.SETTINGS) {
                                        val (manga, progress) = latestReadPair!!
                                        FloatingContinueReadingButton(
                                            manga = manga,
                                            progress = progress,
                                            onClick = ::resumeLastRead,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(end = 24.dp, bottom = 24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fast Startup Splash Loading Screen
                    AnimatedVisibility(
                        visible = !isAppReady,
                        enter = fadeIn(),
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        StartupSplashScreen(
                            progress = startupProgress,
                            status = startupStatus,
                            appIcon = appIcon
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupSplashScreen(
    progress: Float,
    status: String,
    appIcon: BitmapPainter,
) {
    val tokens = LocalGlassTokens.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                color = tokens.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .size(92.dp)
                    .border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .shadow(12.dp, RoundedCornerShape(24.dp), spotColor = tokens.primary)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Image(
                        painter = appIcon,
                        contentDescription = "ZERAVRIX",
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "ZERAVRIX",
                color = tokens.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Native Windows Edition",
                color = tokens.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.height(28.dp))

            // Smooth Progress Bar
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(tokens.surfaceContainerHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(tokens.primary)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = status,
                color = tokens.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FloatingContinueReadingButton(
    manga: Manga,
    progress: ReadingProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = androidx.compose.animation.core.tween(100)
    )

    Surface(
        color = tokens.primary,
        contentColor = tokens.onPrimary,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (hovered) 12.dp else 6.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Continue Reading",
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Continue: ${manga.title}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp)
                )
                val chapterNumber = progress.chapterNumber
                val pageText = if (progress.pageIndex > 0) "Page ${progress.pageIndex + 1}" else ""
                val chapterText = if (chapterNumber != null) "Chapter ${numberText(chapterNumber)}" else ""
                val subText = listOf(chapterText, pageText).filter { it.isNotBlank() }.joinToString(" • ")
                if (subText.isNotBlank()) {
                    Text(
                        text = subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

private fun numberText(value: Float?): String {
    if (value == null) return ""
    return if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
