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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.source.CatalogueSource
import com.zeravrix.zeravrix.source.Source
import com.zeravrix.zeravrix.sources.KotatsuSourceAdapter
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.components.ZChip
import com.zeravrix.zeravrix.ui.components.ZSearchBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.util.concurrent.ConcurrentHashMap

// In-memory cache for fast Explore cover loading and zero repeated network requests
private val exploreCoverCache = ConcurrentHashMap<String, String>()

data class ExploreSourceItem(
    val id: String,
    val name: String,
    val language: String,
    val iconUrl: String,
    val sourceInstance: CatalogueSource,
)

/**
 * Redesigned Explore Page:
 * - Suggested Manga/Manhwa Recommendation Slider with 15s auto-rotation and manual arrow controls
 * - Pinned Sources with right-click unpinning
 * - High-speed sources catalog supporting Kotatsu, Arabic built-in, and imported sources
 * - Robust Manhwa & Manga classification
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

    // View mode: "EXPLORE" (default), "CATALOG" (all sources), "SOURCE_VIEW" (manga from one source)
    var viewMode by remember { mutableStateOf("EXPLORE") }
    var selectedSource by remember { mutableStateOf<CatalogueSource?>(null) }
    var sourceSortOrder by remember { mutableStateOf(SortOrder.POPULARITY) }
    var sourcePage by remember { mutableStateOf(1) }
    var sourceSearchQuery by remember { mutableStateOf("") }
    var sourceMangaList by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var isLoadingSource by remember { mutableStateOf(false) }

    // Filter Chips
    var filterNewChapters by remember { mutableStateOf(false) }
    var filterNsfw by remember { mutableStateOf(false) }
    var filterSelfPublished by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    // Build the complete list of available sources (Kotatsu + Arabic Built-ins + Imported)
    val allSourcesList = remember(sourceRegistry) {
        val list = mutableListOf<ExploreSourceItem>()
        // 1. Built-in and imported catalogue sources
        sourceRegistry.catalogueSources().forEach { src ->
            if (src !is KotatsuSourceAdapter) {
                list += ExploreSourceItem(
                    id = "builtin_${src.id}",
                    name = src.name,
                    language = src.language,
                    iconUrl = sourceRegistry.getSourceIconUrl(src),
                    sourceInstance = src
                )
            }
        }
        // 2. Kotatsu parser sources
        sourceRegistry.getAllKotatsuParserSources().forEach { parser ->
            val adapter = sourceRegistry.getOrCreateAdapter(parser)
            if (adapter != null) {
                list += ExploreSourceItem(
                    id = parser.name,
                    name = parser.title,
                    language = parser.locale,
                    iconUrl = sourceRegistry.getParserSourceFaviconUrl(parser),
                    sourceInstance = adapter
                )
            }
        }
        list
    }

    // Pinned sources (stores source ID or parser name)
    var pinnedSourceIds by remember {
        mutableStateOf(
            setOf(
                "WEBTOONS_EN",
                "MANGADEX",
                "TOONILY",
                "MANGATOWN",
                "builtin_${0x4D414E4741535741L}", // MangaSwat (Arabic)
                "builtin_${0x5445414D584CL}",       // Team X (Arabic)
                "builtin_${0x4D414E47414C454BL}"   // Mangalek (Arabic)
            )
        )
    }

    // Catalog search & language filter
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

    // Load remote manga from selected source
    fun loadSourceManga(source: CatalogueSource, page: Int, order: SortOrder, query: String?) {
        scope.launch {
            isLoadingSource = true
            val items = if (source is KotatsuSourceAdapter) {
                source.fetchList(page = page, order = order, query = query).items
            } else {
                if (!query.isNullOrBlank()) {
                    source.search(page, query).items
                } else if (order == SortOrder.UPDATED) {
                    source.latest(page).items
                } else {
                    source.popular(page).items
                }
            }
            sourceMangaList = items
            isLoadingSource = false
        }
    }

    LaunchedEffect(selectedSource, sourcePage, sourceSortOrder, sourceSearchQuery) {
        val src = selectedSource
        if (src != null) {
            loadSourceManga(src, sourcePage, sourceSortOrder, sourceSearchQuery.ifBlank { null })
        }
    }

    // Robust Manhwa & Category Filter
    val filteredExploreList = remember(exploreMangaList, filterNewChapters, filterNsfw, filterSelfPublished, selectedCategory) {
        exploreMangaList.filter { manga ->
            val matchesCategory = if (selectedCategory == "All") true else {
                val cat = selectedCategory.lowercase()
                val isManhwaTarget = cat == "manhwa" || cat == "webtoon"
                val manhwaKeywords = listOf("manhwa", "webtoon", "manhua", "long strip", "full color", "scroll", "مانهوا", "مانهو", "ويبتون", "ويب تون", "كوري", "صيني")

                if (isManhwaTarget) {
                    manga.genres.any { g ->
                        val l = g.lowercase()
                        manhwaKeywords.any { l.contains(it) }
                    } ||
                    manhwaKeywords.any { manga.title.lowercase().contains(it) } ||
                    manga.url?.lowercase()?.let { u -> u.contains("manhwa") || u.contains("webtoon") || u.contains("comics") } == true ||
                    (manga.sourceId >= 10000L && runCatching {
                        val ordinal = (manga.sourceId - 10000L).toInt()
                        val parser = MangaParserSource.entries.getOrNull(ordinal)
                        parser?.name?.contains("ASURA") == true || parser?.name?.contains("FLAME") == true ||
                        parser?.name?.contains("TOON") == true || parser?.name?.contains("WEBTOON") == true ||
                        parser?.name?.contains("MANHWA") == true
                    }.getOrDefault(false))
                } else {
                    manga.genres.any { it.lowercase().contains(cat) } ||
                    manga.title.lowercase().contains(cat) ||
                    manga.description.lowercase().contains(cat)
                }
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
                // Compact Search Bar
                ZSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    hint = "Search manga, manhwa, authors...",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    onSubmit = { loadExploreContent() }
                )

                // Recommendation Slider (Auto-rotates every 15s with manual arrows)
                val sliderItems = remember(exploreMangaList) {
                    exploreMangaList.take(8)
                }
                if (sliderItems.isNotEmpty() && searchQuery.isBlank()) {
                    RecommendationSlider(
                        items = sliderItems,
                        onOpenManga = onOpenManga,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    )
                }

                // Action Pills Row (Clean, single-line, icon-first)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModernExploreActionButton(
                        title = "Local Files",
                        icon = Icons.Rounded.FolderOpen,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateLocal
                    )
                    ModernExploreActionButton(
                        title = "Bookmarks",
                        icon = Icons.Rounded.Bookmark,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateBookmarks
                    )
                    ModernExploreActionButton(
                        title = "Random",
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
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateDownloads
                    )
                }

                // Filter Chips Row
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

                // Pinned Sources Header & Catalog Button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PINNED SOURCES",
                        color = tokens.textPrimary,
                        fontSize = 12.sp,
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Sources Catalog",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Pinned Sources Row (Displays ONLY sources explicitly pinned by the user)
                val pinnedSources = remember(allSourcesList, pinnedSourceIds) {
                    allSourcesList.filter { it.id in pinnedSourceIds }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pinnedSources.forEach { item ->
                        PinnedSourceBadge(
                            sourceItem = item,
                            onClick = {
                                selectedSource = item.sourceInstance
                                sourcePage = 1
                                sourceSearchQuery = ""
                                viewMode = "SOURCE_VIEW"
                            },
                            onUnpin = {
                                pinnedSourceIds = pinnedSourceIds - item.id
                            }
                        )
                    }
                }

                Text(
                    text = if (searchQuery.isNotBlank()) "SEARCH RESULTS" else "POPULAR DISCOVERIES",
                    color = tokens.textPrimary,
                    fontSize = 12.sp,
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
                // Full Sources Catalog view (Kotatsu + Arabic Built-ins + Imported)
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
                        "SOURCES CATALOG",
                        color = tokens.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.weight(1f))

                    // Language Selector
                    val languages = listOf("ALL", "ar", "en", "ja", "ko", "zh", "es", "fr")
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

                // Compact Search sources input
                OutlinedTextField(
                    value = catalogSearch,
                    onValueChange = { catalogSearch = it },
                    placeholder = { Text("Search sources by name or language...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.primary,
                        unfocusedBorderColor = tokens.outlineVariant,
                    )
                )

                Spacer(Modifier.height(14.dp))

                val filteredSources = remember(allSourcesList, catalogSearch, catalogLanguage) {
                    allSourcesList.filter { item ->
                        val matchName = catalogSearch.isBlank() || item.name.contains(catalogSearch, ignoreCase = true)
                        val matchLang = catalogLanguage == "ALL" || item.language.equals(catalogLanguage, ignoreCase = true)
                        matchName && matchLang
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSources, key = { it.id }) { item ->
                        val isPinned = item.id in pinnedSourceIds
                        Surface(
                            color = tokens.surfaceContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, tokens.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedSource = item.sourceInstance
                                    sourcePage = 1
                                    sourceSearchQuery = ""
                                    viewMode = "SOURCE_VIEW"
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
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                        RemoteCoverImage(
                                            url = item.iconUrl,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = tokens.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Language: ${item.language.uppercase()}",
                                        color = tokens.textMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        pinnedSourceIds = if (isPinned) {
                                            pinnedSourceIds - item.id
                                        } else {
                                            pinnedSourceIds + item.id
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
                    placeholder = { Text("Search on ${src.name}...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
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
                        Text("No titles found for this query/source.", color = tokens.textMuted)
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
                        Text("Page $sourcePage", color = tokens.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

/**
 * Suggested Manga / Manhwa recommendation slider:
 * - Changes title automatically every 15 seconds
 * - Left/right arrow controls for manual navigation without interfering with timer
 */
@Composable
private fun RecommendationSlider(
    items: List<Manga>,
    onOpenManga: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val tokens = LocalGlassTokens.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var resetTick by remember { mutableLongStateOf(0L) }

    // Auto-advance every 15 seconds
    LaunchedEffect(resetTick, items.size) {
        delay(15_000L)
        currentIndex = (currentIndex + 1) % items.size
        resetTick++
    }

    val currentManga = items[currentIndex.coerceIn(0, items.lastIndex)]

    Surface(
        color = tokens.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, tokens.borderGlass, RoundedCornerShape(16.dp))
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp), spotColor = tokens.primary)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenManga(currentManga) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle backdrop cover with vignette gradient
            RemoteCoverImage(
                url = currentManga.coverUrl,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.18f }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                tokens.surfaceContainerHigh,
                                tokens.surfaceContainerHigh.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Content Row
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Thumbnail
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = tokens.surfaceContainer,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(13f / 18f)
                        .border(1.dp, tokens.borderGlass, RoundedCornerShape(10.dp))
                        .shadow(4.dp, RoundedCornerShape(10.dp))
                ) {
                    RemoteCoverImage(
                        url = currentManga.coverUrl,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(Modifier.width(18.dp))

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = tokens.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "FEATURED",
                                color = tokens.onPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (currentManga.genres.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentManga.genres.take(3).joinToString(" • "),
                                color = tokens.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = currentManga.title,
                        color = tokens.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = currentManga.description.ifBlank { "Discover and read this trending title." },
                        color = tokens.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(10.dp))

                    FilledTonalButton(
                        onClick = { onOpenManga(currentManga) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Read Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Left & Right Arrow Controls for manual navigation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = tokens.surfaceContainer.copy(alpha = 0.8f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .border(1.dp, tokens.borderGlass, CircleShape)
                            .clickable {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else items.lastIndex
                                resetTick++
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous", tint = tokens.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    Surface(
                        color = tokens.surfaceContainer.copy(alpha = 0.8f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(34.dp)
                            .border(1.dp, tokens.borderGlass, CircleShape)
                            .clickable {
                                currentIndex = (currentIndex + 1) % items.size
                                resetTick++
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", tint = tokens.textPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Dots indicator at bottom center
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(width = if (index == currentIndex) 14.dp else 4.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index == currentIndex) tokens.primary else tokens.textMuted.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PinnedSourceBadge(
    sourceItem: ExploreSourceItem,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var showContextMenu by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.05f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(82.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(14.dp))
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        showContextMenu = true
                    }
                }
                .clickable(onClick = onClick)
                .padding(4.dp)
        ) {
            Surface(
                color = if (hovered) tokens.primaryContainer else tokens.surfaceContainerHigh,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .size(64.dp)
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
                        url = sourceItem.iconUrl,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = sourceItem.name,
                color = tokens.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right-Click Context Menu to Unpin from Explore
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .background(tokens.surfaceContainerHigh)
                .border(1.dp, tokens.borderGlass, RoundedCornerShape(8.dp))
        ) {
            DropdownMenuItem(
                text = { Text("Remove from Explore", color = tokens.textPrimary, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = tokens.primary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    showContextMenu = false
                    onUnpin()
                }
            )
        }
    }
}

@Composable
private fun ModernExploreActionButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    Surface(
        color = if (hovered) tokens.surfaceContainerHigh else tokens.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = 1.dp,
                color = if (hovered) tokens.primary else tokens.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interaction)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (hovered) tokens.primary else tokens.textPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = if (hovered) tokens.primary else tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
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
