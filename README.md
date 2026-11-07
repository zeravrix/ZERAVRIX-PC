# ⚡ ZERAVRIX PC

**The Native Windows Manga, Manhua & Manhwa Reader**

[![Official Website](https://img.shields.io/badge/Official%20Website-zeravrix.pages.dev-00D2FF.svg?style=for-the-badge&logo=google-chrome&logoColor=white)](https://zeravrix.pages.dev/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20x64-0078D6.svg?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/zeravrix/ZERAVRIX-PC/releases)
[![Language](https://img.shields.io/badge/Language-Kotlin%20100%25-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Compose%20Desktop%20%2F%20Skia-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge)](LICENSE)

---

## Overview

**ZERAVRIX PC** is a high-performance, 100% native desktop application designed specifically for reading Manga, Manhua, and Manhwa on Windows. Built from the ground up with **Kotlin 2.1**, **Jetpack Compose for Desktop**, and **Skia/Skiko hardware acceleration**, it delivers an uncompromising desktop reading experience with zero emulators, zero APK overhead, and ultra-smooth scrolling on high-refresh displays.

### Key Capabilities

* **Tailored Webtoon & Manhwa Engine:** Continuous zero-gap vertical strip rendering, smooth inertia scrolling, automatic bottom-of-chapter continuous prefetch, and viewport-aware memory-bounded caching.
* **Traditional Desktop Reader Modes:** Single-page, Double-page spread, Auto-spread based on window aspect ratio, LTR and RTL reading directions.
* **Smart Memory Governor:** Byte-bounded in-memory LRU bitmap cache (`ImageBitmapLru`) and atomic disk cache (`PageCache`), preventing VRAM exhaustion and out-of-memory spikes even on massive 25,000+ px long-strip webtoon chapters.
* **In-Reader OCR & Translation Tool:** Integrated on-screen drag-to-select translation dialog with multi-language OCR and translation support.
* **Background Reading Soundtrack:** Integrated local audio player + Spotify Web API connection to soundtrack your reading sessions.
* **Flexible Extensibility:** Modular source engine supporting built-in scrapers and dynamic desktop extension JAR loading.
* **True Standalone Packaging:** Self-contained native Windows bundles (Portable ZIP, WiX `.msi`, `.exe` installer) with zero runtime prerequisites on the user's system.

---

## Architecture & Modules

```text
ZERAVRIX PC
├── desktop-app/    Main Compose Desktop window shell, navigation rail, custom chrome & screens
├── reader/         High-performance comic reader, webtoon strip engine, LRU bitmap cache & OCR
├── core/           Domain models, asynchronous stores, library persistence, downloads & Spotify provider
├── source-api/     Desktop source provider contracts and dynamic ServiceLoader extension runtime
├── packaging/      Installer scripts, WiX definitions, and Microsoft Store MSIX packaging tools
└── docs/           System blueprint (v5), execution plans, and architectural specifications
```

---

## Building from Source

### Prerequisites
* **Operating System:** Windows 10 / 11 (x64)
* **JDK:** Java Development Kit (JDK 21 configured as `JAVA_HOME`)
* **Git:** Git for Windows

### Build Commands

```powershell
# 1. Compile all Kotlin modules
.\gradlew.bat compileKotlin

# 2. Run test suite
.\gradlew.bat test

# 3. Create standalone portable Windows app
.\gradlew.bat :desktop-app:createDistributable
# Output: desktop-app/build/compose/binaries/main/app/ZERAVRIX/ZERAVRIX.exe

# 4. Generate Windows MSI Installer
.\gradlew.bat :desktop-app:packageMsi
# Output: desktop-app/build/compose/binaries/main/msi/ZERAVRIX-1.4.0.msi

# 5. Generate Windows EXE Installer
.\gradlew.bat :desktop-app:packageExe
# Output: desktop-app/build/compose/binaries/main/exe/ZERAVRIX-1.4.0.exe
```

---

## Engineering & Blueprint

Project implementation follows **ZERAVRIX PC Native Blueprint v5** located in [`docs/BLUEPRINT.md`](docs/BLUEPRINT.md). For contribution guidelines, architectural rules, and definition of done, consult [`AGENTS.md`](AGENTS.md) and [`.agents/rules/`](.agents/rules/).
