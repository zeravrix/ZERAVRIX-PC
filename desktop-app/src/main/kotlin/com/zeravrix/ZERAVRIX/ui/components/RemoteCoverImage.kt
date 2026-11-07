package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

private val imageHttpClient: HttpClient by lazy {
    HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
}

private val cacheDir: Path by lazy {
    val dir = Path.of(System.getProperty("user.home"), ".zeravrix", "cache", "covers")
    Files.createDirectories(dir)
    dir
}

private fun hashUrl(url: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(url.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

@Composable
fun RemoteCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var imageBitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(url) { mutableStateOf(!url.isNullOrBlank()) }
    var hasError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            isLoading = false
            hasError = true
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            runCatching {
                val hashed = hashUrl(url)
                val cachedFile = cacheDir.resolve(hashed)

                val bytes = if (Files.exists(cachedFile) && Files.size(cachedFile) > 0) {
                    Files.readAllBytes(cachedFile)
                } else {
                    val request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build()

                    val response = imageHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
                    if (response.statusCode() in 200..299) {
                        val body = response.body()
                        runCatching { Files.write(cachedFile, body) }
                        body
                    } else null
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    val skia = SkiaImage.makeFromEncoded(bytes)
                    imageBitmap = skia.toComposeImageBitmap()
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
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E2024)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.fillMaxSize(0.3f), color = Color.Gray)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E2024)), contentAlignment = Alignment.Center) {
                Text("📖", fontSize = 24.sp)
            }
        }
    }
}
