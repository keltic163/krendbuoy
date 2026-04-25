<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [Visual Boy Advance - M](#visual-boy-advance---m)
  - [System Requirements](#system-requirements)
  - [Building](#building)
  - [Building a Libretro core](#building-a-libretro-core)
  - [Visual Studio Support](#visual-studio-support)
  - [Visual Studio Code Support](#visual-studio-code-support)
  - [Dependencies](#dependencies)
  - [Cross compiling for 32 bit on a 64 bit host](#cross-compiling-for-32-bit-on-a-64-bit-host)
  - [Cross Compiling for Win32](#cross-compiling-for-win32)
  - [CMake Options](#cmake-options)
  - [MSys2 Notes](#msys2-notes)
  - [Debug Messages](#debug-messages)
  - [Reporting Crash Bugs](#reporting-crash-bugs)
  - [Contributing](#contributing)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

Our bridged Discord server is [Here](https://discord.gg/EpfxEuGMKH).

We are also on *`#vba-m`* on [Libera IRC](https://libera.chat/) which has a [Web
Chat](https://web.libera.chat/).

# Visual Boy Advance - M

Game Boy and Game Boy Advance Emulator

## Android Libretro Build

For RetroArch Android users, Android libretro build instructions are available here:

```text
docs/android-libretro.md
```

This produces Android-compatible:

```text
vbam_libretro.so
```

for use with RetroArch Android. It does not create a standalone Android app.
