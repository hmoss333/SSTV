#pragma once
#include <vector>
#include "sstv_mode.h"

struct Image {
    int width;
    int height;
    std::vector<uint8_t> data;

    Image(int w, int h)
        : width(w), height(h), data(w * h * 3) {}
};

struct Audio {
    int sampleRate;
    std::vector<float> samples;

    Audio(int sr)
        : sampleRate(sr) {}
};