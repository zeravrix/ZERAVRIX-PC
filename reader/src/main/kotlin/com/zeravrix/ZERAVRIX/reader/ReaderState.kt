package com.zeravrix.zeravrix.reader

enum class PageLayout { SINGLE, DOUBLE, AUTO, VERTICAL }
enum class ReadingDirection { LTR, RTL }

data class ReaderState(
    val pageIndex: Int = 0,
    val zoom: Float = 1f,
    val layout: PageLayout = PageLayout.AUTO,
    val direction: ReadingDirection = ReadingDirection.LTR,
    val saturation: Float = 1f,
)
