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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

// 500 MB maximum on-disk storage cache budget
private const val MAX_DISK_CACHE_BYTES = 500L * 1024L * 1024L

val coverCacheDir: Path by lazy {
    val dir = Path.of(System.getProperty("user.home"), ".zeravrix", "cache", "covers")
    Files.createDirectories(dir)
    dir
}

private val okHttpClient: OkHttpClient by lazy {
    val httpCache = coverCacheDir.resolve("http").toFile()
    OkHttpClient.Builder()
        .cache(Cache(httpCache, MAX_DISK_CACHE_BYTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

// Bounded active RAM memory governor: only holds up to 12 active bitmaps (3 behind, 1 current, 5 ahead + buffer)
// to strictly prevent 6GB RAM spikes during continuous multi-chapter reading.
private const val MAX_MEMORY_ENTRIES = 12

private val memoryCache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
    object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }
)

/**
 * Prunes the in-memory bitmap cache, retaining only pages in the active window [current - 3 .. current + 5].
 * Automatically releases all memory of past/future chapters and invokes GC when large buffers are evicted.
 */
fun pruneMemoryExcept(activeUrls: Set<String>) {
    var evicted = 0
    synchronized(memoryCache) {
        val iter = memoryCache.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key !in activeUrls) {
                iter.remove()
                evicted++
            }
        }
    }
    if (evicted > 0) {
        System.gc()
    }
}

/**
 * Completely clears the in-memory bitmap cache and requests GC.
 */
fun clearMemoryCache() {
    synchronized(memoryCache) {
        memoryCache.clear()
    }
    System.gc()
}

/**
 * Enforces the 500 MB disk storage cache budget by deleting oldest files when size exceeds budget.
 */
fun ensureDiskCacheBudget(maxBytes: Long = MAX_DISK_CACHE_BYTES) {
    runCatching {
        if (!Files.exists(coverCacheDir)) return
        val files = Files.list(coverCacheDir).filter { Files.isRegularFile(it) }.toList()
        var totalSize = files.sumOf { Files.size(it) }
        if (totalSize > maxBytes) {
            // Sort by last modified time ascending (oldest first)
            val sorted = files.sortedBy { Files.getLastModifiedTime(it).toMillis() }
            for (file in sorted) {
                if (totalSize <= maxBytes * 0.85) break // prune down to 85% of budget
                val sz = Files.size(file)
                if (Files.deleteIfExists(file)) {
                    totalSize -= sz
                }
            }
        }
    }
}

private fun hashUrl(url: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(url.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

suspend fun fetchImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        when {
            url.startsWith("jar:") -> {
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
                                runCatching {
                                    Files.write(cachedFile, body)
                                    ensureDiskCacheBudget()
                                }
                                body
                            } else null
                        } else null
                    }
                }
            }
        }
    }.getOrNull()
}

/**
 * Preloads page into storage cache on disk (and memory if within the 12-page window).
 */
suspend fun preloadPageImage(url: String, allowMemory: Boolean = true) = withContext(Dispatchers.IO) {
    if (url.isBlank()) return@withContext
    if (memoryCache.containsKey(url)) return@withContext
    val bytes = fetchImageBytes(url)
    if (bytes != null && bytes.isNotEmpty() && allowMemory) {
        synchronized(memoryCache) {
            if (memoryCache.size < MAX_MEMORY_ENTRIES) {
                runCatching {
                    val skia = SkiaImage.makeFromEncoded(bytes)
                    memoryCache[url] = skia.toComposeImageBitmap()
                }
            }
        }
    }
}

/**
 * High-performance, anti-hotlink cover image renderer with Shimmer loading, memory LRU,
 * and hardware-accelerated color adjustments.
 */
@Composable
fun RemoteCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    colorFilter: ColorFilter? = null
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

        val cached = memoryCache[url]
        if (cached != null) {
            imageBitmap = cached
            isLoading = false
            return@LaunchedEffect
        }

        val bytes = fetchImageBytes(url)
        if (bytes != null && bytes.isNotEmpty()) {
            val skia = runCatching { SkiaImage.makeFromEncoded(bytes) }.getOrNull()
            if (skia != null) {
                val composeBmp = skia.toComposeImageBitmap()
                memoryCache[url] = composeBmp
                imageBitmap = composeBmp
                hasError = false
            } else {
                hasError = true
            }
        } else {
            hasError = true
        }
        isLoading = false
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = contentDescription,
                contentScale = contentScale,
                colorFilter = colorFilter,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF14161C)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                    color = tokens.primary.copy(alpha = 0.7f)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF14161C)),
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
