# ZERAVRIX Native Migration Status

**Current status:** Compatibility-first JVM host, IPC, native shell, reader foundations, and side-by-side `Zeravrix dev` packaging configuration are implemented; a real Windows EXE still requires a Windows App SDK toolchain.

## Completed in this work session

| Area | Implementation | Verification |
|---|---|---|
| Migration map | `MIGRATION-MAP.md` covering current modules, API, dependencies, storage, reader, preserved behavior, and target architecture | Source audit completed before production changes |
| JVM host | New `jvm-host` Gradle application using existing `core`, `source-api`, `SourceManager`, `ServiceLoader`, and `URLClassLoader` | Host tests and real example-extension JAR loading pass |
| Extension compatibility | Corrected stale `META-INF/services` descriptor package names in example/local-source fixtures; extension source code was not rewritten | Example provider loads with source ID `9000001` and responds to search |
| Host lifecycle | Install/update/remove, enable/disable persistence, timeouts, structured failures, reload behavior | JVM host tests pass, including restart-state test |
| Protocol | Versioned newline-delimited JSON host server with hello, source management, source operations, errors, and cancellation request model | Protocol server test and `--stdio` process smoke test pass |
| Native IPC | C++20 stream client, Win32 handle-backed client, Named Pipe client contract, CMake interface target | Portable C++ IPC smoke target passes |
| Native shell | C++20/WinUI 3 `Zeravrix dev` shell and JVM supervisor source | CMake diagnostics pass on Linux; WinUI branch awaits Windows SDK build |
| Reader foundation | Bounded byte-cost LRU cache, atomic disk page cache, viewport prefetch plan, cancellation generations, reader state/navigation controller, WIC decoder source | Native reader-cache and state smoke target passes |
| Core foundation | C++ domain models and `.zeravrix` storage-path abstraction | Included in native CMake interface target |

## Validation performed

From the extracted project root:

```text
./gradlew :jvm-host:test test --no-daemon
./gradlew :jvm-host:installDist --no-daemon
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release --parallel 2
native/build/zeravrix-native-host-check
native/build/zeravrix-reader-cache-check
```

The Gradle suite completes successfully, including the original project tests and the new JVM-host tests. The C++20 diagnostic targets compile and run successfully with GCC 13/CMake 3.28. The WinUI 3 branch cannot be compiled into a real Windows EXE in this Linux sandbox because it requires a Windows SDK, Visual Studio C++ toolchain, C++/WinRT, and Windows App SDK package resolution. The environment has CMake/GCC but no MSVC, MinGW cross-compiler, Windows SDK, Wine, or Windows App SDK.

## Remaining work before production release

The C++ application is currently a shell plus tested infrastructure, not yet a full replacement for the Compose product. Remaining work includes connecting WinUI navigation to the JVM protocol client, implementing the complete native library/search/download/source/settings/music surfaces, wiring WIC output into Direct2D/WinUI rendering, adding native HTTP/download services with cancellation and retry, importing existing properties/manifests, implementing complete backups, protecting credentials with Windows DPAPI/Credential Manager, adding host crash/restart UI state, and producing signed EXE/MSIX artifacts.

The Windows target is configured as `Zeravrix dev.exe` with a distinct installer definition and `.zeravrix-dev` data directory, so it can coexist with the old application. The current Windows supervisor launches the host using inherited standard handles for a reliable local transport. The protocol remains transport-neutral and includes a Named Pipe client implementation for a future direct Named Pipe deployment. The JVM host’s `--stdio` mode is the currently validated integration path.

## Important compatibility finding

The original extension fixtures contained a ServiceLoader descriptor named for `com.zeravrix.ZERAVRIX.source.SourceProvider`, while the active API is `com.zeravrix.zeravrix.source.SourceProvider`. That mismatch prevented discovery of the example provider. The descriptor filenames were corrected; the extension provider implementation and API semantics remain unchanged. This should be treated as a migration fixture correction and checked across all third-party JARs before distribution.
