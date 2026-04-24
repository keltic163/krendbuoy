#include "core/gba/gbaMgbaLog.h"

#include <cstdio>
#include <cstring>

namespace gbaMgbaLog {

namespace {

// Live state.
constexpr uint32_t kBufferBase = 0x04FFF600;
constexpr uint32_t kBufferSize = 0x100;
constexpr uint32_t kFlagsReg   = 0x04FFF700;
constexpr uint32_t kEnableReg  = 0x04FFF780;

constexpr uint16_t kEnableMagic   = 0xC0DE;
constexpr uint16_t kEnabledStatus = 0x1DEA;

char     g_buffer[kBufferSize];
bool     g_enabled = false;

// mGBA log levels: 0 FATAL, 1 ERROR, 2 WARN, 3 INFO, 4 DEBUG.
const char* LevelName(uint16_t level) {
    switch (level & 0x7) {
    case 0: return "FATAL";
    case 1: return "ERROR";
    case 2: return "WARN";
    case 3: return "INFO";
    case 4: return "DEBUG";
    default: return "LOG";
    }
}

void Publish(uint16_t flags) {
    // Bit 0x100 is the publish trigger — required by the protocol.
    if (!(flags & 0x100))
        return;
    // The buffer is a C string; cap it at kBufferSize to be safe on
    // messages that forget the null terminator.
    g_buffer[kBufferSize - 1] = '\0';
    fprintf(stderr, "[mGBA %s] %s\n", LevelName(flags), g_buffer);
    fflush(stderr);
}

} // namespace

void Reset() {
    g_enabled = false;
    std::memset(g_buffer, 0, sizeof(g_buffer));
}

bool Read16(uint32_t address, uint16_t* out) {
    if (address == kEnableReg) {
        *out = g_enabled ? kEnabledStatus : 0;
        return true;
    }
    return false;
}

bool Write8(uint32_t address, uint8_t value) {
    if (address >= kBufferBase && address < kBufferBase + kBufferSize) {
        g_buffer[address - kBufferBase] = static_cast<char>(value);
        return true;
    }
    // Byte writes to the flag/enable registers are rare but valid on real
    // hardware — treat the low byte as the low byte of the halfword and
    // accept the access to keep behavior consistent.
    if (address == kFlagsReg || address == kEnableReg) {
        return true;
    }
    if (address == kFlagsReg + 1 || address == kEnableReg + 1) {
        return true;
    }
    return false;
}

bool Write16(uint32_t address, uint16_t value) {
    if (address >= kBufferBase && address < kBufferBase + kBufferSize) {
        g_buffer[address - kBufferBase]     = static_cast<char>(value & 0xFF);
        g_buffer[address - kBufferBase + 1] = static_cast<char>((value >> 8) & 0xFF);
        return true;
    }
    if (address == kFlagsReg) {
        if (g_enabled)
            Publish(value);
        return true;
    }
    if (address == kEnableReg) {
        g_enabled = (value == kEnableMagic);
        if (!g_enabled) {
            // Any non-magic write clears the latch — matches mGBA.
            g_enabled = false;
        }
        return true;
    }
    return false;
}

bool Write32(uint32_t address, uint32_t value) {
    if (address >= kBufferBase && address < kBufferBase + kBufferSize) {
        g_buffer[address - kBufferBase]     = static_cast<char>(value & 0xFF);
        g_buffer[address - kBufferBase + 1] = static_cast<char>((value >> 8) & 0xFF);
        g_buffer[address - kBufferBase + 2] = static_cast<char>((value >> 16) & 0xFF);
        g_buffer[address - kBufferBase + 3] = static_cast<char>((value >> 24) & 0xFF);
        return true;
    }
    // 32-bit write that straddles the flag/enable registers: split into two
    // halfword writes so the side effects still fire on the correct half.
    if (address == kFlagsReg - 2 || address == kFlagsReg) {
        Write16(address, static_cast<uint16_t>(value & 0xFFFF));
        Write16(address + 2, static_cast<uint16_t>((value >> 16) & 0xFFFF));
        return true;
    }
    if (address == kEnableReg - 2 || address == kEnableReg) {
        Write16(address, static_cast<uint16_t>(value & 0xFFFF));
        Write16(address + 2, static_cast<uint16_t>((value >> 16) & 0xFFFF));
        return true;
    }
    return false;
}

} // namespace gbaMgbaLog
