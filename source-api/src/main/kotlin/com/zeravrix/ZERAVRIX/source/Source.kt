package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage

data class MangaPageResult(val items: List<Manga>, val hasNextPage: Boolean)

data class SourceCapabilities(
    val popular: Boolean = false,
    val latest: Boolean = false,
    val search: Boolean = true,
    val filters: Boolean = false,
)

interface Source {
    val id: Long
    val name: String
    val language: String
    val baseUrl: String
    val capabilities: SourceCapabilities get() = SourceCapabilities()

    suspend fun popular(page: Int): MangaPageResult = MangaPageResult(emptyList(), false)
    suspend fun latest(page: Int): MangaPageResult = MangaPageResult(emptyList(), false)
    suspend fun search(page: Int, query: String): MangaPageResult
    suspend fun details(manga: Manga): Manga
    suspend fun chapters(manga: Manga): List<Chapter>
    suspend fun pages(chapter: Chapter): List<MangaPage>
}

interface CatalogueSource : Source
interface ConfigurableSource : Source {
    fun preferences(): Map<String, String> = emptyMap()
    fun updatePreference(key: String, value: String) {}
}

