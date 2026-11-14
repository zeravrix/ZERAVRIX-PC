package com.zeravrix.zeravrix.jvmhost

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.source.AzoraMihonSource
import com.zeravrix.zeravrix.source.DilarMihonSource
import com.zeravrix.zeravrix.source.HijalaMihonSource
import com.zeravrix.zeravrix.source.KavitaSource
import com.zeravrix.zeravrix.source.KomgaSource
import com.zeravrix.zeravrix.source.MangaSparkMihonSource
import com.zeravrix.zeravrix.source.MangaSwatMihonSource
import com.zeravrix.zeravrix.source.MangalekMihonSource
import com.zeravrix.zeravrix.source.Source
import com.zeravrix.zeravrix.source.SourceBlockedException
import com.zeravrix.zeravrix.source.SourceManager
import com.zeravrix.zeravrix.source.TeamXMihonSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Compatibility-first host registry. The native application will talk to this
 * component over IPC; it never needs a reference to a JVM class loader.
 */
class JvmExtensionHost(
    private val root: Path,
    private val includeBuiltIns: Boolean = true,
) : AutoCloseable {
    private val sourceDirectory = root.resolve("sources")
    private val enabledStateFile = root.resolve("extension-enabled.properties")
    private val sourceManager = SourceManager()
    private val enabledSourceIds = mutableSetOf<Long>()
    private val disabledSourceIds = mutableSetOf<Long>()
    private val sourceJarById = mutableMapOf<Long, Path>()
    private val lock = Any()

    init {
        Files.createDirectories(sourceDirectory)
        synchronized(lock) {
            loadDisabledState()
            if (includeBuiltIns) sourceManager.installAll(BuiltInSources.create(root))
            val loaded = loadInstalledExtensionFiles()
            enabledSourceIds += sourceManager.all().map { it.id }.filterNot { it in disabledSourceIds }
            check(loaded.all { it.id in sourceJarById }) { "Extension registry lost JAR ownership" }
        }
    }

    fun listSources(): List<HostSourceInfo> = synchronized(lock) {
        sourceManager.all().map { it.toHostInfo(it.id in enabledSourceIds) }
    }

    /** Reloads all installed JARs, making an updated package visible to the host. */
    fun loadInstalledExtensions(): List<HostSourceInfo> = synchronized(lock) {
        reloadAll().map { it.toHostInfo(it.id in enabledSourceIds) }
    }

    fun installJar(jar: Path): HostCallResult<List<HostSourceInfo>> = synchronized(lock) {
        runCatching {
            require(Files.isRegularFile(jar)) { "Extension JAR does not exist: $jar" }
            require(jar.fileName.toString().endsWith(".jar", ignoreCase = true)) { "Only .jar extensions are supported" }
            val destination = sourceDirectory.resolve(jar.fileName.toString()).normalize()
            require(destination.parent == sourceDirectory.normalize()) { "Unsafe extension path" }
            val update = sourceJarById.values.any { it.toAbsolutePath().normalize() == destination.toAbsolutePath().normalize() }
            if (update) sourceManager.close()
            if (jar.toAbsolutePath().normalize() != destination.toAbsolutePath().normalize()) {
                Files.copy(jar, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            val installed = if (update) {
                reloadAll().filter { sourceJarById[it.id]?.toAbsolutePath()?.normalize() == destination.toAbsolutePath().normalize() }
            } else {
                val loaded = sourceManager.loadJar(destination)
                loaded.forEach { sourceJarById[it.id] = destination }
                enabledSourceIds += loaded.map { it.id }.filterNot { it in disabledSourceIds }
                loaded
            }
            installed.map { it.toHostInfo(it.id in enabledSourceIds) }
        }.fold(
            onSuccess = { HostCallResult.Success(it) },
            onFailure = { HostCallResult.Failure("EXTENSION_INSTALL_FAILED", it.message ?: "Unable to install extension") },
        )
    }

    /** Removes the complete JAR package that owns the selected source. */
    fun removeExtension(sourceId: Long): HostCallResult<Unit> = synchronized(lock) {
        val jar = sourceJarById[sourceId]
            ?: return@synchronized HostCallResult.Failure("NOT_EXTERNAL_EXTENSION", "Source $sourceId is built into the host")
        runCatching {
            sourceManager.close()
            Files.deleteIfExists(jar)
            disabledSourceIds.remove(sourceId)
            reloadAll()
        }.fold(
            onSuccess = { HostCallResult.Success(Unit) },
            onFailure = { HostCallResult.Failure("EXTENSION_REMOVE_FAILED", it.message ?: "Unable to remove extension") },
        )
    }

    fun setEnabled(sourceId: Long, enabled: Boolean): HostCallResult<Unit> = synchronized(lock) {
        if (sourceManager.get(sourceId) == null) {
            HostCallResult.Failure("SOURCE_NOT_FOUND", "Source $sourceId is not installed")
        } else {
            if (enabled) {
                enabledSourceIds += sourceId
                disabledSourceIds -= sourceId
            } else {
                enabledSourceIds -= sourceId
                disabledSourceIds += sourceId
            }
            saveDisabledState()
            HostCallResult.Success(Unit)
        }
    }

    suspend fun execute(operation: HostOperation, timeout: Duration = 45.seconds): HostCallResult<HostOperationResult> {
        val sourceId = when (operation) {
            is HostOperation.Popular -> operation.sourceId
            is HostOperation.Latest -> operation.sourceId
            is HostOperation.Search -> operation.sourceId
            is HostOperation.Details -> operation.sourceId
            is HostOperation.Chapters -> operation.sourceId
            is HostOperation.Pages -> operation.sourceId
        }
        val source = synchronized(lock) { sourceManager.get(sourceId) }
            ?: return HostCallResult.Failure("SOURCE_NOT_FOUND", "Source $sourceId is not installed")
        if (!synchronized(lock) { sourceId in enabledSourceIds }) {
            return HostCallResult.Failure("SOURCE_DISABLED", "Source $sourceId is disabled")
        }
        return try {
            val result = withTimeout(timeout) {
                when (operation) {
                    is HostOperation.Popular -> HostOperationResult.MangaPage(source.popular(operation.page))
                    is HostOperation.Latest -> HostOperationResult.MangaPage(source.latest(operation.page))
                    is HostOperation.Search -> HostOperationResult.MangaPage(source.search(operation.page, operation.query))
                    is HostOperation.Details -> HostOperationResult.MangaDetails(source.details(operation.manga))
                    is HostOperation.Chapters -> HostOperationResult.ChapterList(source.chapters(operation.manga))
                    is HostOperation.Pages -> HostOperationResult.PageList(source.pages(operation.chapter))
                }
            }
            HostCallResult.Success(result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (timeoutFailure: kotlinx.coroutines.TimeoutCancellationException) {
            HostCallResult.Failure("TIMEOUT", "Source operation exceeded ${timeout.inWholeSeconds}s", retryable = true)
        } catch (blocked: SourceBlockedException) {
            HostCallResult.Failure("SOURCE_BLOCKED", blocked.message ?: "Source verification is required", retryable = false)
        } catch (failure: Throwable) {
            HostCallResult.Failure(
                code = "SOURCE_ERROR",
                message = failure.message ?: failure::class.simpleName.orEmpty().ifBlank { "Source operation failed" },
                retryable = true,
            )
        }
    }

    override fun close() {
        synchronized(lock) {
            enabledSourceIds.clear()
            sourceJarById.clear()
            sourceManager.close()
        }
    }

    private fun loadDisabledState() {
        if (!Files.isRegularFile(enabledStateFile)) return
        val properties = Properties()
        runCatching { Files.newInputStream(enabledStateFile).use(properties::load) }
            .onSuccess { disabledSourceIds += properties.stringPropertyNames().mapNotNull { name -> name.removePrefix("disabled.").toLongOrNull() } }
    }

    private fun saveDisabledState() {
        val properties = Properties()
        disabledSourceIds.forEach { properties.setProperty("disabled.$it", "true") }
        Files.newOutputStream(enabledStateFile).use { properties.store(it, "ZERAVRIX JVM host source state") }
    }

    private fun reloadAll(): List<Source> {
        sourceManager.close()
        sourceJarById.clear()
        if (includeBuiltIns) sourceManager.installAll(BuiltInSources.create(root))
        val loaded = loadInstalledExtensionFiles()
        enabledSourceIds.clear()
        enabledSourceIds += sourceManager.all().map { it.id }.filterNot { it in disabledSourceIds }
        return loaded
    }

    private fun loadInstalledExtensionFiles(): List<Source> {
        val loaded = mutableListOf<Source>()
        Files.list(sourceDirectory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .sorted()
                .forEach { jar ->
                    runCatching { sourceManager.loadJar(jar) }
                        .onSuccess { sources ->
                            sources.forEach { sourceJarById[it.id] = jar }
                            loaded += sources
                        }
                }
        }
        return loaded
    }

    private fun Source.toHostInfo(enabled: Boolean) = HostSourceInfo(
        id = id,
        name = name,
        language = language,
        baseUrl = baseUrl,
        popular = capabilities.popular,
        latest = capabilities.latest,
        search = capabilities.search,
        filters = capabilities.filters,
        enabled = enabled,
    )
}

private object BuiltInSources {
    fun create(root: Path): List<Source> {
        val base = listOf(
            MangaSparkMihonSource(),
            MangalekMihonSource(),
            TeamXMihonSource(),
            MangaSwatMihonSource(),
            HijalaMihonSource(root),
            DilarMihonSource(),
            AzoraMihonSource(),
            KomgaSource(root),
            KavitaSource(root),
        )
        return try {
            val loaderContext = com.zeravrix.zeravrix.source.HeadlessMangaLoaderContext(root)
            val kotatsu = org.koitharu.kotatsu.parsers.model.MangaParserSource.entries
                .filter { !it.isBroken && it.contentType != org.koitharu.kotatsu.parsers.model.ContentType.HENTAI }
                .map { com.zeravrix.zeravrix.source.KotatsuSourceAdapter(it, loaderContext) }
            base + kotatsu
        } catch (_: Throwable) {
            base
        }
    }
}
