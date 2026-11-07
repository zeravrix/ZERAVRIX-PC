package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import com.zeravrix.zeravrix.core.SourcePreferencesStore
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant

/** Native PC implementation of the Mihon Komga extension. */
class KomgaSource(root: Path = Path.of(System.getProperty("user.home"), ".zeravrix")) : DesktopJsonSource(), ConfigurableSource {
    override val id: Long = 0x4B4F4D474143L
    override val name: String get() = "Komga"
    override val language: String = "multi"
    override val baseUrl: String = "zeravrix://komga"
    override val capabilities = SourceCapabilities(popular = true, latest = true, search = true)

    private val store = SourcePreferencesStore(root)
    private val values = store.load(id).toMutableMap()

    private val serverUrl: String get() = values["serverUrl"].orEmpty().trimEnd('/')
    private val username: String get() = values["username"].orEmpty()
    private val password: String get() = values["password"].orEmpty()
    private val apiKey: String get() = values["apiKey"].orEmpty()
    private val cloudflareId: String get() = values["cloudflareClientId"].orEmpty()
    private val cloudflareSecret: String get() = values["cloudflareClientSecret"].orEmpty()

    override fun preferences(): Map<String, String> = linkedMapOf(
        "serverUrl" to serverUrl,
        "username" to username,
        "password" to password,
        "apiKey" to apiKey,
        "cloudflareClientId" to cloudflareId,
        "cloudflareClientSecret" to cloudflareSecret,
    )

    override fun updatePreference(key: String, value: String) {
        values[key] = value
        store.save(id, values)
    }

    private fun authHeaders(): Map<String, String> = buildMap {
        put("Accept", "application/json")
        if (apiKey.isNotBlank()) put("X-API-Key", apiKey)
        if (username.isNotBlank() || password.isNotBlank()) {
            val token = java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
            put("Authorization", "Basic $token")
        }
        if (cloudflareId.isNotBlank() && cloudflareSecret.isNotBlank()) {
            put("CF-Access-Client-Id", cloudflareId)
            put("CF-Access-Client-Secret", cloudflareSecret)
        }
    }

    private fun configured() {
        requireConfigured("server URL" to serverUrl)
        require(serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            "Komga server URL must start with http:// or https://"
        }
        require(username.isNotBlank() && password.isNotBlank() || apiKey.isNotBlank()) {
            "Komga is not configured. Enter username/password or an API key in Sources."
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun endpoint(path: String): String = "$serverUrl/api/v1/${path.trimStart('/')}"

    private fun mangaFromSeries(raw: Map<String, Any?>): Manga? {
        val metadata = raw.mapValue("metadata") ?: emptyMap()
        val id = raw.string("id") ?: return null
        val title = metadata.string("title", "titleSort") ?: raw.string("name", "title") ?: "Untitled"
        val cover = raw.string("thumbnailUrl", "thumbnail_url") ?: endpoint("series/$id/thumbnail")
        val summary = metadata.string("summary", "description") ?: raw.string("summary", "description") ?: ""
        val authors = metadata.listValue("authors").mapNotNull { it.jsonMap().string("name") }
        val genres = (metadata.listValue("genres") + metadata.listValue("tags")).mapNotNull {
            when (it) { is String -> it; else -> it.jsonMap().string("name") }
        }.distinct()
        return Manga(
            id = endpoint("series/$id"),
            title = title,
            coverUrl = cover,
            description = summary,
            sourceId = id(),
            author = authors.joinToString().ifBlank { null },
            genres = genres,
            status = metadata.string("status"),
        )
    }

    private fun id(): Long = this.id

    private suspend fun seriesPage(url: String): MangaPageResult {
        configured()
        val root = DesktopJson.parse(getChecked(url, authHeaders())).jsonMap()
        val content = root.listValue("content", "series", "items")
        val items = content.mapNotNull { mangaFromSeries(it.jsonMap()) }.distinctBy { it.id }
        val totalPages = root.number("totalPages")?.toInt()
        val number = root.number("number")?.toInt() ?: 0
        val hasNext = root["last"] == false || (totalPages != null && number + 1 < totalPages) || items.size >= 20
        return MangaPageResult(items, hasNext)
    }

    override suspend fun popular(page: Int): MangaPageResult = seriesPage(
        endpoint("series?page=${(page - 1).coerceAtLeast(0)}&size=20&deleted=false&sort=metadata.titleSort,asc"),
    )

    override suspend fun latest(page: Int): MangaPageResult = runCatching {
        seriesPage(endpoint("series/latest?page=${(page - 1).coerceAtLeast(0)}&size=20&deleted=false"))
    }.getOrElse {
        seriesPage(endpoint("series?page=${(page - 1).coerceAtLeast(0)}&size=20&deleted=false&sort=lastModifiedDate,desc"))
    }

    override suspend fun search(page: Int, query: String): MangaPageResult = seriesPage(
        endpoint("series?search=${encode(query.trim())}&page=${(page - 1).coerceAtLeast(0)}&size=20&deleted=false"),
    )

    override suspend fun details(manga: Manga): Manga {
        configured()
        val root = DesktopJson.parse(getChecked(manga.id, authHeaders())).jsonMap()
        return mangaFromSeries(root)?.copy(
            title = mangaFromSeries(root)?.title ?: manga.title,
            coverUrl = mangaFromSeries(root)?.coverUrl ?: manga.coverUrl,
        ) ?: manga
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        configured()
        val root = DesktopJson.parse(
            getChecked("${manga.id}/books?unpaged=true&media_status=READY&deleted=false", authHeaders()),
        )
        val books = when (root) {
            is List<*> -> root
            else -> root.jsonMap().listValue("content", "books", "items")
        }
        return books.mapNotNull { rawValue ->
            val raw = rawValue.jsonMap()
            val bookId = raw.string("id") ?: return@mapNotNull null
            val metadata = raw.mapValue("metadata") ?: emptyMap()
            val number = metadata.number("numberSort", "number") ?: raw.number("numberSort", "number")
            val numberText = metadata.string("number", "numberSort") ?: number?.toString().orEmpty()
            val title = metadata.string("title")?.takeIf { it.isNotBlank() } ?: "Chapter $numberText"
            val release = metadata.string("releaseDate") ?: raw.string("fileLastModified")
            Chapter(
                id = endpoint("books/$bookId"),
                mangaId = manga.id,
                title = if (numberText.isBlank()) title else "$numberText - $title",
                number = number,
                sourceId = id,
                uploadDate = release?.let(::parseDate),
                scanlator = metadata.listValue("authors").mapNotNull { it.jsonMap().string("name") }.joinToString().ifBlank { null },
            )
        }.distinctBy { it.id }.sortedWith(compareBy<Chapter> { it.number ?: Float.POSITIVE_INFINITY }.thenBy { it.title })
    }

    override suspend fun pages(chapter: Chapter): List<MangaPage> {
        configured()
        val root = DesktopJson.parse(getChecked("${chapter.id}/pages", authHeaders()))
        val pages = root.jsonList().ifEmpty { root.jsonMap().listValue("content", "pages", "items") }
        return pages.mapNotNull { rawValue ->
            val raw = rawValue.jsonMap()
            val number = raw.number("number")?.toInt() ?: return@mapNotNull null
            val mediaType = raw.string("mediaType").orEmpty().lowercase()
            val suffix = if (mediaType !in setOf("image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif")) "?convert=png" else ""
            MangaPage(
                index = number - 1,
                imageUrl = "${chapter.id}/pages/$number/full$suffix",
                referer = chapter.id,
                headers = authHeaders(),
            )
        }.sortedBy { it.index }
    }

    private fun parseDate(value: String): Long? = runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
