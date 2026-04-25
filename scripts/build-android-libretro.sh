#!/usr/bin/env bash
set -euo pipefail

print_help() {
  cat <<'EOF'
Build the VBA-M libretro core for RetroArch Android.

Usage:
  scripts/build-android-libretro.sh [ABI]

Arguments:
  ABI                  Android ABI to build. Defaults to $ANDROID_ABI or arm64-v8a.

Supported ABIs:
  arm64-v8a
  armeabi-v7a
  x86_64
  x86

Environment:
  ANDROID_NDK_HOME     Path to the Android NDK. ANDROID_NDK_ROOT is also accepted.
  ANDROID_API          Android API level. Defaults to 23.
  MAKE_JOBS            make parallelism. Defaults to nproc or 4.

Output:
  build/android/<abi>/vbam_libretro.so
  build/android/<abi>/README.txt

This builds only the libretro core. It does not create a standalone Android app.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  print_help
  exit 0
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBRETRO_DIR="$ROOT_DIR/src/libretro"
ABI="${1:-${ANDROID_ABI:-arm64-v8a}}"
ANDROID_API="${ANDROID_API:-23}"
JOBS="${MAKE_JOBS:-}"

if [[ -z "$JOBS" ]]; then
  if command -v nproc >/dev/null 2>&1; then
    JOBS="$(nproc)"
  else
    JOBS="4"
  fi
fi

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" ]]; then
  echo "error: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must point to an Android NDK." >&2
  exit 1
fi

HOST_TAG=""
case "$(uname -s)" in
  Linux)  HOST_TAG="linux-x86_64" ;;
  Darwin) HOST_TAG="darwin-x86_64" ;;
  *)
    echo "error: unsupported host OS: $(uname -s)" >&2
    exit 1
    ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
if [[ ! -d "$TOOLCHAIN" ]]; then
  echo "error: Android NDK LLVM toolchain not found at $TOOLCHAIN" >&2
  exit 1
fi

case "$ABI" in
  arm64-v8a)
    CLANG_TRIPLE="aarch64-linux-android"
    MAKE_PLATFORM="unix"
    ;;
  armeabi-v7a)
    CLANG_TRIPLE="armv7a-linux-androideabi"
    MAKE_PLATFORM="armv7-neon-softfloat"
    ;;
  x86_64)
    CLANG_TRIPLE="x86_64-linux-android"
    MAKE_PLATFORM="unix"
    ;;
  x86)
    CLANG_TRIPLE="i686-linux-android"
    MAKE_PLATFORM="unix"
    ;;
  *)
    echo "error: unsupported ABI '$ABI'" >&2
    print_help >&2
    exit 1
    ;;
esac

CC_BIN="$TOOLCHAIN/${CLANG_TRIPLE}${ANDROID_API}-clang"
CXX_BIN="$TOOLCHAIN/${CLANG_TRIPLE}${ANDROID_API}-clang++"
AR_BIN="$TOOLCHAIN/llvm-ar"
STRIP_BIN="$TOOLCHAIN/llvm-strip"

if [[ ! -x "$CC_BIN" || ! -x "$CXX_BIN" ]]; then
  echo "error: compiler for ABI '$ABI' not found in $TOOLCHAIN" >&2
  exit 1
fi

OUT_DIR="$ROOT_DIR/build/android/$ABI"
mkdir -p "$OUT_DIR"

printf 'Building Android libretro core\n'
printf '  ABI: %s\n' "$ABI"
printf '  API: %s\n' "$ANDROID_API"
printf '  NDK: %s\n' "$NDK"
printf '  Output: %s\n' "$OUT_DIR/vbam_libretro.so"

COMMON_FLAGS="-DANDROID -D__LIBRETRO__"

make -C "$LIBRETRO_DIR" clean >/dev/null 2>&1 || true
make -C "$LIBRETRO_DIR" \
  platform="$MAKE_PLATFORM" \
  CC="$CC_BIN" \
  CXX="$CXX_BIN" \
  AR="$AR_BIN" \
  CFLAGS="$COMMON_FLAGS" \
  CXXFLAGS="$COMMON_FLAGS -std=gnu++17" \
  LDFLAGS="-fPIC" \
  -j"$JOBS"

if [[ ! -f "$LIBRETRO_DIR/vbam_libretro.so" ]]; then
  echo "error: expected output not found: $LIBRETRO_DIR/vbam_libretro.so" >&2
  exit 1
fi

cp "$LIBRETRO_DIR/vbam_libretro.so" "$OUT_DIR/vbam_libretro.so"
if [[ -x "$STRIP_BIN" ]]; then
  "$STRIP_BIN" --strip-unneeded "$OUT_DIR/vbam_libretro.so" || true
fi

cat > "$OUT_DIR/README.txt" <<EOF
VBA-M libretro core for RetroArch Android

ABI: $ABI
Android API: $ANDROID_API

This artifact contains vbam_libretro.so, a RetroArch-compatible libretro core.
It is not a standalone Android application.

To test:
1. Copy vbam_libretro.so to your Android device.
2. Open RetroArch Android.
3. Load the core manually if needed.
4. Load a legally obtained Game Boy or Game Boy Advance ROM.

If RetroArch rejects the file, confirm that the artifact ABI matches your device.
Most modern Android phones use arm64-v8a.
EOF

printf 'Built: %s\n' "$OUT_DIR/vbam_libretro.so"
