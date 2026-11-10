Name:           zeravrix
Version:        1.7.2
Release:        1%{?dist}
Summary:        ZERAVRIX — High-Performance Manga, Manhua & Manhwa Reader
License:        GPL-3.0
URL:            https://zeravrix.pages.dev
Requires:       java-21-openjdk, libX11, libXcursor, libXext, libXrender, libXtst, libxkbcommon, mesa-libGL

%description
ZERAVRIX is a native, modern, high-performance Manga, Manhua, and Manhwa reader for PC with 1000+ sources, offline CBZ reading, infinite scroll, and 13 customizable themes.

%install
mkdir -p %{buildroot}/opt/zeravrix
mkdir -p %{buildroot}/usr/bin
mkdir -p %{buildroot}/usr/share/applications
mkdir -p %{buildroot}/usr/share/icons/hicolor/256x256/apps

cp -r app %{buildroot}/opt/zeravrix/
cp zeravrix.sh %{buildroot}/opt/zeravrix/zeravrix
chmod +x %{buildroot}/opt/zeravrix/zeravrix
ln -sf /opt/zeravrix/zeravrix %{buildroot}/usr/bin/zeravrix

install -m 644 zeravrix.desktop %{buildroot}/usr/share/applications/zeravrix.desktop
install -m 644 logo.png %{buildroot}/usr/share/icons/hicolor/256x256/apps/zeravrix.png

%files
/opt/zeravrix
/usr/bin/zeravrix
/usr/share/applications/zeravrix.desktop
/usr/share/icons/hicolor/256x256/apps/zeravrix.png

%changelog
* Mon Nov 10 2026 ZERAVRIX Team <contact@zeravrix.pages.dev> - 1.7.2-1
- Release 1.7.2: strict RAM optimization (<=500MB window preloading), Two-Page mode overhaul, hardware color adjustments (contrast/saturation/brightness), F11 fullscreen, reading history default landing, and Flathub compatibility.
* Sun Nov 08 2026 ZERAVRIX Team <contact@zeravrix.pages.dev> - 1.7.1-1
- Release 1.7.1 with seamless continuous multi-chapter reader, memory cache optimization, and in-app updater
