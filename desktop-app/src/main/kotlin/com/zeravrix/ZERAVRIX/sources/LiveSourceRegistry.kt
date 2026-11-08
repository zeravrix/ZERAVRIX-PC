package com.zeravrix.zeravrix.sources

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.data.DesktopMangaLoaderContext
import com.zeravrix.zeravrix.source.*
import kotlinx.coroutines.*
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
                MangaParserSource.MANGAKAKALOT,
                MangaParserSource.ASURASCANS,
                MangaParserSource.FLAMECOMICS,
                MangaParserSource.MANGASEE,
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

    // Fast in-memory cache for popular titles to ensure 0ms instantaneous switching
    private val popularCache = ConcurrentHashMap<Long, List<Manga>>()
    private var globalPopularCache: List<Manga>? = null

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

    fun getSourceIconUrl(source: Source): String {
        return when (source) {
            is KotatsuSourceAdapter -> source.iconUrl
            else -> "https://www.google.com/s2/favicons?domain=${source.name.lowercase().replace(" ", "")}.com&sz=128"
        }
    }

    fun getParserSourceFaviconUrl(parser: MangaParserSource): String {
        val adapter = getOrCreateAdapter(parser)
        return adapter?.iconUrl ?: "https://www.google.com/s2/favicons?domain=${parser.name.lowercase().replace('_', '-')}.com&sz=128"
    }

    fun catalogueSources(): List<CatalogueSource> = sources.filterIsInstance<CatalogueSource>()

    /**
     * High-speed non-blocking popular fetcher with timeout guards and in-memory caching
     */
    suspend fun fetchPopular(sourceId: Long? = null, page: Int = 1): List<Manga> = withContext(Dispatchers.IO) {
        if (sourceId != null) {
            if (page == 1 && popularCache.containsKey(sourceId)) {
                return@withContext popularCache[sourceId].orEmpty()
            }
            val res = (sourceMap[sourceId] as? CatalogueSource)?.popular(page)?.items.orEmpty()
            if (page == 1 && res.isNotEmpty()) {
                popularCache[sourceId] = res
            }
            res
        } else {
            if (page == 1 && globalPopularCache != null && globalPopularCache!!.isNotEmpty()) {
                return@withContext globalPopularCache!!
            }

            // Target top fast-responding sources with strict 5s timeout to prevent launch stall
            val prioritized = catalogueSources().filter { it.capabilities.popular }
            val quickTargets = prioritized.filter {
                it.name.contains("Dex", ignoreCase = true) ||
                it.name.contains("Kakalot", ignoreCase = true) ||
                it.name.contains("Asura", ignoreCase = true) ||
                it.name.contains("Flame", ignoreCase = true)
            }.ifEmpty { prioritized.take(4) }

            val jobs = quickTargets.map { source ->
                async {
                    withTimeoutOrNull(5500L) {
                        runCatching { source.popular(page).items }.getOrDefault(emptyList())
                    }.orEmpty()
                }
            }

            val results = jobs.awaitAll().flatten().distinctBy { it.id }
            if (page == 1 && results.isNotEmpty()) {
                globalPopularCache = results
            }
            results
        }
    }

    suspend fun fetchLatest(sourceId: Long? = null, page: Int = 1): List<Manga> = withContext(Dispatchers.IO) {
        if (sourceId != null) {
            (sourceMap[sourceId] as? CatalogueSource)?.latest(page)?.items.orEmpty()
        } else {
            val quickTargets = catalogueSources().filter { it.capabilities.latest }.take(4)
            val jobs = quickTargets.map { source ->
                async {
                    withTimeoutOrNull(5500L) {
                        runCatching { source.latest(page).items }.getOrDefault(emptyList())
                    }.orEmpty()
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
            val quickTargets = catalogueSources().filter { it.capabilities.search }.take(5)
            val jobs = quickTargets.map { source ->
                async {
                    withTimeoutOrNull(6000L) {
                        runCatching { source.search(page, query).items }.getOrDefault(emptyList())
                    }.orEmpty()
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
