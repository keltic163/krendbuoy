package com.keltic.vbam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class GameActivity extends Activity {
    private TextView info;
    private ImageView screen;
    private volatile boolean running;
    private Thread frameThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri romUri = getIntent().getData();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);

        screen = new ImageView(this);
        screen.setAdjustViewBounds(true);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(screen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        info = new TextView(this);
        info.setText("Preparing ROM...");
        info.setTextSize(13f);
        info.setGravity(Gravity.CENTER);
        root.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
        new Thread(() -> prepareAndStart(romUri)).start();
    }

    @Override
    protected void onDestroy() {
        running = false;
        NativeBridge.unloadRom();
        super.onDestroy();
    }

    private void prepareAndStart(Uri romUri) {
        if (romUri == null) {
            updateInfo("No ROM URI received.");
            return;
        }

        try {
            File localRom = copyRomToLocalFile(romUri);
            File systemDir = ensureDirectory("system");
            File saveDir = ensureDirectory("save");
            NativeBridge.setDirectories(systemDir.getAbsolutePath(), saveDir.getAbsolutePath());

            boolean loaded = NativeBridge.loadRom(localRom.getAbsolutePath());
            if (!loaded) {
                updateInfo("loadRom failed:\n" + NativeBridge.getLastError());
                return;
            }

            updateInfo("Running...\n" + NativeBridge.getLastError());
            startFrameLoop();
        } catch (Throwable t) {
            updateInfo("ROM prepare/load failed:\n" + t.getMessage());
        }
    }

    private void startFrameLoop() {
        if (running) {
            return;
        }
        running = true;
        frameThread = new Thread(() -> {
            long frame = 0;
            while (running) {
                long start = System.currentTimeMillis();
                boolean ok = NativeBridge.runFrame();
                if (ok) {
                    int width = NativeBridge.getFrameWidth();
                    int height = NativeBridge.getFrameHeight();
                    int[] pixels = NativeBridge.copyFramePixels();
                    if (width > 0 && height > 0 && pixels != null && pixels.length == width * height) {
                        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
                        runOnUiThread(() -> screen.setImageBitmap(bitmap));
                    }
                    frame++;
                    if (frame % 30 == 0) {
                        updateInfo(NativeBridge.getLastError());
                    }
                } else {
                    updateInfo("runFrame failed:\n" + NativeBridge.getLastError());
                    running = false;
                }

                long elapsed = System.currentTimeMillis() - start;
                long sleep = 16L - elapsed;
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        running = false;
                    }
                }
            }
        }, "VBAM-frame-loop");
        frameThread.start();
    }

    private File ensureDirectory(String name) throws Exception {
        File dir = new File(getFilesDir(), name);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    private File copyRomToLocalFile(Uri romUri) throws Exception {
        File dir = ensureDirectory("roms");
        String name = sanitizeFileName(getDisplayName(romUri));
        if (name.isEmpty()) {
            name = "selected.rom";
        }
        File outFile = new File(dir, name);
        try (InputStream input = getContentResolver().openInputStream(romUri);
             FileOutputStream output = new FileOutputStream(outFile)) {
            if (input == null) {
                throw new IllegalStateException("Could not open selected ROM.");
            }
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return outFile;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null) {
                        return name;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "selected.rom";
    }

    private String sanitizeFileName(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private void updateInfo(String message) {
        runOnUiThread(() -> info.setText(message));
    }
}
