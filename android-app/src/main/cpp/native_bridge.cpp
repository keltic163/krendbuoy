#include <jni.h>
#include <android/log.h>

#include <cstdint>
#include <cstring>
#include <string>

#include "libretro.h"

#define LOG_TAG "VBAMFrontend"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
void retro_init(void);
void retro_deinit(void);
unsigned retro_api_version(void);
void retro_set_environment(retro_environment_t cb);
void retro_set_video_refresh(retro_video_refresh_t cb);
void retro_set_audio_sample(retro_audio_sample_t cb);
void retro_set_audio_sample_batch(retro_audio_sample_batch_t cb);
void retro_set_input_poll(retro_input_poll_t cb);
void retro_set_input_state(retro_input_state_t cb);
bool retro_load_game(const struct retro_game_info* game);
void retro_unload_game(void);
void retro_get_system_info(struct retro_system_info* info);
void retro_get_system_av_info(struct retro_system_av_info* info);
}

static bool g_coreInitialized = false;
static bool g_gameLoaded = false;
static std::string g_loadedPath;
static retro_system_info g_systemInfo = {};
static retro_system_av_info g_avInfo = {};

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
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            if (data) {
                *static_cast<const char**>(data) = nullptr;
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

static void ensure_core_initialized() {
    if (g_coreInitialized) {
        return;
    }

    retro_set_environment(frontend_environment);
    retro_set_video_refresh(frontend_video_refresh);
    retro_set_audio_sample(frontend_audio_sample);
    retro_set_audio_sample_batch(frontend_audio_sample_batch);
    retro_set_input_poll(frontend_input_poll);
    retro_set_input_state(frontend_input_state);
    retro_init();
    retro_get_system_info(&g_systemInfo);
    g_coreInitialized = true;

    LOGI("Core initialized: %s %s", g_systemInfo.library_name ? g_systemInfo.library_name : "unknown", g_systemInfo.library_version ? g_systemInfo.library_version : "unknown");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_keltic_vbam_NativeBridge_getCoreName(JNIEnv* env, jclass) {
    ensure_core_initialized();
    std::string name = g_systemInfo.library_name ? g_systemInfo.library_name : "VBA-M";
    if (g_systemInfo.library_version) {
        name += " ";
        name += g_systemInfo.library_version;
    }
    return env->NewStringUTF(name.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_keltic_vbam_NativeBridge_loadRom(JNIEnv* env, jclass, jstring path) {
    if (path == nullptr) {
        return JNI_FALSE;
    }

    const char* rawPath = env->GetStringUTFChars(path, nullptr);
    if (rawPath == nullptr || rawPath[0] == '\0') {
        if (rawPath != nullptr) {
            env->ReleaseStringUTFChars(path, rawPath);
        }
        return JNI_FALSE;
    }

    std::string localPath(rawPath);
    env->ReleaseStringUTFChars(path, rawPath);

    ensure_core_initialized();

    if (g_gameLoaded) {
        retro_unload_game();
        g_gameLoaded = false;
    }

    retro_game_info game = {};
    game.path = localPath.c_str();
    game.data = nullptr;
    game.size = 0;
    game.meta = nullptr;

    bool ok = retro_load_game(&game);
    if (ok) {
        g_gameLoaded = true;
        g_loadedPath = localPath;
        retro_get_system_av_info(&g_avInfo);
        LOGI("retro_load_game success: %s (%ux%u)", localPath.c_str(), g_avInfo.geometry.base_width, g_avInfo.geometry.base_height);
    } else {
        LOGE("retro_load_game failed: %s", localPath.c_str());
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_keltic_vbam_NativeBridge_unloadRom(JNIEnv*, jclass) {
    if (g_gameLoaded) {
        retro_unload_game();
        g_gameLoaded = false;
        g_loadedPath.clear();
    }
}
