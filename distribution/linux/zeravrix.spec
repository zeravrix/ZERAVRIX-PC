Name:           zeravrix
Version:        1.6.0
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
* Sun Nov 08 2026 ZERAVRIX Team <contact@zeravrix.pages.dev> - 1.6.0-1
- Initial release for Fedora Linux
