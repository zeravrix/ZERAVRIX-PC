package com.zeravrix.zeravrix.source

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

/** Small JVM-only compatibility layer for ported Mihon/Keiyoushi sources. */
abstract class DesktopJsonSource : HttpSource() {
    protected suspend fun getChecked(url: String, headers: Map<String, String> = emptyMap()): String {
        val response = request(url, headers, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("$name returned HTTP ${response.statusCode()} at $url")
        }
        return response.body()
    }

    protected suspend fun postChecked(
        url: String,
        body: String = "",
        headers: Map<String, String> = emptyMap(),
        contentType: String = "application/json",
    ): String {
        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val builder = HttpRequest.newBuilder(URI(url))
                .timeout(java.time.Duration.ofSeconds(45))
                .header("User-Agent", HttpSource.USER_AGENT)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            headers.forEach { (key, value) -> builder.header(key, value) }
            client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        }
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("$name returned HTTP ${response.statusCode()} at $url")
        }
        return response.body()
    }

    protected suspend fun postFormChecked(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String =
        postChecked(
            url = url,
            body = fields.entries.joinToString("&") { (key, value) ->
                "${java.net.URLEncoder.encode(key, StandardCharsets.UTF_8)}=${java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            },
            headers = headers,
            contentType = "application/x-www-form-urlencoded; charset=UTF-8",
        )

    protected fun requireConfigured(vararg values: Pair<String, String>): Unit {
        val missing = values.filter { it.second.isBlank() }.joinToString(", ") { it.first }
        if (missing.isNotBlank()) error("$name is not configured. Enter: $missing")
    }
}

internal object DesktopJson {
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
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (peek('}')) { index++; return result }
            while (index < text.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (peek('}')) { index++; break }
                expect(',')
            }
            return result
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (peek(']')) { index++; return result }
            while (index < text.length) {
                result += parseValue()
                skipWhitespace()
                if (peek(']')) { index++; break }
                expect(',')
            }
            return result
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
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

        private fun <T> consume(value: String, result: T): T {
            check(text.startsWith(value, index)) { "Invalid JSON token at $index" }
            index += value.length
            return result
        }

        private fun expect(char: Char) {
            skipWhitespace()
            check(text.getOrNull(index) == char) { "Expected $char at $index" }
            index++
        }

        private fun peek(char: Char): Boolean {
            skipWhitespace()
            return text.getOrNull(index) == char
        }

        private fun skipWhitespace() {
            while (text.getOrNull(index)?.isWhitespace() == true) index++
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.jsonMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
internal fun Any?.jsonList(): List<Any?> = this as? List<Any?> ?: emptyList()

internal fun Map<String, Any?>.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    when (val value = this[key]) {
        is String -> value
        is Number -> value.toString()
        else -> null
    }
}

internal fun Map<String, Any?>.number(vararg keys: String): Float? = keys.firstNotNullOfOrNull { key ->
    when (val value = this[key]) {
        is Number -> value.toFloat()
        is String -> value.toFloatOrNull()
        else -> null
    }
}

internal fun Map<String, Any?>.mapValue(vararg keys: String): Map<String, Any?>? = keys.firstNotNullOfOrNull { key ->
    (this[key] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
}

internal fun Map<String, Any?>.listValue(vararg keys: String): List<Any?> = keys.firstNotNullOfOrNull { key ->
    (this[key] as? List<*>)?.toList()
} ?: emptyList()
