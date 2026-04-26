package com.keltic.vbam;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class GameActivity extends Activity {
    private TextView info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri romUri = getIntent().getData();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("VBA-M Android Runtime");
        title.setTextSize(26f);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        info = new TextView(this);
        info.setText("Preparing ROM...");
        info.setTextSize(16f);
        info.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 32, 0, 0);
        root.addView(info, params);

        setContentView(root);

        new Thread(() -> prepareAndLoadRom(romUri)).start();
    }

    private void prepareAndLoadRom(Uri romUri) {
        if (romUri == null) {
            updateInfo("No ROM URI received.");
            return;
        }

        try {
            File localRom = copyRomToLocalFile(romUri);
            boolean loaded = NativeBridge.loadRom(localRom.getAbsolutePath());
            String message = "ROM copied to local storage:\n" +
                    localRom.getAbsolutePath() +
                    "\n\nNativeBridge.loadRom(): " +
                    (loaded ? "success" : "failed") +
                    "\n\nNext step:\nconnect this native load to retro_load_game() and start video rendering.";
            updateInfo(message);
        } catch (Throwable t) {
            updateInfo("ROM prepare/load failed:\n" + t.getMessage());
        }
    }

    private File copyRomToLocalFile(Uri romUri) throws Exception {
        File dir = new File(getFilesDir(), "roms");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create ROM directory: " + dir.getAbsolutePath());
        }

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
