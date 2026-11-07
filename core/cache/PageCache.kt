package zeravrix.core.cache

import java.nio.file.Path

interface PageCache {
    fun cached(pageUrl: String): Path?
    suspend fun getOrDownload(pageUrl: String, referer: String? = null): Path
    suspend fun clear()
}
