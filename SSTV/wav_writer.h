#pragma once
#include <string>
#include "sstv_types.h"

class WavWriter {
public:
    static bool writeToFile(const std::string& path, const Audio& audio);

private:
    static void writeInt16(std::ofstream& file, int16_t value);
    static void writeInt32(std::ofstream& file, int32_t value);
};