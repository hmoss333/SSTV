#pragma once
#include "sstv_mode.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    int width;
    int height;
    unsigned char* data;
} SSTVImage;

typedef struct {
    int sample_rate;
    int length;
    float* samples;
} SSTVAudio;

SSTVAudio* sstv_encode(
    SSTVImage* image,
    SSTVMode mode
);

SSTVImage* sstv_decode(
    float* samples,
    int length,
    int sample_rate
);

void sstv_free_audio(SSTVAudio* audio);
void sstv_free_image(SSTVImage* image);

#ifdef __cplusplus
}
#endif