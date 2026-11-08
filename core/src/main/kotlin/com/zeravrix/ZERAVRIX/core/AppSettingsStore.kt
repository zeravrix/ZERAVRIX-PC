package com.zeravrix.zeravrix.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Persisted preferences for the native Windows desktop application. */
enum class AppTheme { DARK, LIGHT }
enum class AppLanguage { ENGLISH, ARABIC, FRENCH }
enum class ReaderModePreference { SINGLE, DOUBLE, AUTO, VERTICAL }
enum class AnimationLevel { SMOOTH, REDUCED, OFF }

/**
 * The 13 Authentic Kotatsu Color Schemes
 */
enum class ColorTheme(
    val id: String,
    val title: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val darkBgHex: Long,
    val lightBgHex: Long,
    val description: String = ""
) {
    TOTORO("totoro", "Totoro (Indigo)", 0xFFA6C8FFL, 0xFFBCC7DCL, 0xFF0E1116L, 0xFFF9F9FFL, "Kotatsu default slate indigo"),
    MONET("monet", "Dynamic Monet", 0xFFA8C7FAL, 0xFFBDC7DCL, 0xFF0D1219L, 0xFFF8FAFFL, "Adaptive material palette"),
    EXPRESSIVE("expressive", "Expressive", 0xFFDDBBFAL, 0xFFD2C1DFL, 0xFF140F19L, 0xFFFCF7FFL, "Vibrant expressive lilac"),
    MIKU("miku", "Miku (Teal)", 0xFF6FDDE2L, 0xFFA6CECFL, 0xFF0B1213L, 0xFFF5FAFAL, "Cyan teal palette"),
    ASUKA("asuka", "Asuka (Crimson)", 0xFFFFB4A8L, 0xFFE7BDB6L, 0xFF160E0DL, 0xFFFFF8F6L, "Scarlet red palette"),
    MION("mion", "Mion (Emerald)", 0xFFA1D39AL, 0xFFEEBF6DL, 0xFF0D140CL, 0xFFF7FBF1L, "Lush emerald green"),
    RIKKA("rikka", "Rikka (Lavender)", 0xFFD3BBFDL, 0xFFCDC2DBL, 0xFF120E18L, 0xFFFEF7FFL, "Deep purple lavender"),
    SAKURA("sakura", "Sakura (Rose)", 0xFFFFB1C8L, 0xFFE3BDC6L, 0xFF160E12L, 0xFFFFF8F8L, "Soft cherry blossom rose"),
    MAMIMI("mamimi", "Mamimi (Cobalt)", 0xFFAFC6FFL, 0xFFBFC6DCL, 0xFF0E1118L, 0xFFFAF8FFL, "Deep cobalt blue"),
    KANADE("kanade", "Kanade (Graphite)", 0xFFE2E2E6L, 0xFFDDDCDCL, 0xFF101012L, 0xFFFCF8F9L, "Monochrome dark slate"),
    ITSUKA("itsuka", "Itsuka (Amber)", 0xFFFFBA8FL, 0xFFF7B993L, 0xFF16100AL, 0xFFFFF8F5L, "Warm sunset amber"),
    AMOLED_VOID("amoled", "AMOLED Void", 0xFFA6C8FFL, 0xFFBCC7DCL, 0xFF000000L, 0xFF000000L, "True pitch-black #000000 OLED matrix"),
    HIGH_CONTRAST("mono", "High Contrast", 0xFFFFFFFFL, 0xFFE2E8F0L, 0xFF050505L, 0xFFFFFFFFL, "Crisp high-contrast monochrome")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    val colorTheme: ColorTheme = ColorTheme.TOTORO,
    val isAmoled: Boolean = false,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val readerMode: ReaderModePreference = ReaderModePreference.VERTICAL,
    val nsfwEnabled: Boolean = false,
    val minimizeToTray: Boolean = false,
    val refreshOnOpen: Boolean = false,
    val refreshIntervalHours: Int = 12,
    val lastRefreshEpochMillis: Long = 0L,
    val backupIntervalDays: Int = 7,
    val lastBackupEpochMillis: Long = 0L,
    val animationLevel: AnimationLevel = AnimationLevel.SMOOTH,
    val customIconPath: String? = null,
)

class SettingsStore(private val root: Path) {
    private val file = root.resolve("settings.properties")
    private val lock = Any()

    init {
        Files.createDirectories(root)
        if (!Files.exists(file)) save(AppSettings())
    }

    fun load(): AppSettings = synchronized(lock) {
        val properties = Properties()
        if (Files.exists(file)) Files.newInputStream(file).use { properties.load(it) }
        AppSettings(
            theme = runCatching { AppTheme.valueOf(properties.getProperty("theme", AppTheme.DARK.name)) }.getOrDefault(AppTheme.DARK),
            colorTheme = runCatching { ColorTheme.valueOf(properties.getProperty("colorTheme", ColorTheme.TOTORO.name)) }.getOrDefault(ColorTheme.TOTORO),
            isAmoled = properties.getProperty("isAmoled", "false").toBoolean(),
            language = runCatching { AppLanguage.valueOf(properties.getProperty("language", AppLanguage.ENGLISH.name)) }.getOrDefault(AppLanguage.ENGLISH),
            readerMode = runCatching { ReaderModePreference.valueOf(properties.getProperty("readerMode", ReaderModePreference.VERTICAL.name)) }.getOrDefault(ReaderModePreference.VERTICAL),
            nsfwEnabled = properties.getProperty("nsfwEnabled", "false").toBoolean(),
            minimizeToTray = properties.getProperty("minimizeToTray", "false").toBoolean(),
            refreshOnOpen = properties.getProperty("refreshOnOpen", "false").toBoolean(),
            refreshIntervalHours = properties.getProperty("refreshIntervalHours", "12").toIntOrNull()?.coerceIn(1, 168) ?: 12,
            lastRefreshEpochMillis = properties.getProperty("lastRefreshEpochMillis", "0").toLongOrNull() ?: 0L,
            backupIntervalDays = properties.getProperty("backupIntervalDays", "7").toIntOrNull()?.coerceIn(1, 365) ?: 7,
            lastBackupEpochMillis = properties.getProperty("lastBackupEpochMillis", "0").toLongOrNull() ?: 0L,
            animationLevel = runCatching {
                properties.getProperty("animationLevel")?.let(AnimationLevel::valueOf)
                    ?: if (properties.getProperty("reducedMotion", "false").toBoolean()) AnimationLevel.OFF else AnimationLevel.SMOOTH
            }.getOrDefault(AnimationLevel.SMOOTH),
            customIconPath = properties.getProperty("customIconPath")?.takeIf { it.isNotBlank() },
        )
    }

    fun save(settings: AppSettings) = synchronized(lock) {
        val properties = Properties().apply {
            setProperty("theme", settings.theme.name)
            setProperty("colorTheme", settings.colorTheme.name)
            setProperty("isAmoled", settings.isAmoled.toString())
            setProperty("language", settings.language.name)
            setProperty("readerMode", settings.readerMode.name)
            setProperty("nsfwEnabled", settings.nsfwEnabled.toString())
            setProperty("minimizeToTray", settings.minimizeToTray.toString())
            setProperty("refreshOnOpen", settings.refreshOnOpen.toString())
            setProperty("refreshIntervalHours", settings.refreshIntervalHours.toString())
            setProperty("lastRefreshEpochMillis", settings.lastRefreshEpochMillis.toString())
            setProperty("backupIntervalDays", settings.backupIntervalDays.toString())
            setProperty("lastBackupEpochMillis", settings.lastBackupEpochMillis.toString())
            setProperty("reducedMotion", (settings.animationLevel == AnimationLevel.OFF).toString())
            setProperty("animationLevel", settings.animationLevel.name)
            settings.customIconPath?.takeIf { it.isNotBlank() }?.let { setProperty("customIconPath", it) }
        }
        Files.newOutputStream(file).use { properties.store(it, "ZERAVRIX desktop settings") }
    }
}

data class ExtensionStore(
    val id: String,
    val url: String,
    val name: String = id,
    val shortName: String = id,
    val website: String = "",
    val signingKeyFingerprint: String = "",
    val enabled: Boolean = true,
)

data class RepositorySourceDescriptor(
    val id: Long,
    val name: String,
    val language: String,
    val homeUrl: String,
)

class RepositorySourceStore(private val root: Path) {
    private val file = root.resolve("repository-web-sources.properties")
    private val lock = Any()

    fun load(): List<RepositorySourceDescriptor> = synchronized(lock) {
        val properties = Properties()
        if (Files.exists(file)) Files.newInputStream(file).use { properties.load(it) }
        val count = properties.getProperty("count", "0").toIntOrNull() ?: 0
        (0 until count).mapNotNull { index ->
            val prefix = "source.$index."
            val id = properties.getProperty(prefix + "id")?.toLongOrNull() ?: return@mapNotNull null
            RepositorySourceDescriptor(
                id = id,
                name = properties.getProperty(prefix + "name", "Web source"),
                language = properties.getProperty(prefix + "language", "multi"),
                homeUrl = properties.getProperty(prefix + "homeUrl", ""),
            )
        }
    }

    fun save(sources: List<RepositorySourceDescriptor>) = synchronized(lock) {
        Files.createDirectories(root)
        val properties = Properties().apply {
            setProperty("count", sources.size.toString())
            sources.distinctBy { "${it.name.trim().lowercase()}|${it.homeUrl.trimEnd('/').lowercase()}" }.forEachIndexed { index, source ->
                val prefix = "source.$index."
                setProperty(prefix + "id", source.id.toString())
                setProperty(prefix + "name", source.name)
                setProperty(prefix + "language", source.language)
                setProperty(prefix + "homeUrl", source.homeUrl)
            }
        }
        Files.newOutputStream(file).use { properties.store(it, "ZERAVRIX repository web sources") }
    }
}

/** Per-source settings used by configurable desktop adapters. */
class SourcePreferencesStore(private val root: Path) {
    private val directory = root.resolve("source-prefs")
    private val lock = Any()

    init { Files.createDirectories(directory) }

    fun load(sourceId: Long): Map<String, String> = synchronized(lock) {
        val file = directory.resolve("$sourceId.properties")
        if (!Files.exists(file)) return@synchronized emptyMap()
        val properties = Properties()
        Files.newInputStream(file).use { properties.load(it) }
        properties.stringPropertyNames().associateWith { properties.getProperty(it, "") }
    }

    fun save(sourceId: Long, values: Map<String, String>) = synchronized(lock) {
        Files.createDirectories(directory)
        val properties = Properties().apply { values.forEach { (key, value) -> setProperty(key, value) } }
        Files.newOutputStream(directory.resolve("$sourceId.properties")).use { properties.store(it, "ZERAVRIX source preferences") }
    }
}
