package com.krendstudio.krendbuoy;

import android.graphics.Bitmap;
import android.widget.ImageView;

final class FrameLoopManager {
    interface Host {
        boolean isFrameLoopPaused();
        void updateFrameInfo(String text);
        void runOnUiThread(Runnable action);
        String audioPresetLabelForFrameLoop();
    }

    private final Host host;
    private final ImageView screen;
    private volatile boolean running;
    private Thread frameThread;

    FrameLoopManager(Host host, ImageView screen) {
        this.host = host;
        this.screen = screen;
    }

    void start() {
        if (running) return;
        running = true;
        frameThread = new Thread(() -> {
            long frame = 0;
            while (running) {
                if (host.isFrameLoopPaused()) {
                    sleepQuietly(16);
                    continue;
                }

                long start = System.currentTimeMillis();
                if (NativeBridge.runFrame()) {
                    int width = NativeBridge.getFrameWidth();
                    int height = NativeBridge.getFrameHeight();
                    int[] pixels = NativeBridge.copyFramePixels();
                    if (width > 0 && height > 0 && pixels != null && pixels.length == width * height) {
                        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
                        host.runOnUiThread(() -> screen.setImageBitmap(bitmap));
                    }
                    frame++;
                    if (frame % 30 == 0) {
                        host.updateFrameInfo("audio preset " + host.audioPresetLabelForFrameLoop() + "\n" + NativeBridge.getLastError());
                    }
                } else {
                    host.updateFrameInfo("runFrame failed:\n" + NativeBridge.getLastError());
                    running = false;
                }

                long sleep = 16L - (System.currentTimeMillis() - start);
                if (sleep > 0) sleepQuietly(sleep);
            }
        }, "VBAM-frame-loop-helper");
        frameThread.start();
    }

    void stop() {
        running = false;
        if (frameThread != null) {
            frameThread.interrupt();
            frameThread = null;
        }
    }

    boolean isRunning() {
        return running;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
