package com.zeravrix.zeravrix.source

import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

/** Trusted-extension loader. A JAR executes with the same user privileges as the app. */
class ExtensionLoader {
    private val loaders = mutableListOf<URLClassLoader>()
    private val loadedJars = mutableSetOf<Path>()

    @Synchronized
    fun loadDirectory(directory: Path): List<Source> {
        if (!Files.isDirectory(directory)) return emptyList()
        val result = mutableListOf<Source>()
        Files.list(directory).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".jar", true) }
                .sorted()
                .forEach { result += loadJar(it) }
        }
        return result
    }

    @Synchronized
    fun loadJar(jar: Path): List<Source> {
        require(Files.isRegularFile(jar)) { "Not a file: $jar" }
        val normalized = jar.toAbsolutePath().normalize()
        if (!loadedJars.add(normalized)) return emptyList()
        val loader = URLClassLoader(arrayOf(normalized.toUri().toURL()), javaClass.classLoader)
        loaders += loader
        return ServiceLoader.load(SourceProvider::class.java, loader).iterator().asSequence().map { it.create() }.toList()
    }

    @Synchronized
    fun close() { loaders.forEach { it.close() }; loaders.clear(); loadedJars.clear() }
}
