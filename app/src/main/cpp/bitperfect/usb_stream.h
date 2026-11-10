/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

#pragma once

#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace usbaudio {

enum class Encoding : int { Pcm = 0, Dop = 1, DsdBe = 2, DsdLe = 3 };

struct Format {
    uint32_t sampleRate = 0;
    int channels = 0;
    int bits = 0;
    int subslot = 0;
    Encoding encoding = Encoding::Pcm;
};

class PacketClock {
    double nominal;
    double feedbackFrames = 0;
    bool hasFeedback = false;
    double remainder = 0;

public:
    PacketClock(uint32_t rate, uint32_t intervalUs)
        : nominal(static_cast<double>(rate) * intervalUs / 1000000.0) {}

    bool feedback(const uint8_t *bytes, size_t size, bool highSpeed, uint32_t intervalUs) {
        double frames;
        if (size == 3 && !highSpeed) {
            const uint32_t value = bytes[0] | (bytes[1] << 8) | (bytes[2] << 16);
            frames = value / 16384.0;
        } else if (size == 4) {
            uint32_t value;
            std::memcpy(&value, bytes, 4);
            frames = value / 65536.0;
        } else {
            return false;
        }
        frames = frames * intervalUs / (highSpeed ? 125.0 : 1000.0);
        if (!std::isfinite(frames) || std::fabs(frames - nominal) > nominal * 0.02) return false;
        feedbackFrames = frames;
        hasFeedback = true;
        return true;
    }

    size_t next() {
        remainder += hasFeedback ? feedbackFrames : nominal;
        const auto frames = static_cast<size_t>(std::floor(remainder));
        remainder -= static_cast<double>(frames);
        return frames;
    }
};

inline void finishTransfer(uint8_t *bytes, size_t total, size_t realBytes, const Format &format, uint8_t &marker) {
    std::memset(bytes + realBytes, format.encoding == Encoding::Pcm ? 0 : 0x69, total - realBytes);
    if (format.encoding != Encoding::Dop) return;
    const size_t subslot = static_cast<size_t>(format.subslot);
    const size_t frame = subslot * static_cast<size_t>(format.channels);
    for (size_t at = 0; at + frame <= total; at += frame) {
        for (size_t sample = at; sample < at + frame; sample += subslot) {
            bytes[sample + subslot - 1] = marker;
            if (subslot == 4) bytes[sample] = 0;
        }
        marker ^= 0xff;
    }
}

}
