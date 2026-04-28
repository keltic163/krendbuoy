#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <atomic>
#include <cstdint>
#include <cstdarg>
#include <fstream>
#include <mutex>
#include <string>
#include <vector>
#include "libretro.h"

#define LOG_TAG "VBAMFrontend"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void* core = nullptr;
static bool initialized = false;
static bool loaded = false;
static std::string systemDir;
static std::string saveDir;
static std::string last = "No operation yet.";
static std::vector<uint8_t> romData;
static retro_system_info sysInfo = {};
static retro_system_av_info avInfo = {};
static uint64_t frames = 0;
static uint64_t videoFrames = 0;
static unsigned lastW = 0;
static unsigned lastH = 0;
static size_t lastPitch = 0;
static std::mutex frameMutex;
static std::vector<uint32_t> framePixels;
static int frameWidth = 0;
static int frameHeight = 0;
static std::atomic_bool buttonStates[10];
static std::atomic_uint inputMask{0};
static std::atomic_uint inputQueryCount{0};
static std::atomic_uint controllerDeviceSet{0};

static constexpr int BTN_A = 0;
static constexpr int BTN_B = 1;
static constexpr int BTN_SELECT = 2;
static constexpr int BTN_START = 3;
static constexpr int BTN_UP = 4;
static constexpr int BTN_DOWN = 5;
static constexpr int BTN_LEFT = 6;
static constexpr int BTN_RIGHT = 7;
static constexpr int BTN_L = 8;
static constexpr int BTN_R = 9;

#define LOADSYM(name) do { p_##name = reinterpret_cast<name##_t>(dlsym(core, #name)); if (!p_##name) { fail(std::string("Missing symbol: ") + #name); return false; } } while (0)

typedef void (*retro_init_t)();
typedef void (*retro_deinit_t)();
typedef void (*retro_set_environment_t)(retro_environment_t);
typedef void (*retro_set_video_refresh_t)(retro_video_refresh_t);
typedef void (*retro_set_audio_sample_t)(retro_audio_sample_t);
typedef void (*retro_set_audio_sample_batch_t)(retro_audio_sample_batch_t);
typedef void (*retro_set_input_poll_t)(retro_input_poll_t);
typedef void (*retro_set_input_state_t)(retro_input_state_t);
typedef void (*retro_set_controller_port_device_t)(unsigned, unsigned);
typedef bool (*retro_load_game_t)(const retro_game_info*);
typedef void (*retro_unload_game_t)();
typedef void (*retro_get_system_info_t)(retro_system_info*);
typedef void (*retro_get_system_av_info_t)(retro_system_av_info*);
typedef void (*retro_run_t)();

static retro_init_t p_retro_init = nullptr;
static retro_deinit_t p_retro_deinit = nullptr;
static retro_set_environment_t p_retro_set_environment = nullptr;
static retro_set_video_refresh_t p_retro_set_video_refresh = nullptr;
static retro_set_audio_sample_t p_retro_set_audio_sample = nullptr;
static retro_set_audio_sample_batch_t p_retro_set_audio_sample_batch = nullptr;
static retro_set_input_poll_t p_retro_set_input_poll = nullptr;
static retro_set_input_state_t p_retro_set_input_state = nullptr;
static retro_set_controller_port_device_t p_retro_set_controller_port_device = nullptr;
static retro_load_game_t p_retro_load_game = nullptr;
static retro_unload_game_t p_retro_unload_game = nullptr;
static retro_get_system_info_t p_retro_get_system_info = nullptr;
static retro_get_system_av_info_t p_retro_get_system_av_info = nullptr;
static retro_run_t p_retro_run = nullptr;

static void fail(const std::string& s) { last = s; LOGE("%s", s.c_str()); }

static void retroLogCb(enum retro_log_level level, const char* fmt, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    if (level == RETRO_LOG_ERROR || level == RETRO_LOG_WARN) LOGE("%s", buffer);
    else LOGI("%s", buffer);
}

static bool readFile(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) { fail("Could not open ROM: " + path); return false; }
    auto size = f.tellg();
    if (size <= 0) { fail("ROM is empty: " + path); return false; }
    out.resize((size_t)size);
    f.seekg(0, std::ios::beg);
    if (!f.read(reinterpret_cast<char*>(out.data()), size)) { out.clear(); fail("Could not read ROM: " + path); return false; }
    return true;
}

static uint32_t rgb565ToArgb(uint16_t p) {
    uint8_t r = static_cast<uint8_t>(((p >> 11) & 0x1F) * 255 / 31);
    uint8_t g = static_cast<uint8_t>(((p >> 5) & 0x3F) * 255 / 63);
    uint8_t b = static_cast<uint8_t>((p & 0x1F) * 255 / 31);
    return 0xFF000000u | (static_cast<uint32_t>(r) << 16) | (static_cast<uint32_t>(g) << 8) | b;
}

static uint32_t xrgb8888ToArgb(uint32_t p) { return 0xFF000000u | (p & 0x00FFFFFFu); }

static unsigned buildRetroJoypadMask() {
    unsigned mask = 0;
    if (buttonStates[BTN_B].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_B);
    if (buttonStates[BTN_A].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_A);
    if (buttonStates[BTN_SELECT].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_SELECT);
    if (buttonStates[BTN_START].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_START);
    if (buttonStates[BTN_UP].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_UP);
    if (buttonStates[BTN_DOWN].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_DOWN);
    if (buttonStates[BTN_LEFT].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_LEFT);
    if (buttonStates[BTN_RIGHT].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_RIGHT);
    if (buttonStates[BTN_L].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_L);
    if (buttonStates[BTN_R].load()) mask |= (1u << RETRO_DEVICE_ID_JOYPAD_R);
    return mask;
}

static void videoCb(const void* data, unsigned w, unsigned h, size_t pitch) {
    if (!data || !w || !h) return;
    videoFrames++;
    lastW = w;
    lastH = h;
    lastPitch = pitch;
    std::lock_guard<std::mutex> lock(frameMutex);
    frameWidth = static_cast<int>(w);
    frameHeight = static_cast<int>(h);
    framePixels.resize(static_cast<size_t>(w) * static_cast<size_t>(h));
    if (pitch >= w * 4) {
        const uint32_t* src = static_cast<const uint32_t*>(data);
        size_t srcStride = pitch / sizeof(uint32_t);
        for (unsigned y = 0; y < h; y++) for (unsigned x = 0; x < w; x++) framePixels[y * w + x] = xrgb8888ToArgb(src[y * srcStride + x]);
    } else {
        const uint16_t* src = static_cast<const uint16_t*>(data);
        size_t srcStride = pitch / sizeof(uint16_t);
        for (unsigned y = 0; y < h; y++) for (unsigned x = 0; x < w; x++) framePixels[y * w + x] = rgb565ToArgb(src[y * srcStride + x]);
    }
}

static void audioCb(int16_t, int16_t) {}
static size_t audioBatchCb(const int16_t*, size_t n) { return n; }
static void inputPollCb() {}
static int16_t inputStateCb(unsigned port, unsigned device, unsigned, unsigned id) {
    if (port != 0 || device != RETRO_DEVICE_JOYPAD) return 0;
    inputQueryCount.fetch_add(1);
    if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return static_cast<int16_t>(buildRetroJoypadMask());
    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_A: return buttonStates[BTN_A].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_B: return buttonStates[BTN_B].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_SELECT: return buttonStates[BTN_SELECT].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_START: return buttonStates[BTN_START].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_UP: return buttonStates[BTN_UP].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_DOWN: return buttonStates[BTN_DOWN].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_LEFT: return buttonStates[BTN_LEFT].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT: return buttonStates[BTN_RIGHT].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_L: return buttonStates[BTN_L].load() ? 1 : 0;
        case RETRO_DEVICE_ID_JOYPAD_R: return buttonStates[BTN_R].load() ? 1 : 0;
        default: return 0;
    }
}

static void clearButtons() {
    for (auto& buttonState : buttonStates) buttonState.store(false);
    inputMask.store(0);
}

static bool envCb(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (data && !systemDir.empty()) { *static_cast<const char**>(data) = systemDir.c_str(); return true; }
            return false;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data && !saveDir.empty()) { *static_cast<const char**>(data) = saveDir.c_str(); return true; }
            return false;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) { *static_cast<bool*>(data) = true; return true; }
            return false;
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            if (data) { static_cast<retro_log_callback*>(data)->log = retroLogCb; return true; }
            return false;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            return true;
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
            return true;
        default:
            return false;
    }
}

static bool initCore() {
    if (initialized) return true;
    if (!core) {
        core = dlopen("libvbam_libretro.so", RTLD_NOW | RTLD_GLOBAL);
        if (!core) { const char* e = dlerror(); fail(std::string("dlopen failed: ") + (e ? e : "unknown")); return false; }
    }
    LOADSYM(retro_init); LOADSYM(retro_deinit); LOADSYM(retro_set_environment); LOADSYM(retro_set_video_refresh);
    LOADSYM(retro_set_audio_sample); LOADSYM(retro_set_audio_sample_batch); LOADSYM(retro_set_input_poll);
    LOADSYM(retro_set_input_state); LOADSYM(retro_load_game); LOADSYM(retro_unload_game);
    LOADSYM(retro_get_system_info); LOADSYM(retro_get_system_av_info); LOADSYM(retro_run);
    p_retro_set_controller_port_device = reinterpret_cast<retro_set_controller_port_device_t>(dlsym(core, "retro_set_controller_port_device"));
    p_retro_set_environment(envCb);
    p_retro_set_video_refresh(videoCb);
    p_retro_set_audio_sample(audioCb);
    p_retro_set_audio_sample_batch(audioBatchCb);
    p_retro_set_input_poll(inputPollCb);
    p_retro_set_input_state(inputStateCb);
    p_retro_init();
    if (p_retro_set_controller_port_device) {
        p_retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD);
        controllerDeviceSet.store(1);
    }
    p_retro_get_system_info(&sysInfo);
    initialized = true;
    last = std::string("Core initialized: ") + (sysInfo.library_name ? sysInfo.library_name : "unknown") + ". controllerDeviceSet=" + std::to_string(controllerDeviceSet.load());
    LOGI("%s", last.c_str());
    return true;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_keltic_vbam_NativeBridge_getCoreName(JNIEnv* env, jclass) {
    if (!initCore()) return env->NewStringUTF("VBA-M core unavailable");
    return env->NewStringUTF(sysInfo.library_name ? sysInfo.library_name : "VBA-M");
}
extern "C" JNIEXPORT void JNICALL Java_com_keltic_vbam_NativeBridge_setDirectories(JNIEnv* env, jclass, jstring systemDirectory, jstring saveDirectory) {
    const char* s = systemDirectory ? env->GetStringUTFChars(systemDirectory, nullptr) : nullptr;
    const char* v = saveDirectory ? env->GetStringUTFChars(saveDirectory, nullptr) : nullptr;
    systemDir = s ? s : ""; saveDir = v ? v : "";
    if (s) env->ReleaseStringUTFChars(systemDirectory, s);
    if (v) env->ReleaseStringUTFChars(saveDirectory, v);
    last = "Directories set.";
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_keltic_vbam_NativeBridge_loadRom(JNIEnv* env, jclass, jstring path) {
    const char* raw = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
    if (!raw || raw[0] == 0) { if (raw) env->ReleaseStringUTFChars(path, raw); fail("loadRom failed: empty path"); return JNI_FALSE; }
    std::string localPath(raw); env->ReleaseStringUTFChars(path, raw);
    if (systemDir.empty() || saveDir.empty()) { fail("loadRom failed: directories not set"); return JNI_FALSE; }
    if (!initCore()) return JNI_FALSE;
    if (loaded) { p_retro_unload_game(); loaded = false; }
    frames = videoFrames = 0; lastW = lastH = 0; lastPitch = 0; inputQueryCount.store(0); clearButtons();
    { std::lock_guard<std::mutex> lock(frameMutex); framePixels.clear(); frameWidth = 0; frameHeight = 0; }
    retro_game_info game = {}; game.path = localPath.c_str();
    bool ok = p_retro_load_game(&game);
    if (!ok && readFile(localPath, romData)) { game.data = romData.data(); game.size = romData.size(); ok = p_retro_load_game(&game); }
    if (!ok) { fail("retro_load_game failed. path=" + localPath); return JNI_FALSE; }
    loaded = true; p_retro_get_system_av_info(&avInfo);
    last = "retro_load_game success. geometry=" + std::to_string(avInfo.geometry.base_width) + "x" + std::to_string(avInfo.geometry.base_height) + ". romBytes=" + std::to_string(romData.size()) + ". controllerDeviceSet=" + std::to_string(controllerDeviceSet.load());
    return JNI_TRUE;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_keltic_vbam_NativeBridge_runFrame(JNIEnv*, jclass) {
    if (!loaded || !p_retro_run) { fail("runFrame failed: game not loaded"); return JNI_FALSE; }
    p_retro_run(); frames++;
    last = "retro_run success. frames=" + std::to_string(frames) + ". videoCallbacks=" + std::to_string(videoFrames) + ". lastVideo=" + std::to_string(lastW) + "x" + std::to_string(lastH) + ". pitch=" + std::to_string(lastPitch) + ". inputMask=" + std::to_string(inputMask.load()) + ". inputQueries=" + std::to_string(inputQueryCount.load()) + ". controllerDeviceSet=" + std::to_string(controllerDeviceSet.load());
    return JNI_TRUE;
}
extern "C" JNIEXPORT jint JNICALL Java_com_keltic_vbam_NativeBridge_getFrameWidth(JNIEnv*, jclass) { std::lock_guard<std::mutex> lock(frameMutex); return frameWidth; }
extern "C" JNIEXPORT jint JNICALL Java_com_keltic_vbam_NativeBridge_getFrameHeight(JNIEnv*, jclass) { std::lock_guard<std::mutex> lock(frameMutex); return frameHeight; }
extern "C" JNIEXPORT jintArray JNICALL Java_com_keltic_vbam_NativeBridge_copyFramePixels(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> lock(frameMutex);
    if (framePixels.empty()) return env->NewIntArray(0);
    jintArray result = env->NewIntArray(static_cast<jsize>(framePixels.size()));
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(framePixels.size()), reinterpret_cast<const jint*>(framePixels.data()));
    return result;
}
extern "C" JNIEXPORT void JNICALL Java_com_keltic_vbam_NativeBridge_setButtonState(JNIEnv*, jclass, jint button, jboolean pressed) {
    if (button < 0 || button >= 10) return;
    bool down = pressed == JNI_TRUE;
    buttonStates[button].store(down);
    unsigned bit = 1u << static_cast<unsigned>(button);
    unsigned current = inputMask.load();
    while (!inputMask.compare_exchange_weak(current, down ? (current | bit) : (current & ~bit))) {}
}
extern "C" JNIEXPORT jint JNICALL Java_com_keltic_vbam_NativeBridge_getInputMask(JNIEnv*, jclass) { return static_cast<jint>(inputMask.load()); }
extern "C" JNIEXPORT jstring JNICALL Java_com_keltic_vbam_NativeBridge_getLastError(JNIEnv* env, jclass) { return env->NewStringUTF(last.c_str()); }
extern "C" JNIEXPORT void JNICALL Java_com_keltic_vbam_NativeBridge_unloadRom(JNIEnv*, jclass) {
    clearButtons(); if (loaded && p_retro_unload_game) p_retro_unload_game();
    loaded = false; romData.clear(); last = "ROM unloaded.";
}
