package com.keltic.vbam;

public final class NativeBridge {
    static {
        System.loadLibrary("vbam_libretro");
        System.loadLibrary("vbam_frontend");
    }

    private NativeBridge() {
    }

    public static native String getCoreName();

    public static native boolean loadRom(String path);

    public static native void unloadRom();
}
