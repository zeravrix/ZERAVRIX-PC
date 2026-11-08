package com.zeravrix.zeravrix.core

import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** One image file that goes into a chapter archive. */
data class ArchiveImage(val entryName: String, val file: Path)

/**
 * Per-chapter CBZ: a plain ZIP in "stored" mode (no compression, original image bytes untouched).
 * Written to a temp file on the same drive, flushed to disk, then renamed atomically, so a crash can never
 * leave a half-written `.cbz` behind.
 */
object ChapterArchive {
    val IMAGE_EXTENSIONS: Set<String> = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "jxl")

    fun write(target: Path, images: List<ArchiveImage>, comicInfoXml: String? = null, manifestJson: String? = null) {
        require(images.isNotEmpty()) { "A chapter archive needs at least one image" }
        require(images.map { it.entryName }.toSet().size == images.size) { "Duplicate entry names" }
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        val tmp = target.resolveSibling(target.fileName.toString() + ".part")
        try {
            Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { raw ->
                ZipOutputStream(raw.buffered(256 * 1024)).use { zip ->
                    zip.setMethod(ZipOutputStream.STORED)
                    images.forEach { putStored(zip, it.entryName, it.file) }
                    comicInfoXml?.let { putStoredBytes(zip, "ComicInfo.xml", it.toByteArray(Charsets.UTF_8)) }
                    manifestJson?.let { putStoredBytes(zip, "zeravrix.json", it.toByteArray(Charsets.UTF_8)) }
                }
            }
            FileChannel.open(tmp, StandardOpenOption.WRITE).use { it.force(true) }
            verify(tmp, images.size)
            moveAtomically(tmp, target)
        } catch (failure: Throwable) {
            runCatching { Files.deleteIfExists(tmp) }
            throw failure
        }
    }

    /** Throws if the archive is unreadable, has fewer images than expected, or contains an empty image. */
    fun verify(archive: Path, expectedImages: Int? = null) {
        ZipFile(archive.toFile()).use { zip ->
            var count = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !isImageName(entry.name)) continue
                if (entry.size <= 0L) throw IOException("Empty image entry ${entry.name}")
                count++
            }
            if (count == 0) throw IOException("Archive has no images")
            if (expectedImages != null && count != expectedImages) throw IOException("Expected $expectedImages images but found $count")
        }
    }

    /** Image entry names in reading order (natural sort, so 2 comes before 10). */
    fun imageEntries(archive: Path): List<String> = ZipFile(archive.toFile()).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory && isImageName(it.name) }
            .map { it.name }
            .sortedWith(NaturalOrder)
            .toList()
    }

    fun readEntry(archive: Path, entryName: String): ByteArray = ZipFile(archive.toFile()).use { zip ->
        val entry = zip.getEntry(entryName) ?: throw IOException("No entry $entryName in ${archive.fileName}")
        zip.getInputStream(entry).use(InputStream::readBytes)
    }

    fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    private fun putStored(zip: ZipOutputStream, name: String, file: Path) {
        val size = Files.size(file)
        val crc = CRC32()
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                crc.update(buffer, 0, read)
            }
        }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            this.size = size
            compressedSize = size
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        Files.copy(file, zip)
        zip.closeEntry()
    }

    private fun putStoredBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun moveAtomically(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Compares names by number runs, e.g. "page2.jpg" < "page10.jpg". */
    private object NaturalOrder : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                if (a[i].isDigit() && b[j].isDigit()) {
                    var ei = i
                    while (ei < a.length && a[ei].isDigit()) ei++
                    var ej = j
                    while (ej < b.length && b[ej].isDigit()) ej++
                    val na = a.substring(i, ei).trimStart('0')
                    val nb = b.substring(j, ej).trimStart('0')
                    if (na.length != nb.length) return na.length - nb.length
                    val c = na.compareTo(nb)
                    if (c != 0) return c
                    i = ei
                    j = ej
                } else {
                    val c = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                    if (c != 0) return c
                    i++
                    j++
                }
            }
            return (a.length - i) - (b.length - j)
        }
    }
}
