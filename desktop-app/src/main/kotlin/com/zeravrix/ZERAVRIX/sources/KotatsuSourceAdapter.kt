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

    val domain: String by lazy {
        runCatching {
            loaderContext.newParserInstance(parserSource).domain
        }.getOrDefault(parserSource.name.lowercase().replace('_', '-') + ".com")
    }

    val iconUrl: String get() = "https://www.google.com/s2/favicons?domain=$domain&sz=128"

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

    private fun cleanSlugToTitle(url: String, coverUrl: String?): String {
        val raw = url.trimEnd('/').substringAfterLast('/')
            .replace(Regex("-[a-f0-9]{6,}$"), "")
        val target = if (raw.isNotBlank() && raw != "comics" && raw != "manga") raw else {
            coverUrl?.substringAfterLast('/')?.substringBeforeLast('.')?.substringBeforeLast('.') ?: "Manga"
        }
        return target.replace('-', ' ').replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun org.koitharu.kotatsu.parsers.model.Manga.toCoreManga(sourceId: Long): Manga {
        val parsedTitle = this.title.takeIf { it.isNotBlank() }
            ?: this.altTitles.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: cleanSlugToTitle(this.url, this.coverUrl)

        val baseTags = this.tags?.map { it.title }.orEmpty()
        val isManhwaSource = parserSource == MangaParserSource.ASURASCANS ||
            parserSource == MangaParserSource.FLAMECOMICS ||
            parserSource.name.contains("MANHWA", ignoreCase = true) ||
            parserSource.name.contains("TOON", ignoreCase = true)

        val enrichedGenres = if (baseTags.isEmpty() && isManhwaSource) {
            listOf("Manhwa", "Action", "Webtoon")
        } else if (isManhwaSource && baseTags.none { it.equals("Manhwa", ignoreCase = true) }) {
            baseTags + "Manhwa"
        } else {
            baseTags
        }

        return Manga(
            id = this.id.toString(),
            sourceId = sourceId,
            title = parsedTitle,
            coverUrl = this.coverUrl,
            author = this.authors?.joinToString(", "),
            artist = null,
            description = this.description ?: "",
            genres = enrichedGenres,
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
