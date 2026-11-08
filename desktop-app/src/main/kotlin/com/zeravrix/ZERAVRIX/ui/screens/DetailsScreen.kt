package com.zeravrix.zeravrix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.DesktopDownloader
import com.zeravrix.zeravrix.core.LibraryStore
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.ReadingProgress
import com.zeravrix.zeravrix.sources.LiveSourceRegistry
import com.zeravrix.zeravrix.theme.LocalGlassTokens
import com.zeravrix.zeravrix.ui.components.ZChip
import com.zeravrix.zeravrix.ui.components.ZSplitButton
import com.zeravrix.zeravrix.ui.components.ZTwoPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface ChapterRow {
    data class Header(val text: String, val key: String) : ChapterRow
    data class Item(val chapter: Chapter, val state: ReadState) : ChapterRow
}

private enum class ReadState { READ, CURRENT, UNREAD }

private fun oldestFirst(list: List<Chapter>): List<Chapter> = list.distinctBy { it.id }.sortedBy { it.number ?: Float.MAX_VALUE }

private fun numberText(n: Float?): String = n?.toString()?.removeSuffix(".0") ?: "?"

/**
 * Kotatsu landscape title screen: left pane (cover, favourite, info card, description) and right pane
 * (Continue split button, sort toggle, chapter list with download management and volume headers).
 */
@Composable
fun DetailsScreen(
    manga: Manga,
    sourceRegistry: LiveSourceRegistry,
    libraryStore: LibraryStore,
    downloader: DesktopDownloader,
    onBack: () -> Unit,
    onRead: (chapter: Chapter, startPage: Int) -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val scope = rememberCoroutineScope()
    var chapters by remember(manga.id) { mutableStateOf(emptyList<Chapter>()) }
    var loading by remember(manga.id) { mutableStateOf(true) }
    var inLibrary by remember(manga.id) { mutableStateOf(false) }
    var progress by remember(manga.id) { mutableStateOf<ReadingProgress?>(null) }
    var newestFirst by remember { mutableStateOf(false) }
    var descriptionExpanded by remember(manga.id) { mutableStateOf(false) }

    // Download state
    var downloadedChapterIds by remember(manga.id) {
        mutableStateOf<Set<String>>(downloader.downloadedChapters(manga.id).map { it.chapterId }.toSet())
    }
    var downloadingChapterProgress by remember { mutableStateOf(mapOf<String, Int>()) }

    LaunchedEffect(manga.id) {
        withContext(Dispatchers.IO) {
            libraryStore.recordHistory(manga)
            val (saved, lastProgress, cached) = Triple(
                libraryStore.contains(manga.id),
                libraryStore.latestProgress(manga.id),
                libraryStore.cachedChapters(manga.id)
            )
            inLibrary = saved
            progress = lastProgress
            if (cached.isNotEmpty()) chapters = oldestFirst(cached)
            downloadedChapterIds = downloader.downloadedChapters(manga.id).map { it.chapterId }.toSet()
        }
        val fresh = sourceRegistry.fetchChapters(manga)
        if (fresh.isNotEmpty()) {
            chapters = oldestFirst(fresh)
            withContext(Dispatchers.IO) {
                if (libraryStore.contains(manga.id)) libraryStore.saveChapterCache(manga.id, fresh)
            }
        }
        loading = false
    }

    fun startDownload(chapter: Chapter) {
        if (chapter.id in downloadedChapterIds || chapter.id in downloadingChapterProgress) return
        scope.launch {
            downloadingChapterProgress = downloadingChapterProgress + (chapter.id to 0)
            runCatching {
                val pages = withContext(Dispatchers.IO) { sourceRegistry.fetchPages(chapter) }
                if (pages.isNotEmpty()) {
                    downloader.downloadChapter(manga, chapter, pages) { done, total ->
                        val p = (done * 100 / total).coerceIn(0, 100)
                        downloadingChapterProgress = downloadingChapterProgress + (chapter.id to p)
                    }
                    downloadedChapterIds = downloadedChapterIds + chapter.id
                }
            }
            downloadingChapterProgress = downloadingChapterProgress - chapter.id
        }
    }

    val currentId = progress?.chapterId
    val currentIndex = chapters.indexOfFirst { it.id == currentId }
    val percent = if (currentIndex >= 0 && chapters.isNotEmpty()) (currentIndex + 1) * 100 / chapters.size else 0
    val rows = remember(chapters, newestFirst, currentIndex) {
        val ordered = if (newestFirst) chapters.reversed() else chapters
        val out = ArrayList<ChapterRow>(ordered.size + 8)
        val positions = HashMap<String, Int>(chapters.size * 2).also { map -> chapters.forEachIndexed { i, c -> map[c.id] = i } }
        var lastVolume: Int? = null
        ordered.forEach { chapter ->
            val volume = chapter.volume
            if (volume != null && volume != lastVolume) out += ChapterRow.Header("Volume $volume", "h:${out.size}")
            lastVolume = volume
            val index = positions[chapter.id] ?: -1
            val state = when {
                currentIndex < 0 -> ReadState.UNREAD
                index < currentIndex -> ReadState.READ
                index == currentIndex -> ReadState.CURRENT
                else -> ReadState.UNREAD
            }
            out += ChapterRow.Item(chapter, state)
        }
        out
    }

    fun startPageFor(chapter: Chapter): Int = if (chapter.id == currentId) progress?.pageIndex ?: 0 else 0

    ZTwoPane(
        left = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "←",
                        color = tokens.textPrimary,
                        fontSize = 22.sp,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(0.34f).aspectRatio(13f / 18f).clip(RoundedCornerShape(14.dp)).background(tokens.surfaceGlassRaised)) {
                        RemoteCoverImage(url = manga.coverUrl, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(0.66f)) {
                        Text(manga.title, color = tokens.textPrimary, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        val subtitle = manga.author ?: manga.genres.firstOrNull()
                        if (subtitle != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(subtitle, color = tokens.textMuted, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(14.dp))
                        ZChip(
                            label = if (inLibrary) "In favourites" else "Favourite this",
                            icon = if (inLibrary) "♥" else "♡",
                            selected = inLibrary,
                            onClick = {
                                if (inLibrary) libraryStore.remove(manga.id) else libraryStore.add(manga)
                                inLibrary = !inLibrary
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                InfoCard {
                    InfoRow("Source", sourceRegistry.get(manga.sourceId)?.name ?: "—")
                    manga.author?.let { InfoRow("Author", it) }
                    InfoRow("State", manga.status ?: "—")
                    InfoRow(
                        "Chapters",
                        when {
                            chapters.isEmpty() -> if (loading) "Loading…" else "—"
                            currentIndex >= 0 -> "Chapter ${numberText(chapters[currentIndex].number)} of ${chapters.size}"
                            else -> "${chapters.size} chapters"
                        },
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Progress", color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(96.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(tokens.borderGlass)) {
                            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().background(tokens.primary))
                        }
                        Text("$percent%", color = tokens.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Description", color = tokens.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(
                        if (descriptionExpanded) "Less" else "More",
                        color = tokens.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { descriptionExpanded = !descriptionExpanded }.padding(8.dp),
                    )
                }
                Text(
                    manga.description.ifBlank { "No synopsis available." },
                    color = tokens.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                if (manga.genres.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(manga.genres.joinToString("  •  "), color = tokens.textMuted, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        },
        right = {
            Surface(
                color = tokens.surfaceGlassRaised,
                shape = RoundedCornerShape(topStart = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val target = chapters.firstOrNull { it.id == currentId } ?: chapters.firstOrNull()
                        if (target != null) {
                            ZSplitButton(
                                label = if (progress != null && currentIndex >= 0) "Continue" else "Start reading",
                                onClick = { onRead(target, startPageFor(target)) },
                            ) { dismiss ->
                                DropdownMenuItem(text = { Text("Continue") }, onClick = { dismiss(); onRead(target, startPageFor(target)) })
                                DropdownMenuItem(text = { Text("Start from the first chapter") }, onClick = { dismiss(); onRead(chapters.first(), 0) })
                                DropdownMenuItem(text = { Text("Jump to the last chapter") }, onClick = { dismiss(); onRead(chapters.last(), 0) })
                                DropdownMenuItem(
                                    text = { Text("Download next 5 unread (CBZ)") },
                                    onClick = {
                                        dismiss()
                                        val unread = chapters.filter { it.id !in downloadedChapterIds }.take(5)
                                        unread.forEach { startDownload(it) }
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${chapters.size} chapters", color = tokens.textMuted, fontSize = 13.sp)
                        Spacer(Modifier.width(12.dp))
                        ZChip(
                            label = if (newestFirst) "Newest first" else "Oldest first",
                            icon = if (newestFirst) "↓" else "↑",
                            selected = false,
                            onClick = { newestFirst = !newestFirst },
                        )
                    }
                    when {
                        chapters.isEmpty() && loading -> Text("Loading chapters…", color = tokens.textMuted, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
                        chapters.isEmpty() -> Text("No chapters found", color = tokens.textMuted, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
                        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                            items(rows, key = { row ->
                                when (row) {
                                    is ChapterRow.Header -> row.key
                                    is ChapterRow.Item -> "c:${row.chapter.id}"
                                }
                            }) { row ->
                                when (row) {
                                    is ChapterRow.Header -> Text(
                                        row.text,
                                        color = tokens.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                    is ChapterRow.Item -> {
                                        val isDownloaded = row.chapter.id in downloadedChapterIds
                                        val dlProgress = downloadingChapterProgress[row.chapter.id]
                                        ChapterListRow(
                                            row = row,
                                            isDownloaded = isDownloaded,
                                            downloadProgress = dlProgress,
                                            onDownloadClick = { startDownload(row.chapter) },
                                            onClick = { onRead(row.chapter, startPageFor(row.chapter)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    val tokens = LocalGlassTokens.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(tokens.surfaceGlassRaised).border(1.dp, tokens.borderGlass, shape).padding(horizontal = 16.dp, vertical = 8.dp),
    ) { content() }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val tokens = LocalGlassTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = tokens.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(96.dp))
        Text(value, color = tokens.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChapterListRow(
    row: ChapterRow.Item,
    isDownloaded: Boolean,
    downloadProgress: Int?,
    onDownloadClick: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = LocalGlassTokens.current
    val chapter = row.chapter
    val titleColor = when (row.state) {
        ReadState.READ -> tokens.textMuted
        else -> tokens.textPrimary
    }
    val subtitle = buildList {
        add("#${numberText(chapter.number)}")
        chapter.uploadDate?.takeIf { it > 0 }?.let { add(SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(it))) }
        chapter.scanlator?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" • ")

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp)) {
            if (row.state == ReadState.CURRENT) Text("▶", color = tokens.primary, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title,
                color = titleColor,
                fontSize = 17.sp,
                fontWeight = if (row.state == ReadState.CURRENT) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = tokens.textMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.width(8.dp))

        // Download Action Button
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDownloadClick)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                downloadProgress != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = tokens.primary)
                        Spacer(Modifier.width(4.dp))
                        Text("$downloadProgress%", fontSize = 10.sp, color = tokens.primary, fontWeight = FontWeight.Bold)
                    }
                }
                isDownloaded -> {
                    Text("✓", color = tokens.primary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
                else -> {
                    Text("⬇", color = tokens.textMuted, fontSize = 16.sp)
                }
            }
        }
    }
}
