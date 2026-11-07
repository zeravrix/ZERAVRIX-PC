package com.zeravrix.zeravrix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga as CoreManga
import com.zeravrix.zeravrix.data.DesktopMangaLoaderContext
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter

/**
 * 2-Pane Split Details View (Tablet/Desktop Landscape w600dp-land layout)
 * styled with ZERAVRIX Cyber-Glassmorphic design tokens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSplitPane(
    loaderContext: DesktopMangaLoaderContext,
    manga: Manga,
    isFavourite: Boolean,
    onToggleFavourite: (Manga) -> Unit,
    onOpenChapter: (Manga, MangaChapter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    var fullDetails by remember(manga.id) { mutableStateOf(manga) }
    var isLoadingDetails by remember(manga.id) { mutableStateOf(true) }
    var coverBitmap by remember(manga.coverUrl) { mutableStateOf<ImageBitmap?>(null) }
    var reversedChapters by remember { mutableStateOf(false) }
    var chapterFilter by remember { mutableStateOf("") }

    LaunchedEffect(manga.id) {
        isLoadingDetails = true
        coverBitmap = loaderContext.loadImageBitmap(manga.coverUrl ?: "")
        runCatching {
            val parser = loaderContext.newParserInstance(manga.source)
            parser.getDetails(manga)
        }.onSuccess { detailed ->
            fullDetails = detailed
        }
        isLoadingDetails = false
    }

    val allChapters = remember(fullDetails.chapters, reversedChapters) {
        val list = fullDetails.chapters.orEmpty()
        if (reversedChapters) list.reversed() else list
    }

    val filteredChapters = remember(allChapters, chapterFilter) {
        if (chapterFilter.isBlank()) allChapters
        else allChapters.filter { it.title.orEmpty().contains(chapterFilter, ignoreCase = true) }
    }

    Row(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Left Column: Manga Info, Cover, Badges, and Action Buttons (42% width)
        Surface(
            color = tokens.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Top Navigation Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .background(tokens.surfaceGlass, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.textPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Manga Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.textMuted
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Cover & Primary Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Image with Glow
                    Box(
                        modifier = Modifier
                            .width(135.dp)
                            .aspectRatio(13f / 18f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, tokens.borderGlow, RoundedCornerShape(12.dp))
                            .background(Color(0xFF131A2B)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap!!,
                                contentDescription = fullDetails.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = tokens.textMuted
                            )
                        }
                    }

                    // Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = fullDetails.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = tokens.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        val authorsStr = fullDetails.authors?.joinToString(", ") ?: ""
                        if (authorsStr.isNotBlank()) {
                            Text(
                                text = "By $authorsStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.accentGlow
                            )
                        }

                        // Source & State Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = tokens.accentGlow.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, tokens.accentGlow.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = fullDetails.source.name,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    color = tokens.accentGlow,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            fullDetails.state?.let { state ->
                                Surface(
                                    color = tokens.surfaceGlass,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = state.name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        color = tokens.textMuted
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Action Buttons: Read & Bookmark
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    allChapters.firstOrNull()?.let { firstChap ->
                                        onOpenChapter(fullDetails, firstChap)
                                    }
                                },
                                enabled = allChapters.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentGlow),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(Modifier.width(6.dp))
                                Text("Read", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            IconButton(
                                onClick = { onToggleFavourite(fullDetails) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(tokens.surfaceGlass, RoundedCornerShape(10.dp))
                                    .border(1.dp, if (isFavourite) tokens.accentGlow else tokens.borderGlass, RoundedCornerShape(10.dp))
                            ) {
                                Icon(
                                    if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favourite",
                                    tint = if (isFavourite) Color(0xFFFF4081) else tokens.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tags Flow
                if (fullDetails.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        fullDetails.tags.take(12).forEach { tag ->
                            Surface(
                                color = tokens.surfaceGlass,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, tokens.borderGlass, RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = tag.title,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontSize = 10.sp,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Description
                HorizontalDivider(color = tokens.borderGlass, thickness = 1.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = tokens.textPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = fullDetails.description?.ifBlank { "No synopsis provided." } ?: "No synopsis provided.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = tokens.textMuted
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Right Column: Chapter list with header controls (58% width)
        Surface(
            color = tokens.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chapters Header
                Surface(
                    color = tokens.surfaceGlass,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Chapters (${filteredChapters.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Filter search input
                            OutlinedTextField(
                                value = chapterFilter,
                                onValueChange = { chapterFilter = it },
                                placeholder = { Text("Filter...", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.width(130.dp).height(38.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // Reverse order button
                            IconButton(
                                onClick = { reversedChapters = !reversedChapters },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(tokens.surfaceCard, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    Icons.Default.SwapVert,
                                    contentDescription = "Reverse Order",
                                    tint = tokens.accentGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = tokens.borderGlass, thickness = 1.dp)

                // Chapter List
                if (isLoadingDetails) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tokens.accentGlow)
                    }
                } else if (filteredChapters.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No chapters found.", color = tokens.textMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredChapters, key = { it.id }) { chapter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChapter(fullDetails, chapter) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = listOfNotNull(chapter.scanlator, chapter.branch).joinToString(" • ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = tokens.textMuted
                                        )
                                    }
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            HorizontalDivider(color = tokens.borderGlass.copy(alpha = 0.35f), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSplitPane(
    manga: CoreManga,
    isFavourite: Boolean,
    onToggleFavourite: (CoreManga) -> Unit,
    onOpenChapter: (CoreManga, Chapter) -> Unit,
    sourceRegistry: LiveSourceRegistry,
    loaderContext: DesktopMangaLoaderContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalGlassTokens.current
    var chapters by remember(manga.id) { mutableStateOf<List<Chapter>>(emptyList()) }
    var isLoadingDetails by remember(manga.id) { mutableStateOf(true) }
    var coverBitmap by remember(manga.coverUrl) { mutableStateOf<ImageBitmap?>(null) }
    var reversedChapters by remember { mutableStateOf(false) }
    var chapterFilter by remember { mutableStateOf("") }

    LaunchedEffect(manga.id) {
        isLoadingDetails = true
        coverBitmap = loaderContext.loadImageBitmap(manga.coverUrl.orEmpty())
        chapters = sourceRegistry.fetchChapters(manga)
        isLoadingDetails = false
    }

    val displayChapters = remember(chapters, reversedChapters) {
        if (reversedChapters) chapters.reversed() else chapters
    }

    val filteredChapters = remember(displayChapters, chapterFilter) {
        if (chapterFilter.isBlank()) displayChapters
        else displayChapters.filter { it.title.contains(chapterFilter, ignoreCase = true) }
    }

    Row(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Left Column: Manga Info, Cover, Badges, and Action Buttons (42% width)
        Surface(
            color = tokens.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Back navigation bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.textPrimary
                        )
                    }
                    Text(
                        text = "Manga Overview",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.textMuted
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Poster & Metadata Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Poster with glowing border
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(185.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, tokens.borderGlow, RoundedCornerShape(12.dp))
                            .background(tokens.surfaceGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap!!,
                                contentDescription = manga.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = tokens.textMuted
                            )
                        }
                    }

                    // Metadata
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = manga.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = tokens.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        manga.author?.let { author ->
                            if (author.isNotBlank()) {
                                Text(
                                    text = "By $author",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.accentGlow
                                )
                            }
                        }

                        // State & Source Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = tokens.surfaceGlass,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, tokens.borderGlass, RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = manga.status ?: "Ongoing",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.accentGlow
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Action Buttons: "Read First Chapter" and "Bookmark / Favourite"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val first = chapters.firstOrNull()
                            if (first != null) onOpenChapter(manga, first)
                        },
                        enabled = chapters.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Reading", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onToggleFavourite(manga) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isFavourite) tokens.accentGlow else tokens.textPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isFavourite) tokens.accentGlow else tokens.borderGlass
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavourite) Color(0xFFFF4081) else tokens.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Genres flow row
                if (manga.genres.isNotEmpty()) {
                    Text(
                        text = "Genres",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tokens.textPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        manga.genres.forEach { genre ->
                            Surface(
                                color = tokens.surfaceGlass,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(1.dp, tokens.borderGlass, RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = genre,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                }

                // Synopsis description
                Text(
                    text = "Synopsis",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tokens.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = manga.description.ifBlank { "No synopsis provided." },
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = tokens.textMuted
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // Right Column: Chapters List & Search Filter (58% width)
        Surface(
            color = tokens.surfaceCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Chapters Header with Count and Sort Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Chapters",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = tokens.textPrimary
                        )
                        Surface(
                            color = tokens.surfaceGlass,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, tokens.borderGlass, RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = "${chapters.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.accentGlow
                            )
                        }
                    }

                    // Sort Order Reverse Toggle
                    IconButton(onClick = { reversedChapters = !reversedChapters }) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = "Reverse Order",
                            tint = if (reversedChapters) tokens.accentGlow else tokens.textMuted
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Chapter Search Filter
                OutlinedTextField(
                    value = chapterFilter,
                    onValueChange = { chapterFilter = it },
                    placeholder = { Text("Filter chapters...", fontSize = 13.sp, color = tokens.textMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = tokens.textMuted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (chapterFilter.isNotEmpty()) {
                            IconButton(onClick = { chapterFilter = "" }) {
                                Icon(Icons.Default.Clear, null, tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.borderGlow,
                        unfocusedBorderColor = tokens.borderGlass,
                        focusedTextColor = tokens.textPrimary,
                        unfocusedTextColor = tokens.textPrimary,
                        cursorColor = tokens.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )

                Spacer(Modifier.height(14.dp))

                // Chapters LazyColumn
                if (isLoadingDetails) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tokens.primary)
                    }
                } else if (filteredChapters.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No chapters found", color = tokens.textMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredChapters, key = { it.id }) { chapter ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenChapter(manga, chapter) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = chapter.title.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val scanlator = chapter.scanlator
                                    if (!scanlator.isNullOrBlank()) {
                                        Text(
                                            text = scanlator,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = tokens.textMuted
                                        )
                                    }
                                }

                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            HorizontalDivider(color = tokens.borderGlass.copy(alpha = 0.35f), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}
