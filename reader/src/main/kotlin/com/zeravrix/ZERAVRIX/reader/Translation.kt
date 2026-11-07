package com.zeravrix.zeravrix.reader

import java.awt.image.BufferedImage
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min

/** Normalized selection rectangle inside a rendered manga page. */
data class SelectionRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized(): SelectionRegion = SelectionRegion(
        left = min(left, right).coerceIn(0f, 1f),
        top = min(top, bottom).coerceIn(0f, 1f),
        right = max(left, right).coerceIn(0f, 1f),
        bottom = max(top, bottom).coerceIn(0f, 1f),
    )

    fun isUseful(): Boolean = (right - left) >= .015f && (bottom - top) >= .015f
}

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val provider: String,
)

enum class TranslationLanguage(val code: String, val label: String) {
    AUTO("auto", "Auto-detect"),
    ENGLISH("en", "English"),
    ARABIC("ar", "Arabic"),
    CHINESE("zh-CN", "Chinese"),
    JAPANESE("ja", "Japanese"),
    FRENCH("fr", "French"),
    MALAY("ms", "Malay"),
    INDONESIAN("id", "Indonesian"),
    KOREAN("ko", "Korean"),
}

/**
 * Desktop-safe OCR bridge. It uses a locally installed/bundled Tesseract when available.
 * The executable can be overridden with ZERAVRIX_TESSERACT or -Dzeravrix.tesseract=... .
 */
object MangaOcrBridge {
    private val candidateNames = listOf("tesseract.exe", "tesseract")

    fun executable(): String? {
        val configured = System.getProperty("zeravrix.tesseract")?.trim().orEmpty()
            .ifBlank { System.getenv("ZERAVRIX_TESSERACT")?.trim().orEmpty() }
        if (configured.isNotBlank() && Files.isExecutable(Path.of(configured))) return configured
        val path = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
        return path.asSequence()
            .flatMap { directory -> candidateNames.asSequence().map { Path.of(directory, it) } }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.toString()
    }

    fun recognize(page: Path, region: SelectionRegion, languageHint: TranslationLanguage = TranslationLanguage.AUTO): String {
        val source = ImageIO.read(page.toFile()) ?: return ""
        val r = region.normalized()
        val x = (source.width * r.left).toInt().coerceIn(0, source.width - 1)
        val y = (source.height * r.top).toInt().coerceIn(0, source.height - 1)
        val right = (source.width * r.right).toInt().coerceIn(x + 1, source.width)
        val bottom = (source.height * r.bottom).toInt().coerceIn(y + 1, source.height)
        val crop = source.getSubimage(x, y, right - x, bottom - y)
        val tempDir = Files.createTempDirectory("zeravrix-ocr-")
        val input = tempDir.resolve("selection.png")
        val outputBase = tempDir.resolve("result")
        ImageIO.write(crop, "png", input.toFile())
        val executable = executable() ?: return ""
        val language = when (languageHint) {
            TranslationLanguage.ENGLISH -> "eng"
            TranslationLanguage.CHINESE -> "chi_sim"
            TranslationLanguage.JAPANESE -> "jpn"
            TranslationLanguage.FRENCH -> "fra"
            TranslationLanguage.MALAY -> "msa"
            TranslationLanguage.INDONESIAN -> "ind"
            TranslationLanguage.KOREAN -> "kor"
            TranslationLanguage.ARABIC -> "ara"
            TranslationLanguage.AUTO -> "eng"
        }
        return try {
            val process = ProcessBuilder(executable, input.toString(), outputBase.toString(), "-l", language, "--psm", "6")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                ""
            } else {
                val textFile = Path.of(outputBase.toString() + ".txt")
                if (Files.exists(textFile)) Files.readString(textFile).replace(Regex("\\s+"), " ").trim() else ""
            }
        } finally {
            runCatching { Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
}

/**
 * Fast text translation provider. It prefers a user-configured LibreTranslate-compatible
 * endpoint, then uses the lightweight Google web endpoint as a no-key fallback.
 */
class MangaTranslationEngine(
    private val endpoint: String? = System.getProperty("zeravrix.translation.endpoint")?.trim()?.takeIf { it.isNotBlank() },
) {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    private val cache = ConcurrentHashMap<String, TranslationResult>()

    suspend fun translate(
        text: String,
        source: TranslationLanguage,
        target: TranslationLanguage,
    ): TranslationResult {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        require(clean.isNotBlank()) { "No selectable text was detected." }
        require(target != TranslationLanguage.AUTO) { "Choose Arabic or English as the target language." }
        val key = "${source.code}|${target.code}|${clean.lowercase(Locale.ROOT)}"
        cache[key]?.let { return it }
        val result = if (endpoint != null) {
            runCatching { translateLibre(clean, source, target) }.getOrNull()
        } else null
        val translated = result ?: translateGoogle(clean, source, target)
        val output = TranslationResult(clean, translated, source.label, target.label, if (result != null) "Configured provider" else "Fast web provider")
        cache[key] = output
        return output
    }

    private fun translateLibre(text: String, source: TranslationLanguage, target: TranslationLanguage): String {
        val base = endpoint!!.trimEnd('/')
        val body = "{\"q\":\"${jsonEscape(text)}\",\"source\":\"${source.code}\",\"target\":\"${target.code}\",\"format\":\"text\"}"
        val request = HttpRequest.newBuilder(URI.create("$base/translate"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .timeout(java.time.Duration.ofSeconds(12))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "Translation provider returned HTTP ${response.statusCode()}" }
        return Regex("\\\"translatedText\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
            .find(response.body())?.groupValues?.getOrNull(1)?.let(::jsonUnescape)
            ?.takeIf { it.isNotBlank() }
            ?: error("Translation provider returned no text")
    }

    private fun translateGoogle(text: String, source: TranslationLanguage, target: TranslationLanguage): String {
        val query = URLEncoder.encode(text, Charsets.UTF_8)
        val uri = URI.create("https://translate.googleapis.com/translate_a/single?client=gtx&sl=${source.code}&tl=${target.code}&dt=t&q=$query")
        val request = HttpRequest.newBuilder(uri)
            .header("Accept", "application/json")
            .header("User-Agent", "ZERAVRIX/0.8")
            .timeout(java.time.Duration.ofSeconds(12))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) { "Translation service returned HTTP ${response.statusCode()}" }
        val translated = Regex("\\[\\s*\\[\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"")
            .find(response.body())?.groupValues?.getOrNull(1)?.let(::jsonUnescape)
        require(!translated.isNullOrBlank()) { "Translation service returned no text" }
        return translated
    }

    private fun jsonEscape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun jsonUnescape(value: String): String = value
        .replace("\\\\", "\\")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
}
