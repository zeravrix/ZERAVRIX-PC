package com.zeravrix.zeravrix.sources

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.source.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance registry of built-in live API and web sources.
 * Replaces legacy dynamic JAR extension stores with fast, zero-delay live scrapers.
 */
class LiveSourceRegistry(appDir: Path) {
    val sources: List<Source> = listOf(
        MangalekMihonSource(),
        MangaSparkMihonSource(),
        TeamXMihonSource(),
        MangaSwatMihonSource(),
        AzoraMihonSource(),
        HijalaMihonSource(appDir),
        DilarMihonSource(),
        KomgaSource(appDir),
        KavitaSource(appDir),
    )

    private val sourceMap = ConcurrentHashMap<Long, Source>().apply {
        sources.forEach { put(it.id, it) }
    }

    fun get(id: Long): Source? = sourceMap[id]

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
