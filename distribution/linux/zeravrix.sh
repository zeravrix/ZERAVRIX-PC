#!/bin/bash
# ZERAVRIX Linux Launcher (Fedora / RHEL / Ubuntu / Arch)
# Tuned for high-performance, low-RAM usage with G1GC and hardware acceleration
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/runtime/bin/java" ]; then
    JAVA_EXEC="$SCRIPT_DIR/runtime/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_EXEC="java"
else
    echo "Error: Java 21+ is required to run ZERAVRIX."
    echo "On Fedora: sudo dnf install java-21-openjdk"
    echo "On Ubuntu: sudo apt install openjdk-21-jre"
    exit 1
fi

# Optimized Linux runtime flags:
# - Capped 768MB max heap to prevent memory bloat
# - G1GC with aggressive string deduplication for webtoon image byte arrays
# - Hardware accelerated OpenGL rendering via Skiko
# - Smooth Linux system font antialiasing
exec "$JAVA_EXEC" \
    -Xms128m \
    -Xmx768m \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:MaxGCPauseMillis=20 \
    -Dskiko.renderApi=OPENGL \
    -Dskiko.linux.autodetect=true \
    -Dawt.useSystemAAFontSettings=on \
    -Dswing.aatext=true \
    -Dfile.encoding=UTF-8 \
    -cp "$SCRIPT_DIR/app/*" \
    com.zeravrix.zeravrix.MainKt "$@"
