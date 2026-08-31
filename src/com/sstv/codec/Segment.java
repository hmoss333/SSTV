package com.sstv.codec;

/**
 * One timed element of an SSTV row: the sync pulse, a fixed-frequency pulse
 * (porch/separator), or a pixel-rate scan of one color channel.
 * <p>
 * A mode's whole line is just an ordered list of these (see {@link SSTVMode}),
 * which is what lets the encoder/decoder handle very different line layouts
 * (e.g. Martin's sync-first rows vs. Scottie's sync-in-the-middle rows)
 * with the same code.
 */
public final class Segment {
    public enum Kind { SYNC, TONE, CHANNEL }

    public final Kind kind;
    public final double freq;   // meaningful for SYNC/TONE
    public final int channel;   // meaningful for CHANNEL: 0=R, 1=G, 2=B
    public final double ms;

    private Segment(Kind kind, double freq, int channel, double ms) {
        this.kind = kind;
        this.freq = freq;
        this.channel = channel;
        this.ms = ms;
    }

    /** The sync pulse. Always at {@link SSTVConstants#SYNC_FREQ}; exactly one per row template. */
    public static Segment sync(double ms) {
        return new Segment(Kind.SYNC, SSTVConstants.SYNC_FREQ, -1, ms);
    }

    /** A fixed-frequency pulse that is not the sync pulse (porch / separator). */
    public static Segment tone(double freq, double ms) {
        return new Segment(Kind.TONE, freq, -1, ms);
    }

    /** A full-width scan of one color channel (0=R, 1=G, 2=B). */
    public static Segment channel(int channel, double ms) {
        return new Segment(Kind.CHANNEL, -1, channel, ms);
    }
}
