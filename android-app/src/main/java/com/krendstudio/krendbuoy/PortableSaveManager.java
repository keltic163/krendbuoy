package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

final class PortableSaveManager {
    interface Host {
        void updatePortableSaveInfo(String text);
    }

    private final Activity activity;
    private final Uri portableSaveFolderUri;
    private final Host host;

    PortableSaveManager(Activity activity, Uri portableSaveFolderUri, Host host) {
        this.activity = activity;
        this.portableSaveFolderUri = portableSaveFolderUri;
        this.host = host;
    }

    void importIfAvailable(String romBaseName) {
        if (portableSaveFolderUri == null) return;
        try {
            Uri sav = findChildDocument(romBaseName + ".sav");
            Uri srm = sav != null ? sav : findChildDocument(romBaseName + ".srm");
            if (srm == null) return;
            byte[] data = readAllBytes(srm);
            if (data != null && data.length > 0) {
                boolean ok = NativeBridge.importSram(data);
                if (host != null) host.updatePortableSaveInfo(ok ? "Portable save loaded: " + romBaseName : "Portable save import failed");
            }
        } catch (Throwable t) {
            if (host != null) host.updatePortableSaveInfo("Portable save load failed:\n" + t.getMessage());
        }
    }

    void exportIfEnabled(String romBaseName) {
        if (portableSaveFolderUri == null) return;
        try {
            byte[] data = NativeBridge.exportSram();
            if (data == null || data.length == 0) return;
            Uri target = findChildDocument(romBaseName + ".sav");
            if (target == null) target = createChildDocument(romBaseName + ".sav");
            if (target == null) return;
            try (OutputStream output = activity.getContentResolver().openOutputStream(target, "wt")) {
                if (output != null) {
                    output.write(data);
                    output.flush();
                }
            }
        } catch (Throwable t) {
            if (host != null) host.updatePortableSaveInfo("Portable save write failed:\n" + t.getMessage());
        }
    }

    private Uri findChildDocument(String fileName) {
        if (portableSaveFolderUri == null) return null;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                portableSaveFolderUri,
                DocumentsContract.getTreeDocumentId(portableSaveFolderUri)
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
                if (fileName.equals(cursor.getString(0))) {
                    return DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, cursor.getString(1));
                }
            }
        }
        return null;
    }

    private Uri createChildDocument(String fileName) throws Exception {
        if (portableSaveFolderUri == null) return null;
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(
                portableSaveFolderUri,
                DocumentsContract.getTreeDocumentId(portableSaveFolderUri)
        );
        return DocumentsContract.createDocument(
                activity.getContentResolver(),
                parent,
                "application/octet-stream",
                fileName
        );
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        try (InputStream input = activity.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) return null;
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }
}
