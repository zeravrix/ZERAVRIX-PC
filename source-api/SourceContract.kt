package zeravrix.source.api

data class MangaRef(
    val sourceId: String,
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null
)

data class ChapterRef(
    val id: String,
    val title: String,
    val url: String,
    val number: Float? = null
)

data class PageRef(
    val index: Int,
    val imageUrl: String,
    val referer: String? = null
)

interface DesktopSource {
    val id: String
    val name: String
    val version: String

    suspend fun search(query: String, page: Int = 1): List<MangaRef>
    suspend fun details(manga: MangaRef): MangaRef
    suspend fun chapters(manga: MangaRef): List<ChapterRef>
    suspend fun pages(chapter: ChapterRef): List<PageRef>
}
