package com.zeravrix.zeravrix.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Semaphore

/** Persistent Windows downloader with resumable pages and offline chapter manifests. */
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
)

class DesktopDownloader(private val root: Path, maxConcurrent: Int = 4) {
    private val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
    private val slots = Semaphore(maxConcurrent.coerceIn(1, 12))
    private val downloadsRoot = root.resolve("downloads")

    suspend fun download(url: String, target: Path, headers: Map<String, String> = emptyMap()): Path = withContext(Dispatchers.IO) {
        if (isValidImageFile(target)) return@withContext target
        Files.deleteIfExists(target)
        slots.acquire()
        try {
            Files.createDirectories(target.parent)
            val builder = HttpRequest.newBuilder(URI(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "ZERAVRIX/1.0.3 (Windows; Desktop)")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .GET()
            headers.forEach { (k, v) -> builder.header(k, v) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for $url" }
            val body = response.body()
            val contentType = response.headers().firstValue("content-type").orElse("").lowercase()
            check(body.size >= 256) { "Empty image response for $url" }
            check(!contentType.contains("text/html") && !looksLikeHtml(body)) { "HTML or verification response received for $url" }
            check(looksLikeImage(body)) { "Response is not a decodable image for $url" }
            val tmp = target.resolveSibling(target.fileName.toString() + ".part")
            Files.write(tmp, body)
            check(isValidImageFile(tmp)) { "Downloaded image failed local validation for $url" }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            target
        } finally {
            slots.release()
        }
    }

    suspend fun downloadChapter(
        manga: Manga,
        chapter: Chapter,
        pages: List<MangaPage>,
        onProgress: (suspend (done: Int, total: Int) -> Unit)? = null,
    ): DownloadedChapter = withContext(Dispatchers.IO) {
        val dir = chapterDir(manga.id, chapter.id)
        Files.createDirectories(dir)
        pages.forEachIndexed { index, page ->
            val target = dir.resolve("%04d%s".format(index, extensionFor(page.imageUrl)))
            download(page.imageUrl, target, page.headers + (page.referer?.let { mapOf("Referer" to it) } ?: emptyMap()))
            check(isValidImageFile(target)) { "Offline validation failed for page ${index + 1} of ${chapter.title}" }
            onProgress?.invoke(index + 1, pages.size)
        }
        val stamp = System.currentTimeMillis()
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
        }
        val tmp = dir.resolve("chapter.properties.part")
        Files.newOutputStream(tmp).use { metadata.store(it, "ZERAVRIX downloaded chapter") }
        Files.move(tmp, manifestFile(dir), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        DownloadedChapter(manga.id, manga.sourceId, manga.title, manga.coverUrl, chapter.id, chapter.title, chapter.number, dir, pages.size, stamp)
    }

    fun isDownloaded(mangaId: String, chapterId: String): Boolean = readManifest(chapterDir(mangaId, chapterId)) != null

    fun downloadedChapters(mangaId: String? = null): List<DownloadedChapter> {
        if (!Files.isDirectory(downloadsRoot)) return emptyList()
        val result = mutableListOf<DownloadedChapter>()
        Files.list(downloadsRoot).use { mangaDirs ->
            mangaDirs.filter(Files::isDirectory).forEach { mangaDir ->
                Files.list(mangaDir).use { chapterDirs ->
                    chapterDirs.filter(Files::isDirectory).forEach { chapterDir ->
                        readManifest(chapterDir)?.let { chapter ->
                            if (mangaId == null || chapter.mangaId == mangaId) result += chapter
                        }
                    }
                }
            }
        }
        return result.sortedWith(compareBy<DownloadedChapter> { it.mangaTitle.lowercase() }.thenBy { it.number ?: Float.POSITIVE_INFINITY }.thenBy { it.chapterTitle })
    }

    fun localPages(chapter: DownloadedChapter): List<MangaPage> {
        if (!Files.isDirectory(chapter.directory)) return emptyList()
        val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "img")
        val files = Files.list(chapter.directory).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in extensions }
                .filter(::isValidImageFile)
                .sorted()
                .toList()
        }
        return files.mapIndexed { index, path -> MangaPage(index, path.toAbsolutePath().toUri().toString()) }
    }

    fun removeChapter(chapter: DownloadedChapter) {
        if (!Files.isDirectory(chapter.directory)) return
        Files.walk(chapter.directory).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    fun chapterDir(mangaId: String, chapterId: String): Path = downloadsRoot.resolve(safe(mangaId)).resolve(safe(chapterId))

    private fun readManifest(dir: Path): DownloadedChapter? = runCatching {
        val props = Properties()
        Files.newInputStream(manifestFile(dir)).use { props.load(it) }
        DownloadedChapter(
            mangaId = props.getProperty("mangaId") ?: return@runCatching null,
            sourceId = props.getProperty("sourceId", "0").toLongOrNull() ?: 0L,
            mangaTitle = props.getProperty("mangaTitle", "Downloaded manga"),
            mangaCoverUrl = props.getProperty("mangaCoverUrl").orEmpty().ifBlank { null },
            chapterId = props.getProperty("chapterId") ?: return@runCatching null,
            chapterTitle = props.getProperty("chapterTitle", dir.fileName.toString()),
            number = props.getProperty("number").orEmpty().toFloatOrNull(),
            directory = dir,
            pageCount = props.getProperty("pageCount", "0").toIntOrNull() ?: 0,
            downloadedAt = props.getProperty("downloadedAt", "0").toLongOrNull() ?: 0L,
        ).also { chapter ->
            check(chapter.pageCount > 0 && localPageFiles(dir).size == chapter.pageCount) { "Incomplete download manifest" }
        }
    }.getOrNull()

    private fun manifestFile(dir: Path): Path = dir.resolve("chapter.properties")
    private fun safe(value: String) = value.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }

    private fun localPageFiles(dir: Path): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        val extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "img")
        return Files.list(dir).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in extensions }
                .filter(::isValidImageFile)
                .sorted()
                .toList()
        }
    }

    private fun isValidImageFile(path: Path): Boolean = runCatching {
        if (!Files.isRegularFile(path) || Files.size(path) < 256) return@runCatching false
        Files.newInputStream(path).use { input -> looksLikeImage(input.readNBytes(16)) }
    }.getOrDefault(false)

    private fun looksLikeImage(bytes: ByteArray): Boolean =
        bytes.size >= 4 && (
            bytes.copyOfRange(0, 3).contentEquals(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) ||
                bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) ||
                bytes.copyOfRange(0, 4).contentEquals("GIF8".toByteArray()) ||
                bytes.copyOfRange(0, 2).contentEquals("BM".toByteArray()) ||
                (bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) ||
                bytes.copyOfRange(0, 4).contentEquals("\u0000\u0000\u0000\u000c".toByteArray())
        )

    private fun looksLikeHtml(bytes: ByteArray): Boolean = bytes.decodeToString().trimStart().startsWith("<", ignoreCase = false)

    private fun extensionFor(url: String): String {
        val raw = runCatching { URI(url).path.substringAfterLast('.', "") }.getOrDefault("").lowercase()
        return when (raw) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> ".${raw}"
            else -> ".img"
        }
    }
}
