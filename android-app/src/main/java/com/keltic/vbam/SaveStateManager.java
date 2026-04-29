package com.krendstudio.krendbuoy;

import android.app.Activity;
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

final class SaveStateManager {
    private static final String STATE_FOLDER_NAME = "KrendBuoy States";
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

    String slotLabel(int slot) { long modified = getModifiedTime(slot); return modified <= 0 ? "Slot " + slot + " - Empty" : "Slot " + slot + " - " + new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date(modified)); }
    long getModifiedTime(int slot) { return 0; }
    boolean write(byte[] data, int slot) throws Exception { return false; }
    byte[] read(int slot) throws Exception { return null; }
    String stateFileName(int slot) { return romBaseName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".slot" + slot + ".state"; }
}
