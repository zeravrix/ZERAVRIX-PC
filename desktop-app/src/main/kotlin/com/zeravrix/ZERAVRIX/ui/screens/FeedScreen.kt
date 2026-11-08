package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
 * Replicates Kotatsu's FeedFragment & item_feed.xml with Material 3 polish
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
                .padding(bottom = 14.dp),
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

            FilledTonalButton(
                onClick = { loadFeed() },
                enabled = !isRefreshing,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = tokens.surfaceContainerHigh,
                    contentColor = tokens.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = tokens.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Refreshing...", fontSize = 12.sp)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Check Updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (favourites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.DynamicFeed,
                        contentDescription = null,
                        tint = tokens.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Your feed is empty",
                        color = tokens.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add manga to Favourites to automatically track new releases here.",
                        color = tokens.textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onExploreClick,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = tokens.primary, contentColor = tokens.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
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
                    ModernFeedItemRow(
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
 * Matches Kotatsu item_feed.xml with cyber-glass elevation and vector badges
 */
@Composable
private fun ModernFeedItemRow(
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

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.01f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Surface(
        color = if (hovered) tokens.surfaceContainerHigh else tokens.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (hovered) 1.dp else 1.dp,
                color = if (hovered) tokens.borderGlow else tokens.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .shadow(
                elevation = if (hovered) 6.dp else 1.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = tokens.primary
            )
            .hoverable(interaction)
            .clickable(onClick = onClickManga)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 44x58 Cover Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
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
                Spacer(Modifier.height(3.dp))
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
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (item.isRead) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (item.isRead) "Re-read" else "Read",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
