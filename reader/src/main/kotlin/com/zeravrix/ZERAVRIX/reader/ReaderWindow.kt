package com.zeravrix.zeravrix.reader

import com.zeravrix.zeravrix.core.MangaPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import org.jetbrains.skia.Image as SkiaImage

/** Downloads remote manga pages once and hands the reader local files. */
class PageCache(private val root: Path) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    suspend fun get(page: MangaPage): Path = withContext(Dispatchers.IO) {
        Files.createDirectories(root)
        val resolvedUrl = resolveUrl(page.imageUrl, page.referer)
        if (resolvedUrl.scheme.equals("file", ignoreCase = true)) {
            val local = Path.of(resolvedUrl)
            check(Files.isRegularFile(local) && hasImageSignature(local)) { "Local page is not a supported image: $local" }
            return@withContext local
        }
        val ext = extension(resolvedUrl.toString())
        val file = root.resolve(hash(resolvedUrl.toString()) + ext)
        if (Files.isRegularFile(file) && Files.size(file) > 0) {
            if (hasImageSignature(file)) return@withContext file
            Files.deleteIfExists(file)
        }
        val request = HttpRequest.newBuilder(resolvedUrl)
            .timeout(Duration.ofSeconds(60))
            .header("User-Agent", "ZERAVRIX/1.0.1 (Windows; Desktop)")
            .header("Accept", "image/jpeg,image/png,image/webp,image/apng,image/*;q=0.95,*/*;q=0.6")
            .header("Accept-Encoding", "gzip, deflate")
            .apply { page.referer?.let { header("Referer", it) } }
            .apply { page.headers.forEach { (k, v) -> header(k, v) } }
            .GET().build()
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
                check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} for page ${page.index + 1}" }
                val body = response.body()
                check(body.size >= 256) { "Empty or incomplete image response for page ${page.index + 1}" }
                val contentType = response.headers().firstValue("content-type").orElse("").lowercase(Locale.ROOT)
                check(!contentType.contains("text/html") && !looksLikeHtml(body)) {
                    "Source returned a web page instead of an image for page ${page.index + 1}"
                }
                check(isDecodableImage(body)) { "Source returned bytes that are not a decodable image for page ${page.index + 1}" }
                val temp = file.resolveSibling(file.fileName.toString() + ".part")
                Files.write(temp, body)
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return@withContext file
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                lastFailure = failure
                Files.deleteIfExists(file.resolveSibling(file.fileName.toString() + ".part"))
                if (attempt < 2) kotlinx.coroutines.delay(350L * (attempt + 1))
            }
        }
        throw IllegalStateException("Failed to download page ${page.index + 1}: ${lastFailure?.message ?: "unknown error"}", lastFailure)
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun resolveUrl(raw: String, referer: String?): URI {
        val value = raw.trim()
        val direct = URI(value)
        if (direct.isAbsolute) return direct
        val base = referer?.trim()?.takeIf { it.isNotBlank() }?.let(::URI)
            ?: error("Relative page URL has no referer: $raw")
        return base.resolve(value)
    }

    private fun looksLikeHtml(body: ByteArray): Boolean {
        val prefix = body.copyOfRange(0, minOf(body.size, 256)).toString(Charsets.ISO_8859_1).trimStart().lowercase(Locale.ROOT)
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("<head") || prefix.startsWith("<body")
    }

    private fun hasImageSignature(path: Path): Boolean = runCatching {
        Files.newInputStream(path).use { input ->
            val header = input.readNBytes(16)
            hasImageSignature(header)
        }
    }.getOrDefault(false)

    private fun hasImageSignature(bytes: ByteArray): Boolean {
        if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) return true
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        if (bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }) return true
        if (bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP") return true
        return false
    }

    private fun isDecodableImage(bytes: ByteArray): Boolean = runCatching {
        SkiaImage.makeFromEncoded(bytes).close()
        true
    }.getOrDefault(false)

    private fun extension(url: String): String = runCatching {
        val p = URI(url).path.substringAfterLast('.', "")
        if (p.length in 2..5) ".${p.lowercase()}" else ".img"
    }.getOrDefault(".img")
}
