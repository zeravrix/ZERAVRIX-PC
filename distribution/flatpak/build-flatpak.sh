#!/bin/bash
# Local Flatpak build & test runner
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "=== 1. Building Compose Desktop App Jar Distribution ==="
cd "$ROOT_DIR"
./gradlew :desktop-app:createDistributable

echo "=== 2. Building Flatpak using flatpak-builder ==="
cd "$SCRIPT_DIR"
mkdir -p build-dir repo
flatpak-builder --force-clean --user --install-deps-from=flathub --repo=repo build-dir org.zeravrix.ZERAVRIX.yml

echo "=== 3. Flatpak Build Successful ==="
echo "To test run locally: flatpak run org.zeravrix.ZERAVRIX"
echo "To create single bundle: flatpak build-bundle repo ZERAVRIX.flatpak org.zeravrix.ZERAVRIX"
