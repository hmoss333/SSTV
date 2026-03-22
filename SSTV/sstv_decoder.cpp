#include "sstv_decoder.h"

Image SSTVDecoder::decode(const Audio& audio) {
    SSTVMode mode = detectMode(audio);
    //SSTVMode mode = SSTV_MODE_MARTIN_M1;

    // Placeholder resolution (depends on mode)
    Image image(320, 256);

    decodeLines(audio, image);

    return image;
}

SSTVMode SSTVDecoder::detectMode(const Audio& audio) {
    // TODO: Implement VIS detection
    return SSTVMode::SSTV_MODE_MARTIN_M1;
}

void SSTVDecoder::decodeLines(const Audio& audio, Image& image) {
    // TODO: Implement line reconstruction
}

float SSTVDecoder::detectFrequency(const float* samples, int count) {
    // TODO: FFT or zero-crossing
    return 0.0f;
}