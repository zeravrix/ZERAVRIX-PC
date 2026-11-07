package com.zeravrix.ZERAVRIX.local

import com.zeravrix.ZERAVRIX.core.Chapter
import com.zeravrix.ZERAVRIX.core.Manga
import com.zeravrix.ZERAVRIX.core.MangaPage
import com.zeravrix.ZERAVRIX.source.CatalogueSource
import com.zeravrix.ZERAVRIX.source.MangaPageResult
import com.zeravrix.ZERAVRIX.source.SourceCapabilities
import com.zeravrix.ZERAVRIX.source.SourceProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Safe bundled test source. It reads manga that the user owns from a local directory.
 * Folder layout: <root>/<manga>/<chapter>/*.{jpg,jpeg,png,webp}
 */
class LocalFolderSource : CatalogueSource {
    override val id: Long = 0x5A455241564C4F43L
    override val name = "Local Library"
    override val language = "local"
    override val baseUrl = "file://"
    override val capabilities = SourceCapabilities(search = true)

    private val root: Path = Path.of(System.getProperty("user.home"), ".zeravrix", "local-library")

    private fun mangaDirs(): List<Path> = if (Files.isDirectory(root)) Files.list(root).use { it.filter(Files::isDirectory).sorted().toList() } else emptyList()
    private fun chapterDirs(manga: Path): List<Path> = Files.list(manga).use { it.filter(Files::isDirectory).sorted().toList() }

    override suspend fun search(page: Int, query: String): MangaPageResult {
        val q = query.trim().lowercase()
        val items = mangaDirs().filter { q.isBlank() || it.fileName.toString().lowercase().contains(q) }.map { dir ->
            Manga(id = dir.toAbsolutePath().toString(), title = dir.fileName.toString(), sourceId = id)
        }
        return MangaPageResult(items, false)
    }

    override suspend fun details(manga: Manga) = manga.copy(description = "Local manga from ${manga.id}")

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val dir = Path.of(manga.id)
        return chapterDirs(dir).mapIndexed { index, c ->
            Chapter(id = c.toAbsolutePath().toString(), mangaId = manga.id, title = c.fileName.toString(), number = index + 1f, sourceId = id)
        }
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val dir = Path.of(chapter.id)
        val exts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        return Files.list(dir).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { exts.contains(it.fileName.toString().substringAfterLast('.', "").lowercase()) }
                .sorted()
                .mapIndexed { index, p -> MangaPage(index, p.toAbsolutePath().toUri().toString(), null, emptyMap(), false) }
                .toList()
        }
    }
}

class LocalFolderSourceProvider : SourceProvider {
    override fun create() = LocalFolderSource()
}
