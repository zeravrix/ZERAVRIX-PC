package com.zeravrix.zeravrix.reader

import com.zeravrix.zeravrix.core.MangaPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns reader page work outside Compose. It keeps at most three page downloads
 * active and collapses visible/prefetch/recomposition requests for the same URL
 * into one operation.
 */
class ReaderPageLoader(
    private val pageCache: PageCache,
    maxConcurrentDownloads: Int = 3,
) {
    private val permits = Semaphore(maxConcurrentDownloads.coerceIn(1, 8))
    private val inFlight = ConcurrentHashMap<String, Deferred<java.nio.file.Path>>()

    suspend fun load(page: MangaPage): java.nio.file.Path = coroutineScope {
        val key = key(page)
        val candidate = async(Dispatchers.IO) {
            permits.withPermit { pageCache.get(page) }
        }
        val existing = inFlight.putIfAbsent(key, candidate)
        val selected = existing ?: candidate
        if (existing != null) candidate.cancel()
        try {
            selected.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            inFlight.remove(key, selected)
        }
    }

    fun cancelAll() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }

    private fun key(page: MangaPage): String {
        val raw = buildString {
            append(page.imageUrl).append('|').append(page.referer.orEmpty())
            page.headers.toSortedMap().forEach { (name, value) -> append('|').append(name).append('=').append(value) }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
