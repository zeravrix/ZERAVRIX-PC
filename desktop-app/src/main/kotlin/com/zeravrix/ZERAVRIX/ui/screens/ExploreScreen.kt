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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.sources.KotatsuSourceAdapter
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Replicates Kotatsu's layout-w600dp-land/fragment_explore.xml
 * With:
 * - 4 Action Buttons (Local, Bookmarks, Random, Downloads)
 * - Pinned Sources Grid (72x72 favicon cards, title, pin badge)
 * - Sources Catalog (100+ Kotatsu parsers, language filter, search, pin/unpin)
 * - Source Remote Catalogue (RemoteListFragment: 13:18 manga grid, sort order, search, pagination)
 */
@Composable
fun ExploreScreen(
    sourceRegistry: LiveSourceRegistry,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onOpenManga: (Manga) -> Unit,
    onNavigateBookmarks: () -> Unit = {},
    onNavigateDownloads: () -> Unit = {},
    onNavigateLocal: () -> Unit = {},
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()

    // Mode: "EXPLORE" (4 buttons + pinned sources), "CATALOG" (all 100+ sources), "SOURCE_VIEW" (manga from selected source)
    var viewMode by remember { mutableStateOf("EXPLORE") }
    var selectedSource by remember { mutableStateOf<KotatsuSourceAdapter?>(null) }
    var sourceSortOrder by remember { mutableStateOf(SortOrder.POPULARITY) }
    var sourcePage by remember { mutableStateOf(1) }
    var sourceSearchQuery by remember { mutableStateOf("") }
    var sourceMangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isLoadingSource by remember { mutableStateOf(false) }

    // Pinned sources list
    val allParsers = remember { sourceRegistry.getAllKotatsuParserSources() }
    var pinnedParserNames by remember {
        mutableStateOf(
            setOf(
                "MANGADEX",
                "ASURASCANS",
                "FLAMECOMICS",
                "MANGAKAKALOT",
                "MANGASEE",
                "BATOTO",
                "WEBTOONS_EN",
                "COMICK_FUN"
            )
        )
    }

    // Sources catalog filters
    var catalogSearch by remember { mutableStateOf("") }
    var catalogLanguage by remember { mutableStateOf("ALL") }

    // Load source remote list
    fun loadSourceManga(source: KotatsuSourceAdapter, page: Int, order: SortOrder, query: String?) {
        scope.launch {
            isLoadingSource = true
            val res = source.fetchList(page = page, order = order, query = query)
            sourceMangaList = res.items
            isLoadingSource = false
        }
    }

    LaunchedEffect(selectedSource, sourcePage, sourceSortOrder, sourceSearchQuery) {
        val src = selectedSource
        if (src != null) {
            loadSourceManga(src, sourcePage, sourceSortOrder, sourceSearchQuery.ifBlank { null })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        when (viewMode) {
            "EXPLORE" -> {
                // Header
                Text(
                    "EXPLORE",
                    color = tokens.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(14.dp))

                // 4 Action Buttons matching Kotatsu's item_explore_buttons.xml
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExploreActionButton(
                        title = "Local storage",
                        icon = "📁",
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateLocal
                    )
                    ExploreActionButton(
                        title = "Bookmarks",
                        icon = "🔖",
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateBookmarks
                    )
                    ExploreActionButton(
                        title = "Random manga",
                        icon = "🎲",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                val popular = sourceRegistry.fetchPopular()
                                val random = popular.randomOrNull()
                                if (random != null) onOpenManga(random)
                            }
                        }
                    )
                    ExploreActionButton(
                        title = "Downloads",
                        icon = "⬇",
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateDownloads
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Sources Section Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PINNED SOURCES",
                        color = tokens.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { viewMode = "CATALOG" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.surfaceContainerHigh,
                            contentColor = tokens.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Sources Catalog (${allParsers.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Pinned Sources Grid matching Kotatsu item_explore_source_grid.xml
                val pinnedParsers = allParsers.filter { it.name in pinnedParserNames }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pinnedParsers) { parser ->
                        KotatsuSourceGridItem(
                            parser = parser,
                            onClick = {
                                val adapter = sourceRegistry.getOrCreateAdapter(parser)
                                if (adapter != null) {
                                    selectedSource = adapter
                                    sourcePage = 1
                                    sourceSearchQuery = ""
                                    viewMode = "SOURCE_VIEW"
                                }
                            }
                        )
                    }
                }
            }

            "CATALOG" -> {
                // Sources Catalog view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewMode = "EXPLORE" },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("← Back", fontSize = 12.sp)
                    }

                    Spacer(Modifier.width(16.dp))

                    Text(
                        "SOURCES CATALOG",
                        color = tokens.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.weight(1f))

                    // Language Selector
                    val languages = listOf("ALL", "en", "ja", "ar", "ru", "es", "fr", "it", "ko", "zh")
                    var langMenuExpanded by remember { mutableStateOf(false) }

                    Box {
                        OutlinedButton(
                            onClick = { langMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Language: $catalogLanguage", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = langMenuExpanded,
                            onDismissRequest = { langMenuExpanded = false }
                        ) {
                            languages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.uppercase()) },
                                    onClick = {
                                        catalogLanguage = lang
                                        langMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Search sources input
                OutlinedTextField(
                    value = catalogSearch,
                    onValueChange = { catalogSearch = it },
                    placeholder = { Text("Search 100+ sources by name...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.primary,
                        unfocusedBorderColor = tokens.outlineVariant,
                    )
                )

                Spacer(Modifier.height(14.dp))

                // Filtered sources
                val filteredParsers = allParsers.filter { parser ->
                    val matchName = catalogSearch.isBlank() || parser.title.contains(catalogSearch, ignoreCase = true)
                    val matchLang = catalogLanguage == "ALL" || parser.locale.equals(catalogLanguage, ignoreCase = true)
                    matchName && matchLang
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredParsers) { parser ->
                        val isPinned = parser.name in pinnedParserNames
                        Surface(
                            color = tokens.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, tokens.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable {
                                    val adapter = sourceRegistry.getOrCreateAdapter(parser)
                                    if (adapter != null) {
                                        selectedSource = adapter
                                        sourcePage = 1
                                        sourceSearchQuery = ""
                                        viewMode = "SOURCE_VIEW"
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Favicon
                                Surface(
                                    color = tokens.primaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            parser.title.take(2).uppercase(),
                                            color = tokens.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        parser.title,
                                        color = tokens.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Locale: ${parser.locale.uppercase()}",
                                        color = tokens.textMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                // Pin / Unpin Button
                                IconButton(
                                    onClick = {
                                        pinnedParserNames = if (isPinned) {
                                            pinnedParserNames - parser.name
                                        } else {
                                            pinnedParserNames + parser.name
                                        }
                                    }
                                ) {
                                    Text(
                                        if (isPinned) "📌" else "📍",
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "SOURCE_VIEW" -> {
                val src = selectedSource ?: return
                // Top controls bar for RemoteListFragment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewMode = "EXPLORE" },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("← Sources", fontSize = 12.sp)
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            src.name,
                            color = tokens.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Page $sourcePage • ${src.language.uppercase()}",
                            color = tokens.primary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Sort Order Dropdown
                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Sort: ${sourceSortOrder.name.lowercase().replaceFirstChar { it.uppercase() }}", fontSize = 12.sp)
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        sourceSortOrder = order
                                        sourcePage = 1
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Live search within source
                OutlinedTextField(
                    value = sourceSearchQuery,
                    onValueChange = {
                        sourceSearchQuery = it
                        sourcePage = 1
                    },
                    placeholder = { Text("Search manga on ${src.name}...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.primary,
                        unfocusedBorderColor = tokens.outlineVariant,
                    )
                )

                Spacer(Modifier.height(14.dp))

                if (isLoadingSource) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tokens.primary)
                    }
                } else if (sourceMangaList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No manga found for this query/source.", color = tokens.textMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(sourceMangaList, key = { it.id }) { manga ->
                            KotatsuMangaCard(
                                manga = manga,
                                onClick = { onOpenManga(manga) }
                            )
                        }
                    }

                    // Pagination Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (sourcePage > 1) sourcePage-- },
                            enabled = sourcePage > 1,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary)
                        ) {
                            Text("← Previous", fontSize = 12.sp)
                        }

                        Spacer(Modifier.width(20.dp))

                        Text("Page $sourcePage", color = tokens.textPrimary, fontWeight = FontWeight.Bold)

                        Spacer(Modifier.width(20.dp))

                        Button(
                            onClick = { sourcePage++ },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.surfaceContainerHigh, contentColor = tokens.textPrimary)
                        ) {
                            Text("Next →", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4 Action buttons matching Kotatsu item_explore_buttons.xml
 */
@Composable
private fun ExploreActionButton(
    title: String,
    icon: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    Surface(
        color = tokens.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, tokens.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 72x72 Favicon source item matching Kotatsu item_explore_source_grid.xml
 */
@Composable
private fun KotatsuSourceGridItem(
    parser: MangaParserSource,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        // 72dp x 72dp Favicon View
        Surface(
            color = tokens.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .size(72.dp)
                .border(1.dp, tokens.outlineVariant, RoundedCornerShape(16.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = parser.title.take(2).uppercase(),
                    color = tokens.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("📌", fontSize = 10.sp)
            Spacer(Modifier.width(3.dp))
            Text(
                text = parser.title,
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Kotatsu Manga Card with 13:18 aspect ratio cover
 */
@Composable
private fun KotatsuMangaCard(
    manga: Manga,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        // 13:18 Aspect Ratio Cover
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tokens.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(13f / 18f)
                .border(1.dp, tokens.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        ) {
            RemoteCoverImage(url = manga.coverUrl, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = manga.title,
            color = tokens.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
