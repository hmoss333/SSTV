package com.sstv.codec;

import java.awt.image.BufferedImage;

/**
 * Decodes an SSTV audio waveform back into an image.
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
 *   <li>Decode the VIS header (if present) to auto-detect the mode, then walk
 *       row by row using that mode's segment template. Every mode's sync
 *       pulse sits somewhere in the row template (start, for Martin; between
 *       the Blue and Red scans, for Scottie) -- the decoder locates it at its
 *       expected offset within each row, then reads every segment (channel
 *       scans included) relative to that corrected anchor, which corrects for
 *       any timing drift once per row regardless of where in the row the
 *       sync happens to sit.</li>
 * </ol>
 * This works well on clean signals (anything encoded by {@link SSTVEncoder},
 * or a clean recording). It has no special noise handling -- see the README
 * for what a more robust version would add.
 */
public final class SSTVDecoder {

    private SSTVDecoder() {}

    public static final class Result {
        public final BufferedImage image;
        public final SSTVMode mode;
        /** True if a VIS header was found and decoded to a recognized mode. */
        public final boolean visConfirmed;

        Result(BufferedImage image, SSTVMode mode, boolean visConfirmed) {
            this.image = image;
            this.mode = mode;
            this.visConfirmed = visConfirmed;
        }
    }

    /** Decodes using VIS-header auto-detection, falling back to {@link SSTVModes#DEFAULT}. */
    public static Result decode(double[] samples, double sampleRate) {
        return decode(samples, sampleRate, null);
    }

    /**
     * Decodes as {@code forcedMode} if given (ignoring the VIS header for mode
     * selection, though it's still checked to report {@code visConfirmed}); if
     * {@code forcedMode} is null, the mode is auto-detected from the VIS header,
     * falling back to {@link SSTVModes#DEFAULT} if none is found.
     */
    public static Result decode(double[] samples, double sampleRate, SSTVMode forcedMode) {
        Discriminator disc = new Discriminator(samples, sampleRate, 1900.0, FILTER_WINDOW);
        double[] freq = disc.freq;

        int visStart = findLeaderStart(freq, sampleRate);
        int visCodeDecoded = -1;
        int headerEnd = 0;
        if (visStart >= 0) {
            long[] header = decodeVisHeader(freq, sampleRate, visStart);
            visCodeDecoded = (int) header[0];
            headerEnd = (int) header[1];
        }

        SSTVMode detected = visCodeDecoded >= 0 ? SSTVModes.byVisCode(visCodeDecoded) : null;
        SSTVMode mode = forcedMode != null ? forcedMode : (detected != null ? detected : SSTVModes.DEFAULT);
        boolean visConfirmed = detected != null && detected == mode;

        int pos = headerEnd;
        if (mode.leadingSyncMs > 0) {
            int leadWindow = samplesFor(30, sampleRate);
            int leadPos = findPulse(freq, sampleRate, pos, leadWindow, SSTVConstants.SYNC_FREQ,
                    Math.max(4, samplesFor(mode.leadingSyncMs, sampleRate) / 3));
            if (leadPos >= 0) {
                pos = leadPos + samplesFor(mode.leadingSyncMs, sampleRate);
            }
            // If not found, just proceed from headerEnd -- the per-row loop below will
            // still try to find each row's own sync pulse.
        }

        BufferedImage img = new BufferedImage(mode.width, mode.height, BufferedImage.TYPE_INT_RGB);
        int rowSamplesNominal = samplesFor(mode.rowDurationMs(), sampleRate);
        int syncOffsetSamples = samplesFor(mode.syncOffsetMs(), sampleRate);
        int syncProbeLen = Math.max(4, samplesFor(mode.syncMs(), sampleRate) / 3);
        int searchWindow = samplesFor(15.0, sampleRate); // +/- 15ms drift tolerance per row

        int expectedSyncPos = pos + syncOffsetSamples;
        for (int y = 0; y < mode.height; y++) {
            int syncPos = findPulse(freq, sampleRate, expectedSyncPos, searchWindow, SSTVConstants.SYNC_FREQ, syncProbeLen);
            int rowStart = (syncPos >= 0 ? syncPos : expectedSyncPos) - syncOffsetSamples;

            decodeRow(freq, sampleRate, mode, rowStart, disc.filterDelay, img, y);

            expectedSyncPos = rowStart + rowSamplesNominal + syncOffsetSamples;
        }

        return new Result(img, mode, visConfirmed);
    }

    /**
     * Width (in samples) of the moving-average lowpass used by the FM discriminator.
     * <p>
     * This is deliberately a single fixed value shared by every mode, not scaled to
     * each mode's samples-per-pixel. A moving-average (boxcar) filter only rejects
     * the discriminator's 2*fc (~3800 Hz) image term at specific window lengths --
     * roughly {@code sampleRate/3800} -- regardless of pixel width; empirically,
     * window sizes tuned down to match a faster mode's shorter pixel (e.g. Martin
     * M2 or Scottie S2, at roughly half Martin M1's samples/pixel) land far from
     * that null and let the ripple through, corrupting the signal far worse than
     * the extra inter-pixel smearing a "too-wide" fixed window causes. 12 was found
     * by sweeping window sizes against all four modes together (see README).
     */
    private static final int FILTER_WINDOW = 12;

    private static void decodeRow(double[] freq, double sampleRate, SSTVMode mode, int rowStart,
                                   double filterDelay, BufferedImage img, int y) {
        int[] r = null, g = null, b = null;
        int p = rowStart;
        for (Segment seg : mode.rowTemplate) {
            if (seg.kind == Segment.Kind.CHANNEL) {
                int[] values = readChannel(freq, sampleRate, p, filterDelay, mode.width, seg.ms);
                if (seg.channel == 0) r = values;
                else if (seg.channel == 1) g = values;
                else b = values;
            }
            p += samplesFor(seg.ms, sampleRate);
        }
        for (int x = 0; x < mode.width; x++) {
            int rv = r != null ? r[x] : 0;
            int gv = g != null ? g[x] : 0;
            int bv = b != null ? b[x] : 0;
            img.setRGB(x, y, (rv << 16) | (gv << 8) | bv);
        }
    }

    private static int samplesFor(double ms, double sampleRate) {
        return (int) Math.round(ms / 1000.0 * sampleRate);
    }

    /**
     * Averages instantaneous frequency across one pixel's time window, trimming 25%
     * off each edge to avoid the transition between neighboring pixels, and shifting
     * the window forward by the filter's group delay to stay time-aligned.
     */
    private static int[] readChannel(double[] freq, double sampleRate, int start, double filterDelay,
                                      int width, double scanMs) {
        int[] values = new int[width];
        double pixelSamplesD = (scanMs / width) / 1000.0 * sampleRate;
        for (int x = 0; x < width; x++) {
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
            double avg = count > 0 ? sum / count : SSTVConstants.BLACK_FREQ;
            values[x] = SSTVConstants.valueForFreq(avg);
        }
        return values;
    }

    /** Looks for a sustained pulse at {@code targetFreq} within [center-window, center+window]. */
    private static int findPulse(double[] freq, double sampleRate, int center, int window,
                                  double targetFreq, int probe) {
        int lo = Math.max(0, center - window);
        int hi = Math.min(freq.length - probe, center + window);
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = lo; i <= hi; i++) {
            double sum = 0;
            for (int k = 0; k < probe; k++) sum += freq[i + k];
            double avg = sum / probe;
            double score = Math.abs(avg - targetFreq);
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
            if (Math.abs(avg - SSTVConstants.VIS_LEADER_FREQ) < 60) {
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
        int p = leaderStart + samplesFor(SSTVConstants.VIS_LEADER_MS, sampleRate); // leader 1
        p += samplesFor(SSTVConstants.VIS_BREAK_MS, sampleRate);                   // break
        p += samplesFor(SSTVConstants.VIS_LEADER_MS, sampleRate);                  // leader 2
        p += samplesFor(SSTVConstants.VIS_BIT_MS, sampleRate);                     // start bit

        boolean[] bits = new boolean[8];
        int bitLen = samplesFor(SSTVConstants.VIS_BIT_MS, sampleRate);
        for (int i = 0; i < 8; i++) {
            int a = p + i * bitLen;
            int b = Math.min(freq.length, a + bitLen);
            double sum = 0;
            int count = 0;
            for (int k = a; k < b; k++) {
                sum += freq[k];
                count++;
            }
            double avg = count > 0 ? sum / count : SSTVConstants.VIS_BIT_ZERO_FREQ;
            bits[i] = avg < 1200; // closer to 1100Hz (=1) than 1300Hz (=0)
        }
        int code = VISCode.decode(bits);
        int end = p + 8 * bitLen + samplesFor(SSTVConstants.VIS_BIT_MS, sampleRate); // + stop bit
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
