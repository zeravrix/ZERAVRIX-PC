#!/bin/bash
# ZERAVRIX Linux Launcher (Fedora / RHEL / Ubuntu / Arch)
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/runtime/bin/java" ]; then
    JAVA_EXEC="$SCRIPT_DIR/runtime/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_EXEC="java"
else
    echo "Error: Java 21+ is required to run ZERAVRIX."
    echo "On Fedora: sudo dnf install java-21-openjdk"
    exit 1
fi

exec "$JAVA_EXEC" \
    -Dskiko.renderApi=OPENGL \
    -Dfile.encoding=UTF-8 \
    -cp "$SCRIPT_DIR/app/*" \
    com.zeravrix.zeravrix.MainKt "$@"
