package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

@Composable
fun ExploreScreen(
    sourceRegistry: LiveSourceRegistry,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenManga: (Manga) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSourceId by remember { mutableStateOf<Long?>(null) }
    var mangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshContent() {
        scope.launch {
            isLoading = true
            mangaList = if (searchQuery.isNotBlank()) {
                sourceRegistry.search(searchQuery, selectedSourceId)
            } else {
                sourceRegistry.fetchPopular(selectedSourceId)
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedSourceId, searchQuery) {
        refreshContent()
    }

    val categories = listOf("All", "Manhwa", "Manhua", "Manga", "Action", "Romance", "Fantasy")

    val filteredList = remember(mangaList, selectedCategory) {
        if (selectedCategory == "All") mangaList
        else mangaList.filter { manga ->
            val matchType = selectedCategory.lowercase()
            manga.genres.any { it.lowercase().contains(matchType) } ||
                    manga.title.lowercase().contains(matchType) ||
                    manga.description.lowercase().contains(matchType)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        // Top filter bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        color = if (isSelected) tokens.primary else tokens.surfaceCard,
                        contentColor = if (isSelected) Color.Black else tokens.textPrimary,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                1.dp,
                                if (isSelected) tokens.borderGlow else tokens.borderGlass,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedCategory = category }
                    ) {
                        Text(
                            category,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Refresh Button
            Surface(
                color = tokens.surfaceCard,
                contentColor = tokens.primary,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                    .clickable { refreshContent() }
            ) {
                Text(
                    "⟳ Refresh",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Content Area
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading && mangaList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tokens.primary)
                }
            } else if (filteredList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No titles found", color = tokens.textPrimary, fontWeight = FontWeight.Bold)
                        Text("Try selecting a different source or searching for another term", color = tokens.textMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.id }) { manga ->
                        MangaCard(manga = manga, onClick = { onOpenManga(manga) })
                    }
                }
            }
        }
    }
}

@Composable
fun MangaCard(
    manga: Manga,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()

    Surface(
        color = tokens.surfaceCard,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isHovered) tokens.borderGlow else tokens.borderGlass,
                RoundedCornerShape(12.dp)
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer(
                scaleX = if (isHovered) 1.03f else 1f,
                scaleY = if (isHovered) 1.03f else 1f
            )
    ) {
        Column {
            // Cover Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(Color(0xFF141A29))
            ) {
                RemoteCoverImage(
                    url = manga.coverUrl,
                    modifier = Modifier.fillMaxSize()
                )

                // Glass Gradient Overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                )

                // Status or type badge if present
                manga.status?.takeIf { it.isNotBlank() }?.let { status ->
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xDD000000))
                            .border(1.dp, tokens.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            status,
                            color = tokens.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title & Info
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    manga.title,
                    color = tokens.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    manga.author ?: manga.genres.firstOrNull() ?: "Webtoon",
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val CoverCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()
private val HttpCoverClient by lazy {
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
}

@Composable
fun RemoteCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { CoverCache[it] }) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val bytes = runCatching {
                val req = HttpRequest.newBuilder(URI(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ZERAVRIX/1.6")
                    .GET().build()
                HttpCoverClient.send(req, HttpResponse.BodyHandlers.ofByteArray()).takeIf { it.statusCode() == 200 }?.body()
            }.getOrNull()

            if (bytes != null && bytes.isNotEmpty()) {
                val skiaBmp = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
                if (skiaBmp != null) {
                    CoverCache[url] = skiaBmp
                    bitmap = skiaBmp
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF131A2B)),
            contentAlignment = Alignment.Center
        ) {
            Text("ZX", color = Color(0x3300D2FF), fontWeight = FontWeight.Black, fontSize = 24.sp)
        }
    }
}
