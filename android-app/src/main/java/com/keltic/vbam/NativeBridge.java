package com.krendstudio.krendbuoy;

public final class NativeBridge {
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_SELECT = 2;
    public static final int BUTTON_START = 3;
    public static final int BUTTON_UP = 4;
    public static final int BUTTON_DOWN = 5;
    public static final int BUTTON_LEFT = 6;
    public static final int BUTTON_RIGHT = 7;
    public static final int BUTTON_L = 8;
    public static final int BUTTON_R = 9;

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

    public static native void setButtonState(int button, boolean pressed);

    public static native int getInputMask();

    public static native int getAudioSampleRate();

    public static native void setAudioMaxBufferedSamples(int samples);

    public static native int readAudioSamples(short[] buffer, int maxSamples);

    public static native byte[] exportSram();

    public static native boolean importSram(byte[] data);

    public static native byte[] exportState();

    public static native boolean importState(byte[] data);

    public static native boolean saveSram();

    public static native String getLastError();

    public static native void unloadRom();
}
