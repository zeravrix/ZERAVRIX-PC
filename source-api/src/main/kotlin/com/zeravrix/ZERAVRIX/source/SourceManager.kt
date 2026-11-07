package com.zeravrix.zeravrix.source

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/** Registry for trusted, manually installed manga source extensions. */
class SourceManager(sources: Collection<Source> = emptyList()) : AutoCloseable {
    private val sources = ConcurrentHashMap<Long, Source>()
    private val extensionLoader = ExtensionLoader()

    init {
        installAll(sources)
    }

    fun install(source: Source) {
        val canonical = source.baseUrl.trimEnd('/').lowercase()
        val duplicate = sources.values.firstOrNull { existing ->
            existing.id == source.id || existing.baseUrl.trimEnd('/').lowercase() == canonical
        }
        if (duplicate == null || duplicate.id == source.id) sources[source.id] = source
    }

    fun installAll(items: Collection<Source>) {
        items.forEach(::install)
    }

    fun uninstall(sourceId: Long) {
        sources.remove(sourceId)
    }

    fun get(sourceId: Long): Source? = sources[sourceId]

    fun all(): List<Source> = sources.values.sortedWith(compareBy({ it.language }, { it.name.lowercase() }))

    /** Loads one trusted desktop JAR and registers the sources it provides. */
    fun loadJar(jar: Path): List<Source> {
        val loaded = extensionLoader.loadJar(jar)
        installAll(loaded)
        return loaded
    }

    /** Loads only trusted JARs manually copied or selected into the source directory. */
    fun loadExtensions(directory: Path): List<Source> {
        val loaded = extensionLoader.loadDirectory(directory)
        installAll(loaded)
        return loaded
    }

    override fun close() {
        extensionLoader.close()
        sources.clear()
    }
}
