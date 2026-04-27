#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <cstdint>
#include <cstring>
#include <string>

#include "libretro.h"

#define LOG_TAG "VBAMFrontend"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* g_coreHandle = nullptr;
static bool g_coreInitialized = false;
static bool g_gameLoaded = false;
static std::string g_loadedPath;
static std::string g_systemDirectory;
static std::string g_saveDirectory;
static std::string g_lastError = "No operation yet.";
static retro_system_info g_systemInfo = {};
static retro_system_av_info g_avInfo = {};

typedef void (*retro_init_t)(void);
typedef void (*retro_deinit_t)(void);
typedef unsigned (*retro_api_version_t)(void);
typedef void (*retro_set_environment_t)(retro_environment_t cb);
typedef void (*retro_set_video_refresh_t)(retro_video_refresh_t cb);
typedef void (*retro_set_audio_sample_t)(retro_audio_sample_t cb);
typedef void (*retro_set_audio_sample_batch_t)(retro_audio_sample_batch_t cb);
typedef void (*retro_set_input_poll_t)(retro_input_poll_t cb);
typedef void (*retro_set_input_state_t)(retro_input_state_t cb);
typedef bool (*retro_load_game_t)(const struct retro_game_info* game);
typedef void (*retro_unload_game_t)(void);
typedef void (*retro_get_system_info_t)(struct retro_system_info* info);
typedef void (*retro_get_system_av_info_t)(struct retro_system_av_info* info);

static retro_init_t p_retro_init = nullptr;
static retro_deinit_t p_retro_deinit = nullptr;
static retro_api_version_t p_retro_api_version = nullptr;
static retro_set_environment_t p_retro_set_environment = nullptr;
static retro_set_video_refresh_t p_retro_set_video_refresh = nullptr;
static retro_set_audio_sample_t p_retro_set_audio_sample = nullptr;
static retro_set_audio_sample_batch_t p_retro_set_audio_sample_batch = nullptr;
static retro_set_input_poll_t p_retro_set_input_poll = nullptr;
static retro_set_input_state_t p_retro_set_input_state = nullptr;
static retro_load_game_t p_retro_load_game = nullptr;
static retro_unload_game_t p_retro_unload_game = nullptr;
static retro_get_system_info_t p_retro_get_system_info = nullptr;
static retro_get_system_av_info_t p_retro_get_system_av_info = nullptr;

static void set_last_error(const std::string& message) {
    g_lastError = message;
    LOGE("%s", message.c_str());
}

static void frontend_video_refresh(const void*, unsigned width, unsigned height, size_t pitch) {
    (void)width;
    (void)height;
    (void)pitch;
}

static void frontend_audio_sample(int16_t, int16_t) {
}

static size_t frontend_audio_sample_batch(const int16_t*, size_t frames) {
    return frames;
}

static void frontend_input_poll(void) {
}

static int16_t frontend_input_state(unsigned, unsigned, unsigned, unsigned) {
    return 0;
}

static bool frontend_environment(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
        case RETRO_ENVIRONMENT_SET_SERIALIZATION_QUIRKS:
            return true;

        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            if (data) {
                *static_cast<bool*>(data) = true;
                return true;
            }
            return false;

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            if (data && !g_systemDirectory.empty()) {
                *static_cast<const char**>(data) = g_systemDirectory.c_str();
                return true;
            }
            return false;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data && !g_saveDirectory.empty()) {
                *static_cast<const char**>(data) = g_saveDirectory.c_str();
                return true;
            }
            return false;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
        case RETRO_ENVIRONMENT_GET_VARIABLE:
        default:
            return false;
    }
}

template <typename T>
static bool load_symbol(T* out, const char* name) {
    *out = reinterpret_cast<T>(dlsym(g_coreHandle, name));
    if (*out == nullptr) {
        set_last_error(std::string("Missing libretro symbol: ") + name);
        return false;
    }
    return true;
}

static bool ensure_core_symbols_loaded() {
    if (g_coreHandle == nullptr) {
        g_coreHandle = dlopen("libvbam_libretro.so", RTLD_NOW | RTLD_GLOBAL);
        if (g_coreHandle == nullptr) {
            const char* error = dlerror();
            set_last_error(std::string("dlopen libvbam_libretro.so failed: ") + (error ? error : "unknown error"));
            return false;
        }
    }

    bool ok = load_symbol(&p_retro_init, "retro_init") &&
              load_symbol(&p_retro_deinit, "retro_deinit") &&
              load_symbol(&p_retro_api_version, "retro_api_version") &&
              load_symbol(&p_retro_set_environment, "retro_set_environment") &&
              load_symbol(&p_retro_set_video_refresh, "retro_set_video_refresh") &&
              load_symbol(&p_retro_set_audio_sample, "retro_set_audio_sample") &&
              load_symbol(&p_retro_set_audio_sample_batch, "retro_set_audio_sample_batch") &&
              load_symbol(&p_retro_set_input_poll, "retro_set_input_poll") &&
              load_symbol(&p_retro_set_input_state, "retro_set_input_state") &&
              load_symbol(&p_retro_load_game, "retro_load_game") &&
              load_symbol(&p_retro_unload_game, "retro_unload_game") &&
              load_symbol(&p_retro_get_system_info, "retro_get_system_info") &&
              load_symbol(&p_retro_get_system_av_info, "retro_get_system_av_info");
    if (ok) {
        g_lastError = "Core symbols loaded.";
    }
    return ok;
}

static bool ensure_core_initialized() {
    if (g_coreInitialized) {
        return true;
    }

    if (!ensure_core_symbols_loaded()) {
        return false;
    }

    p_retro_set_environment(frontend_environment);
    p_retro_set_video_refresh(frontend_video_refresh);
    p_retro_set_audio_sample(frontend_audio_sample);
    p_retro_set_audio_sample_batch(frontend_audio_sample_batch);
    p_retro_set_input_poll(frontend_input_poll);
    p_retro_set_input_state(frontend_input_state);
    p_retro_init();
    p_retro_get_system_info(&g_systemInfo);
    g_coreInitialized = true;

    g_lastError = std::string("Core initialized: ") +
            (g_systemInfo.library_name ? g_systemInfo.library_name : "unknown") +
            " " +
            (g_systemInfo.library_version ? g_systemInfo.library_version : "unknown");
    LOGI("%s", g_lastError.c_str());
    return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keltic_vbam_NativeBridge_getCoreName(JNIEnv* env, jclass) {
    if (!ensure_core_initialized()) {
        return env->NewStringUTF("VBA-M core unavailable");
    }

    std::string name = g_systemInfo.library_name ? g_systemInfo.library_name : "VBA-M";
    if (g_systemInfo.library_version) {
        name += " ";
        name += g_systemInfo.library_version;
    }
    return env->NewStringUTF(name.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_keltic_vbam_NativeBridge_setDirectories(JNIEnv* env, jclass, jstring systemDirectory, jstring saveDirectory) {
    const char* systemRaw = systemDirectory ? env->GetStringUTFChars(systemDirectory, nullptr) : nullptr;
    const char* saveRaw = saveDirectory ? env->GetStringUTFChars(saveDirectory, nullptr) : nullptr;

    g_systemDirectory = systemRaw ? systemRaw : "";
    g_saveDirectory = saveRaw ? saveRaw : "";

    if (systemRaw) {
        env->ReleaseStringUTFChars(systemDirectory, systemRaw);
    }
    if (saveRaw) {
        env->ReleaseStringUTFChars(saveDirectory, saveRaw);
    }

    g_lastError = "Directories set. system=" + g_systemDirectory + " save=" + g_saveDirectory;
    LOGI("%s", g_lastError.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_keltic_vbam_NativeBridge_loadRom(JNIEnv* env, jclass, jstring path) {
    if (path == nullptr) {
        set_last_error("loadRom failed: path was null.");
        return JNI_FALSE;
    }

    const char* rawPath = env->GetStringUTFChars(path, nullptr);
    if (rawPath == nullptr || rawPath[0] == '\0') {
        if (rawPath != nullptr) {
            env->ReleaseStringUTFChars(path, rawPath);
        }
        set_last_error("loadRom failed: path was empty.");
        return JNI_FALSE;
    }

    std::string localPath(rawPath);
    env->ReleaseStringUTFChars(path, rawPath);

    if (g_systemDirectory.empty() || g_saveDirectory.empty()) {
        set_last_error("loadRom failed: system/save directories were not set before loading ROM.");
        return JNI_FALSE;
    }

    if (!ensure_core_initialized()) {
        return JNI_FALSE;
    }

    if (g_gameLoaded) {
        p_retro_unload_game();
        g_gameLoaded = false;
    }

    retro_game_info game = {};
    game.path = localPath.c_str();
    game.data = nullptr;
    game.size = 0;
    game.meta = nullptr;

    bool ok = p_retro_load_game(&game);
    if (ok) {
        g_gameLoaded = true;
        g_loadedPath = localPath;
        p_retro_get_system_av_info(&g_avInfo);
        g_lastError = "retro_load_game success. geometry=" +
                std::to_string(g_avInfo.geometry.base_width) + "x" +
                std::to_string(g_avInfo.geometry.base_height);
        LOGI("%s", g_lastError.c_str());
    } else {
        set_last_error("retro_load_game returned false for path: " + localPath +
                       ". Core=" +
                       (g_systemInfo.library_name ? g_systemInfo.library_name : "unknown") +
                       " " +
                       (g_systemInfo.library_version ? g_systemInfo.library_version : "unknown") +
                       ". systemDir=" + g_systemDirectory +
                       ". saveDir=" + g_saveDirectory);
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keltic_vbam_NativeBridge_getLastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_lastError.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_keltic_vbam_NativeBridge_unloadRom(JNIEnv*, jclass) {
    if (g_gameLoaded && p_retro_unload_game != nullptr) {
        p_retro_unload_game();
        g_gameLoaded = false;
        g_loadedPath.clear();
        g_lastError = "ROM unloaded.";
    }
}
