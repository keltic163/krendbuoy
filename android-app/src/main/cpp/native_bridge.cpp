#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_keltic_vbam_NativeBridge_getCoreName(JNIEnv* env, jclass) {
    std::string name = "VBA-M Native Frontend Bridge";
    return env->NewStringUTF(name.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_keltic_vbam_NativeBridge_loadRom(JNIEnv* env, jclass, jstring path) {
    if (path == nullptr) {
        return JNI_FALSE;
    }

    const char* rawPath = env->GetStringUTFChars(path, nullptr);
    bool ok = rawPath != nullptr && rawPath[0] != '\0';
    if (rawPath != nullptr) {
        env->ReleaseStringUTFChars(path, rawPath);
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_keltic_vbam_NativeBridge_unloadRom(JNIEnv*, jclass) {
}
