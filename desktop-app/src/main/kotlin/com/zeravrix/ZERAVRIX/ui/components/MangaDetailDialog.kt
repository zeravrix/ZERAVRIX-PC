package com.zeravrix.zeravrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.LibraryStore
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.screens.RemoteCoverImage
import kotlinx.coroutines.launch

@Composable
fun MangaDetailDialog(
    manga: Manga,
    sourceRegistry: LiveSourceRegistry,
    libraryStore: LibraryStore,
    onDismiss: () -> Unit,
    onReadChapter: (Chapter) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    var chapters by remember { mutableStateOf<List<Chapter>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isBookmarked by remember { mutableStateOf(libraryStore.list().any { it.id == manga.id }) }

    LaunchedEffect(manga.id) {
        isLoading = true
        chapters = sourceRegistry.fetchChapters(manga)
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = tokens.surfaceGlassRaised,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(680.dp)
                .height(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // Header Row (Cover + Info + Close)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(width = 110.dp, height = 155.dp)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        RemoteCoverImage(url = manga.coverUrl, modifier = Modifier.fillMaxSize())
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            manga.title,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            manga.author ?: manga.genres.joinToString(" • "),
                            color = tokens.primary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            manga.description.ifBlank { "No synopsis available." },
                            color = tokens.textMuted,
                            fontSize = 11.sp,
                            maxLines = 3,
                            lineHeight = 16.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Bookmark in library toggle
                            Surface(
                                color = if (isBookmarked) tokens.primary.copy(alpha = 0.2f) else tokens.surfaceCard,
                                contentColor = if (isBookmarked) tokens.primary else tokens.textPrimary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, if (isBookmarked) tokens.borderGlow else tokens.borderGlass, RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (isBookmarked) {
                                            libraryStore.remove(manga.id)
                                            isBookmarked = false
                                        } else {
                                            libraryStore.add(manga)
                                            isBookmarked = true
                                        }
                                    }
                            ) {
                                Text(
                                    if (isBookmarked) "★ In Library" else "+ Add to Library",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Read First / Latest Chapter
                            if (chapters.isNotEmpty()) {
                                Surface(
                                    color = tokens.primary,
                                    contentColor = Color.Black,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onReadChapter(chapters.first()) }
                                ) {
                                    Text(
                                        "Read Chapter 1",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Close Button
                    Surface(
                        color = tokens.surfaceCard,
                        contentColor = tokens.textMuted,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("✕", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = tokens.borderGlass, thickness = 1.dp)
                Spacer(Modifier.height(10.dp))

                // Chapters Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CHAPTERS (${chapters.size})",
                        color = tokens.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Chapters List
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = tokens.primary)
                        }
                    } else if (chapters.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No chapters available from this source.", color = tokens.textMuted, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(chapters, key = { it.id }) { chapter ->
                                Surface(
                                    color = tokens.surfaceCard,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onReadChapter(chapter) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            chapter.title,
                                            color = tokens.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "Read →",
                                            color = tokens.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
