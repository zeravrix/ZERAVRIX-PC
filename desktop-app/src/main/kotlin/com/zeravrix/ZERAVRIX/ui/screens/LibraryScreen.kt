package com.zeravrix.zeravrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.LibraryStore
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.ReadingProgress
import com.zeravrix.zeravrix.theme.LocalGlassTokens

@Composable
fun LibraryScreen(
    libraryStore: LibraryStore,
    onOpenManga: (Manga) -> Unit,
    onResumeReading: (Manga, ReadingProgress) -> Unit,
    onExploreClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val savedManga = remember { mutableStateOf(libraryStore.list()) }
    val progressMap = remember {
        mutableStateOf<List<Pair<Manga, ReadingProgress>>>(
            savedManga.value.mapNotNull { m -> libraryStore.latestProgress(m.id)?.let { m to it } }
        )
    }

    val latestProgress = progressMap.value.maxByOrNull { it.second.updatedAt }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        // Hero: Continue Reading Banner (if progress exists)
        if (latestProgress != null) {
            val (manga, progress) = latestProgress
            Surface(
                color = tokens.surfaceCard,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, tokens.borderGlow, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 76.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        RemoteCoverImage(url = manga.coverUrl, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CONTINUE READING",
                            color = tokens.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            manga.title,
                            color = tokens.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Page ${progress.pageIndex + 1}",
                            color = tokens.textMuted,
                            fontSize = 12.sp
                        )
                    }
                    Surface(
                        color = tokens.primary,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onResumeReading(manga, progress) }
                    ) {
                        Text(
                            "▶ Resume",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MY LIBRARY (${savedManga.value.size})",
                color = tokens.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = tokens.surfaceCard,
                contentColor = tokens.primary,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(12.dp))
                    .clickable { onExploreClick() }
            ) {
                Text(
                    "+ Explore Titles",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (savedManga.value.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Your library is empty",
                        color = tokens.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Discover titles in the Explore tab and bookmark them for reading.",
                        color = tokens.textMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = tokens.primary,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onExploreClick() }
                    ) {
                        Text(
                            "Browse Popular Titles",
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(savedManga.value, key = { it.id }) { manga ->
                    MangaCard(manga = manga, onClick = { onOpenManga(manga) })
                }
            }
        }
    }
}
