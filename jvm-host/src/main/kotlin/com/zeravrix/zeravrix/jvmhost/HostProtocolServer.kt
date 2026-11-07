package com.zeravrix.zeravrix.jvmhost

import com.zeravrix.zeravrix.core.Chapter
import com.zeravrix.zeravrix.core.Manga
import com.zeravrix.zeravrix.core.MangaPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

const val HOST_PROTOCOL_VERSION = 1

/**
 * Transport-neutral server for the native-facing protocol. Windows can carry
 * these frames over named pipes; tests and development can use redirected
 * stdin/stdout without changing request semantics.
 */
class HostProtocolServer(private val host: JvmExtensionHost) {
    private val outputLock = Any()

    suspend fun serve(input: InputStream, output: OutputStream) {
        val reader = input.bufferedReader(Charsets.UTF_8)
        val writer = output.bufferedWriter(Charsets.UTF_8)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val jobs = mutableMapOf<String, Job>()
        try {
            withContext(Dispatchers.IO) {
                readFrames(reader) { line ->
                    val request = runCatching { ProtocolJson.map(ProtocolJson.parse(line)) }.getOrElse { failure ->
                        writeResponse(writer, "", error = HostCallResult.Failure("INVALID_JSON", failure.message ?: "Invalid JSON"))
                        return@readFrames
                    }
                    val requestId = ProtocolJson.string(request, "requestId").orEmpty()
                    val operation = ProtocolJson.string(request, "op").orEmpty()
                    if (ProtocolJson.int(request, "protocol") != HOST_PROTOCOL_VERSION) {
                        writeResponse(writer, requestId, error = HostCallResult.Failure("PROTOCOL_MISMATCH", "Unsupported or missing protocol version"))
                        return@readFrames
                    }
                    if (requestId.isBlank()) {
                        writeResponse(writer, requestId, error = HostCallResult.Failure("INVALID_REQUEST", "requestId is required"))
                        return@readFrames
                    }
                    if (operation == "cancel") {
                        val target = ProtocolJson.string(request, "targetRequestId").orEmpty()
                        val cancelled = synchronized(jobs) { jobs.remove(target)?.cancel(); target.isNotBlank() }
                        writeResponse(writer, requestId, result = mapOf("cancelled" to cancelled, "targetRequestId" to target))
                        return@readFrames
                    }
                    val job = scope.launch {
                        try {
                            val response = dispatch(request)
                            writeResponse(writer, requestId, result = response)
                        } catch (cancelled: CancellationException) {
                            writeResponse(writer, requestId, error = HostCallResult.Failure("CANCELLED", "Request was cancelled", retryable = true))
                        } catch (protocolFailure: HostProtocolFailure) {
                            writeResponse(writer, requestId, error = protocolFailure.failure)
                        } catch (failure: Throwable) {
                            writeResponse(writer, requestId, error = HostCallResult.Failure("HOST_ERROR", failure.message ?: "Host request failed", retryable = false))
                        } finally {
                            synchronized(jobs) { jobs.remove(requestId) }
                        }
                    }
                    synchronized(jobs) { if (requestId.isNotBlank()) jobs[requestId] = job }
                }
                synchronized(jobs) { jobs.values.toList() }.joinAll()
            }
        } finally {
            synchronized(jobs) { jobs.values.forEach(Job::cancel); jobs.clear() }
            scope.cancel()
        }
    }

    private suspend fun dispatch(request: Map<String, Any?>): Any {
        return when (ProtocolJson.string(request, "op")) {
            "hello" -> mapOf(
                "protocol" to HOST_PROTOCOL_VERSION,
                "host" to "ZERAVRIX-JVM-HOST",
                "capabilities" to listOf("list_sources", "install_jar", "remove_extension", "enable_source", "disable_source", "popular", "latest", "search", "details", "chapters", "pages", "cancel"),
            )
            "list_sources" -> mapOf("sources" to host.listSources().map(::sourceMap))
            "load_extensions" -> mapOf("sources" to host.loadInstalledExtensions().map(::sourceMap))
            "install_jar" -> when (val result = host.installJar(Path.of(requiredString(request, "path")))) {
                is HostCallResult.Success -> mapOf("sources" to result.value.map(::sourceMap))
                is HostCallResult.Failure -> throw HostProtocolFailure(result)
            }
            "remove_extension" -> when (val result = host.removeExtension(requiredLong(request, "sourceId"))) {
                is HostCallResult.Success -> mapOf("removed" to true)
                is HostCallResult.Failure -> throw HostProtocolFailure(result)
            }
            "enable_source", "disable_source" -> when (val result = host.setEnabled(requiredLong(request, "sourceId"), ProtocolJson.string(request, "op") == "enable_source")) {
                is HostCallResult.Success -> mapOf("sourceId" to requiredLong(request, "sourceId"), "enabled" to (ProtocolJson.string(request, "op") == "enable_source"))
                is HostCallResult.Failure -> throw HostProtocolFailure(result)
            }
            "popular", "latest", "search", "details", "chapters", "pages" -> operationResult(
                host.execute(parseOperation(request)),
            )
            else -> throw HostProtocolFailure(HostCallResult.Failure("UNKNOWN_OPERATION", "Unsupported operation: ${ProtocolJson.string(request, "op")}"))
        }
    }

    private suspend fun operationResult(result: HostCallResult<HostOperationResult>): Any = when (result) {
        is HostCallResult.Success -> when (val value = result.value) {
            is HostOperationResult.MangaPage -> mapOf(
                "kind" to "manga_page",
                "items" to value.result.items.map(::mangaMap),
                "hasNextPage" to value.result.hasNextPage,
            )
            is HostOperationResult.MangaDetails -> mapOf("kind" to "manga_details", "manga" to mangaMap(value.manga))
            is HostOperationResult.ChapterList -> mapOf("kind" to "chapter_list", "chapters" to value.chapters.map(::chapterMap))
            is HostOperationResult.PageList -> mapOf("kind" to "page_list", "pages" to value.pages.map(::pageMap))
        }
        is HostCallResult.Failure -> throw HostProtocolFailure(result)
    }

    private fun parseOperation(request: Map<String, Any?>): HostOperation {
        val sourceId = requiredLong(request, "sourceId")
        val page = ProtocolJson.int(request, "page") ?: 1
        return when (ProtocolJson.string(request, "op")) {
            "popular" -> HostOperation.Popular(sourceId, page)
            "latest" -> HostOperation.Latest(sourceId, page)
            "search" -> HostOperation.Search(sourceId, page, requiredString(request, "query"))
            "details" -> HostOperation.Details(sourceId, mangaFromMap(ProtocolJson.map(request["manga"])))
            "chapters" -> HostOperation.Chapters(sourceId, mangaFromMap(ProtocolJson.map(request["manga"])))
            "pages" -> HostOperation.Pages(sourceId, chapterFromMap(ProtocolJson.map(request["chapter"])))
            else -> error("Unsupported source operation")
        }
    }

    private fun writeResponse(writer: BufferedWriter, requestId: String, result: Any? = null, error: HostCallResult.Failure? = null) {
        val envelope = linkedMapOf<String, Any?>(
            "protocol" to HOST_PROTOCOL_VERSION,
            "requestId" to requestId,
            "ok" to (error == null),
        )
        if (error == null) envelope["result"] = result else envelope["error"] = mapOf("code" to error.code, "message" to error.message, "retryable" to error.retryable)
        synchronized(outputLock) {
            writer.write(ProtocolJson.stringify(envelope))
            writer.newLine()
            writer.flush()
        }
    }

    private fun readFrames(reader: BufferedReader, handler: (String) -> Unit) {
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isNotBlank()) handler(line)
        }
    }

    private fun sourceMap(source: HostSourceInfo) = mapOf(
        "id" to source.id,
        "name" to source.name,
        "language" to source.language,
        "baseUrl" to source.baseUrl,
        "popular" to source.popular,
        "latest" to source.latest,
        "search" to source.search,
        "filters" to source.filters,
        "enabled" to source.enabled,
    )

    private fun mangaMap(manga: Manga) = linkedMapOf<String, Any?>(
        "id" to manga.id,
        "title" to manga.title,
        "coverUrl" to manga.coverUrl,
        "description" to manga.description,
        "sourceId" to manga.sourceId,
        "author" to manga.author,
        "artist" to manga.artist,
        "genres" to manga.genres,
        "status" to manga.status,
        "cachedChapterCount" to manga.cachedChapterCount,
    )

    private fun chapterMap(chapter: Chapter) = linkedMapOf<String, Any?>(
        "id" to chapter.id,
        "mangaId" to chapter.mangaId,
        "title" to chapter.title,
        "number" to chapter.number,
        "sourceId" to chapter.sourceId,
        "volume" to chapter.volume,
        "scanlator" to chapter.scanlator,
        "uploadDate" to chapter.uploadDate,
    )

    private fun pageMap(page: MangaPage) = mapOf(
        "index" to page.index,
        "imageUrl" to page.imageUrl,
        "referer" to page.referer,
        "headers" to page.headers,
        "isChapterEnd" to page.isChapterEnd,
        "chapterLabel" to page.chapterLabel,
    )

    private fun mangaFromMap(map: Map<String, Any?>) = Manga(
        id = requiredString(map, "id"),
        title = ProtocolJson.string(map, "title").orEmpty(),
        coverUrl = ProtocolJson.string(map, "coverUrl"),
        description = ProtocolJson.string(map, "description").orEmpty(),
        sourceId = ProtocolJson.long(map, "sourceId") ?: 0L,
        author = ProtocolJson.string(map, "author"),
        artist = ProtocolJson.string(map, "artist"),
        genres = ProtocolJson.list(map["genres"]).mapNotNull { it as? String },
        status = ProtocolJson.string(map, "status"),
        cachedChapterCount = ProtocolJson.int(map, "cachedChapterCount"),
    )

    private fun chapterFromMap(map: Map<String, Any?>) = Chapter(
        id = requiredString(map, "id"),
        mangaId = requiredString(map, "mangaId"),
        title = ProtocolJson.string(map, "title").orEmpty(),
        number = (map["number"] as? Number)?.toFloat() ?: ProtocolJson.string(map, "number")?.toFloatOrNull(),
        sourceId = ProtocolJson.long(map, "sourceId") ?: 0L,
        volume = ProtocolJson.int(map, "volume"),
        scanlator = ProtocolJson.string(map, "scanlator"),
        uploadDate = ProtocolJson.long(map, "uploadDate"),
    )

    private fun requiredString(map: Map<String, Any?>, key: String): String = ProtocolJson.string(map, key)?.takeIf { it.isNotBlank() }
        ?: error("Missing required field: $key")

    private fun requiredLong(map: Map<String, Any?>, key: String): Long = ProtocolJson.long(map, key) ?: error("Missing required field: $key")
}

private class HostProtocolFailure(val failure: HostCallResult.Failure) : RuntimeException(failure.message)
