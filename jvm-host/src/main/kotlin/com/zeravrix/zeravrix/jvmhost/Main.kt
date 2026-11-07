package com.zeravrix.zeravrix.jvmhost

import kotlinx.coroutines.runBlocking
import java.nio.file.Path

private const val HOST_VERSION = "1"

fun main(args: Array<String>) {
    val root = argumentValue(args, "--root")?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".zeravrix")
    val host = JvmExtensionHost(root)
    Runtime.getRuntime().addShutdownHook(Thread(host::close, "zeravrix-jvm-host-shutdown"))

    when {
        args.contains("--stdio") -> runBlocking { HostProtocolServer(host).serve(System.`in`, System.out) }
        args.firstOrNull() == "--list" -> {
            println("ZERAVRIX-JVM-HOST protocol=$HOST_VERSION")
            host.listSources().forEach { source ->
                println("${source.id}\t${source.language}\t${source.name}\t${if (source.enabled) "enabled" else "disabled"}")
            }
        }
        args.firstOrNull() == "--load" -> {
            val loaded = host.loadInstalledExtensions()
            println("loaded=${loaded.size}")
            loaded.forEach { println("${it.id}\t${it.name}") }
        }
        args.firstOrNull() == "--version" -> println("ZERAVRIX-JVM-HOST/$HOST_VERSION")
        else -> {
            println("ZERAVRIX-JVM-HOST/$HOST_VERSION")
            println("Usage: --list | --load | --version [--root <path>]")
        }
    }
}

private fun argumentValue(args: Array<String>, name: String): String? {
    val index = args.indexOf(name)
    return args.getOrNull(index + 1)?.takeIf { index >= 0 }
}
