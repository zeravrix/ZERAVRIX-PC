package com.zeravrix.zeravrix.sources

import com.zeravrix.zeravrix.core.*
import com.zeravrix.zeravrix.data.DesktopMangaLoaderContext
import com.zeravrix.zeravrix.source.CatalogueSource
import com.zeravrix.zeravrix.source.MangaPageResult
import com.zeravrix.zeravrix.source.SourceCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance adapter bridging Kotatsu parsers with ZERAVRIX CatalogueSource architecture.
 */
class KotatsuSourceAdapter(
    val parserSource: MangaParserSource,
    val loaderContext: DesktopMangaLoaderContext
) : CatalogueSource {

    override val id: Long = 10_000L + parserSource.ordinal
    override val name: String = parserSource.title
    override val language: String = parserSource.locale
    override val baseUrl: String = parserSource.name.lowercase()

    override val capabilities: SourceCapabilities = SourceCapabilities(
        popular = true,
        latest = true,
        search = true,
        filters = false
    )

    private val cachedMangaMap = ConcurrentHashMap<String, org.koitharu.kotatsu.parsers.model.Manga>()

    suspend fun fetchList(
        page: Int = 1,
        order: SortOrder = SortOrder.POPULARITY,
        query: String? = null
    ): MangaPageResult = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val filter = if (!query.isNullOrBlank()) MangaListFilter(query = query) else MangaListFilter()
            val list = parser.getList(offset = (page - 1) * 20, order = order, filter = filter)
            val items = list.map { m ->
                cachedMangaMap[m.id.toString()] = m
                m.toCoreManga(id)
            }
            MangaPageResult(items = items, hasNextPage = items.size >= 20)
        }.getOrElse { MangaPageResult(emptyList(), false) }
    }

    override suspend fun popular(page: Int): MangaPageResult = fetchList(page, SortOrder.POPULARITY)

    override suspend fun latest(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val list = parser.getList(offset = (page - 1) * 20, order = SortOrder.UPDATED, filter = MangaListFilter())
            val items = list.map { m ->
                cachedMangaMap[m.id.toString()] = m
                m.toCoreManga(id)
            }
            MangaPageResult(items = items, hasNextPage = items.size >= 20)
        }.getOrElse { MangaPageResult(emptyList(), false) }
    }

    override suspend fun search(page: Int, query: String): MangaPageResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext MangaPageResult(emptyList(), false)
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val list = parser.getList(offset = (page - 1) * 20, order = SortOrder.RELEVANCE, filter = MangaListFilter(query = query))
            val items = list.map { m ->
                cachedMangaMap[m.id.toString()] = m
                m.toCoreManga(id)
            }
            MangaPageResult(items = items, hasNextPage = items.size >= 20)
        }.getOrElse { MangaPageResult(emptyList(), false) }
    }

    override suspend fun details(manga: Manga): Manga = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val kotatsuManga = cachedMangaMap[manga.id]
                ?: parser.getList(offset = 0, order = SortOrder.RELEVANCE, filter = MangaListFilter(query = manga.title)).firstOrNull()
                ?: return@withContext manga

            val detailed = parser.getDetails(kotatsuManga)
            cachedMangaMap[detailed.id.toString()] = detailed
            detailed.toCoreManga(id)
        }.getOrDefault(manga)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val kotatsuManga = cachedMangaMap[manga.id]
                ?: parser.getList(offset = 0, order = SortOrder.RELEVANCE, filter = MangaListFilter(query = manga.title)).firstOrNull()
                ?: return@withContext emptyList()

            val detailed = parser.getDetails(kotatsuManga)
            cachedMangaMap[detailed.id.toString()] = detailed
            detailed.chapters.orEmpty().map { it.toCoreChapter(manga.id, id) }
        }.getOrDefault(emptyList())
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val parentManga = cachedMangaMap[chapter.mangaId] ?: return@withContext emptyList()
            val kotatsuChapter = parentManga.chapters?.firstOrNull { it.id.toString() == chapter.id }
                ?: return@withContext emptyList()

            val pagesList = parser.getPages(kotatsuChapter)
            pagesList.mapIndexed { idx, p ->
                val realUrl = parser.getPageUrl(p)
                MangaPage(
                    index = idx,
                    imageUrl = realUrl
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun org.koitharu.kotatsu.parsers.model.Manga.toCoreManga(sourceId: Long): Manga {
        return Manga(
            id = this.id.toString(),
            sourceId = sourceId,
            title = this.title,
            coverUrl = this.coverUrl,
            author = this.authors?.joinToString(", "),
            artist = null,
            description = this.description ?: "",
            genres = this.tags?.map { it.title }.orEmpty(),
            status = this.state?.name
        )
    }

    private fun org.koitharu.kotatsu.parsers.model.MangaChapter.toCoreChapter(mangaId: String, sourceId: Long): Chapter {
        return Chapter(
            id = this.id.toString(),
            mangaId = mangaId,
            sourceId = sourceId,
            title = this.title ?: "Chapter ${this.number}",
            number = this.number,
            uploadDate = this.uploadDate,
            scanlator = this.scanlator
        )
    }
}
