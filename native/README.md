# Native ZERAVRIX Migration

This directory is the incremental C++20 migration target. The production Windows application is intended to use WinUI 3 / Windows App SDK. The Linux build selects a portable diagnostic target because the Windows SDK and Windows App SDK are not available in the sandbox.

## Windows prerequisites

Use Visual Studio 2022 with the Desktop C++ workload, Windows 10/11 SDK, C++/WinRT tooling, CMake, and the Windows App SDK. Configure the Windows App SDK package so `find_package(Microsoft.WindowsAppSDK CONFIG REQUIRED)` resolves in `native/CMakeLists.txt`.

## Current targets

The Windows target is emitted as `Zeravrix dev.exe`, with the distinct installer name `Zeravrix-dev-Setup-1.4.0-dev.exe`. It uses `%USERPROFILE%\\.zeravrix-dev` rather than the legacy `%USERPROFILE%\\.zeravrix` directory, so it can coexist with the old build without sharing settings, cache, downloads, or progress files.

`Zeravrix dev` is the Windows WinUI shell. It owns the native application process and is designed to supervise `ZERAVRIX-JVM-HOST.exe` through a local stream transport. The native process does not load extension JAR files. `zeravrix-native-host-check` validates the newline-framed IPC client on non-Windows platforms. `zeravrix-reader-cache-check` validates the byte-bounded LRU cache, viewport prefetch plan, request-generation cancellation, and atomic disk page cache.

## JVM host handoff

Build the host distribution from the repository root with:

```text
./gradlew :jvm-host:installDist
```

The native packaging milestone must place the resulting host runtime beside the native executable and expose a Windows executable launcher at the path consumed by `JvmHostSupervisor`. A future packaging step may use `jpackage` or a bundled JRE to remove the requirement for a system Java installation.

## Scope status

The migration map, JVM extension host, versioned JSON protocol, native IPC client contract, WinUI shell, process-supervisor contract, bounded reader cache, and atomic disk page cache are implemented. The full native WIC/Direct2D decode implementation, remaining native screens, data migration, and EXE/MSIX packaging remain subsequent milestones and are intentionally not represented as complete here.
