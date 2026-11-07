package com.zeravrix.zeravrix.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Locale

/** One extension package advertised by a repository index. */
data class ExtensionArtifact(
    val id: String,
    val name: String,
    val packageName: String,
    val language: String,
    val version: String,
    val jarUrl: String? = null,
    val apkUrl: String? = null,
    val sourceCount: Int = 0,
    val nsfw: Boolean = false,
    val sources: List<RepositorySourceDescriptor> = emptyList(),
) {
    val hasJar: Boolean get() = !jarUrl.isNullOrBlank()
    val looksLikeAndroidExtension: Boolean
        get() = packageName.startsWith("eu.kanade.tachiyomi") || packageName.startsWith("mihon.") || apkUrl != null
}

sealed class StoreFetchResult {
    data class Repository(
        val store: ExtensionStore,
        val rawSize: Int,
        val indexUrl: String,
        val format: String,
        val artifacts: List<ExtensionArtifact>,
    ) : StoreFetchResult()
    data class Metadata(val store: ExtensionStore, val rawSize: Int) : StoreFetchResult()
    data class BinaryIndex(val store: ExtensionStore, val rawSize: Int, val format: String = "protobuf") : StoreFetchResult()
    data class Failure(val store: ExtensionStore, val message: String) : StoreFetchResult()
}

/**
 * Fetches Mihon-style repository metadata and companion artifact indexes.
 * It never executes downloaded code; the desktop SourceManager remains the only
 * component allowed to load a validated ZERAVRIX ServiceLoader JAR.
 */
class ExtensionStoreClient {
    private val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    suspend fun fetch(store: ExtensionStore): StoreFetchResult = withContext(Dispatchers.IO) {
        runCatching {
            val candidates = candidateIndexUrls(store.url)
            var metadata: StoreFetchResult.Metadata? = null
            var lastFailure: Throwable? = null
            for (url in candidates) {
                try {
                    val response = get(url)
                    if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()} at $url")
                    val bytes = response.body()
                    val contentType = response.headers().firstValue("content-type").orElse("").lowercase(Locale.ROOT)
                    val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                    if (text.startsWith("{") || text.startsWith("[")) {
                        val root = MiniJson.parse(text)
                        val parsedMeta = parseMeta(root)
                        val updatedStore = store.copy(
                            name = parsedMeta["name"] ?: store.name,
                            shortName = parsedMeta["shortName"] ?: store.shortName,
                            website = parsedMeta["website"] ?: store.website,
                            signingKeyFingerprint = parsedMeta["signingKeyFingerprint"] ?: store.signingKeyFingerprint,
                        )
                        val artifacts = parseArtifacts(root)
                        if (artifacts.isNotEmpty()) {
                            return@withContext StoreFetchResult.Repository(updatedStore, bytes.size, url, "json", artifacts)
                        }
                        metadata = StoreFetchResult.Metadata(updatedStore, bytes.size)
                    } else if (contentType.contains("protobuf") || url.endsWith(".pb", true) || bytes.size > 1024) {
                        // The official binary index is intentionally not decoded by a
                        // reflection-based Android protobuf runtime. A companion JSON
                        // index is tried above/below when the repository publishes one.
                        lastFailure = IllegalStateException("Binary index has no desktop JSON companion")
                    }
                } catch (failure: Throwable) {
                    lastFailure = failure
                }
            }
            metadata ?: if (lastFailure != null) {
                StoreFetchResult.Failure(store, lastFailure?.message ?: "Unable to fetch repository index")
            } else {
                StoreFetchResult.Metadata(store, 0)
            }
        }.getOrElse { StoreFetchResult.Failure(store, it.message ?: "Unable to fetch repository index") }
    }

    suspend fun downloadArtifact(artifact: ExtensionArtifact, targetDirectory: Path): Path = withContext(Dispatchers.IO) {
        val url = artifact.jarUrl ?: error("${artifact.name} does not publish a desktop JAR")
        require(url.startsWith("https://")) { "Only HTTPS extension artifacts are accepted" }
        Files.createDirectories(targetDirectory)
        val safeName = url.substringAfterLast('/').substringBefore('?').ifBlank { "${artifact.id}.jar" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = targetDirectory.resolve(safeName).normalize()
        require(destination.parent == targetDirectory.normalize()) { "Unsafe extension file name" }
        if (Files.isRegularFile(destination) && Files.size(destination) > 0) return@withContext destination
        val response = get(url)
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()} while downloading ${artifact.name}" }
        val body = response.body()
        check(body.size >= 512 && !body.copyOfRange(0, minOf(body.size, 128)).decodeToString().trimStart().startsWith("<")) {
            "Downloaded extension is not a valid binary artifact"
        }
        val temporary = destination.resolveSibling(destination.fileName.toString() + ".part")
        Files.write(temporary, body)
        Files.move(temporary, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        destination
    }

    private fun get(url: String): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(Duration.ofSeconds(45))
            .header("User-Agent", "ZERAVRIX/1.0.7 (Windows; Desktop)")
            .header("Accept", "application/json, application/octet-stream;q=0.9,*/*;q=0.8")
            .GET()
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun candidateIndexUrls(url: String): List<String> {
        val normalized = url.trim()
        val result = linkedSetOf(normalized)
        val base = normalized.substringBeforeLast('/', normalized).trimEnd('/')
        when {
            normalized.endsWith("/repo.json", true) -> result += normalized.removeSuffix("repo.json") + "index.json"
            normalized.endsWith("/index.pb", true) -> result += normalized.removeSuffix("index.pb") + "index.json"
            normalized.endsWith("/index.json", true) -> result += normalized.removeSuffix("index.json") + "repo.json"
            !normalized.substringAfterLast('/').contains('.') -> {
                result += "$normalized/index.json"
                result += "$normalized/repo.json"
            }
            else -> {
                result += "$base/index.json"
                result += "$base/repo.json"
            }
        }
        return result.toList()
    }

    private fun parseMeta(root: Any?): Map<String, String> {
        val map = root as? Map<*, *> ?: return emptyMap()
        val meta = (map["meta"] as? Map<*, *>) ?: map
        return listOf("name", "shortName", "website", "signingKeyFingerprint").mapNotNull { key ->
            (meta[key] as? String)?.let { key to it }
        }.toMap()
    }

    private fun parseArtifacts(root: Any?): List<ExtensionArtifact> {
        val candidates = when (root) {
            is List<*> -> root
            is Map<*, *> -> {
                val extensionList = root["extensionList"] as? Map<*, *>
                listOfNotNull(
                    root["extensions"] as? List<*>,
                    root["artifacts"] as? List<*>,
                    root["packages"] as? List<*>,
                    extensionList?.get("extensions") as? List<*>,
                    extensionList?.get("artifacts") as? List<*>,
                ).flatten()
            }
            else -> emptyList()
        }
        return candidates.mapNotNull { value ->
            val extension = value as? Map<*, *> ?: return@mapNotNull null
            val resources = extension["resources"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val packageName = extension.string("pkg") ?: extension.string("packageName") ?: return@mapNotNull null
            val rawName = extension.string("name") ?: packageName.substringAfterLast('.')
            val jarUrl = resources.string("jarUrl") ?: resources.string("jar") ?: extension.string("jarUrl") ?: extension.string("jar") ?: extension.string("downloadUrl")
            val apkName = extension.string("apk")
            val apkUrl = resources.string("apkUrl") ?: resources.string("apk") ?: extension.string("apkUrl") ?: extension.string("apk")
            val sourceDescriptors = (extension["sources"] as? List<*>)?.mapNotNull { rawSource ->
                val source = rawSource as? Map<*, *> ?: return@mapNotNull null
                val sourceName = source.string("name") ?: rawName
                val sourceId = source.string("id")?.toLongOrNull() ?: ("$packageName:$sourceName").hashCode().toLong()
                val homeUrl = source.string("homeUrl").orEmpty()
                if (!homeUrl.startsWith("http")) return@mapNotNull null
                RepositorySourceDescriptor(sourceId, sourceName, source.string("language") ?: source.string("lang") ?: extension.string("lang") ?: "multi", homeUrl)
            }.orEmpty()
            val sourceCount = sourceDescriptors.size
            ExtensionArtifact(
                id = packageName,
                name = rawName,
                packageName = packageName,
                language = extension.string("lang") ?: "multi",
                version = extension.string("version") ?: extension.string("versionName") ?: "unknown",
                jarUrl = jarUrl,
                apkUrl = apkUrl ?: apkName,
                sourceCount = sourceCount,
                nsfw = (extension["nsfw"] as? Number)?.toInt() == 1 || extension.string("contentWarning")?.contains("NSFW", true) == true,
                sources = sourceDescriptors,
            )
        }.distinctBy { it.id }
    }

    private fun Map<*, *>.string(key: String): String? = this[key] as? String
}

/** Minimal JSON reader for repository indexes; it has no code execution or reflection. */
private object MiniJson {
    fun parse(text: String): Any? = Reader(text).parseValue()

    private class Reader(private val text: String) {
        private var index = 0
        fun parseValue(): Any? {
            skipWhitespace()
            return when (text.getOrNull(index)) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> consume("true", true)
                'f' -> consume("false", false)
                'n' -> consume("null", null)
                else -> parseNumber()
            }
        }
        private fun parseObject(): Map<String, Any?> {
            expect('{'); skipWhitespace(); val result = linkedMapOf<String, Any?>()
            if (peek('}')) { index++; return result }
            while (index < text.length) {
                skipWhitespace(); val key = parseString(); skipWhitespace(); expect(':')
                result[key] = parseValue(); skipWhitespace()
                if (peek('}')) { index++; break }
                expect(',')
            }
            return result
        }
        private fun parseArray(): List<Any?> {
            expect('['); skipWhitespace(); val result = mutableListOf<Any?>()
            if (peek(']')) { index++; return result }
            while (index < text.length) {
                result += parseValue(); skipWhitespace()
                if (peek(']')) { index++; break }
                expect(',')
            }
            return result
        }
        private fun parseString(): String {
            expect('"'); val result = StringBuilder()
            while (index < text.length) {
                val char = text[index++]
                if (char == '"') return result.toString()
                if (char == '\\') {
                    when (val escaped = text[index++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(text.substring(index, index + 4).toInt(16).toChar()).also { index += 4 }
                    }
                } else result.append(char)
            }
            error("Unterminated JSON string")
        }
        private fun parseNumber(): Number {
            val start = index
            while (index < text.length && text[index] !in ",]} \t\r\n") index++
            val value = text.substring(start, index)
            return value.toLongOrNull() ?: value.toDouble()
        }
        private fun <T> consume(value: String, result: T): T { check(text.startsWith(value, index)); index += value.length; return result }
        private fun expect(char: Char) { skipWhitespace(); check(text.getOrNull(index) == char) { "Expected $char at $index" }; index++ }
        private fun peek(char: Char): Boolean { skipWhitespace(); return text.getOrNull(index) == char }
        private fun skipWhitespace() { while (text.getOrNull(index)?.isWhitespace() == true) index++ }
    }
}
