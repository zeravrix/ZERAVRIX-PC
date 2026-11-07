package com.zeravrix.zeravrix.jvmhost

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage as CoreMangaPage
import com.zeravrix.zeravrix.source.MangaPageResult

/** Stable, native-facing description of a source without exposing JVM implementation types. */
data class HostSourceInfo(
    val id: Long,
    val name: String,
    val language: String,
    val baseUrl: String,
    val popular: Boolean,
    val latest: Boolean,
    val search: Boolean,
    val filters: Boolean,
    val enabled: Boolean,
)

sealed class HostCallResult<out T> {
    data class Success<T>(val value: T) : HostCallResult<T>()

    data class Failure(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : HostCallResult<Nothing>()
}

sealed interface HostOperation {
    data class Popular(val sourceId: Long, val page: Int = 1) : HostOperation
    data class Latest(val sourceId: Long, val page: Int = 1) : HostOperation
    data class Search(val sourceId: Long, val page: Int = 1, val query: String) : HostOperation
    data class Details(val sourceId: Long, val manga: Manga) : HostOperation
    data class Chapters(val sourceId: Long, val manga: Manga) : HostOperation
    data class Pages(val sourceId: Long, val chapter: Chapter) : HostOperation
}

sealed class HostOperationResult {
    data class MangaPage(val result: MangaPageResult) : HostOperationResult()
    data class MangaDetails(val manga: Manga) : HostOperationResult()
    data class ChapterList(val chapters: List<Chapter>) : HostOperationResult()
    data class PageList(val pages: List<CoreMangaPage>) : HostOperationResult()
}
