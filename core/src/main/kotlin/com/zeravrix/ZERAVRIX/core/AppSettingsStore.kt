package com.zeravrix.zeravrix.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Persisted preferences for the native Windows desktop application. */
enum class AppTheme { DARK, LIGHT }
enum class AppLanguage { ENGLISH, ARABIC, FRENCH }
enum class ReaderModePreference { SINGLE, DOUBLE, AUTO, VERTICAL }
enum class AnimationLevel { SMOOTH, REDUCED, OFF }
enum class ArtworkTheme { NONE, SPIDER_VERSE, ALUCARD, ATTACK_ON_TITAN }

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val readerMode: ReaderModePreference = ReaderModePreference.AUTO,
    val nsfwEnabled: Boolean = false,
    val minimizeToTray: Boolean = false,
    val refreshOnOpen: Boolean = true,
    val refreshIntervalHours: Int = 12,
    val lastRefreshEpochMillis: Long = 0L,
    val backupIntervalDays: Int = 7,
    val lastBackupEpochMillis: Long = 0L,
    val reducedMotion: Boolean = false,
    val animationLevel: AnimationLevel = AnimationLevel.SMOOTH,
    val artworkTheme: ArtworkTheme = ArtworkTheme.NONE,
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
            language = runCatching { AppLanguage.valueOf(properties.getProperty("language", AppLanguage.ENGLISH.name)) }.getOrDefault(AppLanguage.ENGLISH),
            readerMode = runCatching { ReaderModePreference.valueOf(properties.getProperty("readerMode", ReaderModePreference.AUTO.name)) }.getOrDefault(ReaderModePreference.AUTO),
            nsfwEnabled = properties.getProperty("nsfwEnabled", "false").toBoolean(),
            minimizeToTray = properties.getProperty("minimizeToTray", "false").toBoolean(),
            refreshOnOpen = properties.getProperty("refreshOnOpen", "true").toBoolean(),
            refreshIntervalHours = properties.getProperty("refreshIntervalHours", "12").toIntOrNull()?.coerceIn(1, 168) ?: 12,
            lastRefreshEpochMillis = properties.getProperty("lastRefreshEpochMillis", "0").toLongOrNull() ?: 0L,
            backupIntervalDays = properties.getProperty("backupIntervalDays", "7").toIntOrNull()?.coerceIn(1, 365) ?: 7,
            lastBackupEpochMillis = properties.getProperty("lastBackupEpochMillis", "0").toLongOrNull() ?: 0L,
            reducedMotion = properties.getProperty("reducedMotion", "false").toBoolean(),
            animationLevel = runCatching {
                properties.getProperty("animationLevel")?.let(AnimationLevel::valueOf)
                    ?: if (properties.getProperty("reducedMotion", "false").toBoolean()) AnimationLevel.OFF else AnimationLevel.SMOOTH
            }.getOrDefault(AnimationLevel.SMOOTH),
            artworkTheme = runCatching {
                properties.getProperty("artworkTheme")?.let(ArtworkTheme::valueOf)
                    ?: if (properties.getProperty("spiderVerseTheme", "false").toBoolean()) ArtworkTheme.SPIDER_VERSE else ArtworkTheme.NONE
            }.getOrDefault(ArtworkTheme.NONE),
            customIconPath = properties.getProperty("customIconPath")?.takeIf { it.isNotBlank() },
        )
    }

    fun save(settings: AppSettings) = synchronized(lock) {
        val properties = Properties().apply {
            setProperty("theme", settings.theme.name)
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
            setProperty("artworkTheme", settings.artworkTheme.name)
            setProperty("spiderVerseTheme", (settings.artworkTheme == ArtworkTheme.SPIDER_VERSE).toString())
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

class ExtensionStoreStore(private val root: Path) {
    private val file = root.resolve("extension-stores.properties")
    private val lock = Any()

    fun load(): List<ExtensionStore> = synchronized(lock) {
        val properties = Properties()
        if (Files.exists(file)) Files.newInputStream(file).use { properties.load(it) }
        val count = properties.getProperty("count", "0").toIntOrNull() ?: 0
        if (count == 0) return@synchronized emptyList()
        (0 until count).mapNotNull { index ->
            val prefix = "store.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            ExtensionStore(
                id = id,
                url = properties.getProperty(prefix + "url", ""),
                name = properties.getProperty(prefix + "name", id),
                shortName = properties.getProperty(prefix + "shortName", id),
                website = properties.getProperty(prefix + "website", ""),
                signingKeyFingerprint = properties.getProperty(prefix + "fingerprint", ""),
                enabled = properties.getProperty(prefix + "enabled", "true").toBoolean(),
            )
        }
    }

    fun save(stores: List<ExtensionStore>) = synchronized(lock) {
        val uniqueStores = stores.filter { it.url.isNotBlank() }.distinctBy { it.url.trimEnd('/').lowercase() }
        val properties = Properties().apply {
            setProperty("count", uniqueStores.size.toString())
            uniqueStores.forEachIndexed { index, store ->
                val prefix = "store.$index."
                setProperty(prefix + "id", store.id)
                setProperty(prefix + "url", store.url)
                setProperty(prefix + "name", store.name)
                setProperty(prefix + "shortName", store.shortName)
                setProperty(prefix + "website", store.website)
                setProperty(prefix + "fingerprint", store.signingKeyFingerprint)
                setProperty(prefix + "enabled", store.enabled.toString())
            }
        }
        Files.newOutputStream(file).use { properties.store(it, "ZERAVRIX extension stores") }
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
