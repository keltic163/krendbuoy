package com.krendstudio.krendbuoy;

import android.graphics.Bitmap;
import android.widget.ImageView;

final class FrameLoopManager {
    interface Host {
        boolean isFrameLoopPaused();
        void updateFrameInfo(String text);
        void runOnUiThread(Runnable action);
        String audioPresetLabelForFrameLoop();
        int emulationSpeedMultiplierForFrameLoop();
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
                int speed = host.emulationSpeedMultiplierForFrameLoop();
                if (speed < 1 || speed > 3) speed = 1;

                boolean ok = true;
                int width = 0;
                int height = 0;
                int[] pixels = null;

                for (int i = 0; i < speed && running; i++) {
                    if (!NativeBridge.runFrame()) {
                        ok = false;
                        break;
                    }
                    width = NativeBridge.getFrameWidth();
                    height = NativeBridge.getFrameHeight();
                    pixels = NativeBridge.copyFramePixels();
                    frame++;
                }

                if (ok) {
                    if (width > 0 && height > 0 && pixels != null && pixels.length == width * height) {
                        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
                        host.runOnUiThread(() -> screen.setImageBitmap(bitmap));
                    }
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
        Thread thread = frameThread;
        if (thread != null) {
            thread.interrupt();
            if (thread != Thread.currentThread()) {
                try {
                    thread.join(250);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        frameThread = null;
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
