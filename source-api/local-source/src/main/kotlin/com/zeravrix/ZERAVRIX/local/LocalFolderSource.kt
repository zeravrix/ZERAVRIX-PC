package com.zeravrix.zeravrix.local

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.source.CatalogueSource
import com.zeravrix.zeravrix.source.MangaPageResult
import com.zeravrix.zeravrix.source.SourceCapabilities
import com.zeravrix.zeravrix.source.SourceProvider
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bundled test source that reads user-owned manga from a local directory.
 * Layout: <root>/<manga>/<chapter>/*.{jpg,jpeg,png,webp}
 */
class LocalFolderSource : CatalogueSource {
    override val id: Long = 6504664879555235651L
    override val name: String = "Local Library"
    override val language: String = "local"
    override val baseUrl: String = "file://"
    override val capabilities: SourceCapabilities = SourceCapabilities(search = true)

    private val root: Path = Path.of(System.getProperty("user.home"), ".zeravrix", "local-library")

    private fun mangaDirs(): List<Path> =
        if (Files.isDirectory(root)) Files.list(root).use { it.filter(Files::isDirectory).sorted().toList() }
        else emptyList()

    private fun chapterDirs(manga: Path): List<Path> =
        Files.list(manga).use { it.filter(Files::isDirectory).sorted().toList() }

    override suspend fun search(page: Int, query: String): MangaPageResult {
        val q = query.trim().lowercase()
        val items = mangaDirs().filter { q.isBlank() || it.fileName.toString().lowercase().contains(q) }.map { dir ->
            Manga(id = dir.toAbsolutePath().toString(), title = dir.fileName.toString(), sourceId = id)
        }
        return MangaPageResult(items, false)
    }

    override suspend fun details(manga: Manga): Manga =
        manga.copy(description = "Local manga from ${manga.id}")

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
    override fun create(): CatalogueSource = LocalFolderSource()
}
