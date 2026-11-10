# 📦 ZERAVRIX Flatpak & Flathub Publication Guide

This directory contains the production-ready Flatpak manifest and AppStream metadata for **ZERAVRIX**, prepared according to current [Flathub submission requirements](https://docs.flathub.org/docs/for-app-authors/submission/).

---

## 📁 Package Contents

* `org.zeravrix.ZERAVRIX.yml`: The main Flatpak manifest targeting Freedesktop Runtime 24.08 and OpenJDK 21.
* `org.zeravrix.ZERAVRIX.metainfo.xml`: Validated AppStream metadata with descriptions, features, screenshots, and OARS content rating.
* `org.zeravrix.ZERAVRIX.desktop`: Desktop launcher entry for Linux desktop environments (GNOME, KDE Plasma, XFCE).
* `org.zeravrix.ZERAVRIX.png`: High-resolution 1024x1024 application icon.
* `zeravrix-flatpak.sh`: Hardened launcher script with sandbox permissions, Wayland/X11 auto-detection, and OpenGL acceleration.
* `build-flatpak.sh`: Automated local build and verification script.

---

## 🛠️ Testing Locally

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

Install the required Freedesktop 24.08 runtime and OpenJDK SDK extension:

```bash
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install flathub org.freedesktop.Platform//24.08 org.freedesktop.Sdk//24.08 org.freedesktop.Sdk.Extension.openjdk21//24.08
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
flatpak-builder --run build-dir org.zeravrix.ZERAVRIX.yml zeravrix
```

---

## 🚀 Submitting to Official Flathub

1. **Fork the Flathub Repository**:
   Visit [https://github.com/flathub/flathub](https://github.com/flathub/flathub) and click **Fork**.

2. **Create New Branch**:
   ```bash
   git clone https://github.com/<your-username>/flathub.git
   cd flathub
   git checkout -b add-org.zeravrix.ZERAVRIX
   ```

3. **Add Application Files**:
   Create a new directory `org.zeravrix.ZERAVRIX/` and copy the files:
   - `org.zeravrix.ZERAVRIX.yml`
   - `org.zeravrix.ZERAVRIX.metainfo.xml`
   - `org.zeravrix.ZERAVRIX.desktop`
   - `org.zeravrix.ZERAVRIX.png`
   - `zeravrix-flatpak.sh`

4. **Verify Metadata**:
   ```bash
   appstreamcli validate org.zeravrix.ZERAVRIX.metainfo.xml
   ```

5. **Open Pull Request**:
   Commit and push to your fork, then open a Pull Request against `flathub/flathub:new-pr`.
   The Flathub automated CI builder will test the package on `x86_64` and `aarch64`. Once approved, your application repository `flathub/org.zeravrix.ZERAVRIX` will be created automatically!
