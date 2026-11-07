package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.SourcePreferencesStore
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant

/** Native PC implementation of the Mihon Kavita extension. */
class KavitaSource(root: Path = Path.of(System.getProperty("user.home"), ".zeravrix")) : DesktopJsonSource(), ConfigurableSource {
    override val id: Long = 0x4B4156495441L
    override val name: String get() = "Kavita"
    override val language: String = "multi"
    override val baseUrl: String = "zeravrix://kavita"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)

    private val store = SourcePreferencesStore(root)
    private val values = store.load(id).toMutableMap()
    @Volatile private var jwtToken: String = ""

    private val serverUrl: String get() = values["serverUrl"].orEmpty().trimEnd('/')
    private val apiKey: String get() = values["apiKey"].orEmpty()
    private val libraryId: String get() = values["libraryId"].orEmpty().ifBlank { "0" }

    override fun preferences(): Map<String, String> = linkedMapOf(
        "serverUrl" to serverUrl,
        "apiKey" to apiKey,
        "libraryId" to libraryId,
    )

    override fun updatePreference(key: String, value: String) {
        values[key] = value
        if (key == "serverUrl" || key == "apiKey") jwtToken = ""
        store.save(id, values)
    }

    private fun configured() {
        requireConfigured("server URL" to serverUrl, "API key" to apiKey)
        require(serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            "Kavita server URL must start with http:// or https://"
        }
    }

    private fun endpoint(path: String): String = "$serverUrl/api/${path.trimStart('/')}"
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private suspend fun ensureToken() {
        configured()
        if (jwtToken.isNotBlank()) return
        val pluginNames = listOf("ZERAVRIX", "Tachiyomi-Kavita")
        var last: Throwable? = null
        for (plugin in pluginNames) {
            try {
                val response = postChecked(
                    "${endpoint("Plugin/authenticate")}?apiKey=${encode(apiKey)}&pluginName=${encode(plugin)}",
                    body = "",
                    headers = mapOf("Accept" to "application/json"),
                )
                val token = DesktopJson.parse(response).jsonMap().string("token", "accessToken")
                if (!token.isNullOrBlank()) {
                    jwtToken = token
                    return
                }
                last = IllegalStateException("Kavita authentication response did not contain a token")
            } catch (failure: Throwable) {
                last = failure
            }
        }
        throw IllegalStateException("Kavita authentication failed: ${last?.message ?: "unknown error"}", last)
    }

    private suspend fun headers(): Map<String, String> {
        ensureToken()
        return mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $jwtToken",
            "x-api-key" to apiKey,
        )
    }

    private fun seriesId(raw: Map<String, Any?>): String? = raw.string("id", "seriesId", "series_id")

    private fun mangaFromSeries(raw: Map<String, Any?>): Manga? {
        val id = seriesId(raw) ?: return null
        val metadata = raw.mapValue("metadata") ?: emptyMap()
        val title = raw.string("name", "title", "seriesName") ?: metadata.string("title", "name") ?: "Untitled"
        val cover = raw.string("coverImage", "coverUrl", "thumbnailUrl", "poster")
            ?: "${endpoint("Series/$id/thumbnail")}"
        val description = raw.string("summary", "description") ?: metadata.string("summary", "description").orEmpty()
        val authors = raw.listValue("authors", "people").mapNotNull { it.jsonMap().string("name") }
        val genres = (raw.listValue("genres", "tags") + metadata.listValue("genres", "tags")).mapNotNull {
            when (it) { is String -> it; else -> it.jsonMap().string("name", "title") }
        }.distinct()
        return Manga(
            id = endpoint("Series/$id"),
            title = title,
            coverUrl = cover,
            description = description,
            sourceId = this.id,
            author = authors.joinToString().ifBlank { null },
            genres = genres,
            status = raw.string("publicationStatus", "status") ?: metadata.string("status"),
        )
    }

    private fun extractSeries(root: Any?): List<Any?> = when (root) {
        is List<*> -> root
        else -> root.jsonMap().listValue("results", "items", "series", "payload", "data")
    }

    private fun hasMore(root: Any?, count: Int, page: Int): Boolean {
        val map = root.jsonMap()
        if (map["hasNextPage"] is Boolean) return map["hasNextPage"] as Boolean
        if (map["hasNext"] is Boolean) return map["hasNext"] as Boolean
        val totalPages = map.number("totalPages", "pages")?.toInt()
        if (totalPages != null) return page < totalPages
        return count >= 20
    }

    private suspend fun listSeries(page: Int, query: String? = null): MangaPageResult {
        val auth = headers()
        val root: Any?
        if (query.isNullOrBlank()) {
            root = runCatching {
                DesktopJson.parse(getChecked(endpoint("Series/all") + "?libraryId=${encode(libraryId)}&pageNumber=$page&pageSize=20", auth))
            }.getOrElse {
                DesktopJson.parse(postChecked(endpoint("Series/all-v2") + "?pageNumber=$page&pageSize=20", "{}", auth))
            }
        } else {
            root = runCatching {
                DesktopJson.parse(getChecked(endpoint("Search/search") + "?queryString=${encode(query)}", auth))
            }.getOrElse {
                val body = "{\"statements\":[{\"comparison\":\"Matches\",\"field\":\"SeriesName\",\"value\":\"${query.replace("\\", "\\\\").replace("\"", "\\\"")}\"}],\"sortOptions\":{\"sortField\":1,\"isAscending\":true}}"
                DesktopJson.parse(postChecked(endpoint("Series/all-v2") + "?pageNumber=$page&pageSize=20", body, auth))
            }
        }
        val items = extractSeries(root).mapNotNull { mangaFromSeries(it.jsonMap()) }.distinctBy { it.id }
        return MangaPageResult(items, hasMore(root, items.size, page))
    }

    override suspend fun popular(page: Int): MangaPageResult = listSeries(page)
    override suspend fun latest(page: Int): MangaPageResult = listSeries(page)
    override suspend fun search(page: Int, query: String): MangaPageResult = listSeries(page, query)

    override suspend fun details(manga: Manga): Manga {
        val root = DesktopJson.parse(getChecked(manga.id, headers())).jsonMap()
        return mangaFromSeries(root)?.copy(
            title = mangaFromSeries(root)?.title ?: manga.title,
            coverUrl = mangaFromSeries(root)?.coverUrl ?: manga.coverUrl,
        ) ?: manga
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        val id = Regex("/(?:Series|series)/([^/?]+)").find(manga.id)?.groupValues?.get(1)
            ?: manga.id.substringAfterLast('/').substringBefore('?')
        val root = DesktopJson.parse(getChecked(endpoint("Series/volumes") + "?seriesId=${encode(id)}", headers()))
        val volumes = root.jsonList().ifEmpty { root.jsonMap().listValue("results", "items", "volumes", "data") }
        return volumes.flatMap { volumeValue ->
            val volume = volumeValue.jsonMap()
            val volumeTitle = volume.string("name", "title").orEmpty()
            volume.listValue("chapters").mapNotNull { chapterValue ->
                val chapter = chapterValue.jsonMap()
                val chapterId = chapter.string("id", "chapterId") ?: return@mapNotNull null
                val numberText = chapter.string("number", "chapterNumber", "title").orEmpty()
                val title = chapter.string("title", "name")?.takeIf { it.isNotBlank() } ?: "Chapter $numberText"
                Chapter(
                    id = endpoint("Chapter") + "?chapterId=${encode(chapterId)}",
                    mangaId = manga.id,
                    title = if (volumeTitle.isBlank()) title else "$volumeTitle - $title",
                    number = numberText.toFloatOrNull() ?: Regex("([0-9]+(?:\\.[0-9]+)?)").find(numberText)?.groupValues?.get(1)?.toFloatOrNull(),
                    sourceId = this.id,
                    uploadDate = chapter.string("createdAt", "created_at", "releaseDate")?.let(::parseDate),
                    scanlator = "${chapter.number("pages") ?: 0} pages",
                )
            }
        }.distinctBy { it.id }.sortedWith(compareBy<Chapter> { it.number ?: Float.POSITIVE_INFINITY }.thenBy { it.title })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        val chapterId = chapter.id.substringAfter("chapterId=").substringBefore('&').ifBlank {
            chapter.id.substringAfterLast('/').substringBefore('?')
        }
        val auth = headers()
        val root = DesktopJson.parse(getChecked(endpoint("Chapter") + "?chapterId=${encode(chapterId)}", auth)).jsonMap()
        val pages = root.number("pages", "pageCount")?.toInt()
            ?: root.listValue("pages", "images").size
        if (pages <= 0) throw IllegalStateException("Kavita returned no pages for chapter ${chapter.title}")
        return (0 until pages).map { index ->
            MangaPage(
                index = index,
                imageUrl = endpoint("Reader/image") + "?chapterId=${encode(chapterId)}&page=$index&extractPdf=true&apiKey=${encode(apiKey)}",
                referer = endpoint("Chapter") + "?chapterId=${encode(chapterId)}",
                headers = auth,
            )
        }
    }

    private fun parseDate(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
