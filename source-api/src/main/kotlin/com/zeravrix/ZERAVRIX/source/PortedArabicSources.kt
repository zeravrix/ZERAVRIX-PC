package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.SourcePreferencesStore
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.http.HttpResponse

private fun parseChapterNumber(value: String): Float? = Regex("(?:chapter|ch|الفصل|فصل)?\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    .find(value)?.groupValues?.getOrNull(1)?.toFloatOrNull()

private fun parseIsoDate(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private fun Element.imageUrl(): String? {
    // Prefer explicit original/full-size attributes. When only srcset is present,
    // select the largest width/density candidate instead of the first thumbnail.
    val originals = sequenceOf(
        attr("abs:data-original"),
        attr("abs:data-full"),
        attr("abs:data-orig"),
        attr("abs:data-cfsrc"),
        attr("abs:data-src"),
        attr("abs:data-lazy-src"),
        attr("abs:bv-data-src"),
        attr("abs:src"),
    ).map(String::trim).firstOrNull { it.startsWith("http") }
    if (originals != null) return originals
    val largestSrcset = attr("srcset").split(',').mapNotNull { token ->
        val parts = token.trim().split(Regex("\\s+"))
        val absolute = parts.firstOrNull()?.let { raw ->
            if (raw.startsWith("http")) raw
            else runCatching { java.net.URI(baseUri()).resolve(raw).toString() }.getOrDefault(raw)
        }
        val score = parts.getOrNull(1)?.removeSuffix("w")?.removeSuffix("x")?.toFloatOrNull() ?: 0f
        absolute?.takeIf { it.startsWith("http") }?.let { it to score }
    }.maxByOrNull { it.second }?.first
    val fallback = largestSrcset ?: attr("abs:src").trim().takeIf { it.startsWith("http") }
    return fallback?.let(::normalizeCoverUrl)
}

private fun normalizeCoverUrl(url: String): String = url
    .replace(Regex("[-_]\\d{2,5}x\\d{2,5}(?=(?:\\.[A-Za-z]{2,6})(?:\\?|$))"), "")
    .replace(Regex("/thumbs?/(?=[^/]+$)"), "/")

abstract class PortedHtmlSource(
    override val id: Long,
    override val name: String,
    final override val baseUrl: String,
) : DesktopJsonSource(), CatalogueSource, ConfigurableSource {
    private val preferencesStore = SourcePreferencesStore(java.nio.file.Path.of(System.getProperty("user.home"), ".zeravrix"))
    private val sourcePreferences = preferencesStore.load(id).toMutableMap()
    private val browserCookie: String get() = sourcePreferences["cookie"].orEmpty().trim()

    override fun preferences(): Map<String, String> = mapOf("cookie" to browserCookie)
    override fun updatePreference(key: String, value: String) {
        sourcePreferences[key] = value
        preferencesStore.save(id, sourcePreferences)
    }

    protected val sourceHeaders: Map<String, String> get() = if (browserCookie.isBlank()) emptyMap() else mapOf("Cookie" to browserCookie)

    override val language: String = "ar"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)

    protected suspend fun page(url: String, headers: Map<String, String> = emptyMap()): Document {
        val response = request(url, sourceHeaders + headers + mapOf("Accept-Language" to "ar,en;q=0.8"), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in setOf(403, 429) || response.statusCode() in 500..599) {
            throw SourceBlockedException("$name blocked this request (${response.statusCode()}). Complete the site verification in a browser, then refresh.")
        }
        if (response.statusCode() !in 200..299) error("$name returned HTTP ${response.statusCode()} at $url")
        return Jsoup.parse(response.body(), url)
    }

    protected fun cardToManga(card: Element): Manga? {
        val anchor = card.selectFirst("a[href*=/manga/], a[href*=/series/], a[href*=/book/], a[href*=/comics/], a") ?: return null
        val url = anchor.absUrl("href").ifBlank { return null }
        if (url == baseUrl || url.endsWith("/manga/") || url.endsWith("/series/")) return null
        val title = card.selectFirst(".post-title, .entry-title, .data h3, h3, h4")?.text()?.trim()
            ?: anchor.attr("title").trim().ifBlank { anchor.text().trim() }
        if (title.isBlank()) return null
        val image = card.selectFirst("img")?.imageUrl()

        val rawBadges = card.select(".manga-title-badges, .type, .manga-type, span[class*=badge], .genres a, .genres-content a, .mg_genres a, .post-item .summary-content a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val manhwaKeywords = listOf("manhwa", "webtoon", "manhua", "مانهوا", "مانهو", "ويبتون", "ويب تون", "كوري", "صيني")
        val combinedText = (title + " " + url + " " + rawBadges.joinToString(" ")).lowercase()
        val isManhwa = manhwaKeywords.any { combinedText.contains(it) }

        val genres = buildList {
            addAll(rawBadges)
            if (isManhwa && none { it.equals("Manhwa", ignoreCase = true) }) add("Manhwa")
            if (isManhwa && none { it.equals("Webtoon", ignoreCase = true) }) add("Webtoon")
        }.distinct()

        return Manga(url, title, image, genres = genres, sourceId = id)
    }

    protected fun pageImages(document: Document, selectors: String): List<MangaPage> = document.select(selectors).mapNotNull { element ->
        element.imageUrl()?.let { url -> MangaPage(0, url, document.location(), sourceHeaders + mapOf("Referer" to document.location())) }
    }.distinctBy { it.imageUrl }.mapIndexed { index, page -> page.copy(index = index) }
}

abstract class MadaraPortedSource(id: Long, name: String, baseUrl: String) : PortedHtmlSource(id, name, baseUrl.trimEnd('/')) {
    private suspend fun archive(pageNumber: Int, order: String, query: String? = null): MangaPageResult {
        val url = if (query.isNullOrBlank()) {
            "$baseUrl/manga/page/${pageNumber.coerceAtLeast(1)}/?orderby=$order"
        } else {
            "$baseUrl/page/${pageNumber.coerceAtLeast(1)}/?s=${URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)}"
        }
        val document = runCatching { page(url) }.getOrElse {
            if (query.isNullOrBlank()) page("$baseUrl/page/${pageNumber.coerceAtLeast(1)}/?post_type=wp-manga&orderby=$order")
            else throw it
        }
        val cards = document.select("div.page-item-detail, .row.c-tabs-item__content, .manga__item, .manga-item, .item-summary, .search-page .result-item article, .bsx, .listupd .bs, .box")
        val items = cards.mapNotNull(::cardToManga).distinctBy { it.id }
        val fallback = if (items.isEmpty()) document.select("a[href*=/manga/], a[href*=/series/], a[href*=/comics/]").mapNotNull { link ->
            cardToManga(link.parents().firstOrNull { it.selectFirst("img") != null } ?: link)
        }.distinctBy { it.id } else items
        val hasNext = document.select("a.next, a.nextpostslink, .pagination a, .nav-previous").any { it.text().contains("next", true) || it.attr("rel") == "next" || it.text().trim() == (pageNumber + 1).toString() }
        return MangaPageResult(fallback, hasNext || fallback.size >= 20)
    }

    override suspend fun popular(page: Int): MangaPageResult = archive(page, "views")
    override suspend fun latest(page: Int): MangaPageResult = archive(page, "latest")
    override suspend fun search(page: Int, query: String): MangaPageResult = archive(page, "latest", query)

    override suspend fun details(manga: Manga): Manga {
        val document = page(manga.id)
        val title = document.selectFirst(".post-title h1, .summary_content h1, h1.entry-title, h1")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: manga.title
        val cover = document.selectFirst(".summary_image img, .profile-manga .post-content_item img, img")?.imageUrl() ?: manga.coverUrl
        val description = document.selectFirst(".description-summary, .summary__content, .summary_content, .description, .review-content")?.text()?.trim().orEmpty()
        val rawGenres = (document.select(".genres-content a, .genres a, .sgeneros a, a[href*=?genre=], .manga-title-badges, [class*=type]").map { it.text().trim() }.filter(String::isNotBlank) + manga.genres).distinct()
        val manhwaKeywords = listOf("manhwa", "webtoon", "manhua", "مانهوا", "مانهو", "ويبتون", "ويب تون", "كوري", "صيني")
        val text = (title + " " + description + " " + rawGenres.joinToString(" ")).lowercase()
        val isManhwa = manhwaKeywords.any { text.contains(it) }
        val enriched = buildList {
            addAll(rawGenres)
            if (isManhwa && none { it.equals("Manhwa", ignoreCase = true) }) add("Manhwa")
            if (isManhwa && none { it.equals("Webtoon", ignoreCase = true) }) add("Webtoon")
        }.distinct()
        val author = document.selectFirst(".author-content a, [class*=author] a")?.text()?.trim()
        return manga.copy(title = title, coverUrl = cover, description = description, genres = enriched, author = author)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val document = page(manga.id)
        var links: List<Element> = document.select("li.wp-manga-chapter a, .chapter-item a, .listing-chapters_wrap a, #chapter-list a[href]").toList()
        if (links.isEmpty()) {
            val holder = document.selectFirst("div[id^=manga-chapters-holder]")
            val mangaId = holder?.attr("data-id").orEmpty()
            val xhrHeaders = mapOf("Referer" to manga.id, "X-Requested-With" to "XMLHttpRequest", "Accept" to "text/html")
            val response = if (mangaId.isNotBlank()) {
                runCatching { postFormChecked("$baseUrl/wp-admin/admin-ajax.php", mapOf("action" to "manga_get_chapters", "manga" to mangaId), xhrHeaders) }.getOrNull()
            } else null
            val xhrDocument = response?.let { Jsoup.parse(it, manga.id) }
            links = xhrDocument?.select("li.wp-manga-chapter a, .chapter-item a, a[href]")?.toList().orEmpty()
            if (links.isEmpty()) {
                val newer = runCatching { postChecked("${manga.id.trimEnd('/')}/ajax/chapters", "", xhrHeaders) }.getOrNull()
                links = newer?.let { Jsoup.parse(it, manga.id).select("li.wp-manga-chapter a, .chapter-item a, a[href]").toList() }.orEmpty()
            }
        }
        return links.mapNotNull { link ->
            val url = link.absUrl("href").substringBefore("?style=paged")
            if (url.isBlank() || url == manga.id.trimEnd('/')) return@mapNotNull null
            val title = link.text().trim().ifBlank { url.trimEnd('/').substringAfterLast('/') }
            Chapter(url, manga.id, title, parseChapterNumber(title), id, uploadDate = parseDateText(link.selectFirst(".chapter-release-date, .chapterdate")?.text()))
        }.distinctBy { it.id }.sortedWith(compareByDescending<Chapter> { it.number ?: Float.NEGATIVE_INFINITY }.thenBy { it.title })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val document = page(chapter.id)
        return pageImages(document, "div.wp-manga-chapter-img img, .reading-content img, .page-break img, .chapter_image img, img.wp-manga-chapter-img")
    }

    private fun parseDateText(value: String?): Long? = value?.let {
        val formats = listOf("d MMMM، yyyy", "MMMM dd, yyyy", "yyyy-MM-dd")
        formats.firstNotNullOfOrNull { format -> runCatching { SimpleDateFormat(format, Locale("ar")).parse(it)?.time }.getOrNull() }
    }
}

class MangalekMihonSource : MadaraPortedSource(0x4D414E47414C454BL, "Mangalek", "https://mangalik.net")
class MangaSparkMihonSource : MadaraPortedSource(0x535041524B4152L, "MangaSpark", "https://sparkmanga.net")

class TeamXMihonSource : PortedHtmlSource(0x5445414D584C, "Team X", "https://olympustaff.com") {
    private fun pageSuffix(page: Int) = if (page > 1) "?page=$page" else ""

    override suspend fun popular(page: Int): MangaPageResult {
        val document = page("$baseUrl/series${pageSuffix(page)}")
        val items = document.select("div.listupd div.bsx").mapNotNull(::cardToManga).distinctBy { it.id }
        return MangaPageResult(items, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun latest(page: Int): MangaPageResult {
        val document = page("$baseUrl${pageSuffix(page)}")
        val items = document.select("div.last-chapter div.box").mapNotNull { cardToManga(it) }.distinctBy { it.id }
        return MangaPageResult(items, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun search(page: Int, query: String): MangaPageResult {
        val document = page("$baseUrl/search?keyword=${URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)}")
        val items = document.select("div.tx-grid a.tx-card").mapNotNull(::cardToManga).distinctBy { it.id }
        return MangaPageResult(items, false)
    }

    override suspend fun details(manga: Manga): Manga {
        val document = page(manga.id)
        val title = document.selectFirst("div.author-info-title h1")?.text()?.trim() ?: manga.title
        val description = document.select("div.review-content").text().ifBlank { document.select("div.review-content p").text() }
        val genres = document.select("div.review-author-info a").map { it.text().trim() }.filter(String::isNotBlank)
        val coverCandidates = document.select("meta[property=og:image], meta[name=twitter:image], div.text-right img, .series-thumb img, .manga-thumb img, .cover img, .poster img, img")
            .asSequence()
            .mapNotNull { element ->
                if (element.tagName() == "meta") element.attr("content").trim().takeIf { it.startsWith("http") }?.let(::normalizeCoverUrl)
                else element.imageUrl()
            }
            .distinct()
            .toList()
        val cover = coverCandidates.firstOrNull { candidate ->
            !Regex("(?:thumb|thumbnail|small|medium|150x|300x|_\\d{2,4}x\\d{2,4})", RegexOption.IGNORE_CASE).containsMatchIn(candidate)
        } ?: coverCandidates.firstOrNull() ?: manga.coverUrl
        return manga.copy(title = title, description = description, genres = genres, coverUrl = cover)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val first = page(manga.id)
        val pages = first.select("ul.pagination a.page-link").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
        val documents = buildList {
            add(first)
            for (pageNumber in 2..pages.coerceAtMost(50)) add(page("${manga.id}?page=$pageNumber"))
        }
        return documents.flatMap { document ->
            document.select("div.chapter-card").mapNotNull { element ->
                if (element.selectFirst("span.locked") != null) return@mapNotNull null
                val numberText = element.attr("data-number")
                val title = element.selectFirst("div.chapter-info div.chapter-title")?.text()?.takeIf { it.isNotBlank() }
                Chapter(element.selectFirst("a")?.absUrl("href") ?: return@mapNotNull null, manga.id, "الفصل $numberText${if (title.isNullOrBlank() || title == numberText) "" else " - $title"}", numberText.toFloatOrNull(), id, uploadDate = element.attr("data-date").toLongOrNull()?.times(1000))
            }
        }.distinctBy { it.id }.sortedWith(compareByDescending<Chapter> { it.number ?: Float.NEGATIVE_INFINITY }.thenBy { it.title })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val document = page(chapter.id)
        return pageImages(document, "div.image_list canvas[data-src], div.image_list img[src], div.image_list img[data-src]")
    }
}

class MangaSwatMihonSource : DesktopJsonSource(), CatalogueSource {
    override val id: Long = 0x4D414E4741535741L
    override val name: String = "MangaSwat"
    override val language: String = "ar"
    override val baseUrl: String = "https://meshmanga.com"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)
    private val api = "$baseUrl/v2/api/v2"

    private suspend fun apiGet(url: String): Any? = DesktopJson.parse(getChecked(url, mapOf("Accept" to "application/json")))
    private fun poster(raw: Map<String, Any?>): String? = raw.mapValue("poster")?.string("medium", "large", "thumbnail") ?: raw.string("poster")
    private fun listItems(root: Any?): List<Any?> = root.jsonMap().listValue("results", "items")
    private fun next(root: Any?): Boolean = root.jsonMap()["next"] != null

    private fun manga(raw: Map<String, Any?>): Manga? {
        val id = raw.string("id", "serie_id") ?: return null
        return Manga("$api/series/$id/", raw.string("title", "name") ?: "Untitled", poster(raw), sourceId = this.id)
    }

    override suspend fun popular(page: Int): MangaPageResult {
        val root = apiGet("$api/series/?order_by=-followers_count&page=$page")
        val items = listItems(root).mapNotNull { manga(it.jsonMap()) }.distinctBy { it.id }
        return MangaPageResult(items, next(root))
    }

    override suspend fun latest(page: Int): MangaPageResult {
        val root = apiGet("$api/series/releases/?page=$page")
        val items = listItems(root).mapNotNull { manga(it.jsonMap()) }.distinctBy { it.id }
        return MangaPageResult(items, next(root))
    }

    override suspend fun search(page: Int, query: String): MangaPageResult {
        val root = apiGet("$api/series/?search=${URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)}&page=$page")
        val items = listItems(root).mapNotNull { manga(it.jsonMap()) }.distinctBy { it.id }
        return MangaPageResult(items, next(root))
    }

    override suspend fun details(manga: Manga): Manga {
        val id = manga.id.substringAfterLast("/series/").substringBefore('/')
        val root = apiGet("$api/series/$id/").jsonMap()
        val genres = root.listValue("genres").mapNotNull { it.jsonMap().string("name") }
        return manga.copy(title = root.string("title", "name") ?: manga.title, coverUrl = poster(root) ?: manga.coverUrl, description = root.string("story", "description").orEmpty(), author = root.mapValue("author")?.string("name"), artist = root.mapValue("artist")?.string("name"), genres = genres)
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val id = manga.id.substringAfterLast("/series/").substringBefore('/')
        val root = apiGet("$api/chapters/?serie=$id&order_by=-order&page_size=200")
        return buildList {
            var nextUrl: String? = "$api/chapters/?serie=$id&order_by=-order&page_size=200"
            var current: Any? = root
            while (true) {
                addAll(current.jsonMap().listValue("results").mapNotNull { rawValue ->
                    val raw = rawValue.jsonMap(); val chapterId = raw.string("id") ?: return@mapNotNull null
                    val title = raw.string("chapter", "title", "name") ?: "Chapter"
                    Chapter("$api/chapters/$chapterId/", manga.id, title, parseChapterNumber(title), this@MangaSwatMihonSource.id, uploadDate = parseIsoDate(raw.string("created_at", "createdAt")))
                })
                nextUrl = current.jsonMap().string("next")
                if (nextUrl == null) break
                current = apiGet(nextUrl!!)
            }
        }.distinctBy { it.id }
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val root = apiGet(chapter.id).jsonMap()
        return root.listValue("images", "pages").mapNotNull { rawValue ->
            val raw = rawValue.jsonMap(); val url = raw.string("image", "url") ?: return@mapNotNull null
            MangaPage(0, url, chapter.id, mapOf("Referer" to chapter.id))
        }.mapIndexed { index, page -> page.copy(index = index) }
    }
}


class HijalaMihonSource(private val root: java.nio.file.Path = java.nio.file.Path.of(System.getProperty("user.home"), ".zeravrix")) : PortedHtmlSource(0x48494A414C41L, "Hijala", "https://hijala.com") {
    override suspend fun popular(page: Int): MangaPageResult = archive(page, "popular")
    override suspend fun latest(page: Int): MangaPageResult = archive(page, "latest")
    override suspend fun search(page: Int, query: String): MangaPageResult {
        val document = page("$baseUrl/?s=${URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)}&page=$page")
        val items = document.select(".page-item-detail, .bsx, .manga-item, article").mapNotNull(::cardToManga).distinctBy { it.id }
        return MangaPageResult(items, document.select("a.next, a[rel=next]").isNotEmpty())
    }

    private suspend fun archive(pageNumber: Int, order: String): MangaPageResult {
        val document = page("$baseUrl/manga/page/${pageNumber.coerceAtLeast(1)}/?m_orderby=$order")
        val items = document.select(".page-item-detail, .bsx, .manga-item, article").mapNotNull(::cardToManga).distinctBy { it.id }
        return MangaPageResult(items, document.select("a.next, a[rel=next], .pagination a").any { it.attr("rel") == "next" || it.text().contains("next", true) })
    }

    override suspend fun details(manga: Manga): Manga {
        val document = page(manga.id)
        return manga.copy(
            title = document.selectFirst(".post-title h1, .entry-title, h1")?.text()?.trim() ?: manga.title,
            coverUrl = document.selectFirst(".summary_image img, .profile-manga img, img")?.imageUrl() ?: manga.coverUrl,
            description = document.selectFirst(".description-summary, .summary_content, .entry-content, .description")?.text()?.trim().orEmpty(),
            genres = document.select(".genres-content a, .genres a, .mgen a").map { it.text().trim() }.filter(String::isNotBlank),
        )
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val document = page(manga.id)
        return document.select(".chapter-list a, .listing-chapters_wrap a, li.wp-manga-chapter a, ul li a[href]").mapNotNull { link ->
            val url = link.absUrl("href")
            if (url.isBlank() || url == manga.id) return@mapNotNull null
            val title = link.text().trim().ifBlank { url.trimEnd('/').substringAfterLast('/') }
            Chapter(url, manga.id, title, parseChapterNumber(title), id)
        }.distinctBy { it.id }.sortedWith(compareByDescending<Chapter> { it.number ?: Float.NEGATIVE_INFINITY }.thenBy { it.title })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val document = page(chapter.id)
        val pages = pageImages(document, ".reading-content img, .chapter-images img, .chapter-image img, .page-break img, img[data-src], img[src]")
        if (document.selectFirst("#chapter-pages-js-before") == null || pages.size < 2) return pages
        val cacheDir = root.resolve("cache").resolve("hijala-merged")
        java.nio.file.Files.createDirectories(cacheDir)
        val output = mutableListOf<MangaPage>()
        var index = 0
        var pairIndex = 0
        while (index < pages.size) {
            val pair = pages.drop(index).take(2)
            if (pair.size == 2) {
                val merged = mergePair(pair[0].imageUrl, pair[1].imageUrl, cacheDir, pairIndex)
                output += MangaPage(pairIndex, merged.toUri().toString(), chapter.id, mapOf("Referer" to chapter.id))
                pairIndex++
                index += 2
            } else {
                output += pair[0].copy(index = pairIndex)
                index++
            }
        }
        return output
    }

    private suspend fun mergePair(leftUrl: String, rightUrl: String, cacheDir: java.nio.file.Path, pairIndex: Int): java.nio.file.Path {
        val key = java.security.MessageDigest.getInstance("SHA-256").digest((leftUrl + "|" + rightUrl).toByteArray()).joinToString("") { "%02x".format(it) }
        val file = cacheDir.resolve("${key}_$pairIndex.jpg")
        if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) > 0) return file
        val left = request(leftUrl, mapOf("Referer" to baseUrl), java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body()
        val right = request(rightUrl, mapOf("Referer" to baseUrl), java.net.http.HttpResponse.BodyHandlers.ofByteArray()).body()
        val leftImage = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(left)) ?: error("Hijala left page is not an image")
        val rightImage = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(right)) ?: error("Hijala right page is not an image")
        val merged = java.awt.image.BufferedImage(leftImage.width + rightImage.width, maxOf(leftImage.height, rightImage.height), java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = merged.createGraphics()
        try { graphics.drawImage(leftImage, 0, 0, null); graphics.drawImage(rightImage, leftImage.width, 0, null) } finally { graphics.dispose() }
        val temporary = file.resolveSibling(file.fileName.toString() + ".part")
        javax.imageio.ImageIO.write(merged, "jpg", temporary.toFile())
        java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        return file
    }
}
