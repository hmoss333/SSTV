package com.sstv.codec;

/**
 * Encodes/decodes the 8-bit VIS (Vertical Interval Signaling) payload that
 * identifies an SSTV mode: 7 data bits (LSB first) + 1 even-parity bit.
 * <p>
 * The surrounding leader tones / start / stop bits are handled by
 * {@link SSTVEncoder} and {@link SSTVDecoder}; this class only deals with
 * the mode-code byte itself, so it can be reused for any future mode.
 */
public final class VISCode {
    private VISCode() {}

    /** Returns 8 bits: 7 data bits (LSB first) followed by an even-parity bit. */
    public static boolean[] encode(int value) {
        boolean[] bits = new boolean[8];
        int ones = 0;
        for (int i = 0; i < 7; i++) {
            boolean b = ((value >> i) & 1) == 1;
            bits[i] = b;
            if (b) ones++;
        }
        bits[7] = (ones % 2) != 0; // parity bit makes total number of 1 bits even
        return bits;
    }

    /**
     * Decodes 8 bits (7 data bits LSB first + parity).
     * @return the mode value, or -1 if the parity check fails.
     */
    public static int decode(boolean[] bits8) {
        int value = 0;
        int ones = 0;
        for (int i = 0; i < 7; i++) {
            if (bits8[i]) {
                value |= (1 << i);
                ones++;
            }
        }
        boolean parity = bits8[7];
        boolean expectedParity = (ones % 2) != 0;
        return parity == expectedParity ? value : -1;
    }
}
