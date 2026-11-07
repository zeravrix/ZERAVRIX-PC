package com.zeravrix.zeravrix.core

data class Manga(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val description: String = "",
    val sourceId: Long = 0L,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val cachedChapterCount: Int? = null,
)

data class Chapter(
    val id: String,
    val mangaId: String,
    val title: String,
    val number: Float? = null,
    val sourceId: Long = 0L,
    val volume: Int? = null,
    val scanlator: String? = null,
    val uploadDate: Long? = null,
)

data class MangaPage(
    val index: Int,
    val imageUrl: String,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isChapterEnd: Boolean = false,
    val chapterLabel: String? = null,
)

data class ReadingProgress(
    val mangaId: String,
    val chapterId: String,
    val pageIndex: Int,
    val chapterNumber: Float? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
