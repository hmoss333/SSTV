package com.sstv.codec;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registry of the SSTV modes this app knows how to encode/decode.
 * <p>
 * Martin and Scottie modes are both 320x256, RGB, one tone per pixel between
 * 1500 Hz (black) and 2300 Hz (white) -- but their row layouts differ:
 * <ul>
 *   <li><b>Martin</b>: sync, porch, then Green/Blue/Red scans (each followed
 *       by a separator). Sync sits at the very start of the row.</li>
 *   <li><b>Scottie</b>: separator, Green, separator, Blue, <i>then</i> sync,
 *       porch, Red. The sync pulse sits between the Blue and Red scans of the
 *       same row, not at the row's start -- a real quirk of how Scottie was
 *       originally designed. Scottie also sends one extra 9ms sync pulse
 *       right after the VIS header (before any pixel data) so a receiver has
 *       something to lock onto before the first row's own mid-row sync.</li>
 * </ul>
 * Timings below follow the commonly published specs for these modes (as used
 * by e.g. pysstv/slowrx). They have not been cross-checked against a real
 * hardware SSTV decoder -- see the README for how this was verified instead.
 */
public final class SSTVModes {
    private SSTVModes() {}

    public static final SSTVMode MARTIN_M1 = martin("Martin M1", 44, 146.432);
    public static final SSTVMode MARTIN_M2 = martin("Martin M2", 40, 73.216);
    public static final SSTVMode SCOTTIE_S1 = scottie("Scottie S1", 60, 138.240);
    public static final SSTVMode SCOTTIE_S2 = scottie("Scottie S2", 56, 88.064);

    public static final List<SSTVMode> ALL =
            Collections.unmodifiableList(Arrays.asList(MARTIN_M1, MARTIN_M2, SCOTTIE_S1, SCOTTIE_S2));

    /** Used when no VIS header can be decoded, or its code doesn't match a known mode. */
    public static final SSTVMode DEFAULT = MARTIN_M1;

    public static SSTVMode byVisCode(int code) {
        for (SSTVMode m : ALL) {
            if (m.visCode == code) return m;
        }
        return null;
    }

    private static SSTVMode martin(String name, int visCode, double scanMs) {
        List<Segment> row = Arrays.asList(
                Segment.sync(4.862),
                Segment.tone(1500.0, 0.572),   // porch
                Segment.channel(1, scanMs),    // Green
                Segment.tone(1500.0, 0.572),   // separator
                Segment.channel(2, scanMs),    // Blue
                Segment.tone(1500.0, 0.572),   // separator
                Segment.channel(0, scanMs),    // Red
                Segment.tone(1500.0, 0.572)    // separator
        );
        return new SSTVMode(name, visCode, 320, 256, row, 0.0);
    }

    private static SSTVMode scottie(String name, int visCode, double scanMs) {
        List<Segment> row = Arrays.asList(
                Segment.tone(1500.0, 1.5),     // separator
                Segment.channel(1, scanMs),    // Green
                Segment.tone(1500.0, 1.5),     // separator
                Segment.channel(2, scanMs),    // Blue
                Segment.sync(9.0),
                Segment.tone(1500.0, 1.5),     // porch
                Segment.channel(0, scanMs)     // Red
        );
        return new SSTVMode(name, visCode, 320, 256, row, 9.0);
    }
}
