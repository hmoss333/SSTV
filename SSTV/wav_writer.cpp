#include "wav_writer.h"
#include <fstream>
#include <cstdint>
#include <algorithm>

bool WavWriter::writeToFile(const std::string& path, const Audio& audio) {
    if (audio.samples.empty()) return false;

    std::ofstream file(path, std::ios::binary);
    if (!file.is_open()) return false;

    const int numChannels = 1;
    const int bitsPerSample = 16;
    const int sampleRate = audio.sampleRate;

    const int byteRate = sampleRate * numChannels * bitsPerSample / 8;
    const int blockAlign = numChannels * bitsPerSample / 8;

    const int dataSize = audio.samples.size() * sizeof(int16_t);
    const int chunkSize = 36 + dataSize;

    // ===== RIFF HEADER =====
    file.write("RIFF", 4);
    writeInt32(file, chunkSize);
    file.write("WAVE", 4);

    // ===== fmt CHUNK =====
    file.write("fmt ", 4);
    writeInt32(file, 16); // Subchunk1Size (PCM)
    writeInt16(file, 1);  // AudioFormat (1 = PCM)
    writeInt16(file, numChannels);
    writeInt32(file, sampleRate);
    writeInt32(file, byteRate);
    writeInt16(file, blockAlign);
    writeInt16(file, bitsPerSample);

    // ===== data CHUNK =====
    file.write("data", 4);
    writeInt32(file, dataSize);

    // ===== AUDIO DATA =====
    for (float sample : audio.samples) {
        // Clamp [-1.0, 1.0]
        float clamped = std::max(-1.0f, std::min(1.0f, sample));

        // Convert to 16-bit PCM
        int16_t pcm = static_cast<int16_t>(clamped * 32767.0f);

        writeInt16(file, pcm);
    }

    file.close();
    return true;
}

// ===== Helpers (little-endian write) =====
void WavWriter::writeInt16(std::ofstream& file, int16_t value) {
    file.put(value & 0xFF);
    file.put((value >> 8) & 0xFF);
}

void WavWriter::writeInt32(std::ofstream& file, int32_t value) {
    file.put(value & 0xFF);
    file.put((value >> 8) & 0xFF);
    file.put((value >> 16) & 0xFF);
    file.put((value >> 24) & 0xFF);
}