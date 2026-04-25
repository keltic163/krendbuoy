# Android Libretro Build

## Purpose

This repository can build the VBA-M libretro core for use with RetroArch on Android.

This produces:

```text
vbam_libretro.so
```

This does **not** create a standalone Android application.
It is intended for RetroArch Android users who want to test VBA-M as a libretro core.

---

## Supported ABI targets

Primary target:

- arm64-v8a

Additional supported targets:

- armeabi-v7a
- x86_64
- x86

Most modern Android phones should use:

```text
arm64-v8a
```

---

## Local build requirements

You need:

- bash
- make
- git
- Android NDK

Set one of these environment variables:

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk
```

or

```bash
export ANDROID_NDK_ROOT=/path/to/android-ndk
```

Optional:

```bash
export ANDROID_API=23
export MAKE_JOBS=8
```

---

## Local build

Default build:

```bash
scripts/build-android-libretro.sh
```

Explicit ABI:

```bash
scripts/build-android-libretro.sh arm64-v8a
scripts/build-android-libretro.sh armeabi-v7a
```

Build output:

```text
build/android/<abi>/vbam_libretro.so
```

Example:

```text
build/android/arm64-v8a/vbam_libretro.so
```

---

## GitHub Actions build

Workflow:

```text
.github/workflows/build-android-libretro.yml
```

It can be triggered by:

- workflow_dispatch
- push to Android/libretro build files
- pull requests touching Android/libretro build files

Artifacts are uploaded with names like:

```text
vbam-libretro-android-arm64-v8a
```

---

## Downloading the artifact

1. Open GitHub repository
2. Go to Actions
3. Open build-android-libretro workflow
4. Open the successful run
5. Download the artifact for your ABI

---

## Testing with RetroArch Android

1. Install RetroArch on Android
2. Download the correct ABI artifact
3. Extract:

```text
vbam_libretro.so
```

4. Copy the file to your device
5. Open RetroArch
6. Load the core manually if needed
7. Load a legally obtained GBA or GB ROM
8. Test video, audio, input, and save states

---

## Known limitations

- This is not a standalone Android app
- UI, overlays, input mapping, and save-state management are handled by RetroArch
- arm64-v8a is the highest priority target
- Some older ABIs may require additional fixes depending on NDK/toolchain behavior
- Local builds may fail if the Android NDK path is not configured correctly

---

## Recommended workflow

Recommended development order:

```text
Build libretro core
→ Test in RetroArch Android
→ Validate gameplay/audio/save behavior
→ Decide later whether a full standalone Android app is needed
```
