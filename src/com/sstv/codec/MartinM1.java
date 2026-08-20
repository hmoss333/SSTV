package com.sstv.codec;

/**
 * Timing and frequency constants for the Martin M1 SSTV mode.
 * <p>
 * Martin M1 is one of the most common "classic" analog SSTV modes: 320x256
 * pixels, transmitted as Green/Blue/Red scan lines (in that order), each
 * pixel encoded as a tone between 1500 Hz (black) and 2300 Hz (white).
 * <p>
 * This is the only mode implemented in this prototype. Other modes (Scottie,
 * Robot, PD, ...) mostly just change these numbers and the channel order, so
 * this class is the natural place to add them -- see the README.
 */
public final class MartinM1 {
    private MartinM1() {}

    public static final int SAMPLE_RATE = 44100;
    public static final int WIDTH = 320;
    public static final int HEIGHT = 256;

    /** VIS (Vertical Interval Signaling) code that identifies this mode to a receiver. */
    public static final int VIS_CODE = 44;

    public static final double SYNC_FREQ = 1200.0;
    public static final double SYNC_MS = 4.862;

    public static final double PORCH_FREQ = 1500.0;
    public static final double PORCH_MS = 0.572;

    public static final double SEP_FREQ = 1500.0;
    public static final double SEP_MS = 0.572;

    /** Duration of one full-width color channel scan (320 pixels). */
    public static final double SCAN_MS = 146.432;
    public static final double PIXEL_MS = SCAN_MS / WIDTH;

    public static final double BLACK_FREQ = 1500.0;
    public static final double WHITE_FREQ = 2300.0;

    public static double totalLineMs() {
        return SYNC_MS + PORCH_MS + 3 * (SCAN_MS + SEP_MS);
    }

    public static double freqForValue(int value) {
        double v = Math.max(0, Math.min(255, value));
        return BLACK_FREQ + (v / 255.0) * (WHITE_FREQ - BLACK_FREQ);
    }

    public static int valueForFreq(double freq) {
        double v = (freq - BLACK_FREQ) / (WHITE_FREQ - BLACK_FREQ) * 255.0;
        return (int) Math.round(Math.max(0, Math.min(255, v)));
    }
}
