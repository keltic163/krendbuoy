package com.krendstudio.krendbuoy;

import android.content.ContentResolver;
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

/**
 * Handles save-state file naming, reading, writing, and slot timestamps.
 *
 * This class does not call NativeBridge directly. GameActivity remains
 * responsible for exporting/importing emulator state bytes, while this class
 * owns storage details.
 */
final class SaveStateManager {
    private static final String STATE_FOLDER_NAME = "KrendBuoy States";

    private final GameActivity activity;
    private final Uri portableSaveFolderUri;
    private final File fallbackStateRoot;
    private final String romBaseName;

    SaveStateManager(GameActivity activity, Uri portableSaveFolderUri, File fallbackStateRoot, String romBaseName) {
        this.activity = activity;
        this.portableSaveFolderUri = portableSaveFolderUri;
        this.fallbackStateRoot = fallbackStateRoot;
        this.romBaseName = romBaseName == null || romBaseName.isEmpty() ? "selected" : romBaseName;
    }

    String slotLabel(int slot) {
        long modified = getModifiedTime(slot);
        if (modified <= 0) return "Slot " + slot + " - Empty";
        return "Slot " + slot + " - " + formatTimestamp(modified);
    }

    long getModifiedTime(int slot) {
        try {
            if (portableSaveFolderUri != null) {
                Uri stateDir = getOrCreatePortableStateFolder();
                if (stateDir == null) return 0;
                return findChildModifiedTimeIn(stateDir, stateFileName(slot));
            }
            File stateDir = new File(fallbackStateRoot, sanitizeFileName(romBaseName));
            File inFile = new File(stateDir, stateFileName(slot));
            return inFile.exists() ? inFile.lastModified() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    boolean write(byte[] data, int slot) throws Exception {
        if (data == null || data.length == 0) return false;
        if (portableSaveFolderUri != null) {
            Uri stateDir = getOrCreatePortableStateFolder();
            if (stateDir == null) return false;
            Uri target = findChildDocumentIn(stateDir, stateFileName(slot));
            if (target == null) target = createChildDocumentIn(stateDir, stateFileName(slot), "application/octet-stream");
            if (target == null) return false;
            try (OutputStream output = activity.getContentResolver().openOutputStream(target, "wt")) {
                if (output == null) return false;
                output.write(data);
                output.flush();
                return true;
            }
        }

        File stateDir = new File(fallbackStateRoot, sanitizeFileName(romBaseName));
        if (!stateDir.exists() && !stateDir.mkdirs()) return false;
        File outFile = new File(stateDir, stateFileName(slot));
        try (FileOutputStream output = new FileOutputStream(outFile)) {
            output.write(data);
            output.flush();
            return true;
        }
    }

    byte[] read(int slot) throws Exception {
        if (portableSaveFolderUri != null) {
            Uri stateDir = getOrCreatePortableStateFolder();
            if (stateDir == null) return null;
            Uri state = findChildDocumentIn(stateDir, stateFileName(slot));
            if (state == null) return null;
            return readAllBytes(state);
        }

        File stateDir = new File(fallbackStateRoot, sanitizeFileName(romBaseName));
        File inFile = new File(stateDir, stateFileName(slot));
        if (!inFile.exists()) return null;
        try (InputStream input = new FileInputStream(inFile);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024 * 16];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    String stateFileName(int slot) {
        return sanitizeFileName(romBaseName) + ".slot" + slot + ".state";
    }

    private Uri getOrCreatePortableStateFolder() throws Exception {
        if (portableSaveFolderUri == null) return null;
        Uri root = DocumentsContract.buildDocumentUriUsingTree(
                portableSaveFolderUri,
                DocumentsContract.getTreeDocumentId(portableSaveFolderUri)
        );
        Uri existing = findChildDocumentIn(root, STATE_FOLDER_NAME);
        if (existing != null) return existing;
        return createChildDocumentIn(root, STATE_FOLDER_NAME, DocumentsContract.Document.MIME_TYPE_DIR);
    }

    private Uri findChildDocumentIn(Uri parentDocumentUri, String fileName) {
        if (portableSaveFolderUri == null || parentDocumentUri == null) return null;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                portableSaveFolderUri,
                DocumentsContract.getDocumentId(parentDocumentUri)
        );
        try (Cursor cursor = activity.getContentResolver().query(
                childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                null,
                null,
                null
        )) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String documentId = cursor.getString(1);
                if (fileName.equals(name)) {
                    return DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, documentId);
                }
            }
        }
        return null;
    }

    private long findChildModifiedTimeIn(Uri parentDocumentUri, String fileName) {
        if (portableSaveFolderUri == null || parentDocumentUri == null) return 0;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                portableSaveFolderUri,
                DocumentsContract.getDocumentId(parentDocumentUri)
        );
        try (Cursor cursor = activity.getContentResolver().query(
                childrenUri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                null,
                null,
                null
        )) {
            if (cursor == null) return 0;
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (fileName.equals(name)) return cursor.getLong(1);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private Uri createChildDocumentIn(Uri parentDocumentUri, String fileName, String mimeType) throws Exception {
        if (parentDocumentUri == null) return null;
        return DocumentsContract.createDocument(
                activity.getContentResolver(),
                parentDocumentUri,
                mimeType,
                fileName
        );
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        ContentResolver resolver = activity.getContentResolver();
        try (InputStream input = resolver.openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return null;
            byte[] buffer = new byte[1024 * 16];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }

    private String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private String sanitizeFileName(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
