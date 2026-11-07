package com.zeravrix.zeravrix.jvmhost

import java.lang.StringBuilder
import java.util.Locale

/** Small JSON implementation for the narrow, versioned local IPC envelope. */
internal object ProtocolJson {
    fun parse(text: String): Any? = Reader(text).parseDocument()

    fun stringify(value: Any?): String = buildString { appendValue(value) }

    @Suppress("UNCHECKED_CAST")
    fun map(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: emptyMap()

    fun list(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()

    fun string(map: Map<String, Any?>, key: String): String? = map[key] as? String

    fun long(map: Map<String, Any?>, key: String): Long? = when (val value = map[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }

    fun int(map: Map<String, Any?>, key: String): Int? = long(map, key)?.toInt()

    fun bool(map: Map<String, Any?>, key: String): Boolean? = when (val value = map[key]) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }

    private fun StringBuilder.appendValue(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> appendString(value)
            is CharSequence -> appendString(value.toString())
            is Number, is Boolean -> append(value.toString())
            is Map<*, *> -> {
                append('{')
                value.entries.filter { it.key is String }.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    appendString(entry.key as String)
                    append(':')
                    appendValue(entry.value)
                }
                append('}')
            }
            is Iterable<*> -> {
                append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }
            else -> appendString(value.toString())
        }
    }

    private fun StringBuilder.appendString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append("\\u%04x".format(Locale.ROOT, char.code)) else append(char)
            }
        }
        append('"')
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun parseDocument(): Any? {
            val value = parseValue()
            skipWhitespace()
            check(index == text.length) { "Unexpected JSON data at $index" }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (text.getOrNull(index)) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> consume("true", true)
                'f' -> consume("false", false)
                'n' -> consume("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Expected JSON value at $index")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            val result = linkedMapOf<String, Any?>()
            if (peek('}')) { index++; return result }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (peek('}')) { index++; return result }
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            val result = mutableListOf<Any?>()
            if (peek(']')) { index++; return result }
            while (true) {
                result += parseValue()
                skipWhitespace()
                if (peek(']')) { index++; return result }
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                when (val char = text[index++]) {
                    '"' -> return result.toString()
                    '\\' -> when (val escaped = text.getOrNull(index++) ?: error("Incomplete escape")) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> result.append(text.substring(index, index + 4).toInt(16).toChar()).also { index += 4 }
                        else -> error("Invalid escape: $escaped")
                    }
                    else -> result.append(char)
                }
            }
            error("Unterminated JSON string")
        }

        private fun parseNumber(): Number {
            val start = index
            if (text.getOrNull(index) == '-') index++
            while (text.getOrNull(index)?.isDigit() == true) index++
            if (text.getOrNull(index) == '.') {
                index++
                while (text.getOrNull(index)?.isDigit() == true) index++
            }
            if (text.getOrNull(index) in listOf('e', 'E')) {
                index++
                if (text.getOrNull(index) in listOf('+', '-')) index++
                while (text.getOrNull(index)?.isDigit() == true) index++
            }
            val raw = text.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: error("Invalid number: $raw")
        }

        private fun <T> consume(value: String, result: T): T {
            check(text.startsWith(value, index)) { "Expected $value at $index" }
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
