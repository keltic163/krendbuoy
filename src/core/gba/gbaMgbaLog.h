#ifndef VBAM_CORE_GBA_GBAMGBALOG_H_
#define VBAM_CORE_GBA_GBAMGBALOG_H_

#include <cstdint>

// mGBA's debug-console MMIO protocol. Homebrew writes a message to a string
// buffer then writes a flag register to publish it — the emulator prints the
// buffered string. This matches the protocol exposed by mGBA so that code
// built against mgba.h (and compatible homebrew) works on VBA-M.
//
// Memory map (all addresses in the I/O region, hence the REGION_IO dispatcher
// in gbaInline.h must forward writes to these addresses here):
//   0x04FFF600..0x04FFF6FF  — 256-byte message buffer (bytes, halfwords,
//                             words all accepted)
//   0x04FFF700              — trigger register. Writing a value with the
//                             0x100 bit set publishes the buffered string
//                             at log level (value & 0x7)
//   0x04FFF780              — enable register. Write 0xC0DE to enable;
//                             reads return 0x1DEA while enabled, 0 otherwise.
//                             Writing 0 disables.

namespace gbaMgbaLog {

// Region test — cheap check used by the inline memory dispatchers.
inline bool IsRange(uint32_t address) {
    return address >= 0x04FFF600u && address <= 0x04FFF781u;
}

// Write handlers (byte/halfword/word). Return true if the access was
// consumed by this module — when false, the caller should fall back to its
// default (usually "unwritable").
bool Write8(uint32_t address, uint8_t value);
bool Write16(uint32_t address, uint16_t value);
bool Write32(uint32_t address, uint32_t value);

// Read the debug-enable register. Returns true when `address` is the enable
// register and writes the current value (0x1DEA if enabled, 0 otherwise)
// into `out`.
bool Read16(uint32_t address, uint16_t* out);

// Reset state — called from CPUReset so the enable latch and buffer do
// not persist across emulator reset.
void Reset();

} // namespace gbaMgbaLog

#endif // VBAM_CORE_GBA_GBAMGBALOG_H_
