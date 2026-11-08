package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.zeravrix.zeravrix.core.ChapterArchive
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val coverCacheDir: Path by lazy {
    val dir = Path.of(System.getProperty("user.home"), ".zeravrix", "cache", "covers")
    Files.createDirectories(dir)
    dir
}

private val okHttpClient: OkHttpClient by lazy {
    val httpCache = coverCacheDir.resolve("http").toFile()
    OkHttpClient.Builder()
        .cache(Cache(httpCache, 256L * 1024L * 1024L)) // 256 MB cache
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

private fun hashUrl(url: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(url.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * High-performance, anti-hotlink cover image renderer with Shimmer loading & memory LRU
 */
@Composable
fun RemoteCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val tokens = LocalGlassTokens.current
    var imageBitmap by remember(url) { mutableStateOf(url?.let { memoryCache[it] }) }
    var isLoading by remember(url) { mutableStateOf(!url.isNullOrBlank() && imageBitmap == null) }
    var hasError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            isLoading = false
            hasError = true
            return@LaunchedEffect
        }

        if (imageBitmap != null) {
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            runCatching {
                val bytes: ByteArray? = when {
                    url.startsWith("jar:") -> {
                        // CBZ archive entry: jar:file:///.../chapter.cbz!/0000.jpg
                        val jarUriString = url.substringAfter("jar:").substringBefore("!/")
                        val entryName = url.substringAfter("!/")
                        val archivePath = Path.of(URI.create(jarUriString))
                        ChapterArchive.readEntry(archivePath, entryName)
                    }

                    url.startsWith("file:") -> {
                        val filePath = Path.of(URI.create(url))
                        if (Files.exists(filePath)) Files.readAllBytes(filePath) else null
                    }

                    runCatching { Files.exists(Path.of(url)) }.getOrDefault(false) -> {
                        Files.readAllBytes(Path.of(url))
                    }

                    else -> {
                        val hashed = hashUrl(url)
                        val cachedFile = coverCacheDir.resolve(hashed)

                        if (Files.exists(cachedFile) && Files.size(cachedFile) > 0) {
                            Files.readAllBytes(cachedFile)
                        } else {
                            // Derive appropriate Referer from URL host to bypass image hotlinking protections
                            val parsedUri = runCatching { URI.create(url) }.getOrNull()
                            val referer = if (parsedUri?.host != null) {
                                "${parsedUri.scheme ?: "https"}://${parsedUri.host}/"
                            } else "https://mangadex.org/"

                            val request = Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                .header("Referer", referer)
                                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                .build()

                            okHttpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.bytes()
                                    if (body != null && body.isNotEmpty()) {
                                        runCatching { Files.write(cachedFile, body) }
                                        body
                                    } else null
                                } else null
                            }
                        }
                    }
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    val skia = SkiaImage.makeFromEncoded(bytes)
                    val composeBmp = skia.toComposeImageBitmap()
                    if (memoryCache.size > 300) {
                        memoryCache.clear()
                    }
                    memoryCache[url] = composeBmp
                    imageBitmap = composeBmp
                    hasError = false
                } else {
                    hasError = true
                }
            }.onFailure {
                hasError = true
            }
            isLoading = false
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            // Elegant pulsing skeleton shimmer
            val transition = rememberInfiniteTransition()
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.surfaceContainerHigh.copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                    color = tokens.primary.copy(alpha = 0.7f)
                )
            }
        } else {
            // High-grade fallback placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoStories,
                    contentDescription = "Cover Placeholder",
                    tint = tokens.primary.copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
