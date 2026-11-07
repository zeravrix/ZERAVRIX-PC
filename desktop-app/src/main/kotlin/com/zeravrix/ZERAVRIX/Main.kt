package com.zeravrix.zeravrix

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.application
import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.reader.ReaderContent
import com.zeravrix.zeravrix.reader.ReaderLanguage
import com.zeravrix.zeravrix.source.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.nio.file.Path
import java.util.Locale
import java.util.Collections
import java.util.LinkedHashMap
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent
import java.awt.BorderLayout
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView

private enum class Screen { LIBRARY, NEW, SEARCH, DOWNLOADS, MUSIC, SOURCES, EXTENSION_STORES, SETTINGS }
private enum class LibraryFilter { ALL, ON_DEVICE, NEW_CHAPTERS, COMPLETED }

private fun isNsfwSource(source: Source): Boolean {
    val value = "${source.name} ${source.baseUrl}".lowercase(Locale.ROOT)
    return listOf("hentai", "nsfw", "adult", "18+").any(value::contains)
}

private fun isNsfwManga(manga: Manga, manager: SourceManager): Boolean {
    val metadata = "${manga.title} ${manga.description} ${manga.genres.joinToString(" ")} ${manga.status.orEmpty()}".lowercase(Locale.ROOT)
    return isNsfwSource(manager.get(manga.sourceId) ?: return listOf("hentai", "nsfw", "adult", "18+").any(metadata::contains)) ||
        listOf("hentai", "nsfw", "adult", "18+").any(metadata::contains)
}
private data class StoreRowState(val store: ExtensionStore, val message: String, val artifacts: List<ExtensionArtifact> = emptyList())
private data class ReaderProgressTarget(val mangaId: String, val chapterId: String, val chapterNumber: Float?)

private val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }
private val LocalCustomIconPath = staticCompositionLocalOf<String?> { null }

private val PcBackground = Color(0xFF080B12)
private val PcSurface = Color(0xDD0D1322)
private val PcSurfaceRaised = Color(0xEE131A2B)
private val PcSurfaceSoft = Color(0xF0182238)
private val PcText = Color(0xFFF1F4F9)
private val PcMuted = Color(0xFF8E9BB0)
private val PcBlue = Color(0xFF00D2FF)
private val PcBlueStrong = Color(0xFF1D72FE)
private val PcBorder = Color(0x3300D2FF)
private val PcDanger = Color(0xFFFF4D6D)
private val LightBackground = Color(0xFFF7F9FF)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceRaised = Color(0xFFEAF1FF)
private val LightText = Color(0xFF17243A)
private val LightMuted = Color(0xFF5C6B83)
private val LightBlue = Color(0xFF1769E0)
private const val APP_CONTENT_SCOPE = "Manga • Manhua • Manhwa"
private const val DISCORD_SUPPORT_URL = "https://discord.gg/YrrmFNCvXx"
private const val MIHONYOMI_STORE_URL = "https://raw.githubusercontent.com/kitsumed/mihonyomi-extensions/releases/index.json"
private const val KAVITA_STORE_URL = "https://raw.githubusercontent.com/Kareadita/tach-extensions/repo/index.json"
private const val KEIYOUSHI_STORE_URL = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json"
private const val LEGACY_MIHONYOMI_STORE_URL = "https://raw.githubusercontent.com/kitsumed/mihonyomi-extensions/releases/repo.json"
private const val LEGACY_KAVITA_STORE_URL = "https://raw.githubusercontent.com/Kareadita/tach-extensions/repo/repo.json"
private const val LEGACY_KEIYOUSHI_STORE_URL = "https://github.com/keiyoushi/extensions/raw/repo/index.pb"

private val NativeAdaptedArtifactNames = setOf("Mihonyomi Extension: Komga", "Tachiyomi: Kavita", "Mangalek", "MangaSpark", "Arabs Hentai", "Team X", "MangaSwat", "Hijala", "Dilar", "Azora")

private fun isNativeAdaptedArtifact(artifact: ExtensionArtifact): Boolean =
    artifact.name in NativeAdaptedArtifactNames || NativeAdaptedArtifactNames.any { name -> artifact.name.contains(name, ignoreCase = true) }

private val DefaultExtensionStores = listOf(
    ExtensionStore("mihonyomi", MIHONYOMI_STORE_URL, "Mihonyomi Extensions", "Mihonyomi", "https://github.com/kitsumed/mihonyomi-extensions", "3ca8159ccd33c704eff9de5f5841516a18e21ccec5a99c6c2b7b4d55014c81f3"),
    ExtensionStore("kavita", KAVITA_STORE_URL, "Kavita Extension Repo", "Kavita", "https://www.kavitareader.com/", "5887aee6597996821a1f05ec0d88cac45ec2847143278ac6b1efc657bfad6be0"),
    ExtensionStore("keiyoushi", KEIYOUSHI_STORE_URL, "Keiyoushi Extensions", "Keiyoushi", "https://github.com/keiyoushi/extensions", ""),
)
private val CoverClient: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
}
private val CoverBytesCache: MutableMap<String, ByteArray> = Collections.synchronizedMap(object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > 64
})
private val PersistentCoverCacheDir = Path.of(System.getProperty("user.home"), ".zeravrix", "cache", "covers")
private fun persistentCoverPath(url: String): Path = PersistentCoverCacheDir.resolve(
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ".img",
)

private suspend fun cacheCoverBytes(key: String): ByteArray? {
    if (key.isBlank()) return null
    CoverBytesCache[key]?.let { return it }
    val diskPath = persistentCoverPath(key)
    val diskBytes = withContext(Dispatchers.IO) {
        runCatching { if (Files.isRegularFile(diskPath)) Files.readAllBytes(diskPath) else null }.getOrNull()
    }
    if (diskBytes != null && diskBytes.isNotEmpty()) {
        CoverBytesCache[key] = diskBytes
        return diskBytes
    }
    val loaded = withContext(Dispatchers.IO) {
        try {
            when {
                key.startsWith("file:", true) -> Files.readAllBytes(Path.of(URI(key)))
                else -> CoverClient.send(
                    HttpRequest.newBuilder(URI(key))
                        .header("User-Agent", "ZERAVRIX/1.1 (Windows; Desktop)")
                        .header("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.95,*/*;q=0.6")
                        .GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                ).takeIf { it.statusCode() in 200..299 }?.body()
            }
        } catch (_: Exception) {
            null
        }
    }?.takeIf { it.isNotEmpty() && it.size <= 12 * 1024 * 1024 }
    loaded?.also {
        CoverBytesCache[key] = it
        withContext(Dispatchers.IO) {
            runCatching {
                Files.createDirectories(PersistentCoverCacheDir)
                Files.write(diskPath, it)
            }
        }
    }
    return loaded
}

private fun normalizedSearch(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private fun searchRank(title: String, query: String): Int {
    val a = normalizedSearch(title)
    val b = normalizedSearch(query)
    if (a == b) return 0
    if (a.startsWith(b)) return 1
    if (a.contains(b)) return 2
    if (b.isEmpty()) return 9999
    val previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
        for (j in current.indices) previous[j] = current[j]
    }
    return 10 + previous[b.length]
}

private fun Chapter.displayNumber(): Float = number ?: Regex("(?:chapter|ch|الفصل|فصل)?\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    .find(title)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: Float.POSITIVE_INFINITY

private fun List<Chapter>.orderedForDisplay(): List<Chapter> = sortedWith(compareBy<Chapter> { it.displayNumber() }.thenBy { it.title.lowercase(Locale.ROOT) })

private fun Chapter.uploadDateLabel(): String? = uploadDate?.takeIf { it > 0L }?.let { timestamp ->
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp)) }.getOrNull()
}

private fun List<Chapter>.newestChapter(): Chapter? = maxWithOrNull(
    compareBy<Chapter> { it.uploadDate ?: Long.MIN_VALUE }.thenBy { it.displayNumber() },
)

private fun chapterNumberLabel(number: Float?): String = number?.let { value ->
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
} ?: "?"

private fun LibraryStore.hasUnreadChapter(manga: Manga): Boolean {
    val latest = cachedChapters(manga.id).newestChapter() ?: return false
    val saved = latestProgress(manga.id)
    return saved == null || saved.chapterId != latest.id
}

private val PcLightScheme = lightColorScheme(
    primary = LightBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF40689E),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceRaised,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = LightMuted,
    error = Color(0xFFB3261E),
)

private val PcSpiderScheme = darkColorScheme(
    primary = Color(0xFF56D8FF),
    onPrimary = Color(0xFF03131F),
    secondary = Color(0xFFFF4E73),
    background = Color(0xFF060A16),
    surface = Color(0xFF0D1223),
    surfaceVariant = Color(0xFF17152C),
    onBackground = Color(0xFFF5F7FF),
    onSurface = Color(0xFFF5F7FF),
    onSurfaceVariant = Color(0xFFA9B8D4),
    error = Color(0xFFFF6B81),
)

private val PcTitanScheme = darkColorScheme(
    primary = Color(0xFFF4C542),
    onPrimary = Color(0xFF191A1E),
    secondary = Color(0xFFFFD866),
    onSecondary = Color(0xFF191A1E),
    background = Color(0xFF101216),
    surface = Color(0xFF191C22),
    surfaceVariant = Color(0xFF2A3038),
    onBackground = Color(0xFFF4F1E6),
    onSurface = Color(0xFFF4F1E6),
    onSurfaceVariant = Color(0xFFB9B8AF),
    error = Color(0xFFD13D35),
)

private val PcAlucardScheme = darkColorScheme(
    primary = Color(0xFFE21B23),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B6B),
    onSecondary = Color.White,
    background = Color(0xFF070204),
    surface = Color(0xFF130508),
    surfaceVariant = Color(0xFF2B0B12),
    onBackground = Color(0xFFFFF1F1),
    onSurface = Color(0xFFFFF1F1),
    onSurfaceVariant = Color(0xFFD9A7AA),
    error = Color(0xFFB31217),
    errorContainer = Color(0xFF4A0B12),
    onErrorContainer = Color(0xFFFFDAD9),
)

private val PcDarkScheme = darkColorScheme(
    primary = PcBlue,
    onPrimary = Color(0xFF10213F),
    secondary = Color(0xFFB8C9E8),
    background = PcBackground,
    surface = PcSurface,
    surfaceVariant = PcSurfaceRaised,
    onBackground = PcText,
    onSurface = PcText,
    onSurfaceVariant = PcMuted,
    error = PcDanger,
)

private fun setupCrashLogging() {
    val appDir = Path.of(System.getProperty("user.home"), ".zeravrix")
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

fun main() = application {
    setupCrashLogging()
    val appDir = Path.of(System.getProperty("user.home"), ".zeravrix")
    Files.createDirectories(appDir.resolve("sources"))
    Files.createDirectories(appDir.resolve("cache"))
    val library = remember { LibraryStore(appDir) }
    val repositorySourceStore = remember { RepositorySourceStore(appDir) }
    val sourceManager = remember {
        val builtIns = listOf(
            MangaSparkMihonSource(),
            MangalekMihonSource(),
            TeamXMihonSource(),
            MangaSwatMihonSource(),
            ArabsHentaiMihonSource(),
            HijalaMihonSource(appDir),
            DilarMihonSource(),
            AzoraMihonSource(),
            KomgaSource(appDir),
            KavitaSource(appDir),
        )
        val repositorySources = repositorySourceStore.load().map(::RepositoryWebSource)
        SourceManager(builtIns + repositorySources)
            .also { runCatching { it.loadExtensions(appDir.resolve("sources")) } }
    }
    val downloader = remember { DesktopDownloader(appDir) }
    val musicStore = remember { MusicLibraryStore(appDir) }
    val musicPlayer = remember { LocalMusicPlayer() }
    var musicTracks by remember { mutableStateOf(musicStore.tracks()) }
    var musicPlaylists by remember { mutableStateOf(musicStore.playlists()) }
    var currentMusic by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedPlaylistName by remember { mutableStateOf<String?>(null) }
    var loopMusic by remember { mutableStateOf(false) }
    var shuffleMusic by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { musicPlayer.stop() } }
    val selectedPlaylistTracks = musicPlaylists.firstOrNull { it.name == selectedPlaylistName }?.trackIds?.mapNotNull { id -> musicTracks.firstOrNull { it.id == id } }
    val readerMusicTracks = selectedPlaylistTracks ?: musicTracks
    lateinit var playTrack: (MusicTrack) -> Unit
    playTrack = { track ->
        currentMusic = track
        musicPlayer.play(track) {
            val pool = readerMusicTracks
            val position = pool.indexOfFirst { it.id == track.id }
            val next = when {
                pool.isEmpty() -> null
                loopMusic -> track
                shuffleMusic -> pool.randomOrNull()
                else -> pool.getOrNull((position + 1).coerceAtMost(pool.lastIndex))
            }
            if (next != null && (loopMusic || next.id != track.id || pool.size > 1)) playTrack(next) else currentMusic = null
        }
    }
    fun playAdjacentMusic(delta: Int) {
        val pool = readerMusicTracks
        if (pool.isEmpty()) return
        val currentIndex = currentMusic?.let { current -> pool.indexOfFirst { it.id == current.id } } ?: -1
        val targetIndex = if (currentIndex < 0) {
            if (delta > 0) 0 else pool.lastIndex
        } else (currentIndex + delta + pool.size) % pool.size
        pool.getOrNull(targetIndex)?.let(playTrack)
    }
    val settingsStore = remember { SettingsStore(appDir) }
    val extensionStoreStore = remember { ExtensionStoreStore(appDir) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var extensionStores by remember {
        val loaded = extensionStoreStore.load()
        val initial = (if (loaded.isEmpty()) DefaultExtensionStores else loaded).map { store ->
            when (store.url) {
                LEGACY_MIHONYOMI_STORE_URL -> store.copy(url = MIHONYOMI_STORE_URL)
                LEGACY_KAVITA_STORE_URL -> store.copy(url = KAVITA_STORE_URL)
                LEGACY_KEIYOUSHI_STORE_URL -> store.copy(url = KEIYOUSHI_STORE_URL)
                else -> store
            }
        }
        extensionStoreStore.save(initial)
        mutableStateOf(initial)
    }
    var screen by remember { mutableStateOf(Screen.LIBRARY) }
    var globalQuery by remember { mutableStateOf("") }
    var readerPages by remember { mutableStateOf<List<MangaPage>>(emptyList()) }
    var readerTitle by remember { mutableStateOf("ZERAVRIX Reader") }
    var readerOpen by remember { mutableStateOf(false) }
    var readerResumePageIndex by remember { mutableIntStateOf(0) }
    var readerTabToggleSignal by remember { mutableIntStateOf(0) }
    var readerNextLoader by remember { mutableStateOf<(suspend () -> List<MangaPage>)?>(null) }
    var readerProgressTarget by remember { mutableStateOf<ReaderProgressTarget?>(null) }
    fun setReaderProgressTarget(readerTitle: String) {
        val parts = readerTitle.split(" • ", limit = 2)
        val mangaTitle = parts.firstOrNull().orEmpty()
        val chapterTitle = parts.getOrNull(1).orEmpty()
        val localManga = library.list().firstOrNull { it.title == mangaTitle }
        val cachedChapter = localManga?.let { library.cachedChapters(it.id).firstOrNull { chapter -> chapter.title == chapterTitle } }
        val downloadedChapter = downloader.downloadedChapters().firstOrNull { it.mangaTitle == mangaTitle && it.chapterTitle == chapterTitle }
        readerProgressTarget = when {
            localManga != null && cachedChapter != null -> ReaderProgressTarget(localManga.id, cachedChapter.id, cachedChapter.number)
            downloadedChapter != null -> ReaderProgressTarget(downloadedChapter.mangaId, downloadedChapter.chapterId, downloadedChapter.number)
            else -> null
        }
    }
    var mainWindowVisible by remember { mutableStateOf(true) }
    var startupVisible by remember { mutableStateOf(true) }
    LaunchedEffect(startupVisible, settings.artworkTheme == ArtworkTheme.SPIDER_VERSE) {
        if (startupVisible) {
            delay(if (settings.artworkTheme == ArtworkTheme.SPIDER_VERSE) 1500L else 1100L)
            startupVisible = false
        }
    }
    val mainWindowState = rememberWindowState(width = 1440.dp, height = 900.dp, placement = WindowPlacement.Maximized)
    val trayState = rememberTrayState()
    val appIconPainter = remember(settings.customIconPath) { loadAppIconPainter(settings.customIconPath) }
    val trayIcon = remember(appIconPainter) { appIconPainter }

    val refreshSources: () -> Unit = {
        runCatching { sourceManager.loadExtensions(appDir.resolve("sources")) }
        val updated = settings.copy(lastRefreshEpochMillis = System.currentTimeMillis())
        settings = updated
        settingsStore.save(updated)
    }
    LaunchedEffect(settings.refreshIntervalHours, settings.refreshOnOpen) {
        suspend fun refreshIfDue(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val due = now - settings.lastRefreshEpochMillis >= settings.refreshIntervalHours * 3_600_000L
            if (force || (settings.refreshOnOpen && due)) {
                withContext(Dispatchers.IO) { runCatching { sourceManager.loadExtensions(appDir.resolve("sources")) } }
                val updated = settings.copy(lastRefreshEpochMillis = now)
                settings = updated
                settingsStore.save(updated)
            }
        }
        refreshIfDue()
        while (isActive) {
            delay(settings.refreshIntervalHours * 3_600_000L)
            refreshIfDue(force = true)
        }
    }
    LaunchedEffect(settings.backupIntervalDays) {
        val backupManager = BackupManager(appDir)
        while (isActive) {
            val now = System.currentTimeMillis()
            val due = now - settings.lastBackupEpochMillis >= settings.backupIntervalDays * 86_400_000L
            if (due) {
                val stamp = java.time.LocalDateTime.now().toString().replace(":", "-")
                val target = appDir.resolve("backups").resolve("zeravrix-$stamp.zip")
                runCatching { withContext(Dispatchers.IO) { backupManager.exportBackup(target) } }
                    .onSuccess {
                        val updated = settings.copy(lastBackupEpochMillis = now)
                        settings = updated
                        settingsStore.save(updated)
                    }
            }
            delay(86_400_000L)
        }
    }

    if (settings.minimizeToTray && java.awt.SystemTray.isSupported() && trayIcon != null) {
        Tray(
            icon = trayIcon,
            state = trayState,
            tooltip = "ZERAVRIX",
            onAction = { mainWindowVisible = true },
            menu = {
                Item("Show ZERAVRIX", onClick = { mainWindowVisible = true })
                Item("Refresh sources", onClick = refreshSources)
                Separator()
                Item("Exit", onClick = ::exitApplication)
            },
        )
    }
    Window(
        visible = mainWindowVisible,
        onCloseRequest = {
            if (readerOpen) {
                readerOpen = false
                readerNextLoader = null
            } else if (settings.minimizeToTray) {
                mainWindowVisible = false
            } else {
                exitApplication()
            }
        },
        title = if (readerOpen) "ZERAVRIX Reader • $readerTitle" else "ZERAVRIX • $APP_CONTENT_SCOPE",
        state = mainWindowState,
        icon = appIconPainter,
        undecorated = true,
        resizable = true,
    ) {
        MaterialTheme(colorScheme = when {
            settings.artworkTheme == ArtworkTheme.SPIDER_VERSE -> PcSpiderScheme
            settings.artworkTheme == ArtworkTheme.ALUCARD -> PcAlucardScheme
            settings.artworkTheme == ArtworkTheme.ATTACK_ON_TITAN -> PcTitanScheme
            settings.theme == AppTheme.LIGHT -> PcLightScheme
            else -> PcDarkScheme
        }) {
            CompositionLocalProvider(
                LocalAppLanguage provides settings.language,
                LocalCustomIconPath provides settings.customIconPath,
                LocalLayoutDirection provides if (settings.language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Crossfade(targetState = startupVisible, animationSpec = tween(700), label = "startup-transition") { showingSplash ->
                    if (showingSplash) {
                        StartupSplash(artworkTheme = settings.artworkTheme, animations = settings.animationLevel)
                    } else Column(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, when (settings.artworkTheme) {
                                ArtworkTheme.SPIDER_VERSE -> Color(0xFFFF4E73).copy(alpha = .72f)
                                ArtworkTheme.ALUCARD -> Color(0xFFFF3B30).copy(alpha = .78f)
                                ArtworkTheme.ATTACK_ON_TITAN -> Color(0xFFFFC107).copy(alpha = .82f)
                                ArtworkTheme.NONE -> Color(0x3300D2FF)
                            }, if (mainWindowState.placement == WindowPlacement.Maximized) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp))
                            .clip(if (mainWindowState.placement == WindowPlacement.Maximized) RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp))
                    ) {
                    BrandedWindowChrome(
                        title = if (readerOpen) "Reader • $readerTitle" else "ZERAVRIX",
                        artworkTheme = settings.artworkTheme,
                        windowState = mainWindowState,
                        showReaderTab = readerOpen,
                        onReaderTab = { readerTabToggleSignal++ },
                        onClose = {
                            if (readerOpen) {
                                readerOpen = false
                                readerNextLoader = null
                            } else if (settings.minimizeToTray) {
                                mainWindowVisible = false
                            } else {
                                exitApplication()
                            }
                        },
                    )
                    Box(Modifier.fillMaxSize()) {
                if (readerOpen) {
                    ReaderContent(
                        title = readerTitle,
                        pages = readerPages,
                        cacheRoot = appDir.resolve("cache/pages"),
                        onClose = { readerOpen = false; readerNextLoader = null },
                        hostWindowState = mainWindowState,
                        language = when (settings.language) {
                            AppLanguage.ENGLISH -> ReaderLanguage.ENGLISH
                            AppLanguage.ARABIC -> ReaderLanguage.ARABIC
                            AppLanguage.FRENCH -> ReaderLanguage.FRENCH
                        },
                        initialLayout = settings.readerMode.toPageLayout(),
                        initialPageIndex = readerResumePageIndex,
                        nextChapterLoader = readerNextLoader,
                        toggleControlsSignal = readerTabToggleSignal,
                        onPageProgress = { pageIndex -> readerProgressTarget?.let { target -> library.saveProgress(ReadingProgress(target.mangaId, target.chapterId, pageIndex, target.chapterNumber, System.currentTimeMillis())) } },
                        musicTracks = readerMusicTracks,
                        currentMusic = currentMusic,
                        onSelectMusic = { track -> if (track == null) { musicPlayer.stop(); currentMusic = null } else playTrack(track) },
                        loopMusic = loopMusic,
                        shuffleMusic = shuffleMusic,
                        onToggleLoop = { loopMusic = !loopMusic },
                        onToggleShuffle = { shuffleMusic = !shuffleMusic },
                        musicPlaylists = musicPlaylists,
                        musicPlaylistName = selectedPlaylistName,
                        onSelectMusicPlaylist = { selectedPlaylistName = it },
                        onMusicPrevious = { playAdjacentMusic(-1) },
                        onMusicNext = { playAdjacentMusic(1) },
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(if (settings.theme == AppTheme.LIGHT) LightBackground else PcBackground)) {
                        ArtworkThemeBackdrop(settings.artworkTheme, settings.animationLevel)
                        Row(Modifier.fillMaxSize()) {
                        PcNavigationRail(screen, settings.language, settings.artworkTheme) { screen = it }
                        Column(Modifier.fillMaxSize()) {
                            TopCommandBar(
                                language = settings.language,
                                query = globalQuery,
                                onQueryChange = { globalQuery = it },
                                onSearch = { screen = Screen.SEARCH },
                                onRefresh = refreshSources,
                            )
                            Box(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 18.dp)) {
                                when (screen) {
                                    Screen.LIBRARY ->                                         LibraryScreen(
                                        store = library,
                                        manager = sourceManager,
                                        downloader = downloader,
                                        nsfwEnabled = settings.nsfwEnabled,
                                        onSearchManga = { title -> globalQuery = title; screen = Screen.SEARCH },
                                        onRead = { title, pages, nextLoader ->
                                            readerTitle = title
                                            readerPages = pages
                                            readerResumePageIndex = 0
                                            setReaderProgressTarget(title)
                                            readerNextLoader = nextLoader
                                            readerOpen = true
                                        },
                                        onResume = { title, pages, pageIndex, nextLoader ->
                                            readerTitle = title
                                            readerPages = pages
                                            readerResumePageIndex = pageIndex
                                            setReaderProgressTarget(title)
                                            readerNextLoader = nextLoader
                                            readerOpen = true
                                        },
                                    )
                                    Screen.NEW -> NewChaptersScreen(library, sourceManager, downloader, settings.nsfwEnabled) { title, pages, nextLoader ->
                                        readerTitle = title
                                        readerPages = pages
                                        readerResumePageIndex = 0
                                        setReaderProgressTarget(title)
                                        readerNextLoader = nextLoader
                                        readerOpen = true
                                    }
                                    Screen.SEARCH -> SearchScreen(
                                        manager = sourceManager,
                                        store = library,
                                        downloader = downloader,
                                        query = globalQuery,
                                        nsfwEnabled = settings.nsfwEnabled,
                                        onQueryChange = { globalQuery = it },
                                    ) { title, pages, nextLoader ->
                                        readerTitle = title
                                        readerPages = pages
                                        readerResumePageIndex = 0
                                        setReaderProgressTarget(title)
                                        readerNextLoader = nextLoader
                                        readerOpen = true
                                    }
                                    Screen.DOWNLOADS -> DownloadsScreen(library, downloader, sourceManager, settings.nsfwEnabled) { title, pages, pageIndex ->
                                        readerTitle = title
                                        readerPages = pages
                                        readerResumePageIndex = pageIndex
                                        setReaderProgressTarget(title)
                                        readerNextLoader = null
                                        readerOpen = true
                                    }
                                    Screen.MUSIC -> MyMusicScreen(musicStore, musicTracks, musicPlaylists, currentMusic, selectedPlaylistName, loopMusic, shuffleMusic, onRefresh = { musicTracks = musicStore.tracks(); musicPlaylists = musicStore.playlists() }, onSelectPlaylist = { selectedPlaylistName = it }, onPlay = { track -> playTrack(track) }, onStop = { musicPlayer.stop(); currentMusic = null }, onLoop = { loopMusic = !loopMusic }, onShuffle = { shuffleMusic = !shuffleMusic })
                                    Screen.SOURCES -> SourcesScreen(sourceManager, appDir.resolve("sources"), settings.nsfwEnabled, settings.language)
                                    Screen.EXTENSION_STORES -> ExtensionStoresScreen(extensionStores, extensionStoreStore, repositorySourceStore, sourceManager, appDir.resolve("sources"), settings.language) { extensionStores = it }
                                    Screen.SETTINGS -> SettingsScreen(appDir, settings, onOpenMusic = { screen = Screen.MUSIC }, onRestore = {
                                        settings = settingsStore.load()
                                        extensionStores = extensionStoreStore.load().ifEmpty { DefaultExtensionStores }.map { store ->
                                        when (store.url) {
                                            LEGACY_MIHONYOMI_STORE_URL -> store.copy(url = MIHONYOMI_STORE_URL)
                                            LEGACY_KAVITA_STORE_URL -> store.copy(url = KAVITA_STORE_URL)
                                            LEGACY_KEIYOUSHI_STORE_URL -> store.copy(url = KEIYOUSHI_STORE_URL)
                                            else -> store
                                        }
                                    }
                                    }) { updated ->
                                        val themeChanged = updated.artworkTheme != settings.artworkTheme
                                        settings = updated
                                        settingsStore.save(updated)
                                        if (themeChanged) startupVisible = true
                                    }
                                }
                            }
                        }
                    }
                        }
                    }
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkThemeBackdrop(theme: ArtworkTheme, animations: AnimationLevel) {
    if (theme == ArtworkTheme.NONE) return
    val resource = when (theme) {
        ArtworkTheme.ALUCARD -> "/themes/alucard.jpg"
        ArtworkTheme.ATTACK_ON_TITAN -> "/themes/attack-on-titan.jpg"
        else -> "/themes/spiderverse.webp"
    }
    val painter = remember(theme) {
        runCatching { object {}.javaClass.getResourceAsStream(resource)?.readBytes()?.let { BitmapPainter(SkiaImage.makeFromEncoded(it).toComposeImageBitmap()) } }.getOrNull()
    }
    val transition = if (animations == AnimationLevel.SMOOTH) androidx.compose.animation.core.rememberInfiniteTransition(label = "theme-background") else null
    val drift by if (transition != null) transition.animateFloat(-0.025f, 0.025f, androidx.compose.animation.core.infiniteRepeatable(tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "theme-drift") else remember { mutableStateOf(0f) }
    painter?.let { image ->
        androidx.compose.foundation.Image(image, "Artwork theme background", Modifier.fillMaxSize().graphicsLayer(scaleX = 1.04f + drift, scaleY = 1.04f + drift), contentScale = ContentScale.Crop, alpha = if (theme == ArtworkTheme.ALUCARD) .20f else .18f)
        Box(Modifier.fillMaxSize().background(when (theme) {
            ArtworkTheme.ALUCARD -> Color(0xCC100507)
            ArtworkTheme.ATTACK_ON_TITAN -> Color(0xCC11100B)
            else -> Color(0xCC050816)
        }))
        if (animations == AnimationLevel.SMOOTH) {
            Box(Modifier.fillMaxSize().background(when (theme) {
                ArtworkTheme.ALUCARD -> Brush.radialGradient(listOf(Color(0x44FF2015), Color.Transparent))
                ArtworkTheme.ATTACK_ON_TITAN -> Brush.radialGradient(listOf(Color(0x66FFD21C), Color.Transparent))
                else -> Brush.radialGradient(listOf(Color(0x443E8DFF), Color.Transparent))
            }))
        }
    }
}

@Composable
private fun StartupSplash(artworkTheme: ArtworkTheme = ArtworkTheme.NONE, animations: AnimationLevel = AnimationLevel.SMOOTH) {
    var logoScale by remember { mutableStateOf(0.72f) }
    var logoAlpha by remember { mutableStateOf(0.35f) }
    LaunchedEffect(Unit) {
        logoScale = 1f
        logoAlpha = 1f
    }
    val animatedScale by androidx.compose.animation.core.animateFloatAsState(logoScale, tween(900), label = "splash-scale")
    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(logoAlpha, tween(700), label = "splash-alpha")
    val themed = artworkTheme != ArtworkTheme.NONE
    val alucard = artworkTheme == ArtworkTheme.ALUCARD
    val titan = artworkTheme == ArtworkTheme.ATTACK_ON_TITAN
    val scenePainter = remember(artworkTheme) {
        val path = when (artworkTheme) {
            ArtworkTheme.SPIDER_VERSE -> "/themes/spiderverse.webp"
            ArtworkTheme.ALUCARD -> "/themes/alucard.jpg"
            ArtworkTheme.ATTACK_ON_TITAN -> "/themes/attack-on-titan.jpg"
            ArtworkTheme.NONE -> null
        }
        path?.let { resource -> runCatching { object {}.javaClass.getResourceAsStream(resource)?.readBytes()?.let { BitmapPainter(SkiaImage.makeFromEncoded(it).toComposeImageBitmap()) } }.getOrNull() }
    }
    val glitchTransition = if (artworkTheme == ArtworkTheme.SPIDER_VERSE && animations == AnimationLevel.SMOOTH) androidx.compose.animation.core.rememberInfiniteTransition(label = "spider-glitch") else null
    val glitchOffset by if (glitchTransition != null) {
        glitchTransition.animateFloat(
            initialValue = -2f,
            targetValue = 2f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(260), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "spider-glitch-offset",
        )
    } else remember { mutableStateOf(0f) }
    val fluidTransition = if (alucard && animations == AnimationLevel.SMOOTH) androidx.compose.animation.core.rememberInfiniteTransition(label = "alucard-fluid") else null
    val bloodPulse by if (fluidTransition != null) fluidTransition.animateFloat(.18f, .42f, androidx.compose.animation.core.infiniteRepeatable(tween(1500), androidx.compose.animation.core.RepeatMode.Reverse), label = "alucard-blood-pulse") else remember { mutableStateOf(.24f) }
    val lightningTransition = if (titan && animations == AnimationLevel.SMOOTH) androidx.compose.animation.core.rememberInfiniteTransition(label = "titan-lightning") else null
    val lightningPulse by if (lightningTransition != null) lightningTransition.animateFloat(.16f, .72f, androidx.compose.animation.core.infiniteRepeatable(tween(720), androidx.compose.animation.core.RepeatMode.Reverse), label = "titan-lightning-pulse") else remember { mutableStateOf(.24f) }
    Box(Modifier.fillMaxSize().background(if (alucard) Color(0xFF100507) else if (titan) Color(0xFF11100B) else if (themed) Color(0xFF050816) else MaterialTheme.colorScheme.background)) {
        scenePainter?.let { painter ->
            androidx.compose.foundation.Image(painter, if (alucard) "Alucard loading artwork" else "Spider-Verse loading artwork", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .52f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), if (alucard) Color(0xEE070204) else if (titan) Color(0xEE11100B) else Color(0xDD050816)))))
        }
        Box(
            Modifier.fillMaxSize().background(
                if (alucard) Brush.radialGradient(listOf(Color(0x99B31217).copy(alpha = bloodPulse), Color.Transparent)) else if (titan) Brush.radialGradient(listOf(Color(0x99FFD21C).copy(alpha = lightningPulse), Color.Transparent)) else if (themed) Brush.radialGradient(listOf(Color(0x663E8DFF), Color.Transparent)) else Brush.radialGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background))
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (titan && animations == AnimationLevel.SMOOTH) {
            Box(Modifier.fillMaxSize().graphicsLayer(alpha = lightningPulse).background(Brush.linearGradient(listOf(Color.Transparent, Color(0x66FFD21C), Color.Transparent))))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(5) { index ->
                    Box(Modifier.padding(top = (80 + index * 54).dp).width(2.dp).height(190.dp).graphicsLayer(rotationZ = if (index % 2 == 0) 18f else -18f).background(Color(0xCCFFD21C)))
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box {
                if (artworkTheme == ArtworkTheme.SPIDER_VERSE && animations == AnimationLevel.SMOOTH) {
                    AppLogo(Modifier.size(124.dp).graphicsLayer(translationX = glitchOffset, alpha = animatedAlpha * .38f).clip(RoundedCornerShape(28.dp)).background(Color(0x66FF3B6B), RoundedCornerShape(28.dp)))
                }
                if (artworkTheme == ArtworkTheme.ALUCARD && animations == AnimationLevel.SMOOTH) {
                    Box(Modifier.size(124.dp).graphicsLayer(scaleX = 1.02f + bloodPulse / 8f, scaleY = 1.02f + bloodPulse / 8f, alpha = bloodPulse).clip(RoundedCornerShape(28.dp)).background(Color(0x88B31217), RoundedCornerShape(28.dp)))
                }
                AppLogo(
                    Modifier.size(116.dp).graphicsLayer(scaleX = animatedScale, scaleY = animatedScale, alpha = animatedAlpha)
                        .shadow(22.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
                )
            }
            Text("ZERAVRIX", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("BY U1M", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(modifier = Modifier.width(220.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}
}

@Composable
private fun WindowScope.BrandedWindowChrome(
    title: String,
    artworkTheme: ArtworkTheme = ArtworkTheme.NONE,
    windowState: WindowState,
    showReaderTab: Boolean = false,
    onReaderTab: () -> Unit = {},
    onClose: () -> Unit,
) {
    val chromeShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    WindowDraggableArea(
        Modifier.fillMaxWidth().height(52.dp)
            .clip(chromeShape)
            .shadow(12.dp, chromeShape)
            .background(Brush.horizontalGradient(listOf(Color(0xF20D1322), Color(0xDD131A2B), Color(0xF20D1322))))
            .border(1.dp, when (artworkTheme) {
                ArtworkTheme.SPIDER_VERSE -> Color(0xFFFF4E73).copy(alpha = .78f)
                ArtworkTheme.ALUCARD -> Color(0xFFFF3B30).copy(alpha = .78f)
                ArtworkTheme.ATTACK_ON_TITAN -> Color(0xFFF4C542).copy(alpha = .88f)
                ArtworkTheme.NONE -> Color(0x3300D2FF)
            }, chromeShape),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                AppLogo(Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.Center) {
                Text("ZERAVRIX", color = Color(0xFF00D2FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                Text(title, color = PcMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (showReaderTab) {
                ChromeWindowButton("TAB", onReaderTab, artworkTheme = artworkTheme)
                Spacer(Modifier.width(6.dp))
            }
            ChromeWindowButton("—", { windowState.isMinimized = true }, artworkTheme = artworkTheme)
            Spacer(Modifier.width(6.dp))
            ChromeWindowButton(if (windowState.placement == WindowPlacement.Maximized) "❐" else "□", {
                windowState.placement = if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
            }, artworkTheme = artworkTheme)
            Spacer(Modifier.width(6.dp))
            ChromeWindowButton("×", onClose, danger = true, artworkTheme = artworkTheme)
        }
    }
}

@Composable
private fun ChromeWindowButton(label: String, onClick: () -> Unit, danger: Boolean = false, artworkTheme: ArtworkTheme = ArtworkTheme.NONE) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed
    val blood = Color(0xFFB31217)
    val hoverColor = if (artworkTheme == ArtworkTheme.ALUCARD) blood else MaterialTheme.colorScheme.primary
    Surface(
        color = when {
            danger && active -> blood
            active -> hoverColor
            else -> MaterialTheme.colorScheme.surface
        },
        contentColor = when {
            danger && active -> Color.White
            active -> if (artworkTheme == ArtworkTheme.ALUCARD) Color(0xFFFFE5E5) else MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.size(if (label == "TAB") 46.dp else 32.dp).hoverable(interaction).graphicsLayer(scaleX = if (active) 1.06f else 1f, scaleY = if (active) 1.06f else 1f).clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PcNavigationRail(screen: Screen, language: AppLanguage, artworkTheme: ArtworkTheme = ArtworkTheme.NONE, onScreen: (Screen) -> Unit) {
    Column(
        Modifier.width(236.dp).fillMaxHeight()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xEE0D1322),
                        Color(0xDD080B12),
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0x3300D2FF), Color(0x1000D2FF), Color(0x0500D2FF))
                ),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(Modifier.padding(start = 4.dp)) {
            if (artworkTheme != ArtworkTheme.NONE) {
                AppLogo(Modifier.size(56.dp).graphicsLayer(translationX = 2f, alpha = .35f).clip(RoundedCornerShape(12.dp)).background(Color(0x66FF3B6B), RoundedCornerShape(12.dp)))
            }
            AppLogo(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
        }
        Text("ZERAVRIX", color = Color(0xFFF1F4F9), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
        Text("BY U1M", color = Color(0xFF00D2FF), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
        Spacer(Modifier.height(26.dp))
        NavigationItem("◷", uiText(language, "History", "السجل", "Historique"), screen == Screen.LIBRARY) { onScreen(Screen.LIBRARY) }
        NavigationItem("✦", uiText(language, "New", "جديد", "Nouveautés"), screen == Screen.NEW) { onScreen(Screen.NEW) }
        NavigationItem("♡", uiText(language, "Favorites", "المفضلة", "Favoris"), false) { onScreen(Screen.LIBRARY) }
        NavigationItem("◈", uiText(language, "Explore", "استكشاف", "Explorer"), screen == Screen.SEARCH) { onScreen(Screen.SEARCH) }
        NavigationItem("⇩", uiText(language, "Downloads", "التنزيلات", "Téléchargements"), screen == Screen.DOWNLOADS) { onScreen(Screen.DOWNLOADS) }
        NavigationItem("♫", uiText(language, "My Music", "موسيقاي", "Ma musique"), screen == Screen.MUSIC) { onScreen(Screen.MUSIC) }
        Spacer(Modifier.height(20.dp))
        Text(uiText(language, "MANAGE", "إدارة", "GESTION"), color = PcMuted.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        NavigationItem("⌁", uiText(language, "Sources", "المصادر", "Sources"), screen == Screen.SOURCES) { onScreen(Screen.SOURCES) }
        NavigationItem("▦", uiText(language, "Extension stores", "متاجر الإضافات", "Dépôts d’extensions"), screen == Screen.EXTENSION_STORES) { onScreen(Screen.EXTENSION_STORES) }
        NavigationItem("⚙", uiText(language, "Settings", "الإعدادات", "Paramètres"), screen == Screen.SETTINGS) { onScreen(Screen.SETTINGS) }
        Spacer(Modifier.height(24.dp))
        Surface(color = Color(0x33131A2B), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x2200D2FF)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(APP_CONTENT_SCOPE, color = Color(0xFFF1F4F9), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("Native Windows reader", color = PcMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun NavigationItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val active = hovered || pressed
    Surface(
        color = when {
            selected -> Color(0x2E00D2FF)
            active -> Color(0x1400D2FF)
            else -> Color.Transparent
        },
        contentColor = if (selected) Color(0xFF00D2FF) else if (active) Color(0xFFF1F4F9) else PcMuted,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, Color(0x6600D2FF)) else if (active) androidx.compose.foundation.BorderStroke(1.dp, Color(0x2200D2FF)) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).hoverable(interactionSource).clickable(interactionSource, indication = null, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                Text(icon, style = MaterialTheme.typography.titleMedium, color = if (selected) Color(0xFF00D2FF) else if (active) Color(0xFF5CE1E6) else PcMuted)
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (selected) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(5.dp).background(Color(0xFF00D2FF), RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
private fun TopCommandBar(language: AppLanguage, query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xB30D1322), Color(0x000D1322))
                )
            )
            .padding(horizontal = 30.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).height(54.dp).onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) {
                    onSearch()
                    true
                } else false
            },
            singleLine = true,
            placeholder = { Text(uiText(language, "Search manga, manhua, manhwa...", "ابحث عن مانغا، مانهوا، مانها...", "Rechercher manga, manhwa..."), color = PcMuted) },
            leadingIcon = { Text("⌕", color = Color(0xFF00D2FF), style = MaterialTheme.typography.headlineSmall) },
            trailingIcon = { Text("⋮", color = PcMuted, style = MaterialTheme.typography.headlineSmall) },
            shape = RoundedCornerShape(27.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0x99131A2B),
                unfocusedContainerColor = Color(0x66131A2B),
                focusedBorderColor = Color(0x8800D2FF),
                unfocusedBorderColor = Color(0x3300D2FF),
                focusedTextColor = Color(0xFFF1F4F9),
                unfocusedTextColor = Color(0xFFF1F4F9),
            ),
        )
        Spacer(Modifier.width(16.dp))
        PillButton(uiText(language, "Search", "بحث", "Rechercher"), onSearch, emphasized = true)
        Spacer(Modifier.width(10.dp))
        PillButton(uiText(language, "Refresh", "تحديث", "Actualiser"), onRefresh)
    }
}

@Composable
private fun LibraryScreen(
    store: LibraryStore,
    manager: SourceManager,
    downloader: DesktopDownloader,
    nsfwEnabled: Boolean = false,
    onSearchManga: (String) -> Unit,
    onRead: (String, List<MangaPage>, suspend () -> List<MangaPage>) -> Unit,
    onResume: (String, List<MangaPage>, Int, suspend () -> List<MangaPage>) -> Unit,
) {
    var items by remember { mutableStateOf(store.list()) }
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selected by remember { mutableStateOf<Manga?>(null) }
    var continuePending by remember { mutableStateOf<String?>(null) }
    fun removeFromLibrary(mangaId: String) {
        store.remove(mangaId)
        items = store.list()
        if (selected?.id == mangaId) selected = null
    }
    LaunchedEffect(Unit) {
        items = store.list()
    }
    val safeItems = items.filterNot { !nsfwEnabled && isNsfwManga(it, manager) }
    val libraryIds = remember(safeItems) { safeItems.map { it.id }.toSet() }
    val progressSnapshot = remember(items) { store.latestProgressSnapshot(libraryIds) }
    val chapterSnapshot = remember(items) { store.cachedChapterSnapshot(libraryIds) }
    val unreadIds = remember(progressSnapshot, chapterSnapshot) {
        chapterSnapshot.filter { (mangaId, chapters) ->
            val latest = chapters.newestChapter()
            latest != null && progressSnapshot[mangaId]?.chapterId != latest.id
        }.keys
    }
    val libraryCoverKeys = remember(items, nsfwEnabled) {
        safeItems.mapNotNull { it.coverUrl?.trim()?.takeIf(String::isNotBlank) }.distinct()
    }
    LaunchedEffect(libraryCoverKeys) {
        libraryCoverKeys.forEach { key -> runCatching { cacheCoverBytes(key) } }
    }
    val visibleItems = when (filter) {
        LibraryFilter.ALL, LibraryFilter.ON_DEVICE, LibraryFilter.NEW_CHAPTERS -> safeItems
        LibraryFilter.COMPLETED -> emptyList()
    }.sortedWith(compareByDescending<Manga> { it.id in unreadIds }.thenBy { it.title.lowercase(Locale.ROOT) })
    val continueManga = visibleItems.maxByOrNull { progressSnapshot[it.id]?.updatedAt ?: 0L } ?: visibleItems.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(uiText(LocalAppLanguage.current, "Library", "المكتبة", "Bibliothèque"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(uiText(LocalAppLanguage.current, "Your manga, manhua, and manhwa collection", "مجموعتك من المانغا والمانهوا والمانhwa", "Votre collection de manga, manhua et manhwa"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                Text("${visibleItems.size} titles", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                PillButton("Refresh", { items = store.list() })
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryFilter.values().forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label()) },
                        shape = RoundedCornerShape(18.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (visibleItems.isEmpty()) {
                EmptyLibraryState(onSearch = { onSearchManga("") })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 188.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("All titles", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    items(visibleItems, key = { it.id }, contentType = { "library-manga-card" }) { manga ->
                        LibraryCard(manga, progress = progressSnapshot[manga.id], hasUnreadChapter = manga.id in unreadIds, onClick = { if (store.contains(manga.id)) selected = manga }, onAction = {
                            removeFromLibrary(manga.id)
                        }, actionLabel = "Remove")
                    }
                }
            }
        }
        continueManga?.let { manga ->
            ContinuePill(manga, onClick = { continuePending = manga.id; selected = manga }, modifier = Modifier.align(Alignment.BottomEnd))
        }
    }

    selected?.let { manga ->
        LibraryMangaDialog(
            manga = manga,
            manager = manager,
            library = store,
            downloader = downloader,
            autoContinue = continuePending == manga.id,
            onAutoContinueHandled = { if (continuePending == manga.id) continuePending = null },
            onClose = { selected = null },
            onSearch = { onSearchManga(manga.title); selected = null },
            onRead = onRead,
            onResume = onResume,
            onDelete = { removeFromLibrary(manga.id) },
        )
    }
}

@Composable
private fun EmptyLibraryState(onSearch: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(42.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your library is empty", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Search a source and add manga to build your collection.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            PillButton("Explore sources", onSearch, emphasized = true)
        }
    }
}

@Composable
private fun LibraryCard(manga: Manga, progress: ReadingProgress? = null, hasUnreadChapter: Boolean = false, onClick: () -> Unit, onAction: (() -> Unit)? = null, actionLabel: String = "Remove") {
    Column(
        Modifier.widthIn(min = 160.dp, max = 240.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            CoverImage(manga.coverUrl, manga.title, Modifier.fillMaxWidth().height(270.dp))
            if (hasUnreadChapter) {
                Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(20.dp).shadow(10.dp, RoundedCornerShape(50), ambientColor = Color(0xFFE32636), spotColor = Color(0xFFE32636)).background(Color(0xFFE32636), RoundedCornerShape(50)))
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                Text("✓", color = Color(0xFF0D1B31), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
            progress?.let { saved ->
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .94f), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Text(chapterNumberLabel(saved.chapterNumber), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        Text(manga.title, maxLines = 2, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
        manga.author?.takeIf { it.isNotBlank() }?.let { author ->
            Text(author, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        onAction?.let { action -> TextButton(onClick = action, contentPadding = PaddingValues(0.dp)) { Text(actionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) } }
    }
}

@Composable
private fun ContinuePill(manga: Manga, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 10.dp,
        modifier = modifier.padding(18.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("♧", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Continue", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(manga.title, color = Color(0xFFDCE8FF), maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CoverImage(url: String?, title: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var bytes by remember(url) { mutableStateOf(url?.trim()?.let { CoverBytesCache[it] }) }
    var loading by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        val key = url?.trim().orEmpty()
        if (key.isBlank()) return@LaunchedEffect
        if (bytes != null) return@LaunchedEffect
        loading = true
        try {
            bytes = cacheCoverBytes(key)
        } finally {
            loading = false
        }
    }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = bytes) {
        value = bytes?.let { encoded ->
            withContext(Dispatchers.Default) {
                runCatching { SkiaImage.makeFromEncoded(encoded).toComposeImageBitmap() }.getOrNull()
            }
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(
            Brush.verticalGradient(listOf(Color(0xFF4B556D), Color(0xFF262A35))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val decodedBitmap = bitmap
        if (decodedBitmap != null) {
            androidx.compose.foundation.Image(decodedBitmap, title, Modifier.fillMaxSize(), contentScale = contentScale)
        } else if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(title.take(1).uppercase(), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun downloadChapterSet(
    manga: Manga,
    chapters: List<Chapter>,
    source: Source,
    downloader: DesktopDownloader,
    onProgress: (String) -> Unit,
) {
    chapters.forEachIndexed { chapterIndex, chapter ->
        onProgress("Preparing ${chapterIndex + 1}/${chapters.size}: ${chapter.title}")
        val pages = source.pages(chapter)
        check(pages.isNotEmpty()) { "No pages returned for ${chapter.title}" }
        downloader.downloadChapter(manga, chapter, pages) { done, total ->
            onProgress("Downloading ${chapter.title}: $done/$total pages")
        }
    }
}

@Composable
private fun SearchScreen(
    manager: SourceManager,
    store: LibraryStore,
    downloader: DesktopDownloader,
    query: String,
    nsfwEnabled: Boolean = false,
    onQueryChange: (String) -> Unit,
    onRead: (String, List<MangaPage>, suspend () -> List<MangaPage>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Source?>(manager.all().firstOrNull()) }
    var results by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Manga?>(null) }
    var verificationSource by remember { mutableStateOf<Source?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var loadingChapters by remember { mutableStateOf(false) }
    var sourceMenu by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var chapterError by remember { mutableStateOf<String?>(null) }
    var selectedChapterIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cataloguePage by remember { mutableStateOf(0) }
    var catalogueHasNext by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    val gridState = rememberLazyGridState()
    val availableSources = manager.all().filter { nsfwEnabled || !isNsfwSource(it) }

    LaunchedEffect(nsfwEnabled) {
        if (source != null && !nsfwEnabled && isNsfwSource(source!!)) {
            source = availableSources.firstOrNull()
            results = emptyList()
            chapters = emptyList()
        }
    }

    suspend fun fetchExplorePage(page: Int, queryText: String, replace: Boolean) {
        val selectedSource = source ?: return
        if (queryText.isBlank()) {
            val pageResult = selectedSource.latest(page)
            val additions = pageResult.items.filterNot { !nsfwEnabled && isNsfwManga(it, manager) }.distinctBy { "${it.sourceId}:${it.id}" }
            results = if (replace) additions else (results + additions).distinctBy { "${it.sourceId}:${it.id}" }
            catalogueHasNext = pageResult.hasNextPage && additions.isNotEmpty()
        } else {
            val additions = availableSources.flatMap { candidate ->
                try {
                    candidate.search(page, queryText).items.filterNot { !nsfwEnabled && isNsfwManga(it, manager) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    emptyList()
                }
            }.distinctBy { "${it.sourceId}:${it.id}" }
                .sortedWith(compareBy<Manga> { searchRank(it.title, queryText) }.thenBy { it.title.lowercase(Locale.ROOT) })
            results = additions
            catalogueHasNext = false
        }
        cataloguePage = page
    }

    fun beginExploreLoad() {
        loadJob?.cancel()
        val queryText = query.trim()
        loadJob = scope.launch {
            loading = true
            error = null
            chapters = emptyList()
            results = emptyList()
            cataloguePage = 0
            catalogueHasNext = true
            try {
                fetchExplorePage(1, queryText, replace = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                error = failure.message ?: "Source request failed"
                catalogueHasNext = false
            } finally {
                loading = false
            }
        }
    }

    fun loadNextExplorePage() {
        if (loading || !catalogueHasNext || source == null || query.isNotBlank()) return
        val nextPage = cataloguePage + 1
        loadJob?.cancel()
        loadJob = scope.launch {
            loading = true
            try {
                fetchExplorePage(nextPage, "", replace = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                error = failure.message ?: "More titles could not be loaded"
                catalogueHasNext = false
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(source) { beginExploreLoad() }
    LaunchedEffect(gridState, results.size, catalogueHasNext, query) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (query.isBlank() && catalogueHasNext && results.isNotEmpty() && lastVisible >= results.lastIndex - 8) {
                    loadNextExplorePage()
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText(LocalAppLanguage.current, "Explore", "استكشاف", "Explorer"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(uiText(LocalAppLanguage.current, "Search installed sources for manga, manhua, and manhwa", "ابحث في المصادر المثبتة عن المانغا والمانهوا والمانhwa", "Rechercher dans les sources installées"), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Box {
                PillButton(source?.name ?: "Select source", { sourceMenu = true })
                DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                    availableSources.forEach { candidate ->
                        DropdownMenuItem(text = { Text(candidate.name) }, onClick = { source = candidate; sourceMenu = false })
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            PillButton("Verify source", { source?.let { verificationSource = it } }, emphasized = true)
            Spacer(Modifier.width(10.dp))
            PillButton(if (loading) "Loading…" else "Search", { beginExploreLoad() }, emphasized = true)
        }
        Spacer(Modifier.height(22.dp))
        if (manager.all().isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Text("No source extensions are installed. Open Sources and manually install a trusted desktop source JAR.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
            }
        }
        error?.let { Text(it, color = PcDanger, modifier = Modifier.padding(vertical = 8.dp)) }
        if (results.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Text("Search results will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            }
        } else {
            LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(minSize = 188.dp), contentPadding = PaddingValues(bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                items(results, key = { "${it.sourceId}:${it.id}" }, contentType = { "explore-manga-card" }) { manga ->
                    LibraryCard(manga, onClick = {
                        val titleSource = manager.get(manga.sourceId) ?: source ?: return@LibraryCard
                        source = titleSource
                        selected = manga
                        chapterError = null
                        loadingChapters = true
                        scope.launch {
                            val detailResult = runCatching { titleSource.details(manga) }
                            val detailed = detailResult.getOrElse { manga }
                            selected = detailed
                            val chapterResult = runCatching { titleSource.chapters(detailed).orderedForDisplay() }
                            chapters = chapterResult.getOrElse { failure ->
                                chapterError = "Chapter list failed: ${failure.message ?: failure::class.simpleName ?: "unknown error"}"
                                emptyList()
                            }
                            if (chapters.isNotEmpty()) store.saveChapterCache(detailed.id, chapters)
                            downloadedIds = downloader.downloadedChapters(detailed.id).map { it.chapterId }.toSet()
                            loadingChapters = false
                        }
                    }, onAction = { store.add(manga) }, actionLabel = "Add to Favorites")
                }
                if (loading && cataloguePage > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (!catalogueHasNext && query.isBlank() && cataloguePage > 0) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("You reached the end of this source catalogue.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(22.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }

    selected?.let { manga ->
        suspend fun explorePages(chapter: Chapter): List<MangaPage> {
            val offline = downloader.downloadedChapters(manga.id).firstOrNull { it.chapterId == chapter.id }
            return if (offline != null) downloader.localPages(offline) else source?.pages(chapter).orEmpty()
        }
        suspend fun downloadExplore(target: List<Chapter>) {
            val selectedSource = source ?: error("The source extension for this title is not installed.")
            check(target.isNotEmpty()) { "No chapters selected." }
            downloadChapterSet(manga, target, selectedSource, downloader) { message -> downloadStatus = message }
            downloadedIds = downloader.downloadedChapters(manga.id).map { it.chapterId }.toSet()
            selectedChapterIds = emptySet()
            downloadStatus = "Downloaded ${target.size} chapter(s)."
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(manga.title) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    CoverImage(manga.coverUrl, manga.title, Modifier.fillMaxWidth().height(220.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(manga.description.ifBlank { "No description available." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text("Chapters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (downloadStatus.isNotBlank()) Text(downloadStatus, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                    chapterError?.let { Text(it, color = PcDanger, modifier = Modifier.padding(vertical = 6.dp)) }
                    if (!loadingChapters && chapters.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { runCatching { downloadExplore(chapters) }.onFailure { downloadStatus = "Download failed: ${it.message ?: "unknown error"}" } } }) { Text("Download all") }
                            OutlinedButton(onClick = {
                                val unread = chapters.filter { it.id !in downloadedIds }.take(10)
                                scope.launch { runCatching { downloadExplore(unread) }.onFailure { downloadStatus = "Download failed: ${it.message ?: "unknown error"}" } }
                            }) { Text("Download unread next") }
                            OutlinedButton(enabled = selectedChapterIds.isNotEmpty(), onClick = {
                                val chosen = chapters.filter { it.id in selectedChapterIds }
                                scope.launch { runCatching { downloadExplore(chosen) }.onFailure { downloadStatus = "Download failed: ${it.message ?: "unknown error"}" } }
                            }) { Text("Download selected (${selectedChapterIds.size})") }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    when {
                        loadingChapters -> CircularProgressIndicator()
                        chapters.isEmpty() -> Text("No chapters returned by this source.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                        else -> chapters.forEachIndexed { index, chapter ->
                            val isDownloaded = chapter.id in downloadedIds
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = chapter.id in selectedChapterIds, onCheckedChange = { checked -> selectedChapterIds = if (checked) selectedChapterIds + chapter.id else selectedChapterIds - chapter.id })
                                Column(Modifier.weight(1f)) {
                                    Text(chapter.title, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                    if (isDownloaded) Text("Downloaded", color = Color(0xFF58C98B), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        val pages = runCatching { explorePages(chapter) }.getOrElse { failure ->
                                            chapterError = "Chapter failed: ${failure.message ?: failure::class.simpleName ?: "unknown error"}"
                                            emptyList()
                                        }
                                        if (pages.isNotEmpty()) {
                                            store.saveProgress(ReadingProgress(manga.id, chapter.id, 0, chapter.number, System.currentTimeMillis()))
                                            val nextChapter = chapters.getOrNull(index + 1)
                                            onRead("${manga.title} • ${chapter.title}", pages) {
                                                nextChapter?.let { next ->
                                                    runCatching { explorePages(next).mapIndexed { pageIndex, page -> page.copy(index = pageIndex, chapterLabel = if (pageIndex == 0) "Next chapter: ${next.title}" else null) } }.getOrDefault(emptyList())
                                                } ?: emptyList()
                                            }
                                        }
                                    }
                                }) { Text(if (isDownloaded) "Read offline" else "Read") }
                                TextButton(onClick = {
                                    scope.launch { runCatching { downloadExplore(listOf(chapter)) }.onFailure { downloadStatus = "Download failed: ${it.message ?: "unknown error"}" } }
                                }) { Text("Download") }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { store.add(manga); selected = null }) { Text("Add to library") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
    verificationSource?.let { sourceToVerify ->
        VerificationBrowserDialog(
            source = sourceToVerify,
            onClose = { verificationSource = null },
            onSaved = { cookie ->
                (sourceToVerify as? ConfigurableSource)?.updatePreference("cookie", cookie)
                verificationSource = null
                error = "Saved verification cookies for ${sourceToVerify.name}. Retry Explore now."
            },
        )
    }
}

@Composable
private fun LibraryMangaDialog(
    manga: Manga,
    manager: SourceManager,
    library: LibraryStore,
    downloader: DesktopDownloader,
    autoContinue: Boolean = false,
    onAutoContinueHandled: () -> Unit = {},
    onClose: () -> Unit,
    onSearch: () -> Unit,
    onRead: (String, List<MangaPage>, suspend () -> List<MangaPage>) -> Unit,
    onResume: (String, List<MangaPage>, Int, suspend () -> List<MangaPage>) -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val source = remember(manga.sourceId) { manager.get(manga.sourceId) }
    var detailed by remember(manga.id) { mutableStateOf(manga) }
    var chapters by remember(manga.id) { mutableStateOf<List<Chapter>>(emptyList()) }
    var loading by remember(manga.id) { mutableStateOf(true) }
    var loadingChapterId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedChapterIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chapterSearch by remember { mutableStateOf("") }
    var chapterOrderAscending by remember { mutableStateOf(true) }
    val latestProgress = library.latestProgress(detailed.id)
    val displayChapters = chapters.filter { chapter ->
        val query = chapterSearch.trim()
        query.isBlank() || chapter.title.contains(query, ignoreCase = true) || chapter.number?.toString()?.contains(query, ignoreCase = true) == true
    }.let { filtered -> if (chapterOrderAscending) filtered else filtered.asReversed() }

    suspend fun pagesFor(chapter: Chapter): List<MangaPage> {
        val offline = downloader.downloadedChapters(detailed.id).firstOrNull { it.chapterId == chapter.id }
        return if (offline != null) downloader.localPages(offline) else source?.pages(chapter).orEmpty()
    }

    suspend fun downloadSet(target: List<Chapter>) {
        val selectedSource = source ?: error("The source extension for this title is not installed.")
        check(target.isNotEmpty()) { "No chapters selected." }
        downloadChapterSet(detailed, target, selectedSource, downloader) { message -> status = message }
        downloadedIds = downloader.downloadedChapters(detailed.id).map { it.chapterId }.toSet()
        selectedChapterIds = emptySet()
        status = "Downloaded ${target.size} chapter(s)."
    }

    LaunchedEffect(manga.id, source, autoContinue) {
        loading = true
        error = null
        if (source == null) {
            error = "The source extension for this title is not installed."
            loading = false
            onAutoContinueHandled()
        } else {
            val loaded = runCatching { source.details(manga) }.getOrElse { manga }
            detailed = loaded
            val loadedChapters = runCatching { source.chapters(loaded).orderedForDisplay() }
                .onFailure { error = it.message ?: "Chapter list could not be loaded." }
                .getOrDefault(emptyList())
            chapters = loadedChapters
            if (loadedChapters.isNotEmpty()) library.saveChapterCache(loaded.id, loadedChapters)
            downloadedIds = downloader.downloadedChapters(loaded.id).map { it.chapterId }.toSet()
            loading = false
            if (autoContinue) {
                val progress = library.latestProgress(loaded.id)
                val targetIndex = progress?.let { saved -> loadedChapters.indexOfFirst { it.id == saved.chapterId } } ?: -1
                val target = loadedChapters.getOrNull(targetIndex)
                if (target != null) {
                    loadingChapterId = target.id
                    val pages = runCatching { pagesFor(target) }.getOrElse { failure ->
                        status = "Continue failed: ${failure.message ?: failure::class.simpleName ?: "unknown error"}"
                        emptyList()
                    }
                    if (pages.isNotEmpty()) {
                        library.saveProgress(ReadingProgress(loaded.id, target.id, progress?.pageIndex ?: 0, target.number, System.currentTimeMillis()))
                        val next = loadedChapters.getOrNull(targetIndex + 1)
                        onResume("${loaded.title} • ${target.title}", pages, progress?.pageIndex ?: 0) {
                            next?.let { nextChapter ->
                                runCatching { pagesFor(nextChapter).mapIndexed { pageIndex, page -> page.copy(index = pageIndex, chapterLabel = if (pageIndex == 0) "Next chapter: ${nextChapter.title}" else null) } }.getOrDefault(emptyList())
                            } ?: emptyList()
                        }
                        status = ""
                    } else if (status.isBlank()) status = "No pages were returned for ${target.title}."
                    loadingChapterId = null
                } else if (progress != null) {
                    status = "Your last-read chapter is no longer available from this source."
                }
                onAutoContinueHandled()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(min = 760.dp, max = 980.dp),
        title = {
            Column {
                Text(detailed.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                source?.let { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 3.dp)) }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
                    CoverImage(detailed.coverUrl, detailed.title, Modifier.width(270.dp).height(410.dp), contentScale = ContentScale.Fit)
                    Column(Modifier.weight(1f)) {
                        Text(detailed.description.ifBlank { "No description available." }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        val metadata = listOfNotNull(detailed.author?.takeIf { it.isNotBlank() }?.let { "Author: $it" }, detailed.artist?.takeIf { it.isNotBlank() }?.let { "Artist: $it" }, detailed.status?.takeIf { it.isNotBlank() }?.let { "Status: $it" })
                        if (metadata.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            metadata.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                        }
                        latestProgress?.let { saved ->
                            Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                                Text("▶ Last read: Chapter ${saved.chapterNumber?.toString() ?: "?"} • page ${saved.pageIndex + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Open source", onSearch)
                            PillButton("Delete from library", onDelete, danger = true)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(uiText(LocalAppLanguage.current, "Chapters", "الفصول", "Chapitres"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(10.dp))
                    Text(if (loading) "Loading…" else "${chapters.size} available", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                error?.let { Text(it, color = PcDanger, modifier = Modifier.padding(top = 8.dp)) }
                if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (!loading && chapters.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(chapterSearch, { chapterSearch = it }, label = { Text("Find chapter number") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { chapterOrderAscending = !chapterOrderAscending }) { Text(if (chapterOrderAscending) "1 → latest" else "latest → 1") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { scope.launch { runCatching { downloadSet(chapters) }.onFailure { status = "Download failed: ${it.message ?: "unknown error"}" } } }) { Text("Download all") }
                        OutlinedButton(onClick = {
                            val unread = chapters.filter { it.id !in downloadedIds }.take(10)
                            scope.launch { runCatching { downloadSet(unread) }.onFailure { status = "Download failed: ${it.message ?: "unknown error"}" } }
                        }) { Text("Download unread next") }
                        OutlinedButton(enabled = selectedChapterIds.isNotEmpty(), onClick = {
                            val selectedChapters = chapters.filter { it.id in selectedChapterIds }
                            scope.launch { runCatching { downloadSet(selectedChapters) }.onFailure { status = "Download failed: ${it.message ?: "unknown error"}" } }
                        }) { Text("Download selected (${selectedChapterIds.size})") }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    chapters.isEmpty() -> Text("No chapters returned by this source.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
                    else -> Column(Modifier.fillMaxWidth().heightIn(max = 410.dp).verticalScroll(rememberScrollState())) {
                        displayChapters.forEachIndexed { index, chapter ->
                            val reading = loadingChapterId == chapter.id
                            val isDownloaded = chapter.id in downloadedIds
                            val isLatestRead = latestProgress?.chapterId == chapter.id
                            Surface(color = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f) else Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = chapter.id in selectedChapterIds, onCheckedChange = { checked -> selectedChapterIds = if (checked) selectedChapterIds + chapter.id else selectedChapterIds - chapter.id })
                                    Column(Modifier.weight(1f)) {
                                        Text(chapter.title, maxLines = 1, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            chapter.number?.let { Text("Chapter $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                                            chapter.uploadDateLabel()?.let { Text("Uploaded $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                                            if (isLatestRead) Text("▶ Last read", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                            if (isDownloaded) Text("Downloaded", color = Color(0xFF58C98B), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    if (reading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else {
                                        TextButton(onClick = {
                                            scope.launch {
                                                loadingChapterId = chapter.id
                                                status = "Loading ${chapter.title}…"
                                                val pages = runCatching { pagesFor(chapter) }.getOrElse { failure ->
                                                    status = "Chapter failed: ${failure.message ?: failure::class.simpleName ?: "unknown error"}"
                                                    emptyList()
                                                }
                                                if (pages.isEmpty()) {
                                                    if (status.startsWith("Loading")) status = "No pages were returned for ${chapter.title}."
                                                } else {
                                                    library.saveProgress(ReadingProgress(detailed.id, chapter.id, 0, chapter.number, System.currentTimeMillis()))
                                                    val next = displayChapters.getOrNull(index + 1)
                                                    onRead("${detailed.title} • ${chapter.title}", pages) {
                                                        next?.let { nextChapter ->
                                                            runCatching { pagesFor(nextChapter).mapIndexed { pageIndex, page -> page.copy(index = pageIndex, chapterLabel = if (pageIndex == 0) "Next chapter: ${nextChapter.title}" else null) } }.getOrDefault(emptyList())
                                                        } ?: emptyList()
                                                    }
                                                    status = ""
                                                }
                                                loadingChapterId = null
                                            }
                                        }) { Text(if (isDownloaded) "Read offline" else "Read") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                loadingChapterId = chapter.id
                                                runCatching { downloadSet(listOf(chapter)) }.onFailure { status = "Download failed: ${it.message ?: "unknown error"}" }
                                                loadingChapterId = null
                                            }
                                        }) { Text("Download") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun MyMusicScreen(
    store: MusicLibraryStore,
    tracks: List<MusicTrack>,
    playlists: List<MusicPlaylist>,
    current: MusicTrack?,
    selectedPlaylist: String?,
    loop: Boolean,
    shuffle: Boolean,
    onRefresh: () -> Unit,
    onSelectPlaylist: (String?) -> Unit,
    onPlay: (MusicTrack) -> Unit,
    onStop: () -> Unit,
    onLoop: () -> Unit,
    onShuffle: () -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var playlistName by remember { mutableStateOf("") }
    var playlistMenuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My Music", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Local music for reading. Files stay on this PC.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Button(onClick = {
                val chooser = JFileChooser().apply { isMultiSelectionEnabled = true; dialogTitle = "Add local music" }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val imported = chooser.selectedFiles.mapNotNull { file -> runCatching { store.importFile(file.toPath()) }.getOrNull() }
                    status = if (imported.isEmpty()) "No supported music files were imported." else "Imported ${imported.size} music file(s)."
                    onRefresh()
                }
            }) { Text("Add music") }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp)) }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(current?.title ?: "Nothing playing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(if (selectedPlaylist == null) "All tracks" else "Playlist: $selectedPlaylist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onStop) { Text("Stop") }
                FilterChip(selected = loop, onClick = onLoop, label = { Text(if (loop) "Loop ON" else "Loop") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = shuffle, onClick = onShuffle, label = { Text(if (shuffle) "Shuffle ON" else "Shuffle") })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(playlistName, { playlistName = it }, label = { Text("New playlist name") }, singleLine = true, modifier = Modifier.width(260.dp))
            Button(enabled = playlistName.isNotBlank() && selectedTrackIds.isNotEmpty(), onClick = {
                store.savePlaylist(MusicPlaylist(playlistName.trim(), selectedTrackIds.toList()))
                onSelectPlaylist(playlistName.trim())
                playlistName = ""
                status = "Playlist saved."
                onRefresh()
            }) { Text("Save playlist") }
            Box {
                OutlinedButton(onClick = { playlistMenuOpen = true }) { Text(selectedPlaylist ?: "All tracks") }
                DropdownMenu(expanded = playlistMenuOpen, onDismissRequest = { playlistMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("All tracks") }, onClick = { onSelectPlaylist(null); playlistMenuOpen = false })
                    playlists.forEach { playlist ->
                        DropdownMenuItem(text = { Text("${playlist.name} (${playlist.trackIds.size})") }, onClick = { onSelectPlaylist(playlist.name); playlistMenuOpen = false })
                    }
                }
            }
        }
        Text("Select tracks for a playlist", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
        if (tracks.isEmpty()) {
            Text("No music has been added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp))
        } else {
            tracks.forEach { track ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(track.id in selectedTrackIds, { checked -> selectedTrackIds = if (checked) selectedTrackIds + track.id else selectedTrackIds - track.id })
                    Text(track.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { onPlay(track) }) { Text(if (track.id == current?.id) "Playing" else "Play") }
                    TextButton(onClick = { store.removeTrack(track.id); onStop(); onRefresh() }) { Text("Remove") }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            playlists.forEach { playlist ->
                TextButton(onClick = { onSelectPlaylist(playlist.name) }) { Text("${if (playlist.name == selectedPlaylist) "▶ " else ""}${playlist.name} • ${playlist.trackIds.size} tracks") }
            }
        }
    }
}

@Composable
private fun NewChaptersScreen(
    store: LibraryStore,
    manager: SourceManager,
    downloader: DesktopDownloader,
    nsfwEnabled: Boolean,
    onRead: (String, List<MangaPage>, suspend () -> List<MangaPage>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<Pair<Manga, List<Chapter>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableStateOf(0) }

    LaunchedEffect(nsfwEnabled, store, refreshToken) {
        loading = true
        val candidates = store.list().filterNot { !nsfwEnabled && isNsfwManga(it, manager) }
        groups = withContext(Dispatchers.IO) {
            candidates.mapNotNull { manga ->
                val source = manager.get(manga.sourceId) ?: return@mapNotNull null
                val detailed = runCatching { source.details(manga) }.getOrDefault(manga)
                val chapters = runCatching { source.chapters(detailed).orderedForDisplay() }
                    .getOrDefault(emptyList())
                if (chapters.isNotEmpty()) store.saveChapterCache(manga.id, chapters)
                val unread = chapters.filter { !downloader.isDownloaded(manga.id, it.id) }
                val newest = unread.newestChapter()
                if (newest == null) null else manga to listOf(newest)
            }
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("New chapters", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Latest chapters found in your library that are not downloaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedButton(onClick = { loading = true; groups = emptyList(); refreshToken++ }) { Text("Refresh") }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp)) }
        if (loading) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (groups.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                Text("No new chapters were found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp))
            }
        } else {
            Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (manga, chapters) ->
                    val source = manager.get(manga.sourceId)
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoverImage(manga.coverUrl, manga.title, Modifier.size(78.dp).padding(end = 12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(manga.title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text("${chapters.size} new chapter(s)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            chapters.forEachIndexed { index, chapter ->
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(chapter.title, color = MaterialTheme.colorScheme.onSurface)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            chapter.number?.let { Text("Chapter $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                                            chapter.uploadDateLabel()?.let { Text("Uploaded $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                                        }
                                    }
                                    TextButton(enabled = source != null, onClick = {
                                        scope.launch {
                                            val selectedSource = source ?: return@launch
                                            val pages = runCatching { selectedSource.pages(chapter) }.getOrDefault(emptyList())
                                            if (pages.isEmpty()) status = "No pages returned for ${chapter.title}." else onRead("${manga.title} • ${chapter.title}", pages) {
                                                                                                    chapters.getOrNull(index + 1)?.let { next -> selectedSource.pages(next).mapIndexed { pageIndex, page -> page.copy(index = pageIndex, chapterLabel = if (pageIndex == 0) "Next chapter: ${next.title}" else null) } } ?: emptyList()
                                            }
                                        }
                                    }) { Text("Read") }
                                    TextButton(enabled = source != null, onClick = {
                                        scope.launch {
                                            val selectedSource = source ?: return@launch
                                            runCatching { downloadChapterSet(manga, listOf(chapter), selectedSource, downloader) { message -> status = message } }
                                                .onSuccess { status = "Downloaded ${chapter.title}." }
                                                .onFailure { status = "Download failed: ${it.message ?: "unknown error"}" }
                                        }
                                    }) { Text("Download") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    library: LibraryStore,
    downloader: DesktopDownloader,
    manager: SourceManager,
    nsfwEnabled: Boolean,
    onRead: (String, List<MangaPage>, Int) -> Unit,
) {
    var downloaded by remember { mutableStateOf(downloader.downloadedChapters()) }
    val visibleDownloaded = downloaded.filterNot { !nsfwEnabled && (isNsfwSource(manager.get(it.sourceId) ?: return@filterNot false) || listOf("hentai", "nsfw", "adult", "18+").any(it.mangaTitle.lowercase(Locale.ROOT)::contains)) }
    var status by remember { mutableStateOf<String?>(null) }
    var expandedMangaIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val downloadMangaIds = remember(visibleDownloaded) { visibleDownloaded.map { it.mangaId }.toSet() }
    val progressSnapshot = remember(downloaded) { library.latestProgressSnapshot(downloadMangaIds) }
    val mangaGroups = remember(visibleDownloaded) {
        visibleDownloaded.groupBy { "${it.sourceId}:${it.mangaId}" }
            .toList()
            .sortedBy { it.second.firstOrNull()?.mangaTitle?.lowercase(Locale.ROOT).orEmpty() }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "downloads-header", contentType = "downloads-header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(uiText(LocalAppLanguage.current, "Downloads", "التنزيلات", "Téléchargements"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("${visibleDownloaded.size} visible chapters • tap a manga cover to show its downloaded chapters.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
                OutlinedButton(onClick = { downloaded = downloader.downloadedChapters(); status = "Download list refreshed." }) { Text("Refresh") }
            }
            status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
        }
        if (visibleDownloaded.isEmpty()) {
            item(key = "downloads-empty", contentType = "downloads-empty") {
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("No completed chapters have been downloaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp))
                }
            }
        } else {
            mangaGroups.forEach { (groupKey, rawChapters) ->
                val mangaChapters = rawChapters.sortedWith(compareBy<DownloadedChapter> { it.number ?: Float.POSITIVE_INFINITY }.thenBy { it.chapterTitle })
                val first = mangaChapters.first()
                val saved = progressSnapshot[first.mangaId]
                val expanded = groupKey in expandedMangaIds
                item(key = "download-manga-$groupKey", contentType = "download-manga") {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(96.dp).clickable {
                                        expandedMangaIds = if (expanded) expandedMangaIds - groupKey else expandedMangaIds + groupKey
                                    },
                                ) {
                                    CoverImage(first.mangaCoverUrl, first.mangaTitle, Modifier.fillMaxSize().padding(end = 12.dp))
                                    saved?.let { progress ->
                                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .95f), shape = RoundedCornerShape(10.dp), modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 5.dp)) {
                                            Text(chapterNumberLabel(progress.chapterNumber), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(first.mangaTitle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text("${mangaChapters.size} chapter(s) downloaded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (expanded) "Chapters visible" else "Press the cover to show chapters", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    saved?.let { Text("▶ Last read ${chapterNumberLabel(it.chapterNumber)} • page ${it.pageIndex + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                                }
                                TextButton(onClick = { expandedMangaIds = if (expanded) expandedMangaIds - groupKey else expandedMangaIds + groupKey }) { Text(if (expanded) "Hide" else "Show") }
                            }
                        }
                    }
                }
                if (expanded) {
                    items(mangaChapters, key = { "download-chapter-${it.mangaId}-${it.chapterId}" }, contentType = { "download-chapter" }) { chapter ->
                        val chapterSaved = progressSnapshot[chapter.mangaId]?.takeIf { it.chapterId == chapter.chapterId }
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(chapter.chapterTitle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${chapter.pageCount} pages", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                        chapterSaved?.let { Text("▶ Last read • page ${it.pageIndex + 1}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                TextButton(onClick = {
                                    val pages = downloader.localPages(chapter)
                                    if (pages.size != chapter.pageCount) status = "${chapter.chapterTitle} is incomplete; reconnect and download it again." else {
                                        val resumePage = chapterSaved?.pageIndex?.coerceIn(0, pages.lastIndex.coerceAtLeast(0)) ?: 0
                                        library.saveProgress(ReadingProgress(chapter.mangaId, chapter.chapterId, resumePage, chapter.number, System.currentTimeMillis()))
                                        onRead("${chapter.mangaTitle} • ${chapter.chapterTitle}", pages, resumePage)
                                    }
                                }) { Text("Read offline") }
                                TextButton(onClick = {
                                    downloader.removeChapter(chapter)
                                    downloaded = downloader.downloadedChapters()
                                    expandedMangaIds = expandedMangaIds - groupKey
                                    status = "Removed ${chapter.chapterTitle} from downloads."
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(manager: SourceManager, dir: Path, nsfwEnabled: Boolean, language: AppLanguage = AppLanguage.ENGLISH) {
    var sources by remember { mutableStateOf(manager.all().filter { nsfwEnabled || !isNsfwSource(it) }) }
    var status by remember { mutableStateOf<String?>(null) }
    var verificationSource by remember { mutableStateOf<Source?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText(language, "Sources", "المصادر", "Sources"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Native PC extensions adapted from the supplied Mihon/Tachiyomi source manager", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Native PC extension manager", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Komga and Kavita are self-hosted servers. Enter a reachable server address and credentials below. Android APKs are not executed.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        status?.let { Text(it, color = if (it.contains("failed", true) || it.contains("error", true)) PcDanger else PcMuted, modifier = Modifier.padding(vertical = 10.dp)) }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            sources.forEach { source ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("◈", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${source.language} • ${source.baseUrl}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("ID ${source.id}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                        if (source is ConfigurableSource) {
                            val scope = rememberCoroutineScope()
                            var draft by remember(source.id) { mutableStateOf(source.preferences()) }
                            Spacer(Modifier.height(14.dp))
                            Text("Source configuration", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("Saved per source in the ZERAVRIX data directory.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
                            val fields = when (source.name) {
                                "Komga" -> listOf("serverUrl" to "Server URL", "username" to "Username", "password" to "Password", "apiKey" to "API key", "cloudflareClientId" to "Cloudflare Access ID", "cloudflareClientSecret" to "Cloudflare Access secret")
                                "Kavita" -> listOf("serverUrl" to "Server URL", "apiKey" to "API key", "libraryId" to "Library ID (0 for all/automatic)")
                                else -> listOf("cookie" to "Browser cookies (optional; use after Cloudflare verification)")
                            }
                            fields.forEach { (key, label) ->
                                OutlinedTextField(value = draft[key].orEmpty(), onValueChange = { value -> draft = draft.toMutableMap().apply { put(key, value) } }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { draft.forEach { (key, value) -> source.updatePreference(key, value) }; status = "Saved ${source.name} configuration." }) { Text("Save") }
                                OutlinedButton(onClick = {
                                    status = "Testing ${source.name}..."
                                    scope.launch {
                                        val result = runCatching { source.latest(1) }
                                        status = result.fold({ "${source.name} connection succeeded: ${it.items.size} title(s) returned." }, { "${source.name} connection failed: ${it.message ?: it::class.simpleName ?: "unknown error"}" })
                                    }
                                }) { Text("Test connection") }
                                if (source.name != "Komga" && source.name != "Kavita") {
                                    OutlinedButton(onClick = { verificationSource = source }) { Text("Verify in app") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Install a desktop source JAR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Only trusted JVM ServiceLoader JARs are loaded. Supplied Android APKs are source references and are not run inside ZERAVRIX.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
                PillButton("Install JAR", {
                    val chooser = JFileChooser().apply { isMultiSelectionEnabled = true; dialogTitle = "Install source JAR" }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        val copied = chooser.selectedFiles.mapNotNull { file -> runCatching { Files.copy(file.toPath(), dir.resolve(file.name), java.nio.file.StandardCopyOption.REPLACE_EXISTING); file.name }.getOrNull() }
                        val loaded = runCatching { manager.loadExtensions(dir) }
                        sources = manager.all().filter { nsfwEnabled || !isNsfwSource(it) }
                        status = loaded.fold({ "Installed ${copied.size} JAR(s); ${it.size} source(s) loaded." }, { "Source installation failed: ${it.message ?: "unknown error"}" })
                    }
                }, emphasized = true)
            }
        }
    }
    verificationSource?.let { source ->
        VerificationBrowserDialog(
            source = source,
            onClose = { verificationSource = null },
            onSaved = { cookie ->
                (source as? ConfigurableSource)?.updatePreference("cookie", cookie)
                sources = manager.all().filter { nsfwEnabled || !isNsfwSource(it) }
                verificationSource = null
                status = "Saved browser verification cookies for ${source.name}. Test the source now."
            },
        )
    }
}

@Composable
private fun VerificationBrowserDialog(
    source: Source,
    onClose: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val configurable = source as? ConfigurableSource
    var cookie by remember(source.id) { mutableStateOf(configurable?.preferences()?.get("cookie").orEmpty()) }
    var browserUrl by remember(source.id) { mutableStateOf(source.baseUrl) }
    var embeddedUrl by remember(source.id) { mutableStateOf(source.baseUrl) }
    var browserError by remember(source.id) { mutableStateOf<String?>(null) }
    var autoCaptureClipboard by remember(source.id) { mutableStateOf(true) }
    LaunchedEffect(source.id, autoCaptureClipboard) {
        while (autoCaptureClipboard && isActive) {
            delay(650)
            val candidate = runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
            }.getOrNull()?.trim().orEmpty()
            if (candidate.length in 4..12000 && candidate.contains("=") && (candidate.contains(";") || candidate.contains("cf_clearance") || candidate.contains("session"))) {
                if (candidate != cookie) {
                    cookie = candidate
                    browserError = "Cookie text captured from the clipboard. Review it, then choose Save & return."
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.widthIn(min = 900.dp, max = 1180.dp),
        title = { Text("Verify ${source.name} inside ZERAVRIX", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Open the source in your real Windows browser, complete any JavaScript, CAPTCHA, or Cloudflare challenge, then paste the Cookie header below and choose Save & return. The reader will reuse it for catalogue, chapter, and image requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(browserUrl, { browserUrl = it }, label = { Text("Website address") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        val target = browserUrl.trim()
                        if (target.startsWith("http://") || target.startsWith("https://")) embeddedUrl = target else browserError = "Enter a complete http:// or https:// address."
                    }) { Text("Load locally") }
                    OutlinedButton(onClick = {
                        val target = browserUrl.trim()
                        runCatching {
                            check(target.startsWith("http://") || target.startsWith("https://")) { "Enter a complete http:// or https:// address." }
                            check(Desktop.isDesktopSupported()) { "Windows browser integration is unavailable" }
                            Desktop.getDesktop().browse(URI(target))
                        }.onFailure { browserError = it.message ?: "Could not open system browser" }
                    }) { Text("Full browser") }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Lightweight local browser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
                        Text("JavaScript-capable WebView for source verification and CAPTCHA pages. Complete the challenge here, then copy the Cookie header.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp))
                        key(embeddedUrl) {
                            SwingPanel(
                                factory = {
                                    JPanel(BorderLayout()).apply {
                                        val browser = JFXPanel()
                                        add(browser, BorderLayout.CENTER)
                                        Platform.runLater {
                                            val webView = WebView()
                                            webView.engine.userAgent = "ZERAVRIX/1.3 Verification"
                                            webView.engine.load(embeddedUrl)
                                            browser.scene = Scene(webView)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("How to save cookies", {
                                browserError = "After verification, copy the Cookie header from the page tools or browser network details. Clipboard capture is enabled below."
                            }, emphasized = true)
                            OutlinedButton(onClick = { browserError = "The local browser runs JavaScript and supports normal CAPTCHA pages. If a provider blocks embedded views, use Full browser above." }) { Text("Browser help") }
                        }
                    }
                }
                OutlinedTextField(cookie, { cookie = it }, label = { Text("Browser cookies (Cookie header value)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = autoCaptureClipboard, onCheckedChange = { autoCaptureClipboard = it })
                    Text("Automatically capture a copied Cookie header", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                browserError?.let { Text("Browser note: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { onSaved(cookie.trim()) }) { Text("Save & return") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Back") } },
    )
}

@Composable
private fun SettingsScreen(appDir: Path, settings: AppSettings, onOpenMusic: () -> Unit, onRestore: () -> Unit, onSettingsChange: (AppSettings) -> Unit) {
    var backupStatus by remember { mutableStateOf<String?>(null) }
    val backupManager = remember(appDir) { BackupManager(appDir) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(uiText(settings.language, "Settings", "الإعدادات", "Paramètres"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Customize the Windows reader experience", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(22.dp))
        SettingsSection(uiText(settings.language, "Appearance", "المظهر", "Apparence")) {
            SettingRow("Theme", "Choose the black/blue dark theme or blue/white light theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton("Dark", settings.theme == AppTheme.DARK) { onSettingsChange(settings.copy(theme = AppTheme.DARK)) }
                    ChoiceButton("Light", settings.theme == AppTheme.LIGHT) { onSettingsChange(settings.copy(theme = AppTheme.LIGHT)) }
                }
            }
            SettingRow("Language", "English, Arabic, or French") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.values().forEach { language ->
                        ChoiceButton(language.label(), settings.language == language) { onSettingsChange(settings.copy(language = language)) }
                    }
                }
            }
            SettingRow("Artwork theme", "Choose the standard interface, Spider-Verse chromatic effects, Alucard blood-red effects, or Attack on Titan lightning effects. Smooth, Reduced, and Off control animation cost.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ChoiceButton("Standard", settings.artworkTheme == ArtworkTheme.NONE) { onSettingsChange(settings.copy(artworkTheme = ArtworkTheme.NONE)) }
                    ChoiceButton("Spider-Verse", settings.artworkTheme == ArtworkTheme.SPIDER_VERSE) { onSettingsChange(settings.copy(artworkTheme = ArtworkTheme.SPIDER_VERSE)) }
                    ChoiceButton("Alucard", settings.artworkTheme == ArtworkTheme.ALUCARD) { onSettingsChange(settings.copy(artworkTheme = ArtworkTheme.ALUCARD)) }
                    ChoiceButton("Attack on Titan", settings.artworkTheme == ArtworkTheme.ATTACK_ON_TITAN) { onSettingsChange(settings.copy(artworkTheme = ArtworkTheme.ATTACK_ON_TITAN)) }
                }
            }
            SettingRow("Application icon", "Choose a local PNG, JPG, JPEG, or WEBP image. ZERAVRIX copies it into its private app data so it remains available after the original file moves.") {
                val iconStatus = remember { mutableStateOf<String?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Choose local icon", {
                        val chooser = JFileChooser().apply {
                            dialogTitle = "Choose ZERAVRIX application icon"
                            fileFilter = FileNameExtensionFilter("Image files (*.png, *.jpg, *.jpeg, *.webp)", "png", "jpg", "jpeg", "webp")
                            isAcceptAllFileFilterUsed = false
                        }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val selected = chooser.selectedFile.toPath()
                            runCatching {
                                val bytes = Files.readAllBytes(selected)
                                check(SkiaImage.makeFromEncoded(bytes).width > 0) { "The selected image could not be decoded." }
                                val extension = selected.fileName.toString().substringAfterLast('.', "png").lowercase(Locale.ROOT).let { if (it == "jpeg") "jpg" else it }
                                val target = appDir.resolve("branding").resolve("custom-icon.$extension")
                                Files.createDirectories(target.parent)
                                Files.copy(selected, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                onSettingsChange(settings.copy(customIconPath = target.toString()))
                                iconStatus.value = "Custom icon active"
                            }.onFailure { iconStatus.value = "Icon not changed: ${it.message ?: "unsupported image"}" }
                        }
                    }, emphasized = true)
                    if (settings.customIconPath != null) {
                        OutlinedButton(onClick = {
                            runCatching { Files.deleteIfExists(Path.of(settings.customIconPath)) }
                            onSettingsChange(settings.copy(customIconPath = null))
                            iconStatus.value = "Default square logo restored"
                        }) { Text("Reset to default") }
                    }
                }
                Text(settings.customIconPath?.let { "Using custom icon: ${Path.of(it).fileName}" } ?: "Using the supplied square ZERAVRIX logo", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
                iconStatus.value?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsSection(uiText(settings.language, "My Music", "موسيقاي", "Ma musique")) {
            SettingRow("Local music library", "Import tracks and create playlists for the reader") {
                OutlinedButton(onClick = onOpenMusic) { Text("Open My Music") }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsSection(uiText(settings.language, "Reader", "القارئ", "Lecteur")) {
            SettingRow("Default reader mode", "Choose the mode used when opening a chapter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderModePreference.values().forEach { mode ->
                        ChoiceButton(mode.label(), settings.readerMode == mode) { onSettingsChange(settings.copy(readerMode = mode)) }
                    }
                }
            }
            SettingRow("NSFW content", "When enabled, sources may show content they classify as NSFW") {
                Switch(checked = settings.nsfwEnabled, onCheckedChange = { onSettingsChange(settings.copy(nsfwEnabled = it)) })
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsSection(uiText(settings.language, "Updates and desktop behavior", "التحديثات وسلوك سطح المكتب", "Mises à jour et comportement du bureau")) {
            SettingRow("Refresh when the app opens", "Refresh source metadata and update checks when the last refresh is older than the selected interval") {
                Switch(checked = settings.refreshOnOpen, onCheckedChange = { onSettingsChange(settings.copy(refreshOnOpen = it)) })
            }
            SettingRow("Refresh interval", "The app checks for updates while running at this interval") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(6, 12, 24).forEach { hours ->
                        ChoiceButton("${hours}h", settings.refreshIntervalHours == hours) { onSettingsChange(settings.copy(refreshIntervalHours = hours)) }
                    }
                }
            }
            SettingRow("Keep running in the system tray", "Closing the window hides ZERAVRIX and keeps refresh checks active; use the tray menu to exit fully") {
                Switch(checked = settings.minimizeToTray, onCheckedChange = { onSettingsChange(settings.copy(minimizeToTray = it)) })
            }
            SettingRow("Interface animation", "Smooth uses subtle transitions, Reduced lowers animation cost, and Off disables non-essential motion") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimationLevel.values().forEach { level ->
                        ChoiceButton(level.label(), settings.animationLevel == level) { onSettingsChange(settings.copy(animationLevel = level, reducedMotion = level == AnimationLevel.OFF)) }
                    }
                }
            }
            Text("Last refresh: ${if (settings.lastRefreshEpochMillis == 0L) "Not yet" else java.time.Instant.ofEpochMilli(settings.lastRefreshEpochMillis).toString()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        SettingsSection(uiText(settings.language, "Updates and support", "التحديثات والدعم", "Mises à jour et assistance")) {
            SettingRow("ZERAVRIX Discord", "Get updates, report issues, and ask for support in the community server") {
                Button(onClick = {
                    runCatching {
                        check(Desktop.isDesktopSupported()) { "Windows browser integration is unavailable" }
                        Desktop.getDesktop().browse(URI(DISCORD_SUPPORT_URL))
                    }
                }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2))) {
                    Text("◉  Open Discord", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsSection(uiText(settings.language, "Desktop data", "بيانات سطح المكتب", "Données du bureau")) {
            Text(appDir.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Library, downloads, cache, progress, and installed source extensions stay outside the installation directory.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Export backup", {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Export ZERAVRIX backup"
                        selectedFile = java.io.File("zeravrix-backup.zip")
                    }
                    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        backupStatus = runCatching { backupManager.exportBackup(chooser.selectedFile.toPath()); "Backup exported successfully." }
                            .getOrElse { "Backup export failed: ${it.message ?: "unknown error"}" }
                    }
                }, emphasized = true)
                PillButton("Import backup", {
                    val chooser = JFileChooser().apply { dialogTitle = "Import ZERAVRIX backup" }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        backupStatus = runCatching {
                            val count = backupManager.importBackup(chooser.selectedFile.toPath())
                            onRestore()
                            "Restored $count backup entries. Restart the app if an installed source or open file needs reloading."
                        }.getOrElse { "Backup import failed: ${it.message ?: "unknown error"}" }
                    }
                })
            }
            backupStatus?.let { Text(it, color = if (it.startsWith("Backup")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 9.dp)) }
            Text("Automatic backup cadence: every ${settings.backupIntervalDays} days. Backups are portable ZIP snapshots of library, progress, history, settings, and extension-store indexes.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, description: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
        control()
    }
}

@Composable
private fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label) }
}

@Composable
private fun ExtensionStoresScreen(
    stores: List<ExtensionStore>,
    storeStore: ExtensionStoreStore,
    repositorySourceStore: RepositorySourceStore,
    sourceManager: SourceManager,
    sourceDirectory: Path,
    language: AppLanguage = AppLanguage.ENGLISH,
    onStoresChange: (List<ExtensionStore>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rows by remember(stores) { mutableStateOf(stores.map { StoreRowState(it, "Not checked") }) }
    var customUrl by remember { mutableStateOf("") }
    var artifactQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val client = remember { ExtensionStoreClient() }

    suspend fun checkStore(store: ExtensionStore): StoreRowState {
        return when (val result = client.fetch(store)) {
            is StoreFetchResult.Repository -> StoreRowState(
                result.store,
                "${result.format.uppercase()} index • ${result.artifacts.size} package(s) • ${result.rawSize} bytes",
                result.artifacts,
            )
            is StoreFetchResult.Metadata -> StoreRowState(result.store, "Metadata only • no package list at this URL")
            is StoreFetchResult.BinaryIndex -> StoreRowState(result.store, "${result.format.uppercase()} index • desktop JSON companion not found")
            is StoreFetchResult.Failure -> StoreRowState(result.store, "Failed: ${result.message}")
        }
    }

    fun refreshStore(store: ExtensionStore) {
        scope.launch {
            rows = rows.map { if (it.store.id == store.id) it.copy(message = "Checking…") else it }
            val checked = checkStore(store)
            rows = rows.map { if (it.store.id == store.id) checked else it }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText(language, "Extension stores", "متاجر الإضافات", "Dépôts d’extensions"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Discover PC-compatible JVM source JARs without installing APKs or an Android emulator.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            PillButton("Refresh all", {
                scope.launch {
                    rows = stores.map { StoreRowState(it, "Checking…") }
                    rows = stores.map { checkStore(it) }
                    status = "Repository indexes refreshed"
                }
            }, emphasized = true)
        }
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Repository sources", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Use PC web turns a published source home URL into a native lightweight web source. Native ZERAVRIX JARs can also be installed; Android APKs are never executed.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(artifactQuery, { artifactQuery = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Find a source by name or language") })
                Spacer(Modifier.height(12.dp))
                rows.forEach { row ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(row.store.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(row.store.url, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text(row.message, color = if (row.message.startsWith("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                            PillButton("Check", { refreshStore(row.store) })
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = row.store.enabled, onCheckedChange = { enabled ->
                                val updated = stores.map { if (it.id == row.store.id) it.copy(enabled = enabled) else it }
                                storeStore.save(updated)
                                onStoresChange(updated)
                            })
                        }
                        row.artifacts.filter { artifact ->
                            artifactQuery.isBlank() || artifact.name.contains(artifactQuery, true) || artifact.packageName.contains(artifactQuery, true) || artifact.language.contains(artifactQuery, true)
                        }.take(40).forEach { artifact ->
                            Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(artifact.name, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                    Text("${artifact.language} • ${artifact.version} • ${artifact.sourceCount} source(s)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                                when {
                                    isNativeAdaptedArtifact(artifact) -> Text("Native PC source included", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                    artifact.sources.isNotEmpty() -> PillButton("Use PC web", {
                                        scope.launch {
                                            val current = repositorySourceStore.load()
                                            val existingKeys = current.map { "${it.name.trim().lowercase()}|${it.homeUrl.trimEnd('/').lowercase()}" }.toSet()
                                            val additions = artifact.sources.filterNot { descriptor ->
                                                "${descriptor.name.trim().lowercase()}|${descriptor.homeUrl.trimEnd('/').lowercase()}" in existingKeys
                                            }
                                            if (additions.isEmpty()) {
                                                status = "${artifact.name} is already added to Sources."
                                            } else {
                                                repositorySourceStore.save(current + additions)
                                                additions.forEach { descriptor -> sourceManager.install(RepositoryWebSource(descriptor)) }
                                                status = "Added ${additions.size} PC web source(s) from ${artifact.name}. Open Sources to browse them."
                                            }
                                        }
                                    })
                                    artifact.hasJar && !artifact.looksLikeAndroidExtension -> PillButton("Install PC JAR", {
                                        scope.launch {
                                            status = "Downloading ${artifact.name}…"
                                            runCatching {
                                                val jar = client.downloadArtifact(artifact, sourceDirectory)
                                                val loaded = sourceManager.loadJar(jar)
                                                check(loaded.isNotEmpty()) { "JAR did not expose a ZERAVRIX SourceProvider" }
                                            }.onSuccess { status = "Installed ${artifact.name}. Restart or refresh Sources to activate it." }
                                                .onFailure { status = "${artifact.name}: ${it.message ?: "PC extension could not be loaded"}" }
                                        }
                                    })
                                    artifact.hasJar -> Text("Android JAR", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    artifact.apkUrl != null -> Text("Android APK only", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    else -> Text("No downloadable artifact", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Add PC repository index URL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Use an HTTPS JSON index that advertises a native ZERAVRIX ServiceLoader JAR. Android APKs are intentionally not executed by this Windows build.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(customUrl, { customUrl = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("https://example.org/index.json") })
                    Spacer(Modifier.width(10.dp))
                    PillButton("Add", {
                        val url = customUrl.trim()
                        if (url.startsWith("https://")) {
                            val canonical = url.trimEnd('/').lowercase()
                            if (stores.any { it.url.trimEnd('/').lowercase() == canonical }) {
                                status = "This repository URL is already added."
                            } else {
                                val updated = stores + ExtensionStore("custom-${stores.size + 1}", url, url.substringAfter("//").substringBefore('/'))
                                storeStore.save(updated)
                                onStoresChange(updated)
                                customUrl = ""
                                status = "Store added; press Check to inspect it"
                            }
                        } else status = "Only HTTPS store URLs are accepted"
                    }, emphasized = true)
                }
                status?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun AppLogo(modifier: Modifier, mark: Boolean = false) {
    val customPath = LocalCustomIconPath.current
    val bitmap = remember(mark, customPath) {
        runCatching {
            val bytes = customPath?.takeIf { Files.exists(Path.of(it)) }?.let { Files.readAllBytes(Path.of(it)) }
                ?: run {
                    val resource = if (mark) "/branding/logo-mark.png" else "/branding/logo-square.png"
                    object {}.javaClass.getResourceAsStream(resource)?.readBytes()
                }
                ?: return@runCatching null
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) androidx.compose.foundation.Image(bitmap, "ZERAVRIX logo", modifier, contentScale = ContentScale.Fit)
    else Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) { Text("ZX", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
}

private fun loadAppIconPainter(customPath: String?): BitmapPainter {
    val fallback = runCatching { object {}.javaClass.getResourceAsStream("/branding/logo-square.png")?.readBytes() }.getOrNull() ?: ByteArray(0)
    val bytes = customPath?.takeIf { runCatching { Files.exists(Path.of(it)) }.getOrDefault(false) }
        ?.let { runCatching { Files.readAllBytes(Path.of(it)) }.getOrNull() }
        ?: fallback
    val bitmap = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
        .getOrElse { SkiaImage.makeFromEncoded(fallback).toComposeImageBitmap() }
    return BitmapPainter(bitmap)
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit, emphasized: Boolean = false, danger: Boolean = false) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val active = hovered || pressed
    val bloodHover = MaterialTheme.colorScheme.error
    val base = when {
        danger -> MaterialTheme.colorScheme.errorContainer
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val activeColor = when {
        danger -> bloodHover
        MaterialTheme.colorScheme == PcAlucardScheme -> Color(0xFFB31217)
        else -> MaterialTheme.colorScheme.primary
    }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.graphicsLayer(scaleX = if (active) 1.035f else 1f, scaleY = if (active) 1.035f else 1f).shadow(if (active) 10.dp else 0.dp, RoundedCornerShape(20.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) activeColor else base,
            contentColor = when {
                active -> Color.White
                danger -> MaterialTheme.colorScheme.onErrorContainer
                emphasized -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(label, fontWeight = FontWeight.Medium) }
}

private fun AppLanguage.label(): String = when (this) {
    AppLanguage.ENGLISH -> "English"
    AppLanguage.ARABIC -> "العربية"
    AppLanguage.FRENCH -> "Français"
}

private fun ReaderModePreference.toPageLayout(): com.zeravrix.zeravrix.reader.PageLayout = when (this) {
    ReaderModePreference.SINGLE -> com.zeravrix.zeravrix.reader.PageLayout.SINGLE
    ReaderModePreference.DOUBLE -> com.zeravrix.zeravrix.reader.PageLayout.DOUBLE
    // Auto now defaults to continuous vertical mode so a single original page is never placed into a desktop spread.
    ReaderModePreference.AUTO -> com.zeravrix.zeravrix.reader.PageLayout.VERTICAL
    ReaderModePreference.VERTICAL -> com.zeravrix.zeravrix.reader.PageLayout.VERTICAL
}

private fun ReaderModePreference.label(): String = when (this) {
    ReaderModePreference.SINGLE -> "Single"
    ReaderModePreference.DOUBLE -> "Double"
    ReaderModePreference.AUTO -> "Auto (continuous)"
    ReaderModePreference.VERTICAL -> "Webtoon"
}

private fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.ALL -> "All"
    LibraryFilter.ON_DEVICE -> "On device"
    LibraryFilter.NEW_CHAPTERS -> "New chapters"
    LibraryFilter.COMPLETED -> "Completed"
}

private fun uiText(language: AppLanguage, english: String, arabic: String, french: String): String = when (language) {
    AppLanguage.ENGLISH -> english
    AppLanguage.ARABIC -> arabic
    AppLanguage.FRENCH -> french
}

private fun AnimationLevel.label(): String = when (this) {
    AnimationLevel.SMOOTH -> "Smooth"
    AnimationLevel.REDUCED -> "Reduced"
    AnimationLevel.OFF -> "Off"
}
