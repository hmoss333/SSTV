package com.sstv.codec;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/** Minimal mono 16-bit PCM WAV read/write, using only the JDK's built-in audio support. */
public final class WavFile {
    private WavFile() {}

    /** Decoded audio: samples normalized to [-1, 1], plus the file's actual sample rate. */
    public static final class WavData {
        public final double[] samples;
        public final float sampleRate;

        public WavData(double[] samples, float sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    public static void write(File file, double[] samples, int sampleRate) throws IOException {
        byte[] bytes = new byte[samples.length * 2];
        int idx = 0;
        for (double s : samples) {
            double clamped = Math.max(-1.0, Math.min(1.0, s));
            short pcm = (short) Math.round(clamped * 32767.0);
            bytes[idx++] = (byte) (pcm & 0xFF);
            bytes[idx++] = (byte) ((pcm >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(bytes), format, samples.length)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);
        }
    }

    /**
     * Reads any WAV the JDK can decode (mono or stereo, most common bit depths) and
     * returns it as normalized mono samples. Stereo is downmixed by averaging channels.
     */
    public static WavData read(File file) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat baseFormat = in.getFormat();
            int channels = baseFormat.getChannels();

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    channels,
                    channels * 2,
                    baseFormat.getSampleRate(),
                    false);

            AudioInputStream pcmStream = AudioSystem.isConversionSupported(targetFormat, baseFormat)
                    ? AudioSystem.getAudioInputStream(targetFormat, in)
                    : in;
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = pcmStream.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
                byte[] data = buffer.toByteArray();

                int frameCount = data.length / (2 * channels);
                double[] samples = new double[frameCount];
                int idx = 0;
                for (int i = 0; i < frameCount; i++) {
                    int sum = 0;
                    for (int c = 0; c < channels; c++) {
                        short pcm = (short) ((data[idx] & 0xFF) | (data[idx + 1] << 8));
                        idx += 2;
                        sum += pcm;
                    }
                    samples[i] = (sum / (double) channels) / 32768.0;
                }
                return new WavData(samples, targetFormat.getSampleRate());
            } finally {
                if (pcmStream != in) pcmStream.close();
            }
        }
    }
}
