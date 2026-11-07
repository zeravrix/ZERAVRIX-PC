package example

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.source.HttpSource
import com.zeravrix.zeravrix.source.MangaPageResult
import com.zeravrix.zeravrix.source.SourceCapabilities
import com.zeravrix.zeravrix.source.SourceProvider

class ExampleSource : HttpSource() {
    override val id = 9000001L
    override val name = "Example Desktop Source"
    override val language = "en"
    override val baseUrl = "https://example.invalid"
    override val capabilities = SourceCapabilities(search = true)

    override suspend fun search(page: Int, query: String): MangaPageResult =
        MangaPageResult(emptyList(), false)

    override suspend fun details(manga: Manga) = manga
    override suspend fun chapters(manga: Manga): List<Chapter> = emptyList()
    override suspend fun pages(chapter: Chapter): List<MangaPage> = emptyList()
}

class Provider : SourceProvider { override fun create() = ExampleSource() }
