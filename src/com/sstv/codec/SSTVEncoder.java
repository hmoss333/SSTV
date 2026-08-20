package com.sstv.codec;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes a {@link BufferedImage} into a Martin M1 SSTV audio waveform.
 * <p>
 * The image is scaled to 320x256 and transmitted as a VIS header (identifying
 * the mode to a receiver) followed by 256 scan lines, each made of a sync
 * pulse and three color-channel scans (Green, Blue, Red) separated by short
 * separator pulses. Every pixel becomes a short tone whose frequency encodes
 * its brightness (1500 Hz = black, 2300 Hz = white).
 */
public final class SSTVEncoder {

    private SSTVEncoder() {}

    public static double[] encode(BufferedImage source) {
        BufferedImage img = scaleTo(source, MartinM1.WIDTH, MartinM1.HEIGHT);
        ToneBuilder tone = new ToneBuilder(MartinM1.SAMPLE_RATE);

        // --- VIS header: leader / break / leader / start bit / 7 data bits + parity / stop bit ---
        tone.add(1900, 300);
        tone.add(1200, 10);
        tone.add(1900, 300);
        tone.add(1200, 30); // start bit
        for (boolean bit : VISCode.encode(MartinM1.VIS_CODE)) {
            tone.add(bit ? 1100 : 1300, 30); // 1 = 1100Hz, 0 = 1300Hz
        }
        tone.add(1200, 30); // stop bit

        // --- Scan lines ---
        for (int y = 0; y < MartinM1.HEIGHT; y++) {
            tone.add(MartinM1.SYNC_FREQ, MartinM1.SYNC_MS);
            tone.add(MartinM1.PORCH_FREQ, MartinM1.PORCH_MS);

            addChannel(tone, img, y, 1); // Green
            tone.add(MartinM1.SEP_FREQ, MartinM1.SEP_MS);
            addChannel(tone, img, y, 2); // Blue
            tone.add(MartinM1.SEP_FREQ, MartinM1.SEP_MS);
            addChannel(tone, img, y, 0); // Red
            tone.add(MartinM1.SEP_FREQ, MartinM1.SEP_MS);
        }

        return tone.build();
    }

    private static void addChannel(ToneBuilder tone, BufferedImage img, int y, int channelIndex) {
        for (int x = 0; x < MartinM1.WIDTH; x++) {
            int rgb = img.getRGB(x, y);
            int value;
            switch (channelIndex) {
                case 0: value = (rgb >> 16) & 0xFF; break; // R
                case 1: value = (rgb >> 8) & 0xFF; break;  // G
                default: value = rgb & 0xFF; break;        // B
            }
            tone.add(MartinM1.freqForValue(value), MartinM1.PIXEL_MS);
        }
    }

    private static BufferedImage scaleTo(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /**
     * Builds a continuous-phase FM signal from a sequence of (frequency, duration)
     * tones. Keeping phase continuous across tone boundaries (rather than resetting
     * it to zero each time) avoids audible/decodable clicks at every pixel edge --
     * exactly like a real SSTV transmitter.
     */
    static final class ToneBuilder {
        private final int sampleRate;
        private final List<double[]> chunks = new ArrayList<>();
        private double phase = 0.0;

        ToneBuilder(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        void add(double freqHz, double durationMs) {
            int n = (int) Math.round(durationMs / 1000.0 * sampleRate);
            double[] chunk = new double[n];
            double inc = 2.0 * Math.PI * freqHz / sampleRate;
            for (int i = 0; i < n; i++) {
                phase = (phase + inc) % (2.0 * Math.PI);
                chunk[i] = Math.sin(phase);
            }
            chunks.add(chunk);
        }

        double[] build() {
            int total = 0;
            for (double[] c : chunks) total += c.length;
            double[] out = new double[total];
            int pos = 0;
            for (double[] c : chunks) {
                System.arraycopy(c, 0, out, pos, c.length);
                pos += c.length;
            }
            return out;
        }
    }
}
