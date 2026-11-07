package com.zeravrix.zeravrix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.theme.createGlassTokens
import com.zeravrix.zeravrix.theme.createMaterialColorScheme
import com.zeravrix.zeravrix.ui.components.AppNavScreen
import com.zeravrix.zeravrix.ui.components.AppNavigationRail
import com.zeravrix.zeravrix.ui.components.MangaDetailDialog
import com.zeravrix.zeravrix.ui.components.WindowChrome
import com.zeravrix.zeravrix.ui.reader.DesktopReaderView
import com.zeravrix.zeravrix.ui.screens.ExploreScreen
import com.zeravrix.zeravrix.ui.screens.LibraryScreen
import com.zeravrix.zeravrix.ui.screens.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    val settingsStore = remember { SettingsStore(appDir) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    val libraryStore = remember { LibraryStore(appDir) }
    val sourceRegistry = remember { LiveSourceRegistry(appDir) }

    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(AppNavScreen.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMangaForDetail by remember { mutableStateOf<Manga?>(null) }
    var activeReader by remember { mutableStateOf<ActiveReaderSession?>(null) }

    val appIcon = remember { loadAppIconPainter() }
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = DpSize(1280.dp, 820.dp)
    )

    val tokens = remember(settings.colorTheme, settings.theme, settings.isAmoled) {
        createGlassTokens(settings.colorTheme, settings.theme, settings.isAmoled)
    }

    val isRtl = settings.language == AppLanguage.ARABIC

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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tokens.background)
                        .border(1.dp, tokens.borderGlass, outerCorner)
                        .clip(outerCorner)
                ) {
                    // Window Top Chrome Bar
                    WindowChrome(
                        windowState = windowState,
                        currentScreenTitle = currentScreen.title,
                        searchQuery = searchQuery,
                        onSearchClick = { currentScreen = AppNavScreen.EXPLORE },
                        onClose = ::exitApplication,
                        readerActive = activeReader != null,
                        onReaderToggle = { /* focus reader */ }
                    )

                    // Main App Workspace or Reader
                    if (activeReader != null) {
                        val session = activeReader!!
                        DesktopReaderView(
                            mangaTitle = session.manga.title,
                            chapterTitle = session.chapter.title,
                            pages = session.pages,
                            initialPage = session.initialPage,
                            initialMode = settings.readerMode,
                            onClose = { activeReader = null },
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
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Left Navigation Rail
                            AppNavigationRail(
                                currentScreen = currentScreen,
                                onScreenSelect = { screen -> currentScreen = screen }
                            )

                            // Screen Content View
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (currentScreen) {
                                    AppNavScreen.LIBRARY -> LibraryScreen(
                                        libraryStore = libraryStore,
                                        onOpenManga = { manga -> activeMangaForDetail = manga },
                                        onResumeReading = { manga, progress ->
                                            scope.launch {
                                                val chapters = sourceRegistry.fetchChapters(manga)
                                                val targetChapter = chapters.firstOrNull { it.id == progress.chapterId }
                                                    ?: chapters.firstOrNull()
                                                if (targetChapter != null) {
                                                    val pages = sourceRegistry.fetchPages(targetChapter)
                                                    if (pages.isNotEmpty()) {
                                                        activeReader = ActiveReaderSession(
                                                            manga = manga,
                                                            chapter = targetChapter,
                                                            pages = pages,
                                                            initialPage = progress.pageIndex
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        onExploreClick = { currentScreen = AppNavScreen.EXPLORE }
                                    )

                                    AppNavScreen.EXPLORE -> ExploreScreen(
                                        sourceRegistry = sourceRegistry,
                                        searchQuery = searchQuery,
                                        onSearchChange = { searchQuery = it },
                                        onOpenManga = { manga -> activeMangaForDetail = manga }
                                    )

                                    AppNavScreen.LATEST -> ExploreScreen(
                                        sourceRegistry = sourceRegistry,
                                        searchQuery = "",
                                        onSearchChange = {},
                                        onOpenManga = { manga -> activeMangaForDetail = manga }
                                    )

                                    AppNavScreen.DOWNLOADS -> LibraryScreen(
                                        libraryStore = libraryStore,
                                        onOpenManga = { manga -> activeMangaForDetail = manga },
                                        onResumeReading = { _, _ -> },
                                        onExploreClick = { currentScreen = AppNavScreen.EXPLORE }
                                    )

                                    AppNavScreen.MUSIC -> SettingsScreen(
                                        settings = settings,
                                        onSettingsChange = {
                                            settings = it
                                            settingsStore.save(it)
                                        },
                                        onClearCache = {
                                            runCatching {
                                                val cacheDir = appDir.resolve("cache")
                                                if (Files.exists(cacheDir)) {
                                                    Files.walk(cacheDir)
                                                        .sorted(Comparator.reverseOrder())
                                                        .forEach { runCatching { Files.delete(it) } }
                                                }
                                            }
                                        }
                                    )

                                    AppNavScreen.SETTINGS -> SettingsScreen(
                                        settings = settings,
                                        onSettingsChange = {
                                            settings = it
                                            settingsStore.save(it)
                                        },
                                        onClearCache = {
                                            runCatching {
                                                val cacheDir = appDir.resolve("cache")
                                                if (Files.exists(cacheDir)) {
                                                    Files.walk(cacheDir)
                                                        .sorted(Comparator.reverseOrder())
                                                        .forEach { runCatching { Files.delete(it) } }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Manga Detail & Chapter Select Dialog
                if (activeMangaForDetail != null) {
                    val manga = activeMangaForDetail!!
                    MangaDetailDialog(
                        manga = manga,
                        sourceRegistry = sourceRegistry,
                        libraryStore = libraryStore,
                        onDismiss = { activeMangaForDetail = null },
                        onReadChapter = { chapter ->
                            scope.launch {
                                val pages = sourceRegistry.fetchPages(chapter)
                                if (pages.isNotEmpty()) {
                                    activeMangaForDetail = null
                                    activeReader = ActiveReaderSession(
                                        manga = manga,
                                        chapter = chapter,
                                        pages = pages,
                                        initialPage = 0
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
