package com.sstv.codec;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes a {@link BufferedImage} into an SSTV audio waveform for a given
 * {@link SSTVMode}. Scales the image to the mode's resolution, emits a VIS
 * header identifying the mode, then walks each row's segment template
 * (sync/tone/channel, in whatever order that mode uses) to build the signal.
 */
public final class SSTVEncoder {

    private SSTVEncoder() {}

    public static double[] encode(BufferedImage source, SSTVMode mode) {
        BufferedImage img = scaleTo(source, mode.width, mode.height);
        ToneBuilder tone = new ToneBuilder(SSTVConstants.SAMPLE_RATE);

        // --- VIS header: leader / break / leader / start bit / 7 data bits + parity / stop bit ---
        tone.add(SSTVConstants.VIS_LEADER_FREQ, SSTVConstants.VIS_LEADER_MS);
        tone.add(SSTVConstants.VIS_BREAK_FREQ, SSTVConstants.VIS_BREAK_MS);
        tone.add(SSTVConstants.VIS_LEADER_FREQ, SSTVConstants.VIS_LEADER_MS);
        tone.add(SSTVConstants.VIS_BREAK_FREQ, SSTVConstants.VIS_BIT_MS); // start bit
        for (boolean bit : VISCode.encode(mode.visCode)) {
            tone.add(bit ? SSTVConstants.VIS_BIT_ONE_FREQ : SSTVConstants.VIS_BIT_ZERO_FREQ, SSTVConstants.VIS_BIT_MS);
        }
        tone.add(SSTVConstants.VIS_BREAK_FREQ, SSTVConstants.VIS_BIT_MS); // stop bit

        // Some modes (Scottie) send an extra standalone sync pulse here so a
        // receiver can lock before any row data starts.
        if (mode.leadingSyncMs > 0) {
            tone.add(SSTVConstants.SYNC_FREQ, mode.leadingSyncMs);
        }

        // --- Rows ---
        for (int y = 0; y < mode.height; y++) {
            for (Segment seg : mode.rowTemplate) {
                switch (seg.kind) {
                    case SYNC:
                        tone.add(SSTVConstants.SYNC_FREQ, seg.ms);
                        break;
                    case TONE:
                        tone.add(seg.freq, seg.ms);
                        break;
                    case CHANNEL:
                        addChannel(tone, img, y, seg.channel, seg.ms, mode.width);
                        break;
                }
            }
        }

        return tone.build();
    }

    private static void addChannel(ToneBuilder tone, BufferedImage img, int y, int channelIndex,
                                    double scanMs, int width) {
        double pixelMs = scanMs / width;
        for (int x = 0; x < width; x++) {
            int rgb = img.getRGB(x, y);
            int value;
            switch (channelIndex) {
                case 0: value = (rgb >> 16) & 0xFF; break; // R
                case 1: value = (rgb >> 8) & 0xFF; break;  // G
                default: value = rgb & 0xFF; break;        // B
            }
            tone.add(SSTVConstants.freqForValue(value), pixelMs);
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
