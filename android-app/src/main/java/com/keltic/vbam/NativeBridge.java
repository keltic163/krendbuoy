package com.keltic.vbam;

public final class NativeBridge {
    static {
        System.loadLibrary("vbam_libretro");
        System.loadLibrary("vbam_frontend");
    }

    private NativeBridge() {
    }

    public static native String getCoreName();

    public static native void setDirectories(String systemDirectory, String saveDirectory);

    public static native boolean loadRom(String path);

    public static native boolean runFrame();

    public static native int getFrameWidth();

    public static native int getFrameHeight();

    public static native int[] copyFramePixels();

    public static native String getLastError();

    public static native void unloadRom();
}
