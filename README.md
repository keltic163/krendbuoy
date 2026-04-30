# KrendBuoy Android

KrendBuoy is a standalone Android Game Boy / Game Boy Color / Game Boy Advance emulator app built on top of the VisualBoyAdvance-M libretro core.

This repository is currently focused on the Android application. Desktop VisualBoyAdvance-M frontends and platform packaging files are not part of the active app target.

## Current Android structure

```text
android-app/                         Android application module
android-app/src/main/java/           Java Android frontend
android-app/src/main/cpp/            JNI bridge for the libretro core
scripts/build-android-libretro.sh    Builds the VBA-M libretro core for Android
src/libretro/                        Libretro core build entry point
src/core/                            Emulator core sources used by libretro
```

## Main entry points

```text
com.krendstudio.krendbuoy.MainActivity      ROM folder and settings screen
com.krendstudio.krendbuoy.GameActivityV2    In-game emulator screen
com.krendstudio.krendbuoy.NativeBridge      Java/JNI bridge
```

`GameActivityV2` is intentionally split into helper classes so future patches remain small and easier to apply:

```text
AudioPlaybackManager.java
AppSettingsManager.java
FrameLoopManager.java
GameControllerOverlay.java
GameQuickMenu.java
GameSettingsDialogs.java
PortableSaveManager.java
RomSessionManager.java
SaveStateManager.java
```

## Building the Android APK

The GitHub Actions workflow builds the app with:

```text
.github/workflows/build-android-apk.yml
```

The workflow performs these steps:

1. Builds `vbam_libretro.so` for `arm64-v8a` with `scripts/build-android-libretro.sh`.
2. Copies the core into `android-app/src/main/jniLibs/arm64-v8a/libvbam_libretro.so`.
3. Copies `libc++_shared.so` from the Android NDK.
4. Runs `gradle :android-app:assembleDebug`.
5. Uploads the generated debug APK artifact.

## Local build requirements

Install:

- JDK 17
- Android SDK
- Android NDK 26.3.11579264 or compatible
- Gradle
- GNU make

Then run:

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk
scripts/build-android-libretro.sh arm64-v8a
mkdir -p android-app/src/main/jniLibs/arm64-v8a
cp build/android/arm64-v8a/vbam_libretro.so android-app/src/main/jniLibs/arm64-v8a/libvbam_libretro.so
cp "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" android-app/src/main/jniLibs/arm64-v8a/libc++_shared.so
gradle :android-app:assembleDebug
```

## Cleanup policy

Keep files required by the Android APK build:

```text
android-app/**
build.gradle
settings.gradle
gradle.properties
.github/workflows/build-android-apk.yml
scripts/build-android-libretro.sh
src/libretro/**
src/core/**
```

Files only needed for desktop VisualBoyAdvance-M, RetroArch-only documentation, Windows/macOS/Linux packaging, or old upstream release processes should be removed when they are confirmed not to be referenced by the Android build.

## Attribution

KrendBuoy is forked from VisualBoyAdvance-M and uses the VBA-M emulator core. Preserve upstream license notices for retained core source files.
