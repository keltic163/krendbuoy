package com.keltic.vbam;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

        FrameLayout root = new FrameLayout(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(24, 24, 24, 24);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        screen = new ImageView(this);
        screen.setAdjustViewBounds(true);
        screen.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(screen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        info = new TextView(this);
        info.setText("Preparing ROM...");
        info.setTextSize(13f);
        info.setTextColor(Color.DKGRAY);
        info.setGravity(Gravity.CENTER);
        content.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addVirtualControls(root);
        setContentView(root);
        new Thread(() -> prepareAndStart(romUri)).start();
    }

    @Override
    protected void onPause() {
        releaseAllButtons();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        running = false;
        releaseAllButtons();
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

    private void addVirtualControls(FrameLayout root) {
        int keySize = dp(62);
        int smallWidth = dp(92);
        int smallHeight = dp(44);
        int margin = dp(18);
        int gap = dp(8);

        FrameLayout controls = new FrameLayout(this);
        controls.setClipChildren(false);
        controls.setClipToPadding(false);
        root.addView(controls, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        addControl(controls, "↑", NativeBridge.BUTTON_UP, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize + gap, margin + keySize * 2 + gap * 2);
        addControl(controls, "←", NativeBridge.BUTTON_LEFT, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin, margin + keySize + gap);
        addControl(controls, "→", NativeBridge.BUTTON_RIGHT, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize * 2 + gap * 2, margin + keySize + gap);
        addControl(controls, "↓", NativeBridge.BUTTON_DOWN, keySize, keySize,
                Gravity.BOTTOM | Gravity.LEFT, margin + keySize + gap, margin);

        addControl(controls, "B", NativeBridge.BUTTON_B, keySize, keySize,
                Gravity.BOTTOM | Gravity.RIGHT, margin + keySize + gap, margin + keySize + gap);
        addControl(controls, "A", NativeBridge.BUTTON_A, keySize, keySize,
                Gravity.BOTTOM | Gravity.RIGHT, margin, margin + keySize * 2 + gap * 2);

        addControl(controls, "L", NativeBridge.BUTTON_L, keySize, dp(48),
                Gravity.TOP | Gravity.LEFT, margin, margin);
        addControl(controls, "R", NativeBridge.BUTTON_R, keySize, dp(48),
                Gravity.TOP | Gravity.RIGHT, margin, margin);

        addControl(controls, "Select", NativeBridge.BUTTON_SELECT, smallWidth, smallHeight,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, smallWidth / 2 + gap, margin);
        addControl(controls, "Start", NativeBridge.BUTTON_START, smallWidth, smallHeight,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, -(smallWidth / 2 + gap), margin);
    }

    private void addControl(FrameLayout parent, String label, int button, int width, int height, int gravity, int rightOrLeftMargin, int bottomOrTopMargin) {
        TextView view = makeButton(label, button);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT) {
            lp.rightMargin = rightOrLeftMargin;
        } else if ((gravity & Gravity.LEFT) == Gravity.LEFT) {
            lp.leftMargin = rightOrLeftMargin;
        }
        if ((gravity & Gravity.TOP) == Gravity.TOP) {
            lp.topMargin = bottomOrTopMargin;
        } else {
            lp.bottomMargin = bottomOrTopMargin;
        }
        parent.addView(view, lp);
    }

    private TextView makeButton(String label, int button) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(label.length() > 1 ? 14f : 22f);
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(0xAA333333);
        view.setAlpha(0.85f);
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    NativeBridge.setButtonState(button, true);
                    v.setAlpha(1.0f);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL:
                    NativeBridge.setButtonState(button, false);
                    v.setAlpha(0.85f);
                    return true;
                default:
                    return true;
            }
        });
        return view;
    }

    private void releaseAllButtons() {
        for (int i = 0; i <= NativeBridge.BUTTON_R; i++) {
            NativeBridge.setButtonState(i, false);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
