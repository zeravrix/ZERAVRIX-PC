package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Headless adapter bridging Kotatsu parsers with ZERAVRIX CatalogueSource architecture.
 */
class KotatsuSourceAdapter(
    val parserSource: MangaParserSource,
    val loaderContext: HeadlessMangaLoaderContext
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

    companion object {
        val globalMangaCache = ConcurrentHashMap<String, org.koitharu.kotatsu.parsers.model.Manga>()
        val globalChapterCache = ConcurrentHashMap<String, org.koitharu.kotatsu.parsers.model.MangaChapter>()
    }

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("//")) {
            "https://$domain/${url.trimStart('/')}"
        } else url
    }

    private fun getSafeManga(manga: Manga): org.koitharu.kotatsu.parsers.model.Manga {
        val cached = globalMangaCache[manga.id]
        if (cached != null) {
            val normUrl = normalizeUrl(cached.url)
            return if (cached.url != normUrl) cached.copy(url = normUrl) else cached
        }
        val targetUrl = normalizeUrl(manga.url ?: manga.id)
        return org.koitharu.kotatsu.parsers.model.Manga(
            id = manga.id.toLongOrNull() ?: 0L,
            title = manga.title,
            altTitles = emptySet(),
            url = targetUrl,
            publicUrl = targetUrl,
            rating = 0f,
            contentRating = ContentRating.SAFE,
            coverUrl = manga.coverUrl.orEmpty(),
            tags = emptySet(),
            state = null,
            authors = emptySet(),
            largeCoverUrl = null,
            description = manga.description,
            chapters = null,
            source = parserSource
        )
    }

    suspend fun fetchList(
        page: Int = 1,
        order: SortOrder = SortOrder.POPULARITY,
        query: String? = null
    ): MangaPageResult = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val effectiveOrder = if (order in parser.availableSortOrders) order else parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
            val filter = if (!query.isNullOrBlank()) MangaListFilter(query = query) else MangaListFilter()
            val list = parser.getList(offset = (page - 1) * 20, order = effectiveOrder, filter = filter)
            val items = list.filter { it.contentRating != ContentRating.ADULT }.map { m ->
                val normUrl = normalizeUrl(m.url)
                val safeM = if (m.url != normUrl) m.copy(url = normUrl) else m
                globalMangaCache[safeM.id.toString()] = safeM
                safeM.toCoreManga(id)
            }
            MangaPageResult(items = items, hasNextPage = items.size >= 20)
        }.getOrElse { MangaPageResult(emptyList(), false) }
    }

    override suspend fun popular(page: Int): MangaPageResult = fetchList(page, SortOrder.POPULARITY)

    override suspend fun latest(page: Int): MangaPageResult = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val order = if (SortOrder.UPDATED in parser.availableSortOrders) SortOrder.UPDATED else parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
            val list = parser.getList(offset = (page - 1) * 20, order = order, filter = MangaListFilter())
            val items = list.filter { it.contentRating != ContentRating.ADULT }.map { m ->
                val normUrl = normalizeUrl(m.url)
                val safeM = if (m.url != normUrl) m.copy(url = normUrl) else m
                globalMangaCache[safeM.id.toString()] = safeM
                safeM.toCoreManga(id)
            }
            MangaPageResult(items = items, hasNextPage = items.size >= 20)
        }.getOrElse { MangaPageResult(emptyList(), false) }
    }

    override suspend fun search(page: Int, query: String): MangaPageResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext MangaPageResult(emptyList(), false)
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val order = if (SortOrder.RELEVANCE in parser.availableSortOrders) SortOrder.RELEVANCE else parser.availableSortOrders.firstOrNull() ?: SortOrder.POPULARITY
            val list = parser.getList(offset = (page - 1) * 20, order = order, filter = MangaListFilter(query = query))
            val items = list.filter { it.contentRating != ContentRating.ADULT }.map { m ->
                val normUrl = normalizeUrl(m.url)
                val safeM = if (m.url != normUrl) m.copy(url = normUrl) else m
                globalMangaCache[safeM.id.toString()] = safeM
                safeM.toCoreManga(id)
            }
            MangaPageResult(items = items, hasNextPage = items.size >= 20)
        }.getOrElse { MangaPageResult(emptyList(), false) }
    }

    override suspend fun details(manga: Manga): Manga = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val targetManga = getSafeManga(manga)
            val detailed = parser.getDetails(targetManga)
            if (detailed.contentRating == ContentRating.ADULT) return@runCatching manga
            globalMangaCache[detailed.id.toString()] = detailed
            detailed.chapters?.forEach { ch ->
                globalChapterCache[ch.id.toString()] = ch
            }
            detailed.toCoreManga(id)
        }.getOrDefault(manga)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val targetManga = getSafeManga(manga)
            val detailed = parser.getDetails(targetManga)
            if (detailed.contentRating == ContentRating.ADULT) return@runCatching emptyList()
            globalMangaCache[detailed.id.toString()] = detailed
            detailed.chapters?.forEach { ch ->
                globalChapterCache[ch.id.toString()] = ch
            }
            detailed.chapters.orEmpty().map { it.toCoreChapter(manga.id, id) }
        }.getOrDefault(emptyList())
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> = withContext(Dispatchers.IO) {
        runCatching {
            val parser = loaderContext.newParserInstance(parserSource)
            val kotatsuChapter = globalChapterCache[chapter.id]
                ?: org.koitharu.kotatsu.parsers.model.MangaChapter(
                    id = chapter.id.toLongOrNull() ?: 0L,
                    title = chapter.title,
                    number = chapter.number ?: 1f,
                    volume = chapter.volume ?: 0,
                    url = normalizeUrl(chapter.url ?: chapter.id),
                    scanlator = chapter.scanlator,
                    uploadDate = chapter.uploadDate ?: 0L,
                    branch = null,
                    source = parserSource
                )

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
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    private fun org.koitharu.kotatsu.parsers.model.Manga.toCoreManga(sourceId: Long): Manga {
        val parsedTitle = if (this.title.isNotBlank()) {
            this.title
        } else {
            cleanSlugToTitle(this.url, this.coverUrl)
        }

        val baseTags = this.tags?.map { it.title }.orEmpty()
        val pName = parserSource.name.uppercase()
        val isDedicatedManhwaSource = pName.contains("ASURA") ||
            pName.contains("FLAME") ||
            pName.contains("WEBTOON") ||
            pName.contains("TOON") ||
            pName.contains("MANHWA") ||
            pName.contains("REAPER") ||
            pName.contains("COSMIC") ||
            pName.contains("VOID") ||
            pName.contains("DRAKE") ||
            pName.contains("LUMINOUS")

        val manhwaKeywords = listOf(
            "manhwa", "webtoon", "manhua", "long strip", "full color", "web comic", "korean",
            "مانهوا", "مانهو", "ويبتون", "ويب تون", "كوري", "صيني"
        )

        val textToScan = (parsedTitle + " " + this.url + " " + (this.description ?: "") + " " + baseTags.joinToString(" ")).lowercase()
        val hasManhwaKeyword = manhwaKeywords.any { textToScan.contains(it) }

        val isManhwa = isDedicatedManhwaSource || hasManhwaKeyword

        val enrichedGenres = buildList {
            addAll(baseTags)
            if (isManhwa && none { it.equals("Manhwa", ignoreCase = true) }) {
                add("Manhwa")
            }
            if (isManhwa && none { it.equals("Webtoon", ignoreCase = true) }) {
                add("Webtoon")
            }
        }.distinct()

        return Manga(
            id = this.id.toString(),
            sourceId = sourceId,
            title = parsedTitle,
            coverUrl = this.coverUrl,
            author = this.authors?.joinToString(", "),
            artist = null,
            description = this.description ?: "",
            genres = enrichedGenres,
            status = this.state?.name,
            url = this.url
        )
    }

    private fun org.koitharu.kotatsu.parsers.model.MangaChapter.toCoreChapter(mangaId: String, sourceId: Long): Chapter {
        return Chapter(
            id = this.id.toString(),
            mangaId = mangaId,
            sourceId = sourceId,
            title = this.title ?: "Chapter ${this.number}",
            number = this.number,
            volume = this.volume,
            uploadDate = this.uploadDate,
            scanlator = this.scanlator,
            url = this.url
        )
    }
}
