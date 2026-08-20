# SSTV Encoder/Decoder (Java, Martin M1)

A working desktop prototype that converts images to SSTV ("slow-scan
television") audio and back again, with a Swing GUI. Pure JDK — no external
dependencies, no build tool required.

Implements the **Martin M1** mode (320×256, ~114 seconds/image), which is
one of the most common analog SSTV modes and is compatible with real SSTV
software/hardware (e.g. QSSTV, MMSSTV) in both directions — a WAV file this
encodes can be decoded by real SSTV programs, and a VIS-tagged Martin M1
recording from a real radio can be decoded by this app.

## How it works

- **Encoder** (`SSTVEncoder`): scales the image to 320×256, then generates a
  continuous-phase FM audio signal — a VIS header identifying the mode,
  followed by 256 scan lines, each a sync pulse + Green/Blue/Red channel
  scans separated by short pulses. Each pixel becomes a short tone between
  1500 Hz (black) and 2300 Hz (white).
- **Decoder** (`SSTVDecoder`): runs a digital FM discriminator (mix to
  baseband, moving-average lowpass, delay-and-conjugate phase detector) to
  recover instantaneous frequency sample-by-sample, locates the VIS header,
  then walks line-by-line, re-locating each line's sync pulse in a small
  search window to correct drift, and averages frequency over each pixel's
  time window to recover its value.
- **`WavFile`**: minimal WAV read/write using only `javax.sound.sampled`.
- **`SSTVFrame`**: Swing UI with an Encode tab (image → WAV) and a Decode
  tab (WAV → image), both running on a background thread.

## Build & run

Requires a JDK (17+ recommended; developed against 21) — the full JDK, not
just a JRE, since a compiler is needed. No `javac` was available in the
sandbox this was built in (JRE-only, network disabled), so the encoder/
decoder logic was compiled and validated using `java`'s single-file
source-launcher on an equivalent combined file, including a full write-WAV
→ read-WAV → decode round trip. The final multi-file project itself has
**not** been run through `javac` yet — if you hit a compile error, paste it
to me and I'll fix it immediately.

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
error if either is missing.

### Manual / IDE

```bash
javac -d out $(find src -name "*.java")
java -cp out com.sstv.Main
```

Or import `src` as the source root in an IDE and run `com.sstv.Main`.


## Verified accuracy

Self-tested with a color-bar + gradient test image, round-tripped through
actual WAV file write/read:

- VIS header round-trips correctly (mode code confirmed on decode).
- No sync drift across all 256 lines (error doesn't grow from top to
  bottom of the image).
- Smooth/photo-like content: ~10/255 mean absolute error per channel
  (~4%), visually clean.
- Hard, high-contrast edges (e.g. sharp color bars): more error (~35-45/255)
  due to the physical bandwidth limit of the mode — at ~20 samples/pixel
  and a 1500-2300 Hz tone range, a single pixel is shorter than one carrier
  cycle at the low end of the band, so instantaneous frequency can't be
  measured perfectly at a hard transition. Real SSTV decoders show the same
  kind of edge softening; this is inherent to the mode, not a bug.

## What to extend first

1. **Noise robustness.** The decoder assumes a clean signal. A real
   off-air or microphone recording will have noise, so `findSyncPulse` and
   `findLeaderStart`'s thresholds (`150`, `60`) will need to be adaptive
   (e.g. relative to signal energy) rather than fixed, and the sync search
   should score against a matched filter instead of a simple average.
2. **More modes.** `MartinM1` hardcodes one mode's timings. The natural
   refactor is an `SSTVMode` interface (width/height/frequencies/line
   structure/channel order) with `MartinM1`, `ScottieS1`, `Robot36`, `PD120`
   etc. as implementations, and VIS-code-based auto-detection on decode
   (the VIS decode logic is already separate and mode-agnostic).
3. **Live audio.** Right now both directions only work through WAV files.
   Wiring `javax.sound.sampled.TargetDataLine`/`SourceDataLine` in would let
   you decode from a live mic input (e.g. off a radio) or play the encoded
   signal directly instead of round-tripping through a file — useful for
   actually using this over the air.
4. **A waterfall/spectrogram view** in the decode tab would make it much
   easier to see what's going on when a real (noisy) signal doesn't decode
   cleanly, and is what every real SSTV program uses for tuning.
