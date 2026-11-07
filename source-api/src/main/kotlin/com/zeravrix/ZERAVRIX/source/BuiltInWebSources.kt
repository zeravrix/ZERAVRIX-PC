package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.Locale

class SourceBlockedException(message: String) : IllegalStateException(message)

private fun slugTitle(value: String): String = value
    .replace(Regex("[-_]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

private fun parseNumber(text: String): Float? = Regex("(?:chapter|ch|الفصل|فصل)?\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    .find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()

private fun Element.bestImage(): String? = sequenceOf("data-src", "data-lazy-src", "data-original", "src")
    .map { key -> absUrl(key).ifBlank { attr(key).trim() } }
    .firstOrNull { it.isNotBlank() && !it.startsWith("data:") }

abstract class HtmlMangaSource(
    override val id: Long,
    override val name: String,
    override val baseUrl: String,
) : HttpSource(), CatalogueSource {
    override val language: String = "multi"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)

    protected suspend fun page(url: String): Document = withContext(Dispatchers.IO) {
        val response = request(url, mapOf("Accept-Language" to "en-US,en;q=0.8,ar;q=0.6"), java.net.http.HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 403 || response.statusCode() == 429 || response.statusCode() in 500..599) {
            throw SourceBlockedException("$name blocked this request (${response.statusCode()}). Open the source verification page, complete it manually, then refresh.")
        }
        if (response.statusCode() !in 200..299) error("$name returned HTTP ${response.statusCode()}")
        Jsoup.parse(response.body(), url)
    }

    protected fun mangaFromCard(card: Element, sourceId: Long, fallbackUrl: String? = null): Manga? {
        val anchor = card.selectFirst("a[href*=/manga/], a[href*=/series/]") ?: return null
        val url = anchor.absUrl("href").ifBlank { fallbackUrl ?: return null }
        val title = card.selectFirst(".post-title, .entry-title, h3, h4")?.text()?.trim()
            ?: anchor.text().trim().takeIf { it.isNotBlank() }
            ?: slugTitle(url.substringBeforeLast('/').substringAfterLast('/'))
        val image = card.selectFirst("img")?.bestImage()
        return Manga(id = url, title = title, coverUrl = image, sourceId = sourceId)
    }

    protected fun pageImages(document: Document, selector: String): List<MangaPage> = document.select(selector).mapNotNull { image ->
        val url = image.bestImage()
        url?.takeIf { it.startsWith("http") }
    }.distinct().mapIndexed { index, url -> MangaPage(index, url, document.location(), mapOf("Referer" to document.location())) }
}

/** WordPress/Madara adapter shared by SparkManga and Mangalik. */
abstract class WordPressMangaSource(
    id: Long,
    name: String,
    baseUrl: String,
) : HtmlMangaSource(id, name, baseUrl.trimEnd('/')) {
    override suspend fun popular(page: Int): MangaPageResult = listPage(if (page <= 1) baseUrl else "$baseUrl/page/$page/")
    override suspend fun latest(page: Int): MangaPageResult = popular(page)
    override suspend fun search(page: Int, query: String): MangaPageResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
        return listPage("$baseUrl/?s=$encoded&post_type=wp-manga")
    }

    private suspend fun listPage(url: String): MangaPageResult {
        val doc = page(url)
        val cards = doc.select(".c-tabs-item__content, .row.c-tabs-item__content, .item-summary").mapNotNull { it.parent() }.ifEmpty {
            doc.select(".row.row-eq-height, .manga-item, .page-item-detail").toList()
        }
        val items = cards.mapNotNull { mangaFromCard(it, id) }.distinctBy { it.id }
            .ifEmpty {
                doc.select("a[href*=/manga/]").mapNotNull { link ->
                    val url = link.absUrl("href")
                    if (url.endsWith("/manga/") || url == baseUrl + "/manga/") return@mapNotNull null
                    val card = link.parents().firstOrNull { it.selectFirst("img") != null } ?: link
                    mangaFromCard(card, id, url)
                }.distinctBy { it.id }
            }
        return MangaPageResult(items, doc.select("a.next, .pagination a").any { it.text().contains("next", true) || it.text().contains("التالي") })
    }

    override suspend fun details(manga: Manga): Manga {
        val doc = page(manga.id)
        val title = doc.selectFirst(".post-title h1, h1.entry-title, h1")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: manga.title
        val cover = doc.selectFirst(".summary_image img, .summary_image img")?.bestImage() ?: manga.coverUrl
        val description = doc.selectFirst(".description-summary, .summary__content, .summary_content")?.text()?.trim().orEmpty()
        val genres = doc.select(".genres-content a, .genres-content a").map { it.text().trim() }.filter(String::isNotBlank)
        return manga.copy(title = title, coverUrl = cover, description = description, genres = genres)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val detail = runCatching { page(manga.id) }.getOrNull()
        val doc = detail ?: page(baseUrl)
        val links = if (detail != null) {
            doc.select(".chapter-item a, .wp-manga-chapter a, .listing-chapters_wrap a")
        } else {
            doc.select("a[href]").filter { it.absUrl("href").startsWith(manga.id.trimEnd('/') + "/") }
        }
        return links.mapNotNull { link ->
            val url = link.absUrl("href").ifBlank { return@mapNotNull null }
            val text = link.text().trim().ifBlank { url.trimEnd('/').substringAfterLast('/') }
            if (url == manga.id.trimEnd('/')) return@mapNotNull null
            Chapter(url, manga.id, text.ifBlank { "Chapter" }, parseNumber(text), id)
        }.distinctBy { it.id }
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val doc = page(chapter.id)
        return pageImages(doc, ".reading-content img, .page-break img, .reading-content source")
            .ifEmpty { pageImages(doc, "img") }
    }
}

class SparkMangaSource : WordPressMangaSource(0x535041524B4DL, "SparkManga", "https://sparkmanga.net")
class MangalikSource : WordPressMangaSource(0x4D414E47414C4BL, "Mangalik", "https://mangalik.net")

class OlympusStaffSource : HtmlMangaSource(0x4F4C594D505553L, "Olympus Staff", "https://olympustaff.com") {
    override suspend fun popular(page: Int): MangaPageResult = listSeries("$baseUrl/series${if (page > 1) "?page=$page" else ""}", page)
    override suspend fun latest(page: Int): MangaPageResult = listSeries(baseUrl, page)
    override suspend fun search(page: Int, query: String): MangaPageResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
        return listSeries("$baseUrl/series?search=$encoded", page)
    }

    private suspend fun listSeries(url: String, pageNumber: Int): MangaPageResult {
        val doc = page(url)
        val items = doc.select("a[href*=/series/]").mapNotNull { link ->
            val href = link.absUrl("href")
            if (href.count { it == '/' } < 4 || href.endsWith("/series/")) return@mapNotNull null
            val title = link.text().trim().ifBlank { link.selectFirst("img")?.attr("alt") ?: return@mapNotNull null }
            Manga(href, title, link.selectFirst("img")?.bestImage(), sourceId = id)
        }.distinctBy { it.id }
        return MangaPageResult(items, doc.select("a.page-link").any { it.text().trim() == (pageNumber + 1).toString() })
    }

    override suspend fun details(manga: Manga): Manga {
        val doc = page(manga.id)
        val title = doc.selectFirst("h1, .entry-title")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: manga.title
        val cover = doc.selectFirst("img[src*=/images/manga/]")?.bestImage() ?: manga.coverUrl
        val description = doc.selectFirst(".entry-body, .description, .review-content")?.text()?.trim().orEmpty()
        val genres = doc.select("a[href*=?genre=]").map { it.text().trim() }.filter(String::isNotBlank)
        return manga.copy(title = title, coverUrl = cover, description = description, genres = genres)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val doc = page(manga.id)
        return doc.select("a[href*=/series/${manga.id.substringAfterLast('/')}]").mapNotNull { link ->
            val url = link.absUrl("href")
            val number = parseNumber(link.text()) ?: url.trimEnd('/').substringAfterLast('/').toFloatOrNull() ?: return@mapNotNull null
            if (url == manga.id.trimEnd('/')) return@mapNotNull null
            Chapter(url, manga.id, link.text().trim().ifBlank { "Chapter $number" }, number, id)
        }.distinctBy { it.id }
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val doc = page(chapter.id)
        return pageImages(doc, "img.chapter-image, .chapter-image-container img, img[src*=/images/chapter/]")
    }
}

/** MeshManga source backed by the public AppsWat JSON API used by the site. */
class MeshMangaSource : HtmlMangaSource(0x4D4553484DL, "MeshManga", "https://meshmanga.com") {
    private val apiBase = "https://appswat.com/v2/api/v2"
    private val seriesPattern = Regex("""\"id\":(\d+).*?\"title\":\"([^\"]*)\".*?\"slug\":\"([^\"]*)\".*?\"poster\":\{\"thumbnail\":\"([^\"]*)\"""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val chapterPattern = Regex("""\"id\":(\d+),\"title\":\"([^\"]*)\",\"slug\":\"[^\"]*\",\"chapter\":\"([^\"]*)\"""")
    private val imagePattern = Regex("""\"image\":\"([^\"]*)\"""")

    private suspend fun api(url: String): String = withContext(Dispatchers.IO) {
        val response = request(url, mapOf("Accept" to "application/json"), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("MeshManga API returned HTTP ${response.statusCode()}")
        response.body()
    }

    override suspend fun popular(page: Int): MangaPageResult = latest(page)
    override suspend fun latest(page: Int): MangaPageResult {
        val json = api("$apiBase/series/?page_size=60&page=$page")
        val items = seriesPattern.findAll(json).map { match ->
            Manga("$apiBase/series/${match.groupValues[1]}/", match.groupValues[2], match.groupValues[4], sourceId = id)
        }.distinctBy { it.id }.toList()
        return MangaPageResult(items, items.size >= 60)
    }
    override suspend fun search(page: Int, query: String): MangaPageResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
        val json = api("$apiBase/series/?search=$encoded&page_size=60&page=$page")
        val items = seriesPattern.findAll(json).map { match ->
            Manga("$apiBase/series/${match.groupValues[1]}/", match.groupValues[2], match.groupValues[4], sourceId = id)
        }.distinctBy { it.id }.toList()
        return MangaPageResult(items, items.size >= 60)
    }
    override suspend fun details(manga: Manga): Manga {
        val json = api(manga.id)
        val title = Regex("""\"title\":\"([^\"]*)\"""").find(json)?.groupValues?.get(1) ?: manga.title
        val cover = Regex("""\"thumbnail\":\"([^\"]*)\"""").find(json)?.groupValues?.get(1) ?: manga.coverUrl
        val description = Regex("""\"story\":\"(.*?)\"""", setOf(RegexOption.DOT_MATCHES_ALL)).find(json)?.groupValues?.get(1)?.replace("\\n", " ").orEmpty()
        return manga.copy(title = title, coverUrl = cover, description = description)
    }
    override suspend fun chapters(manga: Manga): List<Chapter> {
        val seriesId = Regex("/series/(\\d+)/").find(manga.id)?.groupValues?.get(1) ?: return emptyList()
        val json = api("$apiBase/series/$seriesId/chapters/?page_size=60")
        return chapterPattern.findAll(json).map { match ->
            val number = match.groupValues[3].toFloatOrNull()
            Chapter("$apiBase/chapters/${match.groupValues[1]}/", manga.id, match.groupValues[2], number, id)
        }.distinctBy { it.id }.toList()
    }
    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val json = api(chapter.id)
        return imagePattern.findAll(json).mapIndexed { index, match ->
            MangaPage(index, match.groupValues[1], chapter.id, mapOf("Referer" to chapter.id))
        }.toList()
    }
}

class BuiltInSourceProvider : SourceProvider {
    override fun create(): Source = SparkMangaSource()
}
