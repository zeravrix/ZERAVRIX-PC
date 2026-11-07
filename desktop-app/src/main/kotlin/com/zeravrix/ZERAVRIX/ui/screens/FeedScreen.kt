package com.zeravrix.zeravrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.LibraryStore
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class FeedItem(
    val manga: Manga,
    val chapter: Chapter,
    val isRead: Boolean = false,
)

/**
 * Replicates Kotatsu's FeedFragment & item_feed.xml
 */
@Composable
fun FeedScreen(
    libraryStore: LibraryStore,
    sourceRegistry: LiveSourceRegistry,
    onOpenManga: (Manga) -> Unit,
    onReadChapter: (Manga, Chapter) -> Unit,
    onExploreClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    val favourites = remember { libraryStore.list() }

    var isRefreshing by remember { mutableStateOf(false) }
    var feedItems by remember { mutableStateOf<List<FeedItem>>(emptyList()) }

    fun loadFeed() {
        scope.launch {
            isRefreshing = true
            val items = mutableListOf<FeedItem>()
            favourites.forEach { manga ->
                val cached = libraryStore.cachedChapters(manga.id)
                val chapters = if (cached.isNotEmpty()) cached else {
                    val fetched = runCatching { sourceRegistry.fetchChapters(manga) }.getOrDefault(emptyList())
                    if (fetched.isNotEmpty()) {
                        libraryStore.saveChapterCache(manga.id, fetched)
                    }
                    fetched
                }
                chapters.take(3).forEach { chapter ->
                    val readProg = libraryStore.readProgress(manga.id, chapter.id)
                    items.add(FeedItem(manga = manga, chapter = chapter, isRead = readProg != null))
                }
            }
            feedItems = items.sortedByDescending { it.chapter.uploadDate ?: 0L }
            isRefreshing = false
        }
    }

    LaunchedEffect(favourites) {
        loadFeed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        // Feed Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "UPDATES & FEED",
                    color = tokens.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    "Latest chapters from your saved library.",
                    color = tokens.textMuted,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { loadFeed() },
                enabled = !isRefreshing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.surfaceContainerHigh,
                    contentColor = tokens.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (isRefreshing) "Refreshing..." else "↻ Check Updates", fontSize = 12.sp)
            }
        }

        if (favourites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📰", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Your feed is empty",
                        color = tokens.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add manga to Favourites to track new releases here.",
                        color = tokens.textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Explore Sources", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (feedItems.isEmpty() && isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tokens.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(feedItems, key = { "${it.manga.id}_${it.chapter.id}" }) { item ->
                    FeedItemRow(
                        item = item,
                        onClickManga = { onOpenManga(item.manga) },
                        onRead = { onReadChapter(item.manga, item.chapter) }
                    )
                }
            }
        }
    }
}

/**
 * Matches Kotatsu item_feed.xml: 40x40 cover, title, summary, read button
 */
@Composable
private fun FeedItemRow(
    item: FeedItem,
    onClickManga: () -> Unit,
    onRead: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val date = item.chapter.uploadDate
    val dateStr = remember(date) {
        if (date != null && date > 0) {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(date))
        } else "Recent"
    }

    Surface(
        color = tokens.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, tokens.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClickManga)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 40x40 Cover Image
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tokens.surfaceContainerHigh)
            ) {
                RemoteCoverImage(url = item.manga.coverUrl, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.manga.title,
                    color = tokens.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.chapter.title,
                        color = tokens.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " • $dateStr",
                        color = tokens.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Button(
                onClick = onRead,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.isRead) tokens.surfaceContainerHigh else tokens.primary,
                    contentColor = if (item.isRead) tokens.textMuted else tokens.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(if (item.isRead) "Re-read" else "Read", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
