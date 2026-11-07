package com.zeravrix.zeravrix.sources

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.data.DesktopMangaLoaderContext
import com.zeravrix.zeravrix.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance registry of built-in live API and web sources.
 * Seamlessly integrates both native zero-delay Mihon scrapers and Kotatsu parsers.
 */
class LiveSourceRegistry(
    appDir: Path,
    val loaderContext: DesktopMangaLoaderContext? = null
) {
    val sources: List<Source> = buildList {
        add(MangalekMihonSource())
        add(MangaSparkMihonSource())
        add(TeamXMihonSource())
        add(MangaSwatMihonSource())
        add(AzoraMihonSource())
        add(HijalaMihonSource(appDir))
        add(DilarMihonSource())
        add(KomgaSource(appDir))
        add(KavitaSource(appDir))

        if (loaderContext != null) {
            val kotatsuCurated = listOf(
                MangaParserSource.MANGADEX,
                MangaParserSource.MANGASEE,
                MangaParserSource.FLAMECOMICS,
                MangaParserSource.ASURASCANS,
                MangaParserSource.MANGAKAKALOT,
                MangaParserSource.WEBTOONS_EN,
                MangaParserSource.BATOTO,
                MangaParserSource.COMICK_FUN
            ).filter { !it.isBroken }
            .map { KotatsuSourceAdapter(it, loaderContext) }

            addAll(kotatsuCurated)
        }
    }

    private val sourceMap = ConcurrentHashMap<Long, Source>().apply {
        sources.forEach { put(it.id, it) }
    }

    fun get(id: Long): Source? {
        val existing = sourceMap[id]
        if (existing != null) return existing
        if (loaderContext != null && id >= 10_000L) {
            val ordinal = (id - 10_000L).toInt()
            val parserSource = MangaParserSource.entries.getOrNull(ordinal)
            if (parserSource != null) {
                val adapter = KotatsuSourceAdapter(parserSource, loaderContext)
                sourceMap[id] = adapter
                return adapter
            }
        }
        return null
    }

    fun getAllKotatsuParserSources(): List<MangaParserSource> {
        return MangaParserSource.entries.filter { !it.isBroken }
    }

    fun getOrCreateAdapter(parserSource: MangaParserSource): KotatsuSourceAdapter? {
        if (loaderContext == null) return null
        val id = 10_000L + parserSource.ordinal
        val existing = sourceMap[id] as? KotatsuSourceAdapter
        if (existing != null) return existing
        val adapter = KotatsuSourceAdapter(parserSource, loaderContext)
        sourceMap[id] = adapter
        return adapter
    }

    fun catalogueSources(): List<CatalogueSource> = sources.filterIsInstance<CatalogueSource>()

    suspend fun fetchPopular(sourceId: Long? = null, page: Int = 1): List<Manga> = withContext(Dispatchers.IO) {
        if (sourceId != null) {
            (sourceMap[sourceId] as? CatalogueSource)?.popular(page)?.items.orEmpty()
        } else {
            val jobs = catalogueSources()
                .filter { it.capabilities.popular }
                .map { source ->
                    async {
                        runCatching { source.popular(page).items }.getOrDefault(emptyList())
                    }
                }
            jobs.awaitAll().flatten().distinctBy { it.id }
        }
    }

    suspend fun fetchLatest(sourceId: Long? = null, page: Int = 1): List<Manga> = withContext(Dispatchers.IO) {
        if (sourceId != null) {
            (sourceMap[sourceId] as? CatalogueSource)?.latest(page)?.items.orEmpty()
        } else {
            val jobs = catalogueSources()
                .filter { it.capabilities.latest }
                .map { source ->
                    async {
                        runCatching { source.latest(page).items }.getOrDefault(emptyList())
                    }
                }
            jobs.awaitAll().flatten().distinctBy { it.id }
        }
    }

    suspend fun search(query: String, sourceId: Long? = null, page: Int = 1): List<Manga> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        if (sourceId != null) {
            (sourceMap[sourceId] as? CatalogueSource)?.search(page, query)?.items.orEmpty()
        } else {
            val jobs = catalogueSources()
                .filter { it.capabilities.search }
                .map { source ->
                    async {
                        runCatching { source.search(page, query).items }.getOrDefault(emptyList())
                    }
                }
            jobs.awaitAll().flatten().distinctBy { it.id }
        }
    }

    suspend fun fetchChapters(manga: Manga): List<Chapter> = withContext(Dispatchers.IO) {
        val source = sourceMap[manga.sourceId] ?: return@withContext emptyList()
        runCatching { source.chapters(manga) }.getOrDefault(emptyList())
    }

    suspend fun fetchPages(chapter: Chapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val source = sourceMap[chapter.sourceId] ?: return@withContext emptyList()
        runCatching { source.pages(chapter) }.getOrDefault(emptyList())
    }
}
