package com.zeravrix.zeravrix.reader

import androidx.compose.ui.graphics.ImageBitmap

/** Small synchronized access-order cache for decoded reader pages. */
class ImageBitmapLru(private val maxEntries: Int) {
    private val values = object : LinkedHashMap<Int, ImageBitmap>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(index: Int): ImageBitmap? = values[index]

    @Synchronized
    fun put(index: Int, bitmap: ImageBitmap) {
        values[index] = bitmap
    }

    @Synchronized
    fun snapshot(): Map<Int, ImageBitmap> = LinkedHashMap(values)
}
