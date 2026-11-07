package com.zeravrix.zeravrix.jvmhost

import com.zeravrix.zeravrix.core.Manga
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmExtensionHostTest {
    @Test
    fun listsBuiltInSourcesWithoutLoadingExternalJars() {
        val root = Files.createTempDirectory("zeravrix-host-builtins")
        JvmExtensionHost(root).use { host ->
            val komga = host.listSources().firstOrNull { it.name == "Komga" }
            assertTrue(komga != null)
            assertTrue(komga.enabled)
            assertTrue(komga.search)
        }
    }

    @Test
    fun loadsAndCallsExistingServiceLoaderExtensionJar() = runBlocking {
        val jar = System.getProperty("example.extension.jar")?.let(java.nio.file.Path::of)
            ?: error("Gradle did not provide the example extension fixture")
        val root = Files.createTempDirectory("zeravrix-host-extension")
        JvmExtensionHost(root, includeBuiltIns = false).use { host ->
            val installed = assertIs<HostCallResult.Success<List<HostSourceInfo>>>(host.installJar(jar))
            val source = installed.value.single()
            assertEquals(9000001L, source.id)
            assertEquals("Example Desktop Source", source.name)

            val result = host.execute(HostOperation.Search(source.id, query = "compatibility"))
            val success = assertIs<HostCallResult.Success<HostOperationResult>>(result)
            val page = assertIs<HostOperationResult.MangaPage>(success.value)
            assertTrue(page.result.items.isEmpty())
        }
    }

    @Test
    fun disabledSourceStateSurvivesHostRestart() {
        val jar = java.nio.file.Path.of(requireNotNull(System.getProperty("example.extension.jar")))
        val root = Files.createTempDirectory("zeravrix-host-persistence")
        JvmExtensionHost(root, includeBuiltIns = false).use { host ->
            val source = assertIs<HostCallResult.Success<List<HostSourceInfo>>>(host.installJar(jar)).value.single()
            assertIs<HostCallResult.Success<Unit>>(host.setEnabled(source.id, false))
        }
        JvmExtensionHost(root, includeBuiltIns = false).use { host ->
            assertTrue(!host.listSources().single().enabled)
        }
    }

    @Test
    fun disabledSourceReturnsStructuredFailure() = runBlocking {
        val jar = System.getProperty("example.extension.jar")?.let(java.nio.file.Path::of)
            ?: error("Gradle did not provide the example extension fixture")
        val root = Files.createTempDirectory("zeravrix-host-disabled")
        JvmExtensionHost(root, includeBuiltIns = false).use { host ->
            val source = assertIs<HostCallResult.Success<List<HostSourceInfo>>>(host.installJar(jar)).value.single()
            assertIs<HostCallResult.Success<Unit>>(host.setEnabled(source.id, false))
            val result = host.execute(HostOperation.Details(source.id, Manga("id", "Title")))
            val failure = assertIs<HostCallResult.Failure>(result)
            assertEquals("SOURCE_DISABLED", failure.code)
        }
    }
}
