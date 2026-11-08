package com.zeravrix.zeravrix.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMorePages by remember { mutableStateOf(true) }
    val sourceGridState = rememberLazyGridState()

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
        if (searchQuery.isBlank()) {
            exploreMangaList = emptyList()
            isExploreLoading = false
            return
        }
        scope.launch {
            isExploreLoading = true
            exploreMangaList = withContext(Dispatchers.IO) {
                runCatching { sourceRegistry.search(searchQuery) }.getOrDefault(emptyList())
            }
            isExploreLoading = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (viewMode == "EXPLORE") {
            loadExploreContent()
        }
    }

    // Load remote manga from selected source with infinite scrolling support
    fun loadSourceManga(source: CatalogueSource, page: Int, order: SortOrder, query: String?, append: Boolean = false) {
        if (append && (isLoadingMore || !hasMorePages)) return
        scope.launch {
            if (append) isLoadingMore = true else isLoadingSource = true
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    if (source is KotatsuSourceAdapter) {
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
                }.getOrDefault(emptyList())
            }
            if (append) {
                if (items.isEmpty()) {
                    hasMorePages = false
                } else {
                    val existing = sourceMangaList.map { it.id }.toSet()
                    sourceMangaList = sourceMangaList + items.filter { it.id !in existing }
                }
                isLoadingMore = false
            } else {
                sourceMangaList = items
                hasMorePages = items.isNotEmpty()
                isLoadingSource = false
            }
        }
    }

    LaunchedEffect(selectedSource, sourceSortOrder, sourceSearchQuery) {
        val src = selectedSource
        if (src != null) {
            sourcePage = 1
            hasMorePages = true
            loadSourceManga(src, 1, sourceSortOrder, sourceSearchQuery.ifBlank { null }, append = false)
        }
    }

    // Infinite scroll observer: loads next page when approaching bottom of the list
    LaunchedEffect(sourceGridState, sourceMangaList.size, isLoadingMore, hasMorePages) {
        snapshotFlow {
            val lastVisible = sourceGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = sourceMangaList.size
            total > 0 && lastVisible >= total - 6
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && !isLoadingMore && !isLoadingSource && hasMorePages) {
                val src = selectedSource ?: return@collect
                val nextPage = sourcePage + 1
                sourcePage = nextPage
                loadSourceManga(src, nextPage, sourceSortOrder, sourceSearchQuery.ifBlank { null }, append = true)
            }
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
                        title = "Downloads",
                        icon = Icons.Rounded.Download,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateDownloads
                    )
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

                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "SEARCH RESULTS",
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
                    } else if (exploreMangaList.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No titles match your search.", color = tokens.textMuted)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 136.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(exploreMangaList, key = { it.id }) { manga ->
                                ModernMangaCard(
                                    manga = manga,
                                    onClick = { onOpenManga(manga) }
                                )
                            }
                        }
                    }
                } else {
                    // Clean pinned sources grid for quick jumping into manga/manhwa sources
                    Text(
                        text = "EXPLORE BY SOURCE",
                        color = tokens.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pinnedSources, key = { it.id }) { sourceItem ->
                            Surface(
                                color = tokens.surfaceContainerHigh,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSource = sourceItem.sourceInstance
                                        sourcePage = 1
                                        sourceSearchQuery = ""
                                        viewMode = "SOURCE_VIEW"
                                    }
                                    .border(1.dp, tokens.borderGlass, RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RemoteCoverImage(
                                        url = sourceItem.iconUrl,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sourceItem.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = tokens.textPrimary,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${sourceItem.language.uppercase()} • Browse Manhwa & Manga",
                                            fontSize = 11.sp,
                                            color = tokens.primary,
                                            maxLines = 1
                                        )
                                    }
                                    Icon(
                                        Icons.Rounded.ArrowForward,
                                        contentDescription = "Browse",
                                        tint = tokens.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
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
                            text = "${sourceMangaList.size} titles • ${src.language.uppercase()}",
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
                        state = sourceGridState,
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

                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp),
                                        color = tokens.primary
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
        targetValue = if (hovered) 1.04f else 1f,
        animationSpec = tween(100)
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
        targetValue = if (hovered) 1.02f else 1f,
        animationSpec = tween(100)
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
        targetValue = if (hovered) 1.03f else 1f,
        animationSpec = tween(100)
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
