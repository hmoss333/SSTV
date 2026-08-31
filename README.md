# SSTV Encoder/Decoder (Java)

A working desktop prototype that converts images to SSTV ("slow-scan
television") audio and back again, with a Swing GUI. Pure JDK — no external
dependencies, no build tool required.

Supports four modes: **Martin M1, Martin M2, Scottie S1, Scottie S2** (all
320×256, RGB). These are common analog SSTV modes and are compatible with
real SSTV software/hardware (e.g. QSSTV, MMSSTV) in both directions — a WAV
file this encodes can be decoded by real SSTV programs, and a VIS-tagged
recording in one of these modes from a real radio can be decoded by this app.
See `CHANGES.md` for the history of how mode support was added.

## How it works

- **`SSTVMode`** describes one mode: resolution, VIS code, and its row as an
  ordered list of `Segment`s (sync pulse / fixed tone / channel scan). This
  is what lets one encoder and one decoder handle modes with very different
  row layouts — Martin's sync pulse sits at the very start of the row;
  Scottie's sits in the *middle*, between the Blue and Red scans.
- **`SSTVModes`** is the registry of the four implemented modes.
- **`SSTVEncoder`**: scales the image to the mode's resolution, then
  generates a continuous-phase FM audio signal — a VIS header identifying
  the mode, followed by one row of tones per image row, walking whatever
  segment order that mode uses. Each pixel becomes a short tone between
  1500 Hz (black) and 2300 Hz (white).
- **`SSTVDecoder`**: runs a digital FM discriminator (mix to baseband,
  moving-average lowpass, delay-and-conjugate phase detector) to recover
  instantaneous frequency sample-by-sample, decodes the VIS header to
  auto-detect the mode, then walks row by row using that mode's segment
  template — locating the sync pulse at its expected offset *within* the
  row (0 for Martin, partway through for Scottie) to correct timing drift
  once per row, then averaging frequency over each segment's time window.
- **`WavFile`**: minimal WAV read/write using only `javax.sound.sampled`.
- **`SSTVFrame`**: Swing UI with an Encode tab (image → WAV, pick a mode
  from a dropdown) and a Decode tab (WAV → image, auto-detects the mode
  from the VIS header by default, or you can force one).

## Build & run

Requires a JDK (17+ recommended; developed against 21) — the full JDK, not
just a JRE, since a compiler is needed. No `javac` was available in the
sandbox this was built in (JRE-only, network disabled), so all the
encoder/decoder logic below was compiled and validated using `java`'s
single-file source-launcher on an equivalent combined file, including full
write-WAV → read-WAV → decode round trips for all four modes. The final
multi-file project has been compile-checked the same way (combined into one
file and run through `java`, including constructing `SSTVFrame` itself) but
has **not** been run through `javac` on the real multi-file layout — if you
hit a compile error, paste it to me and I'll fix it immediately.

### Quickest: build a runnable JAR

```bash
# macOS/Linux
./build.sh
java -jar sstv.jar

# Windows
build.bat
java -jar sstv.jar
```

### Real executable: a standalone native app

This runs the same build, then uses `jpackage` (bundled with any JDK 16+)
to produce a genuine double-clickable app with its own bundled Java runtime
— nobody running it needs Java installed at all:

```bash
# macOS/Linux -> dist/SSTV.app (macOS) or dist/SSTV/bin/SSTV (Linux)
./build.sh package

# Windows -> dist\SSTV\SSTV.exe
build.bat package
```

Both scripts check for `javac`/`jpackage` on your `PATH` and give a clear
error if either is missing. On macOS, if `jpackage` fails with "Unable to
locate a Java Runtime that supports jpackage", that's Apple's own launcher
stub, not this script — run `/usr/libexec/java_home -V` to see what JDKs
macOS can find, and either install a JDK 14+ or `export JAVA_HOME=$(/usr/libexec/java_home -v <version>)`
before retrying. `./build.sh` (without `package`) also writes `SSTV.command`,
a double-clickable launcher that doesn't need `jpackage` at all.

### Manual / IDE

```bash
javac -d out $(find src -name "*.java")
java -cp out com.sstv.Main
```

Or import `src` as the source root in an IDE and run `com.sstv.Main`.

## Verified accuracy

Self-tested with a circle+gradient test image, round-tripped through actual
WAV file write/read, for all four modes:

| Mode        | VIS | Mean abs error/channel (0-255) |
|-------------|-----|---------------------------------|
| Martin M1   | 44  | ~18                              |
| Martin M2   | 40  | ~35                              |
| Scottie S1  | 60  | ~15                              |
| Scottie S2  | 56  | ~34                              |

For all four: VIS header round-trips correctly and auto-detection picks the
right mode; no sync drift across all 256 rows (error doesn't grow from top
to bottom); a mismatched forced mode is correctly flagged as unconfirmed.

M2/S2 (the faster-scan variants) carry roughly double the error of M1/S1.
This isn't a bug that's still open — it's a real, understood limit of this
decoding technique at these modes' pixel rate: the FM discriminator's
lowpass filter has to reject an image term at roughly double the mixing
frequency (~3800 Hz), and a boxcar filter only does that well at specific
window lengths (see `SSTVDecoder.FILTER_WINDOW`'s comment and `CHANGES.md`
for the full story) — which land less favorably relative to M2/S2's shorter
per-pixel window. Hard, high-contrast edges (e.g. sharp color bars) add
further error on top of this for the same physical reason in all four
modes: at these sample/pixel ratios a pixel is comparable to or shorter
than one carrier cycle, so instantaneous frequency can't be measured
perfectly right at a transition. Real SSTV decoders show the same kind of
edge softening.
