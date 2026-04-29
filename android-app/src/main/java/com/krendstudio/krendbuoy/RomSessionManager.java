package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

final class RomSessionManager {
    static final class Result {
        final boolean loaded;
        final String romBaseName;
        final String errorMessage;

        private Result(boolean loaded, String romBaseName, String errorMessage) {
            this.loaded = loaded;
            this.romBaseName = romBaseName;
            this.errorMessage = errorMessage;
        }

        static Result success(String romBaseName) {
            return new Result(true, romBaseName, null);
        }

        static Result failure(String message) {
            return new Result(false, "selected", message);
        }
    }

    private final Activity activity;

    RomSessionManager(Activity activity) {
        this.activity = activity;
    }

    Result load(Uri romUri) {
        if (romUri == null) return Result.failure("No ROM URI received.");
        try {
            String romDisplayName = getDisplayName(romUri);
            String romBaseName = removeKnownRomExtension(romDisplayName);
            File localRom = copyRomToLocalFile(romUri, romDisplayName);
            File systemDir = ensureDirectory("system");
            File saveDir = ensureDirectory("save");
            NativeBridge.setDirectories(systemDir.getAbsolutePath(), saveDir.getAbsolutePath());
            if (!NativeBridge.loadRom(localRom.getAbsolutePath())) {
                return Result.failure("loadRom failed:\n" + NativeBridge.getLastError());
            }
            return Result.success(romBaseName);
        } catch (Throwable t) {
            return Result.failure("ROM prepare/load failed:\n" + t.getMessage());
        }
    }

    File ensureDirectory(String name) {
        File dir = new File(activity.getFilesDir(), name);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private File copyRomToLocalFile(Uri uri, String displayName) throws Exception {
        String extension = getKnownRomExtension(displayName);
        File target = new File(activity.getCacheDir(), "selected-rom-" + System.currentTimeMillis() + extension);
        try (InputStream input = activity.getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IllegalStateException("Unable to open ROM input stream");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        return target;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Throwable ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null || path.isEmpty() ? "selected" : path;
    }

    private String removeKnownRomExtension(String name) {
        if (name == null || name.isEmpty()) return "selected";
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".gba") || lower.endsWith(".gbc")) return name.substring(0, name.length() - 4);
        if (lower.endsWith(".gb")) return name.substring(0, name.length() - 3);
        return name;
    }

    private String getKnownRomExtension(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".gba")) return ".gba";
        if (lower.endsWith(".gbc")) return ".gbc";
        if (lower.endsWith(".gb")) return ".gb";
        return "";
    }
}
