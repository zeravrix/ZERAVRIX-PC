package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.zeravrix.zeravrix.ui.components.ZChip
import com.zeravrix.zeravrix.ui.components.ZSearchBar
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder

/**
 * Restyled Kotatsu Landscape Explore & Sources Screen
 * Featuring:
 * - High-speed search bar with instant vector clear
 * - Modern Glass Action Buttons (Local Storage, Bookmarks, Random Manga, Downloads)
 * - Required Filter Chips: "New chapters", "18+", "Self-published", and categories
 * - Pinned Sources Grid with cyber-glass badges & hover physics
 * - Full 1000+ Sources Catalog with language filter and pinning
 * - Remote Source Manga Catalogue with sorting and pagination
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

    // View mode: "EXPLORE" (default), "CATALOG" (all 1000+ sources), "SOURCE_VIEW" (manga from one source)
    var viewMode by remember { mutableStateOf("EXPLORE") }
    var selectedSource by remember { mutableStateOf<KotatsuSourceAdapter?>(null) }
    var sourceSortOrder by remember { mutableStateOf(SortOrder.POPULARITY) }
    var sourcePage by remember { mutableStateOf(1) }
    var sourceSearchQuery by remember { mutableStateOf("") }
    var sourceMangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isLoadingSource by remember { mutableStateOf(false) }

    // Required Filter Chips
    var filterNewChapters by remember { mutableStateOf(false) }
    var filterNsfw by remember { mutableStateOf(false) }
    var filterSelfPublished by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    // Pinned sources list
    val allParsers = remember { sourceRegistry.getAllKotatsuParserSources() }
    var pinnedParserNames by remember {
        mutableStateOf(
            setOf(
                "MANGADEX",
                "MANGAKAKALOT",
                "ASURASCANS",
                "FLAMECOMICS",
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

    // Explore popular manga
    var exploreMangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isExploreLoading by remember { mutableStateOf(false) }

    fun loadExploreContent() {
        scope.launch {
            isExploreLoading = true
            exploreMangaList = if (searchQuery.isNotBlank()) {
                sourceRegistry.search(searchQuery)
            } else {
                sourceRegistry.fetchPopular()
            }
            isExploreLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (viewMode == "EXPLORE") {
            loadExploreContent()
        }
    }

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

    val filteredExploreList = remember(exploreMangaList, filterNewChapters, filterNsfw, filterSelfPublished, selectedCategory) {
        exploreMangaList.filter { manga ->
            val matchesCategory = if (selectedCategory == "All") true else {
                val cat = selectedCategory.lowercase()
                manga.genres.any { it.lowercase().contains(cat) } ||
                    manga.title.lowercase().contains(cat) ||
                    manga.description.lowercase().contains(cat) ||
                    (cat == "manhwa" && manga.genres.any {
                        val g = it.lowercase()
                        g.contains("webtoon") || g.contains("long strip") || g.contains("full color") || g.contains("manhwa")
                    })
            }
            val matchesNsfw = if (!filterNsfw) true else {
                manga.genres.any { g ->
                    val l = g.lowercase()
                    l.contains("18+") || l.contains("mature") || l.contains("erotica") || l.contains("hentai") || l.contains("adult")
                }
            }
            val matchesSelfPublished = if (!filterSelfPublished) true else {
                manga.genres.any { g ->
                    val l = g.lowercase()
                    l.contains("webtoon") || l.contains("indie") || l.contains("self-published") || l.contains("comic")
                }
            }
            matchesCategory && matchesNsfw && matchesSelfPublished
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        when (viewMode) {
            "EXPLORE" -> {
                // High-performance search bar
                ZSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    hint = "Search 1000+ sources by manhwa, manga or author…",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )

                // 2x2 Action Cards Grid (Professional layout)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModernExploreActionButton(
                            title = "Local Storage",
                            subtitle = "Browse CBZ & offline archives",
                            icon = Icons.Rounded.Folder,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateLocal
                        )
                        ModernExploreActionButton(
                            title = "Bookmarks",
                            subtitle = "Saved favorite titles",
                            icon = Icons.Rounded.Bookmark,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateBookmarks
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModernExploreActionButton(
                            title = "Random Pick",
                            subtitle = "Discover unexpected gems",
                            icon = Icons.Rounded.Casino,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    val popular = sourceRegistry.fetchPopular()
                                    val random = popular.randomOrNull()
                                    if (random != null) onOpenManga(random)
                                }
                            }
                        )
                        ModernExploreActionButton(
                            title = "Downloads",
                            subtitle = "Active chapter queue",
                            icon = Icons.Rounded.Download,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateDownloads
                        )
                    }
                }

                // Filter Chips Row (Including requested "New chapters", "18+", "Self-published")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZChip(
                        label = "New chapters",
                        icon = Icons.Rounded.AutoAwesome,
                        selected = filterNewChapters,
                        onClick = { filterNewChapters = !filterNewChapters }
                    )
                    ZChip(
                        label = "18+",
                        icon = Icons.Rounded.Explicit,
                        selected = filterNsfw,
                        onClick = { filterNsfw = !filterNsfw }
                    )
                    ZChip(
                        label = "Self-published",
                        icon = Icons.Rounded.Edit,
                        selected = filterSelfPublished,
                        onClick = { filterSelfPublished = !filterSelfPublished }
                    )

                    listOf("All", "Manhwa", "Manga", "Manhua", "Action", "Romance", "Fantasy").forEach { cat ->
                        ZChip(
                            label = cat,
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                // Sources Header & Catalog Access
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PINNED SOURCES",
                        color = tokens.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    FilledTonalButton(
                        onClick = { viewMode = "CATALOG" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = tokens.surfaceContainerHigh,
                            contentColor = tokens.primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Sources Catalog (${allParsers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Pinned sources 72x72 favicon row
                val pinnedParsers = allParsers.filter { it.name in pinnedParserNames }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pinnedParsers.forEach { parser ->
                        ModernKotatsuSourceBadge(
                            parser = parser,
                            sourceRegistry = sourceRegistry,
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

                Text(
                    text = if (searchQuery.isNotBlank()) "SEARCH RESULTS" else "POPULAR DISCOVERIES",
                    color = tokens.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                if (isExploreLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = tokens.primary)
                    }
                } else if (filteredExploreList.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No titles match your active filters or search.", color = tokens.textMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 136.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredExploreList, key = { it.id }) { manga ->
                            ModernMangaCard(
                                manga = manga,
                                onClick = { onOpenManga(manga) }
                            )
                        }
                    }
                }
            }

            "CATALOG" -> {
                // Sources Catalog view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { viewMode = "EXPLORE" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = tokens.surfaceContainerHigh,
                            contentColor = tokens.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back", fontSize = 12.sp)
                    }

                    Spacer(Modifier.width(16.dp))

                    Text(
                        "SOURCES CATALOG (1000+ PARSERS)",
                        color = tokens.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
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
                            Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
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
                    placeholder = { Text("Search sources by name or language...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.primary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.primary,
                        unfocusedBorderColor = tokens.outlineVariant,
                    )
                )

                Spacer(Modifier.height(14.dp))

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
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = tokens.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    val iconUrl = remember(parser) { sourceRegistry.getParserSourceFaviconUrl(parser) }
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                        RemoteCoverImage(
                                            url = iconUrl,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = parser.title,
                                        color = tokens.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Language: ${parser.locale.uppercase()}",
                                        color = tokens.textMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        pinnedParserNames = if (isPinned) {
                                            pinnedParserNames - parser.name
                                        } else {
                                            pinnedParserNames + parser.name
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.Add,
                                        contentDescription = if (isPinned) "Unpin" else "Pin",
                                        tint = if (isPinned) tokens.primary else tokens.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "SOURCE_VIEW" -> {
                val src = selectedSource ?: return
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { viewMode = "EXPLORE" },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = tokens.surfaceContainerHigh,
                            contentColor = tokens.textPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sources", fontSize = 12.sp)
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            text = src.name,
                            color = tokens.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Page $sourcePage • ${src.language.uppercase()}",
                            color = tokens.primary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    var sortMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { sortMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Sort, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
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

                OutlinedTextField(
                    value = sourceSearchQuery,
                    onValueChange = {
                        sourceSearchQuery = it
                        sourcePage = 1
                    },
                    placeholder = { Text("Search on ${src.name}...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.primary)
                    },
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
                        columns = GridCells.Adaptive(minSize = 136.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(sourceMangaList, key = { it.id }) { manga ->
                            ModernMangaCard(
                                manga = manga,
                                onClick = { onOpenManga(manga) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { if (sourcePage > 1) sourcePage-- },
                            enabled = sourcePage > 1,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Previous", fontSize = 12.sp)
                        }

                        Spacer(Modifier.width(20.dp))
                        Text("Page $sourcePage", color = tokens.textPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(20.dp))

                        FilledTonalButton(
                            onClick = { sourcePage++ },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Next", fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernExploreActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Surface(
        color = if (hovered) tokens.surfaceContainerHigh else tokens.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 1.dp,
                color = if (hovered) tokens.primary else tokens.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tokens.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = tokens.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = tokens.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ModernKotatsuSourceBadge(
    parser: MangaParserSource,
    sourceRegistry: LiveSourceRegistry,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val iconUrl = remember(parser) { sourceRegistry.getParserSourceFaviconUrl(parser) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            color = if (hovered) tokens.primaryContainer else tokens.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .size(68.dp)
                .border(
                    width = if (hovered) 2.dp else 1.dp,
                    color = if (hovered) tokens.primary else tokens.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                )
                .shadow(
                    elevation = if (hovered) 6.dp else 1.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = tokens.primary
                )
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(10.dp)) {
                RemoteCoverImage(
                    url = iconUrl,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                tint = tokens.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = parser.title,
                color = tokens.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ModernMangaCard(
    manga: Manga,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tokens.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(13f / 18f)
                .border(
                    width = if (hovered) 1.5.dp else 1.dp,
                    color = if (hovered) tokens.borderGlow else tokens.borderGlass,
                    shape = RoundedCornerShape(12.dp)
                )
                .shadow(
                    elevation = if (hovered) 10.dp else 2.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = tokens.primary
                )
        ) {
            RemoteCoverImage(url = manga.coverUrl, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = manga.title,
            color = if (hovered) tokens.primary else tokens.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Backward-compatible alias for KotatsuMangaCard
@Composable
fun KotatsuMangaCard(manga: Manga, onClick: () -> Unit) {
    ModernMangaCard(manga, onClick)
}
