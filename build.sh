#!/usr/bin/env bash
# Builds the SSTV app into a real executable.
#
#   ./build.sh          -> compiles + builds sstv.jar (run anywhere with: java -jar sstv.jar)
#   ./build.sh package  -> also builds a native, double-clickable app (SSTV.app on macOS,
#                          a SSTV/ folder with a native launcher on Linux) via jpackage,
#                          with no separate Java install required to run it.
#
# Requires a full JDK (17+) on PATH -- i.e. `javac` and `jar` must work.
# The native-app step additionally requires `jpackage` (bundled with JDK 16+).

set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
    echo "error: javac not found on PATH. Install a JDK (17+) and try again." >&2
    echo "       (a JRE is not enough -- you need the full JDK, which includes javac.)" >&2
    exit 1
fi

echo "== Compiling =="
rm -rf out
mkdir out
javac -d out $(find src -name "*.java")

echo "== Building sstv.jar =="
rm -f sstv.jar
jar cfe sstv.jar com.sstv.Main -C out .
echo "Built sstv.jar"
echo "  Run it with: java -jar sstv.jar"

if [[ "${1:-}" == "package" ]]; then
    if ! command -v jpackage >/dev/null 2>&1; then
        echo "error: jpackage not found on PATH. It ships with JDK 16+; update your JDK to use 'package'." >&2
        exit 1
    fi

    echo "== Building native app image with jpackage =="
    rm -rf build/jarinput dist
    mkdir -p build/jarinput
    cp sstv.jar build/jarinput/

    jpackage \
        --type app-image \
        --input build/jarinput \
        --dest dist \
        --name SSTV \
        --main-jar sstv.jar \
        --main-class com.sstv.Main \
        --description "SSTV Encoder/Decoder (Martin M1)" \
        --vendor "Prototype"

    case "$(uname -s)" in
        Darwin) echo "Built dist/SSTV.app -- double-click it, or run: open dist/SSTV.app" ;;
        *)      echo "Built dist/SSTV/ -- run it with: ./dist/SSTV/bin/SSTV" ;;
    esac
    echo "This is a standalone app: it bundles its own Java runtime, so it runs without"
    echo "anyone else needing Java installed."
fi
