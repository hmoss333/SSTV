#pragma once
#include "sstv_types.h"

class SSTVDecoder {
public:
    Image decode(const Audio& audio);

private:
    // ===== Internal Steps =====
    SSTVMode detectMode(const Audio& audio);
    void decodeLines(const Audio& audio, Image& image);

    // DSP helpers
    float detectFrequency(const float* samples, int count);
};