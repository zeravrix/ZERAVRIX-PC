package com.zeravrix.zeravrix.source

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.bitmap.Bitmap as KotatsuBitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect as KotatsuRect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.network.UserAgents
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class HeadlessBitmap(val image: BufferedImage) : KotatsuBitmap {
    override val width: Int get() = image.width
    override val height: Int get() = image.height

    override fun drawBitmap(sourceBitmap: KotatsuBitmap, src: KotatsuRect, dst: KotatsuRect) {
        val srcImage = (sourceBitmap as? HeadlessBitmap)?.image ?: return
        val g = image.createGraphics()
        g.drawImage(
            srcImage,
            dst.left, dst.top, dst.right, dst.bottom,
            src.left, src.top, src.right, src.bottom,
            null
        )
        g.dispose()
    }
}

class HeadlessCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = store.getOrPut(url.host) { mutableListOf() }
        synchronized(hostCookies) {
            cookies.forEach { newCookie ->
                hostCookies.removeAll { it.name == newCookie.name }
                hostCookies.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = store[url.host] ?: return emptyList()
        synchronized(hostCookies) {
            val now = System.currentTimeMillis()
            hostCookies.removeAll { it.expiresAt < now }
            return hostCookies.toList()
        }
    }
}

class HeadlessMangaLoaderContext(
    appDir: Path = Path.of(System.getProperty("user.home"), ".zeravrix")
) : MangaLoaderContext() {

    private val cookieJarInstance = HeadlessCookieJar()
    private val cacheDir = appDir.resolve("http_cache").toFile()

    override val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(
            Dispatcher(Executors.newFixedThreadPool(32)).apply {
                maxRequests = 128
                maxRequestsPerHost = 24
            }
        )
        .connectionPool(ConnectionPool(32, 10, TimeUnit.MINUTES))
        .cookieJar(cookieJarInstance)
        .cache(Cache(cacheDir, 512L * 1024L * 1024L))
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
            if (original.header("User-Agent").isNullOrBlank()) {
                builder.header("User-Agent", UserAgents.FIREFOX_DESKTOP)
            }
            if (original.header("Accept").isNullOrBlank()) {
                builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            }
            chain.proceed(builder.build())
        }
        .build()

    override val cookieJar: CookieJar get() = cookieJarInstance

    override fun getDefaultUserAgent(): String = UserAgents.FIREFOX_DESKTOP

    override fun getConfig(source: MangaSource): MangaSourceConfig {
        return object : MangaSourceConfig {
            override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
        }
    }

    @Deprecated("Deprecated in Kotatsu MangaLoaderContext", ReplaceWith("evaluateJs(baseUrl, script)"))
    override suspend fun evaluateJs(script: String): String? = null
    override suspend fun evaluateJs(baseUrl: String, script: String): String? = null

    override fun createBitmap(width: Int, height: Int): KotatsuBitmap {
        return HeadlessBitmap(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB))
    }

    fun newParserInstance(source: MangaSource): MangaParser {
        val parserSource = (source as? MangaParserSource)
            ?: MangaParserSource.entries.firstOrNull { it.name.equals(source.name, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unsupported source: ${source.name}")
        return newParserInstance(parserSource)
    }

    override fun redrawImageResponse(
        response: Response,
        redraw: (KotatsuBitmap) -> KotatsuBitmap
    ): Response {
        val body = response.body ?: return response
        return try {
            val original = ImageIO.read(body.byteStream()) ?: return response
            val bmp = HeadlessBitmap(original)
            val transformed = (redraw(bmp) as? HeadlessBitmap)?.image ?: original
            val baos = ByteArrayOutputStream()
            ImageIO.write(transformed, "png", baos)
            val mediaType = "image/png".toMediaTypeOrNull()
            val newBody = baos.toByteArray().toResponseBody(mediaType)
            response.newBuilder().body(newBody).build()
        } catch (e: Exception) {
            response
        }
    }
}
