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
    private static final String NO_MEDIA_FILE_NAME = ".nomedia";
    private final Activity activity;
    private final Uri portableSaveFolderUri;
    private final File fallbackStateRoot;
    private final String romBaseName;

    // Performance: Cache file references in the current ROM state directory.
    private final Map<String, Long> fileTimeCache = new HashMap<>();
    private final Map<String, Uri> portableFileMap = new HashMap<>();
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
        if (modified <= 0) return prefix + " - " + activity.getString(R.string.common_missing).toLowerCase();
        return prefix + " - " + new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(modified));
    }

    void refreshDirectoryCache() {
        directoryScanned = false;
        fileTimeCache.clear();
        filesExist.clear();
        portableFileMap.clear();
        try {
            if (portableSaveFolderUri != null) {
                Uri dir = getOrCreatePortableStateDir();
                if (dir == null) return;
                scanPortableDirectory(dir);
            } else {
                File root = fallbackStateRoot;
                ensureNoMedia(root);
                scanFallbackDirectory(new File(root, sanitize(romBaseName)));
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
                Uri thumb = portableFileMap.get(thumbName);
                if (thumb == null) return null;
                try (InputStream in = activity.getContentResolver().openInputStream(thumb)) {
                    return BitmapFactory.decodeStream(in);
                }
            }
            File dir = new File(fallbackStateRoot, sanitize(romBaseName));
            File thumb = new File(dir, thumbName);
            return thumb.exists() ? BitmapFactory.decodeFile(thumb.getAbsolutePath()) : null;
        } catch (Throwable ignored) {}
        return null;
    }

    boolean write(byte[] data, int slot, Bitmap thumbnail) throws Exception {
        if (data == null || data.length == 0) return false;
        String sName = stateFileName(slot);
        String tName = thumbnailFileName(slot);

        if (portableSaveFolderUri != null) {
            Uri dir = getOrCreatePortableStateDir();
            if (dir == null) return false;

            Uri target = portableFileMap.get(sName);
            if (target == null) target = DocumentsContract.createDocument(activity.getContentResolver(), dir, "application/octet-stream", sName);
            if (target == null) return false;
            try (OutputStream out = activity.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) return false;
                out.write(data);
                out.flush();
            }

            if (thumbnail != null) {
                Uri thumbTarget = portableFileMap.get(tName);
                if (thumbTarget == null) thumbTarget = DocumentsContract.createDocument(activity.getContentResolver(), dir, "application/octet-stream", tName);
                if (thumbTarget != null) {
                    try (OutputStream out = activity.getContentResolver().openOutputStream(thumbTarget, "wt")) {
                        thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
                        out.flush();
                    }
                }
            }
            refreshDirectoryCache();
            return true;
        }

        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        if (!dir.exists() && !dir.mkdirs()) return false;
        ensureNoMedia(fallbackStateRoot);
        ensureNoMedia(dir);

        try (FileOutputStream out = new FileOutputStream(new File(dir, sName))) {
            out.write(data);
            out.flush();
        }

        if (thumbnail != null) {
            try (FileOutputStream out = new FileOutputStream(new File(dir, tName))) {
                thumbnail.compress(Bitmap.CompressFormat.PNG, 90, out);
                out.flush();
            }
        }
        refreshDirectoryCache();
        return true;
    }

    byte[] read(int slot) throws Exception {
        String sName = stateFileName(slot);
        if (portableSaveFolderUri != null) {
            if (!directoryScanned) refreshDirectoryCache();
            Uri state = portableFileMap.get(sName);
            if (state == null) return null;
            return readUriBytes(state);
        }
        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        File file = new File(dir, sName);
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
        return sanitize(romBaseName) + ".slot" + slot + ".thumb";
    }

    private Uri getOrCreatePortableStateDir() throws Exception {
        Uri root = getOrCreatePortableStateRootDir();
        if (root == null) return null;
        ensureNoMedia(root);

        String romDirName = sanitize(romBaseName);
        Uri existing = findChildDocument(root, romDirName);
        Uri romDir = existing != null ? existing : DocumentsContract.createDocument(activity.getContentResolver(), root, DocumentsContract.Document.MIME_TYPE_DIR, romDirName);
        if (romDir != null) {
            ensureNoMedia(romDir);
        }
        return romDir;
    }

    private Uri getOrCreatePortableStateRootDir() throws Exception {
        Uri root = DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, DocumentsContract.getTreeDocumentId(portableSaveFolderUri));
        Uri existing = findChildDocument(root, STATE_DIR_NAME);
        if (existing != null) return existing;
        return DocumentsContract.createDocument(activity.getContentResolver(), root, DocumentsContract.Document.MIME_TYPE_DIR, STATE_DIR_NAME);
    }

    private void scanPortableDirectory(Uri dir) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(portableSaveFolderUri, DocumentsContract.getDocumentId(dir));
        try (Cursor cursor = activity.getContentResolver().query(children, new String[]{
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
        }, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (name == null || NO_MEDIA_FILE_NAME.equals(name)) continue;
                    long time = cursor.getLong(1);
                    String docId = cursor.getString(2);
                    
                    fileTimeCache.put(name, time);
                    filesExist.add(name);
                    portableFileMap.put(name, DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, docId));
                }
            }
        }
    }

    private void scanFallbackDirectory(File dir) {
        ensureNoMedia(dir);
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            fileTimeCache.put(f.getName(), f.lastModified());
            filesExist.add(f.getName());
        }
    }

    private void ensureNoMedia(Uri dir) {
        try {
            if (portableSaveFolderUri == null || dir == null) return;
            if (findChildDocument(dir, NO_MEDIA_FILE_NAME) == null) {
                DocumentsContract.createDocument(activity.getContentResolver(), dir, "application/octet-stream", NO_MEDIA_FILE_NAME);
            }
        } catch (Throwable ignored) {}
    }

    private void ensureNoMedia(File dir) {
        try {
            if (dir == null) return;
            if (!dir.exists()) dir.mkdirs();
            File noMedia = new File(dir, NO_MEDIA_FILE_NAME);
            if (!noMedia.exists()) noMedia.createNewFile();
        } catch (Throwable ignored) {}
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
        if (input == null) return "selected";
        String value = input.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        value = value.replaceAll("^[.]+", "_").trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) return "selected";
        return value;
    }
}
