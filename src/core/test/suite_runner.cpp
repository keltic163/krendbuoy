// Headless runner for mGBA's test suite (suite.gba).
//
// Drives the emulator through every sub-suite by injecting synthetic keypad
// input, then reads IWRAM to report per-suite pass/total counts and dumps the
// SRAM log at the end.
//
// Usage: suite_runner [path/to/suite.gba]
// Default ROM path: /Users/andyvand/Downloads/suite-master/suite.gba

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "core/base/system.h"
#include "core/base/sound_driver.h"
#include "core/gba/gba.h"
#include "core/gba/gbaGlobals.h"
#include "core/gba/gbaFlash.h"
#include "core/gba/gbaSound.h"

// ---- System-callback stubs (must be provided by the embedder) --------------

static uint32_t g_joy_mask = 0;

// Core expects the embedder to instantiate this.
struct CoreOptions coreOptions;

void systemMessage(int, const char*, ...) {}
void log(const char*, ...) {}
bool systemPauseOnFrame() { return false; }
void systemGbPrint(uint8_t*, int, int, int, int, int) {}
void systemScreenCapture(int) {}
void systemDrawScreen() {}
void systemSendScreen() {}
bool systemReadJoypads() { return true; }
uint32_t systemReadJoypad(int) { return g_joy_mask; }
uint32_t systemGetClock() { return 0; }
void systemSetTitle(const char*) {}
namespace {
class NullSoundDriver : public SoundDriver {
  public:
    bool init(long) override { return true; }
    void pause() override {}
    void reset() override {}
    void resume() override {}
    void write(uint16_t*, int) override {}
    void setThrottle(unsigned short) override {}
};
} // namespace
std::unique_ptr<SoundDriver> systemSoundInit() {
    return std::unique_ptr<SoundDriver>(new NullSoundDriver);
}
void systemOnWriteDataToSoundBuffer(const uint16_t*, int) {}
void systemOnSoundShutdown() {}
void systemScreenMessage(const char*) {}
void systemUpdateMotionSensor() {}
int systemGetSensorX() { return 0; }
int systemGetSensorY() { return 0; }
int systemGetSensorZ() { return 0; }
uint8_t systemGetSensorDarkness() { return 0; }
void systemCartridgeRumble(bool) {}
void systemPossibleCartridgeRumble(bool) {}
void updateRumbleFrame() {}
bool systemCanChangeSoundQuality() { return false; }
void systemShowSpeed(int) {}
void system10Frames() {}
void systemFrame() {}
void systemGbBorderOn() {}
void (*dbgOutput)(const char* s, uint32_t addr) = nullptr;
void (*dbgSignal)(int sig, int number) = nullptr;

uint8_t  systemColorMap8[0x10000];
uint16_t systemColorMap16[0x10000];
uint32_t systemColorMap32[0x10000];
uint16_t systemGbPalette[24];
int systemRedShift = 0;
int systemGreenShift = 0;
int systemBlueShift = 0;
int systemColorDepth = 32;
int systemVerbose = 0;
int systemFrameSkip = 0;
int systemSaveUpdateCounter = 0;
int systemSpeed = 0;
int emulating = 0;

// ---- GBA joypad bits (what systemReadJoypad returns in active-high) --------
static constexpr uint32_t KEY_A      = 1u << 0;
static constexpr uint32_t KEY_B      = 1u << 1;
static constexpr uint32_t KEY_SELECT = 1u << 2;
static constexpr uint32_t KEY_START  = 1u << 3;
static constexpr uint32_t KEY_RIGHT  = 1u << 4;
static constexpr uint32_t KEY_LEFT   = 1u << 5;
static constexpr uint32_t KEY_UP     = 1u << 6;
static constexpr uint32_t KEY_DOWN   = 1u << 7;

// ---- IWRAM helpers ---------------------------------------------------------
//
// IWRAM is mapped at 0x03000000..0x03007FFF. g_internalRAM points at the first
// byte, so IWRAM offset N = g_internalRAM[N - 0x03000000].

static uint32_t iwram_read32(uint32_t addr) {
    uint32_t off = addr - 0x03000000u;
    return (uint32_t)g_internalRAM[off] |
           ((uint32_t)g_internalRAM[off + 1] << 8) |
           ((uint32_t)g_internalRAM[off + 2] << 16) |
           ((uint32_t)g_internalRAM[off + 3] << 24);
}

static int32_t iwram_read_i32(uint32_t addr) {
    return (int32_t)iwram_read32(addr);
}

static uint32_t rom_read32(uint32_t addr) {
    uint32_t off = addr - 0x08000000u;
    return (uint32_t)g_rom[off] |
           ((uint32_t)g_rom[off + 1] << 8) |
           ((uint32_t)g_rom[off + 2] << 16) |
           ((uint32_t)g_rom[off + 3] << 24);
}

// ---- Suite table -----------------------------------------------------------
//
// These TestSuite struct addresses were found with arm-none-eabi-objdump -t on
// suite.elf. The definition order (in main.c `suites[]`) is the order we
// navigate through by pressing DOWN.
struct SuiteDesc {
    const char* name;
    uint32_t struct_addr; // TestSuite struct in ROM
};

static const SuiteDesc kSuites[] = {
    {"memory",        0x0803d798},
    {"io-read",       0x0803cef4},
    {"timing",        0x0804417c},
    {"timers",        0x08043598},
    {"timer-irq",     0x08043480},
    {"shifter",       0x08042664},
    {"carry",         0x0802dd60},
    {"multiply-long", 0x08041cb8},
    {"bios-math",     0x0802cbf4},
    {"dma",           0x0802e258},
    {"sio-read",      0x08042e28},
    {"sio-timing",    0x080433e4},
    {"misc-edge",     0x08041a88},
    {"video",         0x080471ac},
};
static constexpr int kNumSuites = sizeof(kSuites) / sizeof(kSuites[0]);

// Offsets inside the TestSuite struct on ARM (4-byte aligned, 28 bytes):
//   +0  const char* name
//   +4  void (*run)(void)
//   +8  size_t (*list)(...)
//   +12 void (*show)(size_t)
//   +16 const size_t nTests
//   +20 const unsigned* passes
//   +24 const unsigned* totalResults

struct SuiteCounters {
    uint32_t passes_addr;
    uint32_t total_addr;
    uint32_t n_tests;
};

static SuiteCounters read_suite_counters(uint32_t struct_addr) {
    SuiteCounters c;
    c.n_tests      = rom_read32(struct_addr + 16);
    c.passes_addr  = rom_read32(struct_addr + 20);
    c.total_addr   = rom_read32(struct_addr + 24);
    return c;
}

// ---- Emulation driver ------------------------------------------------------

static constexpr int TICKS_PER_FRAME = 280896;

static void run_frames(int n, uint32_t joy = 0) {
    g_joy_mask = joy;
    for (int i = 0; i < n; ++i) {
        GBASystem.emuMain(TICKS_PER_FRAME);
    }
}

static void press_button(uint32_t mask, int hold_frames = 6) {
    // Release → press → release so the ROM's keysDown logic fires.
    run_frames(4, 0);
    run_frames(hold_frames, mask);
    run_frames(4, 0);
}

// ---- activeTestInfo in IWRAM ----------------------------------------------
//
// From suite-master/src/main.c:
//   IWRAM_DATA struct ActiveInfo activeTestInfo = { {'I','n','f','o'}, -1, -1, -1 };
// Layout on ARM (packed/aligned, 4-byte members): int32 tag, int32 suiteId,
// int32 testId, int32 subTestId. The .iwram section starts at 0x03000000 and
// the linker places activeTestInfo first after any preceding IWRAM data.
//
// We resolve the address at runtime by scanning IWRAM for the 'Info' tag since
// there can be multiple copies (IWRAM vs shadow). This avoids hardcoding.
static uint32_t g_active_info_addr = 0;

static bool locate_active_info() {
    // Search the first 2 KiB for the 'Info' ASCII tag followed by 3 int32s
    // whose initial state we expect to be -1.
    for (uint32_t off = 0; off < 0x800; off += 4) {
        uint32_t tag = iwram_read32(0x03000000u + off);
        if (tag == 0x6f666e49u /* 'Info' little-endian */) {
            g_active_info_addr = 0x03000000u + off;
            return true;
        }
    }
    return false;
}

static int32_t active_suite_id() {
    if (!g_active_info_addr) return -2;
    return iwram_read_i32(g_active_info_addr + 4);
}
static int32_t active_test_id() {
    if (!g_active_info_addr) return -2;
    return iwram_read_i32(g_active_info_addr + 8);
}

// ---- SRAM log dump ---------------------------------------------------------

static void dump_sram_log(FILE* out) {
    // VBA-M stores both SRAM and Flash save data in flashSaveMemory.
    uint8_t* sram = flashSaveMemory;
    int sram_size = 0x10000;
    // Find a reasonable end point by looking for the last non-zero byte.
    int end = sram_size - 1;
    while (end > 0 && sram[end] == 0) --end;
    if (end <= 0) {
        fputs("---- SRAM log dump: empty ----\n", out);
        return;
    }
    fputs("---- SRAM log dump ----\n", out);
    fwrite(sram, 1, end + 1, out);
    fputs("\n---- end SRAM log ----\n", out);
}

// ---- Main ------------------------------------------------------------------

int main(int argc, char** argv) {
    const char* rom_path =
        (argc > 1) ? argv[1] : "/Users/andyvand/Downloads/suite-master/suite.gba";

    // Load the ROM file into a buffer.
    FILE* f = fopen(rom_path, "rb");
    if (!f) {
        fprintf(stderr, "suite_runner: cannot open %s\n", rom_path);
        return 1;
    }
    fseek(f, 0, SEEK_END);
    long rom_bytes = ftell(f);
    fseek(f, 0, SEEK_SET);
    std::vector<char> rom_buf(rom_bytes);
    if (fread(rom_buf.data(), 1, rom_bytes, f) != (size_t)rom_bytes) {
        fprintf(stderr, "suite_runner: short read on %s\n", rom_path);
        fclose(f);
        return 1;
    }
    fclose(f);

    // Core wants SRAM so the ROM's savprintf() can store its test log.
    coreOptions.saveType = 2; // GBA_SAVE_SRAM
    coreOptions.useBios = 0;
    coreOptions.skipBios = true;

    if (!CPULoadRomData(rom_buf.data(), (int)rom_bytes)) {
        fprintf(stderr, "suite_runner: CPULoadRomData failed\n");
        return 1;
    }
    CPUInit("", false);
    SetSaveType(2);
    soundInit();
    CPUReset();
    emulating = 1;

    // Let the ROM boot past crt0 + splash (no input).
    run_frames(180, 0);

    if (!locate_active_info()) {
        fprintf(stderr,
                "suite_runner: could not locate activeTestInfo tag in IWRAM\n");
    } else {
        fprintf(stderr,
                "suite_runner: activeTestInfo at 0x%08x\n",
                g_active_info_addr);
    }

    // Cache the per-suite pass/total pointers from ROM now (they never move).
    SuiteCounters counters[kNumSuites];
    for (int i = 0; i < kNumSuites; ++i) {
        counters[i] = read_suite_counters(kSuites[i].struct_addr);
        fprintf(stderr,
                "suite_runner: %-14s  struct=%08x passes=%08x total=%08x nTests=%u\n",
                kSuites[i].name, kSuites[i].struct_addr,
                counters[i].passes_addr, counters[i].total_addr,
                counters[i].n_tests);
    }

    // Drive the test UI: press A to enter each suite, wait for completion,
    // press B to return, press DOWN to advance selection.
    for (int i = 0; i < kNumSuites; ++i) {
        fprintf(stderr, "\n[%d/%d] running %s...\n",
                i + 1, kNumSuites, kSuites[i].name);

        press_button(KEY_A, 8);

        // Wait for suite->run() to start (activeTestInfo.suiteId becomes >= 0
        // once runSuite() executes).
        int waited = 0;
        while (active_suite_id() < 0 && waited < 60) {
            run_frames(1, 0);
            ++waited;
        }

        // Poll pass/total until they stabilize. Allow up to 120 seconds.
        uint32_t last_passes = 0, last_total = 0;
        int stable = 0;
        for (int frame = 0; frame < 7200; ++frame) {
            run_frames(1, 0);
            uint32_t p = iwram_read32(counters[i].passes_addr);
            uint32_t t = iwram_read32(counters[i].total_addr);
            if (p == last_passes && t == last_total && t > 0) {
                if (++stable >= 180) break; // 3 seconds stable
            } else {
                stable = 0;
            }
            last_passes = p;
            last_total = t;
        }

        uint32_t passes = iwram_read32(counters[i].passes_addr);
        uint32_t total  = iwram_read32(counters[i].total_addr);
        fprintf(stderr, "    %s: %u / %u\n", kSuites[i].name, passes, total);

        // Return to main menu.
        press_button(KEY_B, 6);
        // Move selection to next suite.
        if (i + 1 < kNumSuites) {
            press_button(KEY_DOWN, 6);
        }
    }

    // Final report.
    puts("\n================ suite.gba results ================");
    uint32_t grand_pass = 0, grand_total = 0;
    for (int i = 0; i < kNumSuites; ++i) {
        uint32_t p = iwram_read32(counters[i].passes_addr);
        uint32_t t = iwram_read32(counters[i].total_addr);
        grand_pass += p;
        grand_total += t;
        printf("  %-14s  %5u / %-5u  (%s)\n",
               kSuites[i].name, p, t,
               (t > 0 && p == t) ? "PASS" : "FAIL");
    }
    printf("  %-14s  %5u / %-5u\n", "TOTAL", grand_pass, grand_total);
    puts("===================================================\n");

    // Dump the suite's own SRAM log.
    dump_sram_log(stdout);

    GBASystem.emuCleanUp();
    return (grand_total > 0 && grand_pass == grand_total) ? 0 : 2;
}
