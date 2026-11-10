<p align="center">
  <img src="assets/logo.png" alt="ZERAVRIX Logo" width="160" />
</p>

# ⚡ ZERAVRIX

**High-Performance Native Manga, Manhua & Manhwa Reader for PC**

[![Official Website](https://img.shields.io/badge/Official%20Website-zeravrix.pages.dev-00D2FF.svg?style=for-the-badge&logo=google-chrome&logoColor=white)](https://zeravrix.pages.dev/)
[![Latest Release](https://img.shields.io/badge/Release-v1.7.2-10B981.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/zeravrix/ZERAVRIX-PC/releases/tag/v1.7.2)
[![Platform](https://img.shields.io/badge/Platform-Windows%20x64%20%7C%20Linux%20%7C%20Flatpak-0078D6.svg?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/zeravrix/ZERAVRIX-PC/releases)
[![Language](https://img.shields.io/badge/Language-Kotlin%2097%25-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Compose%20Desktop%20%2F%20Skia-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://github.com/JetBrains/compose-multiplatform)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=for-the-badge)](LICENSE)

---

## Overview

**ZERAVRIX** is an ultra-fast, native desktop application engineered specifically for reading Manga, Manhua, and Manhwa on PC. Built from the ground up with **Kotlin 2.1**, **Jetpack Compose for Desktop**, and **Skia/Skiko hardware acceleration**, it delivers an uncompromising reading experience on high-refresh displays with zero emulators, zero APK overhead, and smooth inertia scrolling.

---

## ✨ Features (v1.7.2)

### ⚡ Intelligent Memory Management & Bounded RAM
- **Active Window Memory Governor:** Strictly limits decoded in-memory bitmaps to the active reading window (keeping 3 pages behind and preloading 5 pages ahead) to eliminate memory bloat and keep total RAM consumption low.
- **Automatic Old Chapter Purging:** Pages from previous chapters are automatically evicted from RAM during continuous reading.
- **500 MB On-Disk Storage Cache:** Compressed web images are persisted in high-speed disk storage up to 500 MB, allowing instant backwards navigation without network fetches or RAM bloat.
- **Linux Memory Optimization:** G1GC with string deduplication and capped heap options prevents runaway RAM usage.

### 📖 Enhanced Two-Page & Single-Page Reading Modes
- **Screen Space Optimization:** Two-Page mode now utilizes the full vertical display height with realistic book spine gutters and proper proportions.
- **Synchronized 2-Page Navigation:** Page turns in two-page mode advance and reverse by pairs without desynchronization.
- **Odd/Cover Page Handling:** Single cover pages and chapter ends are cleanly centered.

### 🎨 Hardware-Accelerated Image Adjustments
- **Real-Time Sliders:** Adjust **Contrast**, **Saturation**, and **Brightness** on the fly with live visual feedback.
- **Invert Colors (Night Mode):** Instant color inversion for comfortable dark-room reading of bright manga pages.
- **Skia GPU Pipeline:** All image adjustments run directly on your GPU using hardware SkColorFilters with zero CPU overhead.

### 🖥️ Fullscreen, Canvas-Wide Scrolling & Clean Chrome
- **F11 Fullscreen:** Toggle immersive borderless fullscreen anywhere in the application or reader with `F11`.
- **Canvas-Wide Mouse Scrolling:** Mouse wheel scrolling works anywhere on the window, including margins, letterboxes, and gaps between webtoon strips.
- **Minimalist Top Chrome:** Cleaned upper navigation bar displaying only the original brand logo.
- **Reading History Landing View:** The app launches directly into your Reading History shelf on startup.

### 🌐 Manhwa Exploration & State Preservation
- **Stateful Source Navigation:** Opening a manhwa from a source and pressing **Back** returns you to your exact browsing state and pixel scroll position without resetting to the source directory.
- **1000+ Sources & Full Arabic Support:** Instant access to global and Arabic catalogs (MangaSpark, MangaSwat, Team X, Azora, Hijala, 3asq, Komga, Kavita, and more).
- **Persistent Source Pinning:** Pin preferred sources to the Explore screen with saved state across restarts.

### 📦 Flatpak Packaging
- **Production-Ready Flatpak Manifest:** Standalone `org.zeravrix.ZERAVRIX.yml` for Linux packaging.
- **Bundled OpenJDK 21 & Skia Acceleration:** Sandboxed, safe execution across Fedora, Ubuntu, Arch, and SteamOS without manual dependency installation.

---

## 📥 Download & Installation

Visit the [**Latest GitHub Release (v1.7.2)**](https://github.com/zeravrix/ZERAVRIX-PC/releases/tag/v1.7.2) to download the package for your platform:

| Platform | Package | Description |
| :--- | :--- | :--- |
| **Windows 10 / 11** | `ZERAVRIX-1.7.2-Setup.exe` | **Recommended:** Standalone Windows installer with desktop shortcut and Start menu registration |
| **Windows Portable** | `ZERAVRIX-1.7.2-portable.zip` | No installation required. Extract anywhere and launch `ZERAVRIX.exe` |
| **Linux (Flatpak)** | `org.zeravrix.ZERAVRIX.flatpak` | Sandboxed, dependency-free bundle for all modern Linux distributions |
| **Linux (Fedora / RHEL RPM)** | `zeravrix-1.7.2-1.x86_64.rpm` | Native RPM package with system launcher and menu integration |
| **Linux (Tarball Bundle)** | `ZERAVRIX-1.7.2-Linux-Fedora.tar.gz` | Portable Linux bundle with low-RAM G1GC launcher script |

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
   tar -xzf ZERAVRIX-1.7.2-Linux-Fedora.tar.gz
   cd zeravrix-1.7.2-linux
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
├── distribution/   Linux desktop entries, Flatpak manifest, RPM spec, and launchers
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
./gradlew compileKotlin

# 2. Run unit tests
./gradlew test

# 3. Create standalone portable distribution
./gradlew :desktop-app:createDistributable
# Output: desktop-app/build/compose/binaries/main/app/zeravrix/

# 4. Generate standalone Windows Setup Installer (.exe)
./gradlew :desktop-app:packageExe
# Output: desktop-app/build/compose/binaries/main/exe/zeravrix-1.7.2.exe
```

---

## 🌐 Community & Official Links

- **Official Website:** [https://zeravrix.pages.dev](https://zeravrix.pages.dev)
- **GitHub Repository:** [https://github.com/zeravrix/ZERAVRIX-PC](https://github.com/zeravrix/ZERAVRIX-PC)
- **Discord Server:** [Join the ZERAVRIX Community](https://discord.gg/zeravrix)

---

## 📄 License

ZERAVRIX is released under the [GNU General Public License v3.0](LICENSE).
