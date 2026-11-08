package com.zeravrix.zeravrix.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties

/** Small dependency-free desktop persistence layer. Replaceable by Room/SQLDelight later without changing UI. */
class LibraryStore(private val root: Path) {
    private val libraryDir = root.resolve("library")
    private val progressDir = root.resolve("progress")
    private val chapterCacheDir = root.resolve("chapter-cache")
    private val historyDir = root.resolve("history")

    init {
        Files.createDirectories(libraryDir)
        Files.createDirectories(progressDir)
        Files.createDirectories(chapterCacheDir)
        Files.createDirectories(historyDir)
    }

    @Synchronized fun list(): List<Manga> = Files.list(libraryDir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".properties") }
            .map { runCatching { readManga(it) }.getOrNull() }
            .toList()
            .filterNotNull()
            .sortedBy { it.title.lowercase() }
    }

    @Synchronized fun recordHistory(manga: Manga) {
        val p = Properties().apply {
            setProperty("id", manga.id)
            setProperty("title", manga.title)
            setProperty("coverUrl", manga.coverUrl.orEmpty())
            setProperty("description", manga.description)
            setProperty("sourceId", manga.sourceId.toString())
            setProperty("author", manga.author.orEmpty())
            setProperty("artist", manga.artist.orEmpty())
            setProperty("genres", manga.genres.joinToString("|"))
            setProperty("status", manga.status.orEmpty())
            manga.cachedChapterCount?.let { setProperty("cachedChapterCount", it.toString()) }
            manga.url?.let { setProperty("url", it) }
            setProperty("lastAccessed", System.currentTimeMillis().toString())
        }
        Files.newOutputStream(historyFile(manga.id)).use { p.store(it, "ZERAVRIX reading history") }
    }

    @Synchronized fun listHistory(): List<Manga> {
        val historyMap = mutableMapOf<String, Manga>()
        if (Files.exists(historyDir)) {
            Files.list(historyDir).use { stream ->
                stream.filter { it.fileName.toString().endsWith(".properties") }
                    .forEach { file ->
                        runCatching { readManga(file) }.getOrNull()?.let {
                            historyMap[it.id] = it
                        }
                    }
            }
        }
        list().forEach { manga ->
            if (latestProgress(manga.id) != null) {
                historyMap.putIfAbsent(manga.id, manga)
            }
        }
        return historyMap.values.toList()
    }

    @Synchronized fun contains(mangaId: String): Boolean = Files.exists(mangaFile(mangaId))

    @Synchronized fun add(manga: Manga) {
        val p = Properties().apply {
            setProperty("id", manga.id)
            setProperty("title", manga.title)
            setProperty("coverUrl", manga.coverUrl.orEmpty())
            setProperty("description", manga.description)
            setProperty("sourceId", manga.sourceId.toString())
            setProperty("author", manga.author.orEmpty())
            setProperty("artist", manga.artist.orEmpty())
            setProperty("genres", manga.genres.joinToString("|"))
            setProperty("status", manga.status.orEmpty())
            manga.cachedChapterCount?.let { setProperty("cachedChapterCount", it.toString()) }
            manga.url?.let { setProperty("url", it) }
        }
        Files.newOutputStream(mangaFile(manga.id)).use { p.store(it, "ZERAVRIX library") }
    }

    @Synchronized fun remove(mangaId: String) {
        Files.deleteIfExists(mangaFile(mangaId))
        // Remove records written by older builds or by a source that normalized IDs differently.
        Files.list(libraryDir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".properties") }
                .forEach { file ->
                    if (runCatching { readManga(file).id == mangaId }.getOrDefault(false)) Files.deleteIfExists(file)
                }
        }
        Files.deleteIfExists(chapterCacheFile(mangaId))
        val prefix = safe(mangaId) + "_"
        Files.list(progressDir).use { stream ->
            stream.filter { it.fileName.toString().startsWith(prefix) }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Synchronized fun saveChapterCache(mangaId: String, chapters: List<Chapter>, refreshedAt: Long = System.currentTimeMillis()) {
        val properties = Properties().apply {
            setProperty("mangaId", mangaId)
            setProperty("refreshedAt", refreshedAt.toString())
            setProperty("count", chapters.size.toString())
            chapters.forEachIndexed { index, chapter ->
                val prefix = "chapter.$index."
                setProperty(prefix + "id", chapter.id)
                setProperty(prefix + "mangaId", chapter.mangaId)
                setProperty(prefix + "title", chapter.title)
                chapter.number?.let { setProperty(prefix + "number", it.toString()) }
                setProperty(prefix + "sourceId", chapter.sourceId.toString())
                chapter.volume?.let { setProperty(prefix + "volume", it.toString()) }
                chapter.scanlator?.let { setProperty(prefix + "scanlator", it) }
                chapter.uploadDate?.let { setProperty(prefix + "uploadDate", it.toString()) }
            }
        }
        Files.newOutputStream(chapterCacheFile(mangaId)).use { properties.store(it, "ZERAVRIX cached chapter summaries") }
        if (Files.exists(mangaFile(mangaId))) {
            runCatching { add(readManga(mangaFile(mangaId)).copy(cachedChapterCount = chapters.size)) }
        }
    }

    @Synchronized fun cachedChapters(mangaId: String): List<Chapter> {
        val file = chapterCacheFile(mangaId)
        if (!Files.exists(file)) return emptyList()
        val properties = Properties()
        Files.newInputStream(file).use { properties.load(it) }
        val count = properties.getProperty("count", "0").toIntOrNull() ?: return emptyList()
        return (0 until count).mapNotNull { index ->
            val prefix = "chapter.$index."
            val id = properties.getProperty(prefix + "id") ?: return@mapNotNull null
            Chapter(
                id = id,
                mangaId = properties.getProperty(prefix + "mangaId", mangaId),
                title = properties.getProperty(prefix + "title", ""),
                number = properties.getProperty(prefix + "number")?.toFloatOrNull(),
                sourceId = properties.getProperty(prefix + "sourceId", "0").toLongOrNull() ?: 0L,
                volume = properties.getProperty(prefix + "volume")?.toIntOrNull(),
                scanlator = properties.getProperty(prefix + "scanlator"),
                uploadDate = properties.getProperty(prefix + "uploadDate")?.toLongOrNull(),
            )
        }
    }

    @Synchronized fun cachedChapterRefreshTime(mangaId: String): Long {
        val file = chapterCacheFile(mangaId)
        if (!Files.exists(file)) return 0L
        val properties = Properties()
        Files.newInputStream(file).use { properties.load(it) }
        return properties.getProperty("refreshedAt", "0").toLongOrNull() ?: 0L
    }

    @Synchronized fun saveProgress(progress: ReadingProgress) {
        val p = Properties().apply {
            setProperty("mangaId", progress.mangaId)
            setProperty("chapterId", progress.chapterId)
            setProperty("pageIndex", progress.pageIndex.toString())
            progress.chapterNumber?.let { setProperty("chapterNumber", it.toString()) }
            setProperty("updatedAt", progress.updatedAt.toString())
        }
        Files.newOutputStream(progressFile(progress.mangaId, progress.chapterId)).use { p.store(it, "ZERAVRIX reading progress") }
    }

    @Synchronized fun latestProgressSnapshot(mangaIds: Set<String>): Map<String, ReadingProgress> = Files.list(progressDir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".properties") }
            .toList()
            .mapNotNull { file ->
                runCatching {
                    val p = Properties()
                    Files.newInputStream(file).use { p.load(it) }
                    val mangaId = p.getProperty("mangaId") ?: return@runCatching null
                    if (mangaId !in mangaIds) null else mangaId to ReadingProgress(
                        mangaId,
                        p.getProperty("chapterId", ""),
                        p.getProperty("pageIndex", "0").toIntOrNull() ?: 0,
                        p.getProperty("chapterNumber")?.toFloatOrNull(),
                        p.getProperty("updatedAt", "0").toLongOrNull() ?: 0L,
                    )
                }.getOrNull()
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, values) -> values.maxBy { it.updatedAt } }
    }

    @Synchronized fun cachedChapterSnapshot(mangaIds: Set<String>): Map<String, List<Chapter>> = mangaIds.associateWith { cachedChapters(it) }

    @Synchronized fun latestProgress(mangaId: String): ReadingProgress? = Files.list(progressDir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".properties") }
            .toList()
            .mapNotNull { file ->
                runCatching {
                    val p = Properties()
                    Files.newInputStream(file).use { p.load(it) }
                    if (p.getProperty("mangaId") != mangaId) null else ReadingProgress(
                        p.getProperty("mangaId", mangaId),
                        p.getProperty("chapterId", ""),
                        p.getProperty("pageIndex", "0").toIntOrNull() ?: 0,
                        p.getProperty("chapterNumber")?.toFloatOrNull(),
                        p.getProperty("updatedAt", "0").toLongOrNull() ?: 0L,
                    )
                }.getOrNull()
            }
            .maxByOrNull { it.updatedAt }
    }

    @Synchronized fun readProgress(mangaId: String, chapterId: String): ReadingProgress? {
        val file = progressFile(mangaId, chapterId)
        if (!Files.exists(file)) return null
        val p = Properties()
        Files.newInputStream(file).use { p.load(it) }
        return ReadingProgress(
            p.getProperty("mangaId", mangaId),
            p.getProperty("chapterId", chapterId),
            p.getProperty("pageIndex", "0").toIntOrNull() ?: 0,
            p.getProperty("chapterNumber")?.toFloatOrNull(),
            p.getProperty("updatedAt", "0").toLongOrNull() ?: 0L,
        )
    }

    private fun readManga(file: Path): Manga {
        val p = Properties()
        Files.newInputStream(file).use { p.load(it) }
        return Manga(
            id = p.getProperty("id"), title = p.getProperty("title", ""),
            coverUrl = p.getProperty("coverUrl").takeUnless { it.isNullOrBlank() },
            description = p.getProperty("description", ""), sourceId = p.getProperty("sourceId", "0").toLong(),
            author = p.getProperty("author").takeUnless { it.isNullOrBlank() },
            artist = p.getProperty("artist").takeUnless { it.isNullOrBlank() },
            genres = p.getProperty("genres", "").split('|').filter { it.isNotBlank() },
            status = p.getProperty("status").takeUnless { it.isNullOrBlank() },
            cachedChapterCount = p.getProperty("cachedChapterCount")?.toIntOrNull(),
            url = p.getProperty("url").takeUnless { it.isNullOrBlank() },
        )
    }

    private fun safe(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    private fun mangaFile(id: String) = libraryDir.resolve("${safe(id)}.properties")
    private fun historyFile(id: String) = historyDir.resolve("${safe(id)}.properties")
    private fun chapterCacheFile(mangaId: String) = chapterCacheDir.resolve("${safe(mangaId)}.properties")
    private fun progressFile(mangaId: String, chapterId: String): Path {
        val mangaPart = safe(mangaId)
        val chapterPart = safe(chapterId)
        return progressDir.resolve("${mangaPart}_${chapterPart}.properties")
    }
}
