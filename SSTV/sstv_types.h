#pragma once
#include <vector>

enum class SSTVMode {
    MartinM1 = 0,
    ScottieS1 = 1,
    PD90 = 2
};

struct Image {
    int width;
    int height;
    std::vector<uint8_t> data; // RGB (w * h * 3)

    Image(int w, int h)
        : width(w), height(h), data(w * h * 3) {}
};

struct Audio {
    int sampleRate;
    std::vector<float> samples;

    Audio(int sr)
        : sampleRate(sr) {}
};