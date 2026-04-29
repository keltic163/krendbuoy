package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.database.Cursor;
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
import java.util.Locale;

final class SaveStateManager {
    private static final String STATE_DIR_NAME = "KrendBuoy States";
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
        try {
            if (portableSaveFolderUri != null) {
                Uri dir = getOrCreatePortableStateDir();
                return dir == null ? 0 : findChildModifiedTime(dir, stateFileName(slot));
            }
            File file = new File(new File(fallbackStateRoot, sanitize(romBaseName)), stateFileName(slot));
            return file.exists() ? file.lastModified() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    boolean write(byte[] data, int slot) throws Exception {
        if (data == null || data.length == 0) return false;
        if (portableSaveFolderUri != null) {
            Uri dir = getOrCreatePortableStateDir();
            if (dir == null) return false;
            Uri target = findChildDocument(dir, stateFileName(slot));
            if (target == null) target = DocumentsContract.createDocument(activity.getContentResolver(), dir, "application/octet-stream", stateFileName(slot));
            if (target == null) return false;
            try (OutputStream out = activity.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) return false;
                out.write(data);
                out.flush();
                return true;
            }
        }
        File dir = new File(fallbackStateRoot, sanitize(romBaseName));
        if (!dir.exists() && !dir.mkdirs()) return false;
        try (FileOutputStream out = new FileOutputStream(new File(dir, stateFileName(slot)))) {
            out.write(data);
            out.flush();
            return true;
        }
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
