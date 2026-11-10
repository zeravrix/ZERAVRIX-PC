# 📦 ZERAVRIX Flatpak Packaging Guide

This directory contains the production-ready Flatpak manifest and AppStream metadata for **ZERAVRIX**.

---

## 📁 Package Contents

* `io.github.zeravrix.ZERAVRIX-PC.yml`: The main Flatpak manifest targeting Freedesktop Runtime 26.08 and OpenJDK 21.
* `io.github.zeravrix.ZERAVRIX-PC.metainfo.xml`: Validated AppStream metadata with descriptions, features, screenshots, and OARS content rating.
* `io.github.zeravrix.ZERAVRIX-PC.desktop`: Desktop launcher entry for Linux desktop environments (GNOME, KDE Plasma, XFCE).
* `io.github.zeravrix.ZERAVRIX-PC.png`: High-resolution application icon.
* `zeravrix-flatpak.sh`: Hardened launcher script with sandbox permissions, X11 display socket, and OpenGL acceleration.
* `build-flatpak.sh`: Automated local build and verification script.

---

## 🛠️ Building & Running Locally

### Prerequisites

Install `flatpak` and `flatpak-builder`:

```bash
# Fedora / RHEL
sudo dnf install flatpak flatpak-builder

# Ubuntu / Debian
sudo apt install flatpak flatpak-builder

# Arch Linux
sudo pacman -S flatpak flatpak-builder
```

Install the required Freedesktop 26.08 runtime and OpenJDK SDK extension:

```bash
flatpak install org.freedesktop.Platform//26.08 org.freedesktop.Sdk//26.08 org.freedesktop.Sdk.Extension.openjdk21//26.08
```

### Build & Run

From the root of the repository:

```bash
# Step 1: Build distributable jar files
./gradlew :desktop-app:createDistributable

# Step 2: Build the Flatpak container
cd distribution/flatpak
chmod +x build-flatpak.sh zeravrix-flatpak.sh
./build-flatpak.sh

# Step 3: Run the sandboxed application
flatpak-builder --run build-dir io.github.zeravrix.ZERAVRIX-PC.yml zeravrix
```

### Create Standalone Bundle

To export a standalone `.flatpak` single-file bundle:

```bash
flatpak build-bundle repo ZERAVRIX.flatpak io.github.zeravrix.ZERAVRIX-PC
```
