package com.zeravrix.zeravrix.core

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopDownloaderTest {
    @Test
    fun writesManifestAndFindsOfflinePages() = runBlocking {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/page.webp") { exchange ->
            val output = ByteArrayOutputStream()
            val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB)
            for (x in 0 until 64) for (y in 0 until 64) image.setRGB(x, y, (x * 97 shl 16) or (y * 53 shl 8) or ((x + y) * 31))
            ImageIO.write(image, "png", output)
            val body = output.toByteArray()
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val root = Files.createTempDirectory("zeravrix-downloader-test")
            val downloader = DesktopDownloader(root)
            val manga = Manga("manga-1", "Test Manga")
            val chapter = Chapter("chapter-1", manga.id, "Chapter 1", 1f)
            val page = MangaPage(0, "http://127.0.0.1:${server.address.port}/page.webp")
            val saved = downloader.downloadChapter(manga, chapter, listOf(page))
            assertTrue(downloader.isDownloaded(manga.id, chapter.id))
            assertEquals(1, saved.pageCount)
            val listed = downloader.downloadedChapters(manga.id)
            assertEquals(1, listed.size)
            assertEquals(1, downloader.localPages(listed.single()).size)
        } finally {
            server.stop(0)
        }
    }
}
