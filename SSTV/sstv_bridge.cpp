#include "sstv_bridge.h"
#include "sstv_encoder.h"
#include "sstv_decoder.h"
#include "sstv_types.h"

SSTVMode mode = SSTV_MODE_MARTIN_M1;

SSTVAudio* sstv_encode(SSTVImage* image, SSTVMode mode) {
    // Convert C struct → C++ object
    Image img(image->width, image->height);
    std::copy(
        image->data,
        image->data + (image->width * image->height * 3),
        img.data.begin()
    );

    SSTVEncoder encoder;
    Audio audio = encoder.encode(img, SSTVMode(mode));

    // Allocate output
    SSTVAudio* out = new SSTVAudio;
    out->sample_rate = audio.sampleRate;
    out->length = audio.samples.size();
    out->samples = new float[out->length];

    std::copy(audio.samples.begin(), audio.samples.end(), out->samples);

    return out;
}

SSTVImage* sstv_decode(float* samples, int length, int sample_rate) {
    Audio audio(sample_rate);
    audio.samples.assign(samples, samples + length);

    SSTVDecoder decoder;
    Image img = decoder.decode(audio);

    SSTVImage* out = new SSTVImage;
    out->width = img.width;
    out->height = img.height;

    int size = img.width * img.height * 3;
    out->data = new unsigned char[size];

    std::copy(img.data.begin(), img.data.end(), out->data);

    return out;
}

void sstv_free_audio(SSTVAudio* audio) {
    delete[] audio->samples;
    delete audio;
}

void sstv_free_image(SSTVImage* image) {
    delete[] image->data;
    delete image;
}