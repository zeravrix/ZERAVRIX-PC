package com.zeravrix.zeravrix.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** A chapter saved on disk. [archive] is the per-chapter CBZ; it is null only for old folder-style downloads. */
data class DownloadedChapter(
    val mangaId: String,
    val sourceId: Long,
    val mangaTitle: String,
    val mangaCoverUrl: String?,
    val chapterId: String,
    val chapterTitle: String,
    val number: Float?,
    val directory: Path,
    val pageCount: Int,
    val downloadedAt: Long,
    val archive: Path? = null,
)

/**
 * Windows downloader (blueprint section 19).
 *
 *  - pages download in parallel ([maxConcurrent] at a time) and are streamed to disk, never held whole in memory
 *  - the first 512 bytes are sniffed, so an HTML / Cloudflare page is rejected without reading the rest
 *  - failed pages retry with backoff (2 s, 4 s, 8 s) and honour `Retry-After`
 *  - a stalled connection (no bytes for [stallTimeoutMs]) is cut and retried
 *  - pages that already downloaded survive a failure or a restart, so the chapter resumes where it stopped
 *  - the finished chapter is packed into one stored (uncompressed) CBZ; original image bytes are never re-encoded
 *  - the list of downloads is kept in memory, so screens never rescan the disk
 */
class DesktopDownloader(
    private val root: Path,
    maxConcurrent: Int = 4,
    private val retryDelaysMs: List<Long> = listOf(2_000L, 4_000L, 8_000L),
    private val stallTimeoutMs: Long = 60_000L,
) {
    private val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
    private val slots = Semaphore(maxConcurrent.coerceIn(1, 12))
    private val downloadsRoot = root.resolve("downloads")

    private val indexLock = Any()
    private var index: MutableMap<String, DownloadedChapter>? = null

    /** Downloads one image to [target] (extension is corrected from the real image type). Returns the final path. */
    suspend fun download(url: String, target: Path, headers: Map<String, String> = emptyMap()): Path = withContext(Dispatchers.IO) {
        if (isValidImageFile(target)) return@withContext target
        Files.createDirectories(target.toAbsolutePath().parent)
        var lastFailure: Throwable? = null
        for (attempt in 0..retryDelaysMs.size) {
            try {
                return@withContext slots.withPermit { fetchToFile(url, target, headers) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: NonRetryableDownloadException) {
                throw failure
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt >= retryDelaysMs.size) break
                val wait = (failure as? RetryAfterException)?.waitMs ?: retryDelaysMs[attempt]
                delay(wait.coerceAtMost(MAX_RETRY_WAIT_MS))
            }
        }
        throw IOException("Failed to download $url: ${lastFailure?.message}", lastFailure)
    }

    suspend fun downloadChapter(
        manga: Manga,
        chapter: Chapter,
        pages: List<MangaPage>,
        onProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
    ): DownloadedChapter = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "Chapter ${chapter.title} has no pages" }
        val dir = chapterDir(manga.id, chapter.id)
        Files.createDirectories(dir)
        val work = dir.resolve("pages.part")
        Files.createDirectories(work)

        val finished = AtomicInteger(0)
        val progressLock = Mutex()
        val images = coroutineScope {
            pages.mapIndexed { index, page ->
                async {
                    val file = downloadPageInto(work, index, page)
                    val done = finished.incrementAndGet()
                    if (onProgress != null) progressLock.withLock { onProgress(done, pages.size) }
                    ArchiveImage(file.fileName.toString(), file)
                }
            }.awaitAll()
        }

        val stamp = System.currentTimeMillis()
        val archive = dir.resolve(ARCHIVE_NAME)
        ChapterArchive.write(archive, images, comicInfoXml(manga, chapter, pages.size), manifestJson(manga, chapter, pages.size, stamp))

        val metadata = Properties().apply {
            setProperty("mangaId", manga.id)
            setProperty("sourceId", manga.sourceId.toString())
            setProperty("mangaTitle", manga.title)
            setProperty("mangaCoverUrl", manga.coverUrl.orEmpty())
            setProperty("chapterId", chapter.id)
            setProperty("chapterTitle", chapter.title)
            setProperty("number", chapter.number?.toString().orEmpty())
            setProperty("pageCount", pages.size.toString())
            setProperty("downloadedAt", stamp.toString())
            setProperty("format", "cbz")
            setProperty("archive", ARCHIVE_NAME)
        }
        val tmp = dir.resolve("chapter.properties.part")
        Files.newOutputStream(tmp).use { metadata.store(it, "ZERAVRIX downloaded chapter") }
        moveReplacing(tmp, manifestFile(dir))
        deleteRecursively(work)

        val saved = DownloadedChapter(manga.id, manga.sourceId, manga.title, manga.coverUrl, chapter.id, chapter.title, chapter.number, dir, pages.size, stamp, archive)
        synchronized(indexLock) { loadedIndex()[key(manga.id, chapter.id)] = saved }
        saved
    }

    fun isDownloaded(mangaId: String, chapterId: String): Boolean =
        synchronized(indexLock) { loadedIndex().containsKey(key(mangaId, chapterId)) }

    fun downloadedChapters(mangaId: String? = null): List<DownloadedChapter> {
        val all = synchronized(indexLock) { loadedIndex().values.toList() }
        return all.filter { mangaId == null || it.mangaId == mangaId }
            .sortedWith(compareBy<DownloadedChapter> { it.mangaTitle.lowercase() }.thenBy { it.number ?: Float.POSITIVE_INFINITY }.thenBy { it.chapterTitle })
    }

    /** Forget the in-memory list and rescan the disk on next use (call after the user changes the folder by hand). */
    fun refreshIndex() = synchronized(indexLock) { index = null }

    /** Pages of a saved chapter. CBZ pages use `jar:file:///...cbz!/0000.jpg` URLs; use [readLocalPage] to get the bytes. */
    fun localPages(chapter: DownloadedChapter): List<MangaPage> {
        val archive = chapter.archive
        if (archive != null) {
            if (!Files.isRegularFile(archive)) return emptyList()
            return ChapterArchive.imageEntries(archive).mapIndexed { i, name -> MangaPage(i, "jar:${archive.toUri()}!/$name") }
        }
        return localPageFiles(chapter.directory).mapIndexed { i, p -> MangaPage(i, p.toAbsolutePath().toUri().toString()) }
    }

    /** Bytes of one saved page, read straight from the CBZ (no extraction). */
    fun readLocalPage(chapter: DownloadedChapter, index: Int): ByteArray {
        val archive = chapter.archive
        if (archive != null) {
            val entries = ChapterArchive.imageEntries(archive)
            return ChapterArchive.readEntry(archive, entries.getOrNull(index) ?: throw IOException("No page $index"))
        }
        return Files.readAllBytes(localPageFiles(chapter.directory).getOrNull(index) ?: throw IOException("No page $index"))
    }

    /** Full check of the CBZ (every entry present and non-empty). Used by migration and repair, not on every list. */
    fun verify(chapter: DownloadedChapter): Boolean = runCatching {
        val archive = chapter.archive ?: return false
        ChapterArchive.verify(archive, chapter.pageCount)
        true
    }.getOrDefault(false)

    fun removeChapter(chapter: DownloadedChapter) {
        if (Files.isDirectory(chapter.directory)) deleteRecursively(chapter.directory)
        synchronized(indexLock) { index?.remove(key(chapter.mangaId, chapter.chapterId)) }
    }

    fun chapterDir(mangaId: String, chapterId: String): Path = downloadsRoot.resolve(safe(mangaId)).resolve(safe(chapterId))

    // ---- internals -------------------------------------------------------------------------------------------

    private suspend fun downloadPageInto(workDir: Path, index: Int, page: MangaPage): Path {
        val prefix = "%04d".format(index)
        existingPage(workDir, prefix)?.let { return it }
        val headers = page.headers + (page.referer?.let { mapOf("Referer" to it) } ?: emptyMap())
        return download(resolveUrl(page.imageUrl, page.referer), workDir.resolve("$prefix.img"), headers)
    }

    private fun existingPage(workDir: Path, prefix: String): Path? {
        if (!Files.isDirectory(workDir)) return null
        return Files.list(workDir).use { stream ->
            stream.filter { it.fileName.toString().startsWith("$prefix.") && !it.fileName.toString().endsWith(".part") }
                .filter(::isValidImageFile).findFirst().orElse(null)
        }
    }

    private fun resolveUrl(raw: String, referer: String?): String {
        val value = raw.trim()
        val direct = URI(value)
        if (direct.isAbsolute) return value
        val base = referer?.trim()?.takeIf { it.isNotBlank() } ?: throw NonRetryableDownloadException("Relative page URL has no referer: $raw")
        return URI(base).resolve(value).toString()
    }

    private suspend fun fetchToFile(url: String, target: Path, headers: Map<String, String>): Path {
        val builder = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "ZERAVRIX/1.0 (Windows; Desktop)")
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        val code = response.statusCode()
        response.body().use { input ->
            if (code !in 200..299) {
                val wait = retryAfterMs(response.headers().firstValue("retry-after").orElse(null))
                when {
                    code == 429 || code == 503 -> throw RetryAfterException("HTTP $code for $url", wait)
                    code == 408 || code >= 500 -> throw IOException("HTTP $code for $url")
                    else -> throw NonRetryableDownloadException("HTTP $code for $url")
                }
            }
            val contentType = response.headers().firstValue("content-type").orElse("").lowercase()
            val head = input.readNBytes(SNIFF_BYTES)
            if (contentType.contains("text/html") || looksLikeHtml(head)) throw IOException("HTML or verification page received for $url")
            val extension = sniffExtension(head) ?: throw IOException("Response is not an image for $url")
            val finalTarget = target.resolveSibling(target.fileName.toString().substringBeforeLast('.') + extension)
            val tmp = finalTarget.resolveSibling(finalTarget.fileName.toString() + ".part")
            val lastByteAt = AtomicLong(System.nanoTime())
            try {
                coroutineScope {
                    val watchdog = launch {
                        while (true) {
                            delay(1_000)
                            if ((System.nanoTime() - lastByteAt.get()) / 1_000_000 > stallTimeoutMs) {
                                runCatching { input.close() }
                                break
                            }
                        }
                    }
                    try {
                        Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { out ->
                            out.write(head)
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                lastByteAt.set(System.nanoTime())
                                out.write(buffer, 0, read)
                            }
                        }
                    } finally {
                        watchdog.cancel()
                    }
                }
                if (Files.size(tmp) < MIN_IMAGE_BYTES) throw IOException("Empty or truncated image for $url")
                moveReplacing(tmp, finalTarget)
            } catch (failure: Throwable) {
                runCatching { Files.deleteIfExists(tmp) }
                throw failure
            }
            return finalTarget
        }
    }

    private fun loadedIndex(): MutableMap<String, DownloadedChapter> {
        index?.let { return it }
        val map = ConcurrentHashMap<String, DownloadedChapter>()
        if (Files.isDirectory(downloadsRoot)) {
            Files.list(downloadsRoot).use { mangaDirs ->
                mangaDirs.filter(Files::isDirectory).forEach { mangaDir ->
                    Files.list(mangaDir).use { chapterDirs ->
                        chapterDirs.filter(Files::isDirectory).forEach { chapterDir ->
                            readManifest(chapterDir)?.let { map[key(it.mangaId, it.chapterId)] = it }
                        }
                    }
                }
            }
        }
        index = map
        return map
    }

    private fun readManifest(dir: Path): DownloadedChapter? = runCatching {
        val props = Properties()
        Files.newInputStream(manifestFile(dir)).use { props.load(it) }
        val archiveName = props.getProperty("archive")?.takeIf { it.isNotBlank() }
        val archive = archiveName?.let { dir.resolve(it) }
        val pageCount = props.getProperty("pageCount", "0").toIntOrNull() ?: 0
        check(pageCount > 0) { "Incomplete download manifest" }
        if (archive != null) {
            check(Files.isRegularFile(archive) && Files.size(archive) > 0) { "Missing archive" }
        } else {
            check(localPageFiles(dir).size == pageCount) { "Incomplete download manifest" }
        }
        DownloadedChapter(
            mangaId = props.getProperty("mangaId") ?: return@runCatching null,
            sourceId = props.getProperty("sourceId", "0").toLongOrNull() ?: 0L,
            mangaTitle = props.getProperty("mangaTitle", "Downloaded manga"),
            mangaCoverUrl = props.getProperty("mangaCoverUrl").orEmpty().ifBlank { null },
            chapterId = props.getProperty("chapterId") ?: return@runCatching null,
            chapterTitle = props.getProperty("chapterTitle", dir.fileName.toString()),
            number = props.getProperty("number").orEmpty().toFloatOrNull(),
            directory = dir,
            pageCount = pageCount,
            downloadedAt = props.getProperty("downloadedAt", "0").toLongOrNull() ?: 0L,
            archive = archive,
        )
    }.getOrNull()

    private fun localPageFiles(dir: Path): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { ChapterArchive.isImageName(it.fileName.toString()) }
                .filter(::isValidImageFile)
                .sorted()
                .toList()
        }
    }

    private fun manifestFile(dir: Path): Path = dir.resolve("chapter.properties")
    private fun key(mangaId: String, chapterId: String) = "$mangaId\u0000$chapterId"
    private fun safe(value: String) = value.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }

    private fun isValidImageFile(path: Path): Boolean = runCatching {
        if (!Files.isRegularFile(path) || Files.size(path) < MIN_IMAGE_BYTES) return@runCatching false
        Files.newInputStream(path).use { input -> sniffExtension(input.readNBytes(32)) != null }
    }.getOrDefault(false)

    private fun moveReplacing(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { walk -> walk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    private fun comicInfoXml(manga: Manga, chapter: Chapter, pageCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ComicInfo>\n")
        append("  <Series>").append(xml(manga.title)).append("</Series>\n")
        append("  <Title>").append(xml(chapter.title)).append("</Title>\n")
        chapter.number?.let { append("  <Number>").append(it.toString().removeSuffix(".0")).append("</Number>\n") }
        chapter.volume?.let { append("  <Volume>").append(it).append("</Volume>\n") }
        manga.author?.takeIf { it.isNotBlank() }?.let { append("  <Writer>").append(xml(it)).append("</Writer>\n") }
        manga.artist?.takeIf { it.isNotBlank() }?.let { append("  <Penciller>").append(xml(it)).append("</Penciller>\n") }
        if (manga.genres.isNotEmpty()) append("  <Genre>").append(xml(manga.genres.joinToString(", "))).append("</Genre>\n")
        chapter.scanlator?.takeIf { it.isNotBlank() }?.let { append("  <Translator>").append(xml(it)).append("</Translator>\n") }
        append("  <PageCount>").append(pageCount).append("</PageCount>\n</ComicInfo>\n")
    }

    private fun manifestJson(manga: Manga, chapter: Chapter, pageCount: Int, stamp: Long): String =
        "{\"app\":\"ZERAVRIX\",\"format\":1," +
            "\"mangaId\":${json(manga.id)},\"sourceId\":${manga.sourceId},\"mangaTitle\":${json(manga.title)}," +
            "\"chapterId\":${json(chapter.id)},\"chapterTitle\":${json(chapter.title)}," +
            "\"number\":${chapter.number ?: "null"},\"pageCount\":$pageCount,\"downloadedAt\":$stamp}"

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun json(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    private class NonRetryableDownloadException(message: String) : IOException(message)
    private class RetryAfterException(message: String, val waitMs: Long?) : IOException(message)

    companion object {
        const val ARCHIVE_NAME = "chapter.cbz"
        private const val SNIFF_BYTES = 512
        private const val MIN_IMAGE_BYTES = 256L
        private const val MAX_RETRY_WAIT_MS = 120_000L

        /** `Retry-After` is either a number of seconds or an HTTP date. Returns milliseconds, or null if absent/invalid. */
        internal fun retryAfterMs(value: String?, now: ZonedDateTime = ZonedDateTime.now()): Long? {
            val text = value?.trim().orEmpty()
            if (text.isEmpty()) return null
            text.toLongOrNull()?.let { return (it * 1000).coerceAtLeast(0) }
            return runCatching {
                Duration.between(now, ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis().coerceAtLeast(0)
            }.getOrNull()
        }

        /** Returns the file extension (with dot) for the image type found in the first bytes, or null if it is not an image. */
        internal fun sniffExtension(b: ByteArray): String? {
            fun at(i: Int, vararg v: Int) = b.size >= i + v.size && v.indices.all { (b[i + it].toInt() and 0xFF) == v[it] }
            fun ascii(i: Int, s: String) = b.size >= i + s.length && s.indices.all { b[i + it].toInt() == s[it].code }
            return when {
                at(0, 0xFF, 0xD8, 0xFF) -> ".jpg"
                at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ".png"
                ascii(0, "GIF87a") || ascii(0, "GIF89a") -> ".gif"
                ascii(0, "RIFF") && ascii(8, "WEBP") -> ".webp"
                ascii(0, "BM") && b.size >= 14 -> ".bmp"
                ascii(4, "ftypavif") || ascii(4, "ftypavis") -> ".avif"
                ascii(4, "ftypheic") || ascii(4, "ftypheix") || ascii(4, "ftypmif1") -> ".heic"
                at(0, 0xFF, 0x0A) || at(0, 0x00, 0x00, 0x00, 0x0C, 0x4A, 0x58, 0x4C, 0x20) -> ".jxl"
                else -> null
            }
        }

        /** True if the first bytes look like a web page (Cloudflare challenge, error page, login form). */
        internal fun looksLikeHtml(head: ByteArray): Boolean {
            val text = String(head, 0, minOf(head.size, SNIFF_BYTES), Charsets.ISO_8859_1).trimStart().lowercase()
            return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head") ||
                text.startsWith("<body") || text.startsWith("<?xml") || text.startsWith("<!--") || text.startsWith("{\"error")
        }
    }
}
