package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SaveStateManager {
    static final int SLOT_COUNT = 10;
    static final int AUTO_SAVE_SLOT = 0;
    private static final String STATE_DIR_NAME = "KrendBuoy States";
    private final Activity activity;
    private final Uri portableSaveFolderUri;
    private final File fallbackStateRoot;
    private final String romBaseName;
    
    // Performance: Cache file references in the directory
    private final Map<String, Long> fileTimeCache = new HashMap<>();
    private final Set<String> filesExist = new HashSet<>();
    private boolean directoryScanned = false;

    SaveStateManager(Activity activity, Uri portableSaveFolderUri, File fallbackStateRoot, String romBaseName) {
        this.activity = activity;
        this.portableSaveFolderUri = portableSaveFolderUri;
        this.fallbackStateRoot = fallbackStateRoot;
        this.romBaseName = romBaseName == null || romBaseName.isEmpty() ? "selected" : romBaseName;
    }

    String slotLabel(int slot) {
        long modified = getModifiedTime(slot);
        String prefix = (slot == AUTO_SAVE_SLOT) ? "Auto-Save" : "Slot " + slot;
        if (modified <= 0) return prefix + " - Empty";
        return prefix + " - " + new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(modified));
    }

    void refreshDirectoryCache() {
        directoryScanned = false;
        fileTimeCache.clear();
        filesExist.clear();
        try {
            if (portableSaveFolderUri != null) {
                Uri dir = getOrCreatePortableStateDir();
                if (dir == null) return;
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(portableSaveFolderUri, DocumentsContract.getDocumentId(dir));
                try (Cursor cursor = activity.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String name = cursor.getString(0);
                            long time = cursor.getLong(1);
                            fileTimeCache.put(name, time);
                            filesExist.add(name);
                        }
                    }
                }
            } else {
                File dir = new File(fallbackStateRoot, sanitize(romBaseName));
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        fileTimeCache.put(f.getName(), f.lastModified());
                        filesExist.add(f.getName());
                    }
                }
            }
            directoryScanned = true;
        } catch (Throwable ignored) {}
    }

    long getModifiedTime(int slot) {
        if (!directoryScanned) refreshDirectoryCache();
        Long time = fileTimeCache.get(stateFileName(slot));
        return time != null ? time : 0;
    }

    Bitmap getThumbnail(int slot) {
        String thumbName = thumbnailFileName(slot);
        if (!directoryScanned) refreshDirectoryCache();
        if (!filesExist.contains(thumbName)) return null;

        try {
            if (portableSaveFolderUri != null) {
                Uri dir = getOrCreatePortableStateDir();
                Uri thumb = findChildDocument(dir, thumbName);
                if (thumb == null) return null;
                try (InputStream in = activity.getContentResolver().openInputStream(thumb)) {
                    return BitmapFactory.decodeStream(in);
                }
            }
            File file = new File(new File(fallbackStateRoot, sanitize(romBaseName)), thumbName);
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignored) {}
        return null;
    }

    boolean write(byte[] data, int slot, Bitmap thumbnail) throws Exception {
        if (data == null || data.length == 0) return false;
        if (portableSaveFolderUri != null) {
            Uri dir = getOrCreatePortableStateDir();
            if (dir == null) return false;
            
            // Save state
            Uri target = findChildDocument(dir, stateFileName(slot));
            if (target == null) target = DocumentsContract.createDocument(activity.getContentResolver(), dir, "application/octet-stream", stateFileName(slot));
            if (target == null) return false;
            try (OutputStream out = activity.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) return false;
                out.write(data);
                out.flush();
            }

            // Save thumbnail
            if (thumbnail != null) {
                Uri thumbTarget = findChildDocument(dir, thumbnailFileName(slot));
                if (thumbTarget == null) thumbTarget = DocumentsContract.createDocument(activity.getContentResolver(), dir, "image/png", thumbnailFileName(slot));
                if (thumbTarget != null) {
                    try (OutputStream out = activity.getContentResolver().openOutputStream(thumbTarget, "wt")) {
                        thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
                        out.flush();
                    }
                }
            }
            return true;
        }

        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        if (!dir.exists() && !dir.mkdirs()) return false;

        // Save state
        try (FileOutputStream out = new FileOutputStream(new File(dir, stateFileName(slot)))) {
            out.write(data);
            out.flush();
        }

        // Save thumbnail
        if (thumbnail != null) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, thumbnailFileName(slot)))) {
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
                out.flush();
            }
        }
        return true;
    }

    byte[] read(int slot) throws Exception {
        if (portableSaveFolderUri != null) {
            Uri dir = getOrCreatePortableStateDir();
            if (dir == null) return null;
            Uri state = findChildDocument(dir, stateFileName(slot));
            if (state == null) return null;
            return readUriBytes(state);
        }
        File file = new File(new File(fallbackStateRoot, sanitize(romBaseName)), stateFileName(slot));
        if (!file.exists()) return null;
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            return out.toByteArray();
        }
    }

    String stateFileName(int slot) {
        return sanitize(romBaseName) + ".slot" + slot + ".state";
    }

    String thumbnailFileName(int slot) {
        return sanitize(romBaseName) + ".slot" + slot + ".png";
    }

    private Uri getOrCreatePortableStateDir() throws Exception {
        Uri root = DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, DocumentsContract.getTreeDocumentId(portableSaveFolderUri));
        Uri existing = findChildDocument(root, STATE_DIR_NAME);
        if (existing != null) return existing;
        return DocumentsContract.createDocument(activity.getContentResolver(), root, DocumentsContract.Document.MIME_TYPE_DIR, STATE_DIR_NAME);
    }

    private Uri findChildDocument(Uri parent, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(portableSaveFolderUri, DocumentsContract.getDocumentId(parent));
        try (Cursor cursor = activity.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                if (name.equals(cursor.getString(0))) return DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, cursor.getString(1));
            }
        }
        return null;
    }

    private long findChildModifiedTime(Uri parent, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(portableSaveFolderUri, DocumentsContract.getDocumentId(parent));
        try (Cursor cursor = activity.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
            if (cursor == null) return 0;
            while (cursor.moveToNext()) if (name.equals(cursor.getString(0))) return cursor.getLong(1);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private byte[] readUriBytes(Uri uri) throws Exception {
        try (InputStream in = activity.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return null;
            copy(in, out);
            return out.toByteArray();
        }
    }

    private void copy(InputStream in, ByteArrayOutputStream out) throws Exception {
        byte[] buffer = new byte[16384];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
