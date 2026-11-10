#!/bin/bash
# Local Flatpak build & test runner
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Building Flatpak using flatpak-builder ==="
cd "$SCRIPT_DIR"
mkdir -p build-dir repo
flatpak-builder --force-clean --user --install-deps-from=flathub --repo=repo build-dir io.github.zeravrix.ZERAVRIX.yml

echo "=== Flatpak Build Successful ==="
echo "To test run locally: flatpak-builder --run build-dir io.github.zeravrix.ZERAVRIX.yml zeravrix"
echo "To create single bundle: flatpak build-bundle repo ZERAVRIX.flatpak io.github.zeravrix.ZERAVRIX"
