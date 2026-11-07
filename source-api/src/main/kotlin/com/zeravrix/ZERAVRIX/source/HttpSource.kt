package com.zeravrix.zeravrix.source

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

abstract class HttpSource : Source {
    protected val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2).build()
    }

    protected suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        request(url, headers, HttpResponse.BodyHandlers.ofString()).body()
    }

    protected suspend fun getDocument(url: String, headers: Map<String, String> = emptyMap()): Document =
        Jsoup.parse(getText(url, headers), url)

    protected suspend fun <T> request(url: String, headers: Map<String, String> = emptyMap(), handler: HttpResponse.BodyHandler<T>): HttpResponse<T> = withContext(Dispatchers.IO) {
        val builder = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", USER_AGENT).GET()
        headers.forEach { (k, v) -> builder.header(k, v) }
        client.send(builder.build(), handler)
    }

    override suspend fun popular(page: Int) = MangaPageResult(emptyList(), false)
    override suspend fun latest(page: Int) = MangaPageResult(emptyList(), false)
    override suspend fun search(page: Int, query: String) = MangaPageResult(emptyList(), false)
    override suspend fun details(manga: Manga) = manga
    override suspend fun chapters(manga: Manga) = emptyList<Chapter>()
    override suspend fun pages(chapter: Chapter) = emptyList<MangaPage>()

    companion object { const val USER_AGENT = "ZERAVRIX/1.0.1 (Windows; Desktop)" }
}
