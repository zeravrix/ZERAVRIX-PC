package com.zeravrix.zeravrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.DesktopDownloader
import com.zeravrix.zeravrix.core.LibraryStore
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.ReadingProgress
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.components.ProgressBadge
import com.zeravrix.zeravrix.ui.components.SectionHeader
import com.zeravrix.zeravrix.ui.components.ZChip
import com.zeravrix.zeravrix.ui.components.ZSearchBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ShelfKind(val title: String) {
    HISTORY("History"),
    FAVOURITES("Favourites"),
    DOWNLOADS("Downloads"),
}

private enum class ShelfFilter(val label: String, val icon: String) {
    ON_DEVICE("On device", "▣"),
    READING("Reading", "▶"),
    COMPLETED("Completed", "✓"),
    FAVOURITES("Favourites", "♡"),
    NOT_IN_FAVOURITES("Not in favourites", "⊘"),
}

private data class ShelfEntry(
    val manga: Manga,
    val progress: ReadingProgress?,
    val percent: Int?,
    val inLibrary: Boolean,
    val onDevice: Boolean,
)

private fun filtersFor(kind: ShelfKind): List<ShelfFilter> = when (kind) {
    ShelfKind.HISTORY -> listOf(ShelfFilter.ON_DEVICE, ShelfFilter.READING, ShelfFilter.COMPLETED, ShelfFilter.FAVOURITES, ShelfFilter.NOT_IN_FAVOURITES)
    ShelfKind.FAVOURITES -> listOf(ShelfFilter.ON_DEVICE, ShelfFilter.READING, ShelfFilter.COMPLETED)
    ShelfKind.DOWNLOADS -> listOf(ShelfFilter.READING, ShelfFilter.COMPLETED, ShelfFilter.FAVOURITES, ShelfFilter.NOT_IN_FAVOURITES)
}

private fun matches(entry: ShelfEntry, filter: ShelfFilter): Boolean = when (filter) {
    ShelfFilter.ON_DEVICE -> entry.onDevice
    ShelfFilter.READING -> entry.percent != null && entry.percent < 100
    ShelfFilter.COMPLETED -> entry.percent != null && entry.percent >= 100
    ShelfFilter.FAVOURITES -> entry.inLibrary
    ShelfFilter.NOT_IN_FAVOURITES -> !entry.inLibrary
}

/** Disk work for one shelf; always call from Dispatchers.IO. */
private fun loadEntries(kind: ShelfKind, store: LibraryStore, downloader: DesktopDownloader): List<ShelfEntry> {
    val library = store.list()
    val libraryIds = library.mapTo(HashSet()) { it.id }
    val downloads = downloader.downloadedChapters()
    val downloadedIds = downloads.mapTo(HashSet()) { it.mangaId }
    val base: List<Manga> = when (kind) {
        ShelfKind.HISTORY -> store.listHistory()
        ShelfKind.FAVOURITES -> library
        ShelfKind.DOWNLOADS -> downloads.distinctBy { it.mangaId }
            .map { Manga(id = it.mangaId, title = it.mangaTitle, coverUrl = it.mangaCoverUrl, sourceId = it.sourceId) }
    }
    val ids = base.mapTo(HashSet()) { it.id }
    val progress = store.latestProgressSnapshot(ids)
    val chapters = store.cachedChapterSnapshot(ids)
    val entries = base.map { manga ->
        val p = progress[manga.id]
        val highest = chapters[manga.id].orEmpty().mapNotNull { it.number }.maxOrNull()
        val readNumber: Float? = p?.chapterNumber
        val percent = when {
            p == null -> null
            highest != null && highest > 0f && readNumber != null -> (readNumber / highest * 100f).toInt().coerceIn(0, 100)
            else -> 0
        }
        ShelfEntry(manga, p, percent, manga.id in libraryIds, manga.id in downloadedIds)
    }
    return when (kind) {
        ShelfKind.HISTORY -> entries.sortedByDescending { it.progress?.updatedAt ?: 0L }
        else -> entries.sortedBy { it.manga.title.lowercase() }
    }
}

private fun groupLabel(updatedAt: Long): String {
    if (updatedAt <= 0L) return "Recently viewed"
    val zone = ZoneId.systemDefault()
    val ageMs = System.currentTimeMillis() - updatedAt
    if (ageMs < 10 * 60 * 1000L) return "Just now"
    val day = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        day == today -> "Today"
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> "This week"
        else -> "Older"
    }
}

/** History / Favourites / Downloads in Kotatsu's landscape layout: search bar, filter chips, (grouped) cover grid. */
@Composable
fun MangaShelfScreen(
    kind: ShelfKind,
    libraryStore: LibraryStore,
    downloader: DesktopDownloader,
    onOpenManga: (Manga) -> Unit,
    onExploreClick: () -> Unit,
    refreshKey: Any? = null,
) {
    val tokens = LocalGlassTokens.current
    var query by remember(kind) { mutableStateOf("") }
    var activeFilter by remember(kind) { mutableStateOf<ShelfFilter?>(null) }

    val rawEntries by produceState(emptyList<ShelfEntry>(), kind, refreshKey) {
        value = withContext(Dispatchers.IO) { loadEntries(kind, libraryStore, downloader) }
    }

    val visibleEntries = remember(rawEntries, query, activeFilter) {
        rawEntries.asSequence()
            .filter { activeFilter == null || matches(it, activeFilter!!) }
            .filter { query.isBlank() || it.manga.title.contains(query, ignoreCase = true) }
            .toList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 14.dp)) {
        ZSearchBar(
            query = query,
            onQueryChange = { query = it },
            hint = "Search in ${kind.title.lowercase()}…",
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filtersFor(kind).forEach { filter ->
                ZChip(
                    label = filter.label,
                    icon = filter.icon,
                    selected = activeFilter == filter,
                    onClick = { activeFilter = if (activeFilter == filter) null else filter },
                )
            }
        }

        if (visibleEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            query.isNotBlank() -> "No matching titles"
                            kind == ShelfKind.DOWNLOADS -> "No downloaded chapters"
                            kind == ShelfKind.HISTORY -> "Reading history is empty"
                            else -> "No favourites yet"
                        },
                        color = tokens.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            query.isNotBlank() -> "Try a different search word"
                            kind == ShelfKind.DOWNLOADS -> "Download a chapter from any title's details screen to read offline"
                            kind == ShelfKind.HISTORY -> "Titles you open or read will appear here"
                            else -> "Browse sources in Explore to find manga you like"
                        },
                        color = tokens.textMuted,
                        fontSize = 14.sp,
                    )
                }
            }
            return
        }

        val grouped: List<Pair<String?, List<ShelfEntry>>> = remember(visibleEntries, kind) {
            if (kind != ShelfKind.HISTORY) {
                listOf(null to visibleEntries)
            } else {
                val groups = LinkedHashMap<String, MutableList<ShelfEntry>>()
                visibleEntries.forEach { entry ->
                    val label = groupLabel(entry.progress?.updatedAt ?: 0L)
                    groups.getOrPut(label) { ArrayList() } += entry
                }
                groups.map { it.key to it.value }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            grouped.forEach { (header, items) ->
                if (header != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(header) }
                }
                items(items, key = { it.manga.id }) { entry ->
                    ShelfCard(entry = entry, onClick = { onOpenManga(entry.manga) })
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(entry: ShelfEntry, onClick: () -> Unit) {
    val tokens = LocalGlassTokens.current
    val manga = entry.manga
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(13f / 18f)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.surfaceGlassRaised),
        ) {
            RemoteCoverImage(url = manga.coverUrl, modifier = Modifier.fillMaxSize())
            if (entry.percent != null && entry.percent > 0) {
                ProgressBadge(
                    percent = entry.percent,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            manga.title,
            color = tokens.textPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
