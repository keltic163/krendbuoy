package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class SaveStateManager {
    private final Activity activity;
    private final Uri portableSaveFolderUri;
    private final File fallbackStateRoot;
    private final String romBaseName;

    SaveStateManager(Activity activity, Uri portableSaveFolderUri, File fallbackStateRoot, String romBaseName) {
        this.activity = activity;
        this.portableSaveFolderUri = portableSaveFolderUri;
        this.fallbackStateRoot = fallbackStateRoot;
        this.romBaseName = romBaseName == null || romBaseName.isEmpty() ? "selected" : romBaseName;
    }

    String slotLabel(int slot) {
        long modified = getModifiedTime(slot);
        if (modified <= 0) return "Slot " + slot + " - Empty";
        return "Slot " + slot + " - " + new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(modified));
    }

    long getModifiedTime(int slot) {
        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        File file = new File(dir, stateFileName(slot));
        return file.exists() ? file.lastModified() : 0;
    }

    boolean write(byte[] data, int slot) throws Exception {
        if (data == null || data.length == 0) return false;
        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        if (!dir.exists() && !dir.mkdirs()) return false;
        File file = new File(dir, stateFileName(slot));
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
            out.flush();
            return true;
        }
    }

    byte[] read(int slot) throws Exception {
        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        File file = new File(dir, stateFileName(slot));
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    String stateFileName(int slot) {
        return sanitize(romBaseName) + ".slot" + slot + ".state";
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
