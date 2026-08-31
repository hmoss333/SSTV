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

# Always produce a double-clickable launcher too, no jpackage required.
# On macOS, a .command file opens in Finder like an app (Terminal runs it).
# On Linux desktops, a chmod+x .sh with a matching .desktop file is the
# equivalent, but most Linux users are comfortable with a shell script directly.
cat > SSTV.command << 'LAUNCHER'
#!/usr/bin/env bash
cd "$(dirname "$0")"
exec java -jar sstv.jar
LAUNCHER
chmod +x SSTV.command
echo "Also wrote SSTV.command -- double-click it in Finder to run (macOS may ask you to"
echo "right-click > Open the first time, since it isn't signed)."

if [[ "${1:-}" == "package" ]]; then
    if ! command -v jpackage >/dev/null 2>&1; then
        echo "error: jpackage not found on PATH. It ships with JDK 16+; update your JDK to use 'package'." >&2
        exit 1
    fi
    if ! jpackage --version >/dev/null 2>&1; then
        cat >&2 << 'DIAG'
error: jpackage exists on PATH but couldn't run.

On macOS this is Apple's own Java launcher stub talking, not this script:
/usr/bin/jpackage always exists, but it defers to `java_home` to find a real
JDK 14+ behind it, and none was found. This usually means:
  - only a JRE is installed (jpackage needs a full JDK), or
  - the JDK is older than 14, or
  - a Homebrew-installed JDK was never registered with java_home.

To diagnose:
  /usr/libexec/java_home -V          # lists every JDK macOS can see

If a suitable JDK (14+) is listed but isn't the default, point at it directly:
  export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # match a version from the list above
  ./build.sh package

If nothing 14+ is listed, install one (e.g. https://adoptium.net, or
`brew install openjdk@21` followed by:
  sudo ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" \
      /Library/Java/JavaVirtualMachines/openjdk-21.jdk
then re-run `/usr/libexec/java_home -V` to confirm it shows up).

In the meantime, sstv.jar and SSTV.command above already work fine without
jpackage -- you only need this step for a fully bundled, Java-free app.
DIAG
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
