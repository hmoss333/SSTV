package com.sstv.codec;

/** Constants shared by every SSTV mode implemented here. */
public final class SSTVConstants {
    private SSTVConstants() {}

    public static final int SAMPLE_RATE = 44100;

    public static final double SYNC_FREQ = 1200.0;
    public static final double BLACK_FREQ = 1500.0;
    public static final double WHITE_FREQ = 2300.0;

    // VIS header tones/timing -- identical across modes; only the 7-bit code differs.
    public static final double VIS_LEADER_FREQ = 1900.0;
    public static final double VIS_LEADER_MS = 300.0;
    public static final double VIS_BREAK_FREQ = 1200.0;
    public static final double VIS_BREAK_MS = 10.0;
    public static final double VIS_BIT_MS = 30.0;
    public static final double VIS_BIT_ZERO_FREQ = 1300.0;
    public static final double VIS_BIT_ONE_FREQ = 1100.0;

    public static double freqForValue(int value) {
        double v = Math.max(0, Math.min(255, value));
        return BLACK_FREQ + (v / 255.0) * (WHITE_FREQ - BLACK_FREQ);
    }

    public static int valueForFreq(double freq) {
        double v = (freq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255.0;
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }
}
