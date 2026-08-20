package com.sstv.codec;

import java.awt.image.BufferedImage;

/**
 * Decodes a Martin M1 SSTV audio waveform back into an image.
 * <p>
 * Approach (standard technique for analog FM-style signals like SSTV):
 * <ol>
 *   <li>Mix the signal down to baseband around a center frequency (1900 Hz,
 *       the midpoint of the 1500-2300 Hz pixel band) to get I/Q components.</li>
 *   <li>Lowpass-filter I/Q with a short moving average. A moving average
 *       (FIR, linear phase) is used instead of a simple exponential/IIR
 *       filter because at ~20 samples per pixel an IIR filter's overshoot
 *       and slow settling smear adjacent pixels together badly -- a boxcar
 *       filter settles cleanly within one window.</li>
 *   <li>Estimate instantaneous frequency sample-to-sample via the
 *       delay-and-conjugate method (a standard digital FM discriminator).</li>
 *   <li>Locate the VIS header (if present) to confirm the mode, then walk
 *       line by line, re-locating each line's sync pulse within a small
 *       search window to correct for any timing drift.</li>
 * </ol>
 * This works well on clean signals (anything encoded by {@link SSTVEncoder},
 * or a clean recording). It has no special noise handling -- see the README
 * for what a more robust version would add.
 */
public final class SSTVDecoder {

    private SSTVDecoder() {}

    public static final class Result {
        public final BufferedImage image;
        /** Decoded VIS mode code, or -1 if no VIS header could be confidently located. */
        public final int visCode;

        Result(BufferedImage image, int visCode) {
            this.image = image;
            this.visCode = visCode;
        }
    }

    /** Width (in samples) of the moving-average lowpass used by the FM discriminator. */
    private static final int FILTER_WINDOW = 12;

    public static Result decode(double[] samples, double sampleRate) {
        Discriminator disc = new Discriminator(samples, sampleRate, 1900.0, FILTER_WINDOW);
        double[] freq = disc.freq;

        int visStart = findLeaderStart(freq, sampleRate);
        int visCode = -1;
        int cursor;
        if (visStart >= 0) {
            long[] header = decodeVisHeader(freq, sampleRate, visStart);
            visCode = (int) header[0];
            cursor = (int) header[1];
        } else {
            // No clean leader tone found (e.g. a clip with the header trimmed off) --
            // just assume the audio starts right at the first sync pulse.
            cursor = 0;
        }

        BufferedImage img = new BufferedImage(MartinM1.WIDTH, MartinM1.HEIGHT, BufferedImage.TYPE_INT_RGB);
        int lineSamplesNominal = samplesFor(MartinM1.totalLineMs(), sampleRate);
        int searchWindow = samplesFor(15.0, sampleRate); // +/- 15ms drift tolerance per line

        int pos = cursor;
        for (int y = 0; y < MartinM1.HEIGHT; y++) {
            int syncPos = findSyncPulse(freq, sampleRate, pos, searchWindow);
            if (syncPos < 0) syncPos = pos; // couldn't confirm a pulse; keep marching forward

            int p = syncPos + samplesFor(MartinM1.SYNC_MS, sampleRate) + samplesFor(MartinM1.PORCH_MS, sampleRate);

            int[] green = readChannel(freq, sampleRate, p, disc.filterDelay);
            p += samplesFor(MartinM1.SCAN_MS, sampleRate) + samplesFor(MartinM1.SEP_MS, sampleRate);

            int[] blue = readChannel(freq, sampleRate, p, disc.filterDelay);
            p += samplesFor(MartinM1.SCAN_MS, sampleRate) + samplesFor(MartinM1.SEP_MS, sampleRate);

            int[] red = readChannel(freq, sampleRate, p, disc.filterDelay);
            p += samplesFor(MartinM1.SCAN_MS, sampleRate) + samplesFor(MartinM1.SEP_MS, sampleRate);

            for (int x = 0; x < MartinM1.WIDTH; x++) {
                int rgb = (red[x] << 16) | (green[x] << 8) | blue[x];
                img.setRGB(x, y, rgb);
            }

            pos = syncPos + lineSamplesNominal;
        }

        return new Result(img, visCode);
    }

    private static int samplesFor(double ms, double sampleRate) {
        return (int) Math.round(ms / 1000.0 * sampleRate);
    }

    /**
     * Averages instantaneous frequency across one pixel's time window, trimming 25%
     * off each edge to avoid the transition between neighboring pixels, and shifting
     * the window forward by the filter's group delay to stay time-aligned.
     */
    private static int[] readChannel(double[] freq, double sampleRate, int start, double filterDelay) {
        int[] values = new int[MartinM1.WIDTH];
        double pixelSamplesD = MartinM1.PIXEL_MS / 1000.0 * sampleRate;
        for (int x = 0; x < MartinM1.WIDTH; x++) {
            double a = start + x * pixelSamplesD + filterDelay;
            double b = start + (x + 1) * pixelSamplesD + filterDelay;
            int lo0 = (int) Math.round(a);
            int hi0 = (int) Math.round(b);
            int margin = Math.max(1, (hi0 - lo0) / 4);
            int lo = Math.max(0, lo0 + margin);
            int hi = Math.min(freq.length, hi0 - margin);
            if (hi <= lo) {
                lo = Math.max(0, lo0);
                hi = Math.min(freq.length, hi0);
            }
            double sum = 0;
            int count = 0;
            for (int i = lo; i < hi; i++) {
                sum += freq[i];
                count++;
            }
            double avg = count > 0 ? sum / count : MartinM1.BLACK_FREQ;
            values[x] = MartinM1.valueForFreq(avg);
        }
        return values;
    }

    /** Looks for a sustained ~1200 Hz pulse within [center-window, center+window]. */
    private static int findSyncPulse(double[] freq, double sampleRate, int center, int window) {
        int syncLen = samplesFor(MartinM1.SYNC_MS, sampleRate);
        int probe = Math.max(4, syncLen / 3);
        int lo = Math.max(0, center - window);
        int hi = Math.min(freq.length - probe, center + window);
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = lo; i <= hi; i++) {
            double sum = 0;
            for (int k = 0; k < probe; k++) sum += freq[i + k];
            double avg = sum / probe;
            double score = Math.abs(avg - MartinM1.SYNC_FREQ);
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return bestScore < 150 ? best : -1; // reject if nothing looked like a real pulse
    }

    /** Scans forward for a long, stable run near 1900 Hz -- the VIS leader tone. */
    private static int findLeaderStart(double[] freq, double sampleRate) {
        int winLen = samplesFor(40, sampleRate);
        int needRun = samplesFor(200, sampleRate); // require ~200ms of stability
        int step = Math.max(1, winLen / 4);
        int consecutive = 0;
        for (int i = 0; i + winLen < freq.length; i += step) {
            double sum = 0;
            for (int k = 0; k < winLen; k++) sum += freq[i + k];
            double avg = sum / winLen;
            if (Math.abs(avg - 1900.0) < 60) {
                consecutive += step;
                if (consecutive >= needRun) return Math.max(0, i - consecutive + step);
            } else {
                consecutive = 0;
            }
        }
        return -1;
    }

    /** @return {@code [decodedVisCode (or -1), sampleIndexJustAfterHeader]} */
    private static long[] decodeVisHeader(double[] freq, double sampleRate, int leaderStart) {
        int p = leaderStart + samplesFor(300, sampleRate); // leader 1
        p += samplesFor(10, sampleRate);                   // break
        p += samplesFor(300, sampleRate);                  // leader 2
        p += samplesFor(30, sampleRate);                   // start bit

        boolean[] bits = new boolean[8];
        int bitLen = samplesFor(30, sampleRate);
        for (int i = 0; i < 8; i++) {
            int a = p + i * bitLen;
            int b = Math.min(freq.length, a + bitLen);
            double sum = 0;
            int count = 0;
            for (int k = a; k < b; k++) {
                sum += freq[k];
                count++;
            }
            double avg = count > 0 ? sum / count : 1300;
            bits[i] = avg < 1200; // closer to 1100Hz (=1) than 1300Hz (=0)
        }
        int code = VISCode.decode(bits);
        int end = p + 8 * bitLen + samplesFor(30, sampleRate); // + stop bit
        return new long[]{code, end};
    }

    /** FM discriminator producing an instantaneous-frequency array from raw samples. */
    private static final class Discriminator {
        final double[] freq;
        final double filterDelay;

        Discriminator(double[] samples, double sampleRate, double fc, int win) {
            int n = samples.length;
            double[] iRaw = new double[n];
            double[] qRaw = new double[n];
            double w = 2.0 * Math.PI * fc / sampleRate;
            double phase = 0;
            for (int i = 0; i < n; i++) {
                double c = Math.cos(phase);
                double s = Math.sin(phase);
                iRaw[i] = samples[i] * c;
                qRaw[i] = -samples[i] * s;
                phase += w;
                if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
            }
            double[] I = movingAverage(iRaw, win);
            double[] Q = movingAverage(qRaw, win);
            this.filterDelay = (win - 1) / 2.0;

            double[] f = new double[n];
            double scale = sampleRate / (2.0 * Math.PI);
            for (int i = 1; i < n; i++) {
                double re = I[i] * I[i - 1] + Q[i] * Q[i - 1];
                double im = Q[i] * I[i - 1] - I[i] * Q[i - 1];
                f[i] = fc + Math.atan2(im, re) * scale;
            }
            f[0] = fc;
            this.freq = f;
        }

        private static double[] movingAverage(double[] x, int win) {
            int n = x.length;
            double[] out = new double[n];
            double sum = 0;
            for (int i = 0; i < n; i++) {
                sum += x[i];
                if (i >= win) sum -= x[i - win];
                out[i] = sum / Math.min(i + 1, win);
            }
            return out;
        }
    }
}
