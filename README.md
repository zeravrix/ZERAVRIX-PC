<p align="center">
  <img src="assets/logo.png" alt="ZERAVRIX Logo" width="160" />
</p>

# ⚡ ZERAVRIX

**High-Performance Native Manga, Manhua & Manhwa Reader for PC**

[![Official Website](https://img.shields.io/badge/Official%20Website-zeravrix.pages.dev-00D2FF.svg?style=for-the-badge&logo=google-chrome&logoColor=white)](https://zeravrix.pages.dev/)
[![Latest Release](https://img.shields.io/badge/Release-v1.7.1-10B981.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/zeravrix/ZERAVRIX-PC/releases/tag/v1.7.1)
[![Platform](https://img.shields.io/badge/Platform-Windows%20x64%20%7C%20Linux%20Fedora-0078D6.svg?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/zeravrix/ZERAVRIX-PC/releases)
[![Language](https://img.shields.io/badge/Language-Kotlin%2097%25-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Compose%20Desktop%20%2F%20Skia-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge)](LICENSE)

---

## Overview

**ZERAVRIX** is an ultra-fast, native desktop application engineered specifically for reading Manga, Manhua, and Manhwa on PC. Built from the ground up with **Kotlin 2.1**, **Jetpack Compose for Desktop**, and **Skia/Skiko hardware acceleration**, it delivers an uncompromising reading experience on high-refresh displays with zero emulators, zero APK overhead, and smooth inertia scrolling.

---

## ✨ Features

### 📖 Continuous Multi-Chapter Reader
- **Seamless Infinite Scroll:** Chapters flow directly into subsequent chapters without stopping, loading screens, or restarting at page 1.
- **Dynamic Chapter Dividers:** Visual cards notify you when crossing chapter boundaries with progress indicators (`Chapter X of Y`).
- **End-of-Manga Detection:** Polished completion card notifies you when you are caught up with the latest released chapter.
- **Multiple Reading Modes:** Continuous Vertical Webtoon Strip, Single Page, Double Page spread, and Automatic Spread matching your window aspect ratio.
- **Precise Progress Tracking:** Reading progress and exact scroll position are saved automatically per title and preserved across application restarts.

### 🌐 1000+ Sources & Full Arabic Support
- **Direct Built-in Scrapers:** Instant access to global and Arabic catalogs (MangaSpark, MangaSwat, Team X, Azora, Hijala, 3asq, Komga, Kavita, and more).
- **Extension Repository Importer:** Add external Tachiyomi/Mihon and Kotatsu extension indices with one click.
- **Persistent Source Pinning:** Pin your preferred sources directly to the Explore screen. Pinned selections remain saved across restarts and navigation.
- **Right-Click Context Menus:** Quick mouse controls to pin/unpin sources from Explore, or remove titles from History and Favorites.

### ⚡ Performance & Smart Caching
- **LRU Memory Governor:** 350-entry thread-safe memory cache prevents out-of-memory spikes and VRAM bloat on massive 25,000+ pixel webtoon strips.
- **Persistent HTTP Disk Cache:** 512 MB disk cache ensures instant page loading when re-reading or navigating backwards.
- **Zero-Lag UI:** Background asynchronous prefetch streams upcoming pages and chapters ahead of your viewport.

### 🎨 Personalization & Visual Design
- **13 Studio Themes:** Void AMOLED, Indigo Night, Sakura, Matcha, Ember, Ocean, Lavender, Crimson, Aurora, Sunset, Graphite, Paper, and Mono with instant preview.
- **Custom Desktop App Icon:** Select any custom `.png`, `.jpg`, or `.ico` image for the desktop and titlebar directly from Settings, or reset to default with one click.
- **Cyber-Glassmorphism UI:** Translucent acrylic panels, smooth spring animations, and distraction-free layouts.

### 🔄 In-App Updates
- **Automatic GitHub Checker:** On launch, ZERAVRIX silently checks for newer versions on GitHub.
- **One-Click Download:** An update badge appears in the titlebar and Settings screen only when an update exists, letting you download the latest installer with a single click.

### 📁 Offline Reading
- **CBZ & ZIP Archive Support:** Drag and drop local `.cbz`, `.zip`, and `.cbr` comic archives directly into your library.
- **Automatic Cover Extraction:** Generates high-resolution library covers from comic archives automatically.

---

## 📥 Download & Installation

Visit the [**Latest GitHub Release (v1.7.1)**](https://github.com/zeravrix/ZERAVRIX-PC/releases/tag/v1.7.1) to download the package for your platform:

| Platform | Package | Description |
| :--- | :--- | :--- |
| **Windows 10 / 11** | `ZERAVRIX-1.7.1-Setup.exe` | **Recommended:** Standalone Windows installer with desktop shortcut and Start menu registration |
| **Windows Portable** | `ZERAVRIX-1.7.1-portable.zip` | No installation required. Extract anywhere and launch `ZERAVRIX.exe` |
| **Linux (Fedora / RHEL)** | `ZERAVRIX-1.7.1-Linux-Fedora.tar.gz` | Native bundle with OpenGL hardware acceleration launcher, desktop entry, and RPM spec |
| **Linux (Fedora Archive)** | `ZERAVRIX-1.7.1-Linux-Fedora.zip` | Standard ZIP archive for manual extraction and desktop integration |

### Running on Linux (Fedora / RHEL / Ubuntu)

1. Ensure Java 21+ and OpenGL libraries are installed:
   ```bash
   # Fedora / RHEL
   sudo dnf install java-21-openjdk mesa-libGL libX11 libXcursor libXext libXrender libXtst libxkbcommon

   # Ubuntu / Debian
   sudo apt install openjdk-21-jre libgl1-mesa-glx libx11-6 libxcursor1 libxext6 libxrender1 libxtst6 libxkbcommon0
   ```
2. Extract the archive:
   ```bash
   tar -xzf ZERAVRIX-1.7.1-Linux-Fedora.tar.gz
   cd zeravrix-1.7.1-linux
   ```
3. Launch ZERAVRIX:
   ```bash
   chmod +x zeravrix
   ./zeravrix
   ```

---

## 🛠️ Architecture & Modules

```text
ZERAVRIX
├── desktop-app/    Compose Desktop window shell, navigation rail, titlebar & screens
├── reader/         High-performance comic reader, webtoon strip engine, LRU bitmap cache
├── core/           Domain models, asynchronous stores, library persistence & updater
├── source-api/     Desktop source provider contracts and dynamic extension runtime
├── distribution/   Linux desktop entries, launcher scripts, and RPM spec
└── installer/      Windows Inno Setup and WiX packaging definitions
```

---

## 💻 Building from Source

### Prerequisites
* **Operating System:** Windows 10/11 or modern Linux distribution (Fedora 39+, Ubuntu 24.04+)
* **JDK:** Java Development Kit 21 (`JAVA_HOME` configured)
* **Git**

### Build Commands

```powershell
# 1. Compile all Kotlin modules
.\gradlew.bat compileKotlin

# 2. Run unit tests
.\gradlew.bat test

# 3. Create standalone portable distribution
.\gradlew.bat :desktop-app:createDistributable
# Output: desktop-app/build/compose/binaries/main/app/zeravrix/

# 4. Generate standalone Windows Setup Installer (.exe)
.\gradlew.bat :desktop-app:packageExe
# Output: desktop-app/build/compose/binaries/main/exe/zeravrix-1.7.1.exe
```

---

## 🌐 Community & Official Links

- **Official Website:** [https://zeravrix.pages.dev](https://zeravrix.pages.dev)
- **GitHub Repository:** [https://github.com/zeravrix/ZERAVRIX-PC](https://github.com/zeravrix/ZERAVRIX-PC)
- **Discord Server:** [Join the ZERAVRIX Community](https://discord.gg/zeravrix)

---

## 📄 License

ZERAVRIX is released under the [GNU General Public License v3.0](LICENSE).
