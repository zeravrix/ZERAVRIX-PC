package com.zeravrix.zeravrix.core

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable point-in-time backup for ZERAVRIX library and reading progress. */
class BackupManager(private val root: Path) {
    private val allowedRoots = setOf("library/", "progress/", "history/", "settings.properties", "extension-stores.properties")

    @Synchronized
    fun exportBackup(target: Path): Path {
        target.parent?.let { Files.createDirectories(it) }
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            val files = mutableListOf<Path>()
            listOf("library", "progress", "history").forEach { directory ->
                val dir = root.resolve(directory)
                if (Files.isDirectory(dir)) {
                    Files.walk(dir).use { stream -> stream.filter(Files::isRegularFile).forEach(files::add) }
                }
            }
            listOf("settings.properties", "extension-stores.properties").map(root::resolve).filter(Files::isRegularFile).forEach(files::add)
            files.distinct().forEach { file ->
                val relative = root.relativize(file).toString().replace('\\', '/')
                zip.putNextEntry(ZipEntry(relative))
                Files.newInputStream(file).use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val manifest = "ZERAVRIX_BACKUP_VERSION=1\ncreatedAt=${Instant.now()}\n"
            zip.putNextEntry(ZipEntry("backup.properties"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        return target
    }

    @Synchronized
    fun importBackup(archive: Path): Int {
        require(Files.isRegularFile(archive)) { "Backup file does not exist" }
        var restored = 0
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory && isAllowed(name)) {
                    val target = root.resolve(name).normalize()
                    require(target.startsWith(root.normalize())) { "Unsafe backup path" }
                    target.parent?.let { Files.createDirectories(it) }
                    Files.newOutputStream(target).use { zip.copyTo(it) }
                    restored++
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return restored
    }

    private fun isAllowed(name: String): Boolean {
        return name != "backup.properties" && allowedRoots.any { name == it.removeSuffix("/") || name.startsWith(it) }
    }
}
