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
 * The 13 Blueprint Themes defined in Blueprint §7A.4
 */
enum class ColorTheme(
    val id: String,
    val title: String,
    val primaryHex: Long,
    val secondaryHex: Long = 0xFF1D72FEL,
    val accentHex: Long = 0xFF00D2FFL,
    val description: String = ""
) {
    INDIGO_NIGHT("indigo_night", "Indigo Cyber", 0xFF00D2FFL, 0xFF1D72FEL, 0xFF00D2FFL, "Signature cyber blue on obsidian void"),
    SAKURA("sakura", "Sakura", 0xFFE5739BL, 0xFFFF85A1L, 0xFFFFB3C6L, "Soft cherry blossom rose"),
    MATCHA("matcha", "Matcha", 0xFF6FA66AL, 0xFF88D49EL, 0xFFB7E4C7L, "Calming organic green, easy on eyes"),
    EMBER("ember", "Ember", 0xFFE8743BL, 0xFFFF9F1CL, 0xFFFFBF69L, "Warm sunset fire orange"),
    OCEAN("ocean", "Ocean", 0xFF2E9CCAL, 0xFF48CAE4L, 0xFF90E0EFL, "Deep marine cyan blue"),
    LAVENDER("lavender", "Lavender", 0xFF9B87F5L, 0xFFBDB2FFL, 0xFFD8BBFFL, "Regal electric violet"),
    CRIMSON("crimson", "Crimson", 0xFFD64550L, 0xFFFF4D6DL, 0xFFFF758FL, "Bold high-energy scarlet"),
    AURORA("aurora", "Aurora", 0xFF2FBF9BL, 0xFF8B6CFFL, 0xFF5CE1E6L, "Northern lights teal & purple duo"),
    SUNSET("sunset", "Sunset", 0xFFF2A33AL, 0xFFE5567AL, 0xFFFFC2D1L, "Golden amber & radiant rose"),
    GRAPHITE("graphite", "Graphite", 0xFF8A93A6L, 0xFFCBD5E1L, 0xFFE2E8F0L, "Minimal monochrome slate"),
    PAPER("paper", "Paper / Sepia", 0xFF8B6F47L, 0xFFD4A373L, 0xFFEDC4B3L, "Warm paper book tone for long reading sessions"),
    MONO("mono", "Mono Contrast", 0xFFFFFFFFL, 0xFFA0AEC0L, 0xFFE2E8F0L, "Ultra high-contrast crisp black & white"),
    AMOLED_VOID("amoled", "AMOLED Void", 0xFF00D2FFL, 0xFF00E5FFL, 0xFF00D2FFL, "True pitch-black #000000 OLED matrix")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    val colorTheme: ColorTheme = ColorTheme.INDIGO_NIGHT,
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
            colorTheme = runCatching { ColorTheme.valueOf(properties.getProperty("colorTheme", ColorTheme.INDIGO_NIGHT.name)) }.getOrDefault(ColorTheme.INDIGO_NIGHT),
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
