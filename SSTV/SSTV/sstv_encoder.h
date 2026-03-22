#pragma once
#include "sstv_types.h"

class SSTVEncoder {
public:
    explicit SSTVEncoder(int sampleRate = 44100);

    Audio encode(const Image& image, SSTVMode mode);

private:
    int m_sampleRate;

    // ===== Internal Steps =====
    void writeVISHeader(std::vector<float>& buffer, SSTVMode mode);
    void encodeLine(const Image& image, int y, std::vector<float>& buffer);
    void generateTone(std::vector<float>& buffer, float freq, float durationMs);

    // Utilities
    float pixelToFrequency(uint8_t value);
};