package zeravrix.source.api

class SourceManager(
    private val sources: MutableMap<String, DesktopSource> = linkedMapOf()
) {
    fun register(source: DesktopSource) { sources[source.id] = source }
    fun unregister(id: String) { sources.remove(id) }
    fun all(): List<DesktopSource> = sources.values.toList()
    fun get(id: String): DesktopSource? = sources[id]

    suspend fun search(sourceId: String, query: String, page: Int = 1): List<MangaRef> =
        requireNotNull(get(sourceId)) { "Source not installed: $sourceId" }
            .search(query, page)
}
