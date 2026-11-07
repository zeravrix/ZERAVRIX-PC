package com.zeravrix.zeravrix.jvmhost

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class HostProtocolServerTest {
    @Test
    fun servesVersionedRequestsAndStructuredErrors() = runBlocking {
        val jar = java.nio.file.Path.of(requireNotNull(System.getProperty("example.extension.jar")))
        val root = Files.createTempDirectory("zeravrix-protocol")
        JvmExtensionHost(root, includeBuiltIns = false).use { host ->
            val installed = host.installJar(jar)
            assertTrue(installed is HostCallResult.Success)
            val input = """
                {"protocol":1,"requestId":"hello","op":"hello"}
                {"protocol":1,"requestId":"sources","op":"list_sources"}
                {"protocol":1,"requestId":"search","op":"search","sourceId":9000001,"page":1,"query":"compatibility"}
                {"protocol":1,"requestId":"bad","op":"not_supported"}
            """.trimIndent().replace("\n", "\r\n")
            val output = ByteArrayOutputStream()
            HostProtocolServer(host).serve(ByteArrayInputStream(input.toByteArray()), output)
            val response = output.toString(Charsets.UTF_8)
            assertTrue(response.contains("\"requestId\":\"hello\""))
            assertTrue(response.contains("\"protocol\":1"))
            assertTrue(response.contains("Example Desktop Source"))
            assertTrue(response.contains("\"requestId\":\"search\""))
            assertTrue(response.contains("\"requestId\":\"bad\""))
            assertTrue(response.contains("UNKNOWN_OPERATION"))
        }
    }
}
