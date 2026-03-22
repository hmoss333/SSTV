#include "sstv_encoder.h"
#include <cmath>

SSTVEncoder::SSTVEncoder(int sampleRate)
    : m_sampleRate(sampleRate) {}

Audio SSTVEncoder::encode(const Image& image, SSTVMode mode) {
    Audio audio(m_sampleRate);

    // 1. VIS header (mode signaling)
    writeVISHeader(audio.samples, mode);

    // 2. Encode each line
    for (int y = 0; y < image.height; y++) {
        encodeLine(image, y, audio.samples);
    }

    return audio;
}

void SSTVEncoder::writeVISHeader(std::vector<float>& buffer, SSTVMode mode) {
    // TODO: Implement VIS encoding
}

void SSTVEncoder::encodeLine(const Image& image, int y, std::vector<float>& buffer) {
    for (int x = 0; x < image.width; x++) {
        int idx = (y * image.width + x) * 3;

        uint8_t r = image.data[idx];
        uint8_t g = image.data[idx + 1];
        uint8_t b = image.data[idx + 2];

        float freq = pixelToFrequency(r); // Simplified

        generateTone(buffer, freq, 0.5f); // duration placeholder
    }
}

void SSTVEncoder::generateTone(std::vector<float>& buffer, float freq, float durationMs) {
    int sampleCount = static_cast<int>((durationMs / 1000.0f) * m_sampleRate);

    for (int i = 0; i < sampleCount; i++) {
        float t = static_cast<float>(i) / m_sampleRate;
        float sample = sin(2.0f * M_PI * freq * t);
        buffer.push_back(sample);
    }
}

float SSTVEncoder::pixelToFrequency(uint8_t value) {
    // Map 0–255 → 1500–2300 Hz (typical SSTV range)
    return 1500.0f + (value / 255.0f) * 800.0f;
}