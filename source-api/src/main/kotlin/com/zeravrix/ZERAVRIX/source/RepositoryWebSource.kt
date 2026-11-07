package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.RepositorySourceDescriptor
import com.zeravrix.zeravrix.core.SourcePreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.net.http.HttpResponse
import java.util.Locale

/**
 * Lightweight PC compatibility bridge for repository entries that publish a
 * public HTML source home URL but only an Android Mihon package. It uses the
 * existing desktop HTTP/Jsoup pipeline and never loads APK or Android classes.
 */
class RepositoryWebSource(
    private val descriptor: RepositorySourceDescriptor,
    root: Path = Path.of(System.getProperty("user.home"), ".zeravrix"),
) : HtmlMangaSource(
    id = descriptor.id,
    name = descriptor.name,
    baseUrl = descriptor.homeUrl.trimEnd('/'),
) , ConfigurableSource {
    private val preferencesStore = SourcePreferencesStore(root)
    private var sourcePreferences: Map<String, String> = preferencesStore.load(id)
    private val browserCookie: String get() = sourcePreferences["cookie"].orEmpty().trim()
    override fun preferences(): Map<String, String> = sourcePreferences
    override fun updatePreference(key: String, value: String) {
        sourcePreferences = sourcePreferences.toMutableMap().apply { put(key, value) }
        preferencesStore.save(id, sourcePreferences)
    }
    override val language: String = descriptor.language
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)

    private suspend fun repositoryPage(url: String): Document = withContext(Dispatchers.IO) {
        val headers = buildMap {
            put("Accept-Language", "en-US,en;q=0.8,ar;q=0.6")
            if (browserCookie.isNotBlank()) put("Cookie", browserCookie)
        }
        val response = request(url, headers, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 403 || response.statusCode() == 429 || response.statusCode() in 500..599) {
            throw SourceBlockedException("${name} needs verification. Open Verify in Explore, complete CAPTCHA manually, save the browser cookie, then retry.")
        }
        if (response.statusCode() !in 200..299) error("${name} returned HTTP ${response.statusCode()}")
        Jsoup.parse(response.body(), url)
    }

    override suspend fun latest(page: Int): MangaPageResult = listPage(listUrl(page, null))
    override suspend fun popular(page: Int): MangaPageResult = latest(page)

    override suspend fun search(page: Int, query: String): MangaPageResult =
        listPage(listUrl(page, query.trim().takeIf { it.isNotBlank() }))

    private fun listUrl(page: Int, query: String?): String {
        val encoded = query?.let { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        return when {
            encoded != null -> "$baseUrl/?s=$encoded&page=$page"
            page <= 1 -> baseUrl
            else -> "$baseUrl/page/$page/"
        }
    }

    private suspend fun listPage(url: String): MangaPageResult {
        val document = repositoryPage(url)
        val cards = document.select(".page-item-detail, .manga-item, .item-summary, .row.c-tabs-item__content, article, .manga-card")
        val items = cards.mapNotNull { card -> mangaFromGenericCard(card) }
            .ifEmpty {
                document.select("a[href]").mapNotNull { link ->
                    val href = link.absUrl("href").ifBlank { link.attr("href") }
                    if (!href.startsWith("http") || href == baseUrl || href.contains("/chapter", true)) return@mapNotNull null
                    val parent = link.parents().firstOrNull { it.selectFirst("img") != null } ?: link
                    mangaFromGenericCard(parent, href)
                }
            }
            .distinctBy { it.id }
        val hasNext = document.select("a.next, a[rel=next], .pagination a").any { it.text().contains("next", true) || it.attr("rel") == "next" }
        return MangaPageResult(items, hasNext || items.size >= 20)
    }

    private fun mangaFromGenericCard(card: Element, fallbackUrl: String? = null): Manga? {
        val link = card.select("a[href]").maxByOrNull { candidate ->
            val href = candidate.attr("href").lowercase(Locale.ROOT)
            (if (candidate.selectFirst("img") != null) 5 else 0) +
                (if (href.contains("/manga/") || href.contains("/series/") || href.contains("/title/") || href.contains("/comic/")) 4 else 0) +
                (if (!href.contains("chapter") && !href.contains("episode") && !href.contains("genre") && !href.contains("tag") && !href.contains("login")) 2 else 0)
        } ?: return null
        val url = link.absUrl("href").ifBlank { fallbackUrl ?: link.attr("href") }
        if (!url.startsWith("http") || url == baseUrl || url.contains("/genre/", true) || url.contains("/tag/", true) || url.contains("/login", true) || url.contains("/search", true)) return null
        val title = card.selectFirst(".post-title, .entry-title, .item-title, h1, h2, h3, h4")?.text()?.trim()
            ?: link.text().trim().takeIf { it.isNotBlank() }
            ?: url.trimEnd('/').substringAfterLast('/').replace('-', ' ').replace('_', ' ')
        val image = card.selectFirst("img")?.repositoryImage()
        return Manga(url, title, image, sourceId = id)
    }

    override suspend fun details(manga: Manga): Manga {
        val document = repositoryPage(manga.id)
        val title = document.selectFirst("h1, .entry-title, .post-title")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: manga.title
        val cover = document.selectFirst(".summary_image img, .manga-thumb img, .cover img, img")?.repositoryImage() ?: manga.coverUrl
        val description = document.selectFirst(".description-summary, .summary__content, .description, .entry-content, .post-content")?.text()?.trim().orEmpty()
        val genres = document.select("a[href*=genre], .genres-content a, .genre a").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        return manga.copy(title = title, coverUrl = cover, description = description, genres = genres)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val document = repositoryPage(manga.id)
        val selector = ".chapter-item a, .wp-manga-chapter a, .listing-chapters_wrap a, .chapters a, .chapter-list a, a.chapter-link, [data-chapter-url], [data-chapter-link], a[href*=/chapter], a[href*=/episode], a[href*=/chapitre], a[href*=/ch-]"
        val candidates = document.select(selector).ifEmpty { document.select("a[href], [data-chapter-url], [data-chapter-link]") }
        return candidates.mapNotNull { link ->
            val rawUrl = link.absUrl("href").ifBlank { link.attr("href").ifBlank { link.attr("data-chapter-url").ifBlank { link.attr("data-chapter-link") } } }
            val url = if (rawUrl.startsWith("http")) rawUrl else java.net.URI(document.location()).resolve(rawUrl).toString()
            val text = link.text().trim().ifBlank { link.attr("data-title").trim() }.ifBlank { url.trimEnd('/').substringAfterLast('/') }
            val context = (link.className() + " " + link.parent()?.className().orEmpty() + " " + link.parents().take(3).joinToString(" ") { it.className() }).lowercase(Locale.ROOT)
            val hasNumber = Regex("(?:^|\\s|[-_])(?:chapter|ch|episode|ep|volume|vol)?[-_ .]*[0-9]+(?:\\.[0-9]+)?", RegexOption.IGNORE_CASE).containsMatchIn("$text $url")
            val likelyChapter = context.contains("chapter") || context.contains("episode") || context.contains("volume") || context.contains("listing") || context.contains("chapter-list") || text.contains("chapter", true) || text.contains("ch.", true) || text.contains("episode", true) || text.contains("الفصل", true) || url.contains("chapter", true) || url.contains("episode", true) || url.contains("chapitre", true) || url.contains("/read/", true) || (hasNumber && context.contains("list"))
            if (!likelyChapter || !url.startsWith("http") || url == manga.id) return@mapNotNull null
            Chapter(url, manga.id, text.ifBlank { "Chapter" }, repositoryChapterNumber(text, url), id)
        }.distinctBy { it.id }.sortedWith(compareBy<Chapter> { it.number ?: Float.POSITIVE_INFINITY }.thenBy { it.title.lowercase(Locale.ROOT) })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val document = repositoryPage(chapter.id)
        val selector = ".reading-content img, .page-break img, .chapter-image, .chapter-image-container img, .reader img, .reader-page img, .reading-detail img, .comic-page img, .pages img, img[data-page], img[data-image], source"
        val images = document.select(selector).mapNotNull { it.repositoryPageImage(document.location()) }
            .ifEmpty { document.select("img, source").mapNotNull { it.repositoryPageImage(document.location()) } }
            .distinct()
        if (images.isNotEmpty()) return images.mapIndexed { index, url -> MangaPage(index, url, document.location(), buildMap { put("Referer", document.location()); put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"); if (browserCookie.isNotBlank()) put("Cookie", browserCookie) }) }
        val embedded = Regex("https?://[^\\\"'\\s<>]+", RegexOption.IGNORE_CASE).findAll(document.html()).map { it.value.trimEnd(')', ']', ',', ';') }.filter { candidate ->
            !candidate.contains("logo", true) && !candidate.contains("avatar", true) && !candidate.contains("icon", true) && (candidate.contains("image", true) || candidate.contains("img", true) || candidate.contains("chapter", true) || candidate.matches(Regex(".*\\.(?:jpe?g|png|gif|webp)(?:\\?.*)?", RegexOption.IGNORE_CASE)))
        }.distinct().toList()
        return embedded.mapIndexed { index, url -> MangaPage(index, url, document.location(), buildMap { put("Referer", document.location()); put("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"); if (browserCookie.isNotBlank()) put("Cookie", browserCookie) }) }
    }

    private fun Element.repositoryImage(): String? = sequenceOf("data-src", "data-lazy-src", "data-original", "data-url", "data-image", "data-cfsrc", "src")
        .map { key -> absUrl(key).ifBlank { attr(key).trim() } }
        .plus(attr("srcset").substringBefore(',').substringBefore(' ').trim())
        .firstOrNull { it.startsWith("http") && !it.contains("logo", true) && !it.contains("avatar", true) }

    private fun Element.repositoryPageImage(baseUrl: String): String? = sequenceOf("data-src", "data-lazy-src", "data-original", "data-url", "data-image", "data-cfsrc", "src")
        .map { key -> absUrl(key).ifBlank { attr(key).trim() } }
        .plus(attr("srcset").split(',').asSequence().map { it.trim().substringBefore(' ') })
        .map { candidate -> if (candidate.startsWith("//")) "https:$candidate" else candidate }
        .firstOrNull { it.startsWith("http") && !it.contains("logo", true) && !it.contains("avatar", true) && !it.contains("icon", true) }

    private fun repositoryChapterNumber(text: String, url: String): Float? = Regex("(?:chapter|ch|episode|chapitre|الفصل|فصل)[^0-9]*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        .find("$text $url")?.groupValues?.getOrNull(1)?.toFloatOrNull()
}
