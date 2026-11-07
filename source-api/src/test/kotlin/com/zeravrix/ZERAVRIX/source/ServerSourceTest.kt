package com.zeravrix.zeravrix.source

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerSourceTest {
    @Test
    fun komgaReturnsChaptersAndPages() = runBlocking {
        val server = testServer { exchange ->
            when {
                exchange.requestURI.path == "/api/v1/series" -> json(exchange, """{"content":[{"id":"s1","metadata":{"title":"Demo Series","summary":"Summary","numberSort":1}}],"last":true}""")
                exchange.requestURI.path == "/api/v1/series/s1" -> json(exchange, """{"id":"s1","metadata":{"title":"Demo Series","summary":"Summary"}}""")
                exchange.requestURI.path == "/api/v1/series/s1/books" -> json(exchange, """[{"id":"b1","metadata":{"numberSort":1,"number":"1","title":"First"}}]""")
                exchange.requestURI.path == "/api/v1/books/b1/pages" -> json(exchange, """[{"number":1,"mediaType":"image/jpeg"},{"number":2,"mediaType":"image/png"}]""")
                else -> json(exchange, "{}", 404)
            }
        }
        val root = Files.createTempDirectory("zeravrix-komga-test")
        val source = KomgaSource(root)
        source.updatePreference("serverUrl", "http://127.0.0.1:${server.address.port}")
        source.updatePreference("username", "demo")
        source.updatePreference("password", "password")
        val list = source.latest(1)
        assertEquals(1, list.items.size)
        val manga = source.details(list.items.first())
        val chapters = source.chapters(manga)
        assertEquals(1, chapters.size)
        val pages = source.pages(chapters.first())
        assertEquals(2, pages.size)
        assertTrue(pages[0].imageUrl.contains("/pages/1"))
        server.stop(0)
    }

    @Test
    fun kavitaAuthenticatesAndReturnsReaderPages() = runBlocking {
        val server = testServer { exchange ->
            when {
                exchange.requestURI.path == "/api/Plugin/authenticate" -> json(exchange, """{"username":"demo","token":"jwt-token","apiKey":"key"}""")
                exchange.requestURI.path == "/api/Series/all" -> json(exchange, """{"results":[{"id":1,"name":"Kavita Demo","summary":"Summary"}],"hasNextPage":false}""")
                exchange.requestURI.path == "/api/Series/1" -> json(exchange, """{"id":1,"name":"Kavita Demo","summary":"Summary"}""")
                exchange.requestURI.path == "/api/Series/volumes" -> json(exchange, """[{"name":"Volume 1","chapters":[{"id":7,"number":"1","title":"First"}]}]""")
                exchange.requestURI.path == "/api/Chapter" -> json(exchange, """{"pages":2}""")
                else -> json(exchange, "{}", 404)
            }
        }
        val root = Files.createTempDirectory("zeravrix-kavita-test")
        val source = KavitaSource(root)
        source.updatePreference("serverUrl", "http://127.0.0.1:${server.address.port}")
        source.updatePreference("apiKey", "key")
        val list = source.latest(1)
        assertEquals(1, list.items.size)
        val chapters = source.chapters(list.items.first())
        assertEquals(1, chapters.size)
        val pages = source.pages(chapters.first())
        assertEquals(2, pages.size)
        assertTrue(pages[0].imageUrl.contains("Reader/image"))
        server.stop(0)
    }

    private fun testServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            try { handler(exchange) } finally { exchange.close() }
        }
        server.start()
        return server
    }

    private fun json(exchange: HttpExchange, value: String, status: Int = 200) {
        val bytes = value.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
