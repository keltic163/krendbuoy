package com.krendstudio.krendbuoy;

import android.app.Activity;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class CheatManager {
    private final Activity activity;
    private final Uri portableSaveFolderUri;
    private final File fallbackRoot;
    private final String romBaseName;
    private final List<CheatEntry> cheats = new ArrayList<>();

    CheatManager(Activity activity, Uri portableSaveFolderUri, File fallbackRoot, String romBaseName) {
        this.activity = activity;
        this.portableSaveFolderUri = portableSaveFolderUri;
        this.fallbackRoot = fallbackRoot;
        this.romBaseName = romBaseName;
        load();
    }

    List<CheatEntry> getCheats() {
        return cheats;
    }

    void addCheat(String name, String code) {
        cheats.add(new CheatEntry(name, code, true));
        save();
        applyToCore();
    }

    void removeCheat(int index) {
        if (index >= 0 && index < cheats.size()) {
            cheats.remove(index);
            save();
            applyToCore();
        }
    }

    void toggleCheat(int index) {
        if (index >= 0 && index < cheats.size()) {
            cheats.get(index).enabled = !cheats.get(index).enabled;
            save();
            applyToCore();
        }
    }

    void updateCheat(int index, String name, String code) {
        if (index >= 0 && index < cheats.size()) {
            CheatEntry entry = cheats.get(index);
            entry.name = name;
            entry.code = code;
            save();
            applyToCore();
        }
    }

    void applyToCore() {
        NativeBridge.cheatReset();
        for (int i = 0; i < cheats.size(); i++) {
            CheatEntry entry = cheats.get(i);
            NativeBridge.cheatSet(i, entry.enabled, entry.code);
        }
    }

    private String getFileName() {
        return sanitize(romBaseName) + ".cheats.json";
    }

    private void load() {
        cheats.clear();
        try {
            byte[] data = null;
            if (portableSaveFolderUri != null) {
                Uri dir = getPortableDir();
                if (dir != null) {
                    Uri file = findChild(dir, getFileName());
                    if (file != null) data = readUri(file);
                }
            } else {
                File f = new File(new File(fallbackRoot, sanitize(romBaseName)), getFileName());
                if (f.exists()) data = readFile(f);
            }

            if (data != null) {
                JSONArray array = new JSONArray(new String(data));
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    cheats.add(new CheatEntry(obj.getString("name"), obj.getString("code"), obj.optBoolean("enabled", true)));
                }
            }
        } catch (Throwable ignored) {}
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (CheatEntry entry : cheats) {
                JSONObject obj = new JSONObject();
                obj.put("name", entry.name);
                obj.put("code", entry.code);
                obj.put("enabled", entry.enabled);
                array.put(obj);
            }
            byte[] data = array.toString().getBytes();

            if (portableSaveFolderUri != null) {
                Uri dir = getOrCreatePortableDir();
                if (dir == null) return;
                Uri target = findChild(dir, getFileName());
                if (target == null) target = DocumentsContract.createDocument(activity.getContentResolver(), dir, "application/json", getFileName());
                if (target == null) return;
                try (OutputStream out = activity.getContentResolver().openOutputStream(target, "wt")) {
                    if (out != null) out.write(data);
                }
            } else {
                File dir = new File(fallbackRoot, sanitize(romBaseName));
                if (!dir.exists() && !dir.mkdirs()) return;
                try (FileOutputStream out = new FileOutputStream(new File(dir, getFileName()))) {
                    out.write(data);
                }
            }
        } catch (Throwable ignored) {}
    }

    private Uri getPortableDir() {
        try {
            Uri root = DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, DocumentsContract.getTreeDocumentId(portableSaveFolderUri));
            return findChild(root, "KrendBuoy States");
        } catch (Throwable t) { return null; }
    }

    private Uri getOrCreatePortableDir() {
        try {
            Uri root = DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, DocumentsContract.getTreeDocumentId(portableSaveFolderUri));
            Uri existing = findChild(root, "KrendBuoy States");
            if (existing != null) return existing;
            return DocumentsContract.createDocument(activity.getContentResolver(), root, DocumentsContract.Document.MIME_TYPE_DIR, "KrendBuoy States");
        } catch (Throwable t) { return null; }
    }

    private Uri findChild(Uri parent, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(portableSaveFolderUri, DocumentsContract.getDocumentId(parent));
        try (android.database.Cursor cursor = activity.getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) if (name.equals(cursor.getString(0))) return DocumentsContract.buildDocumentUriUsingTree(portableSaveFolderUri, cursor.getString(1));
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private byte[] readUri(Uri uri) throws Exception {
        try (InputStream in = activity.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return null;
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private byte[] readFile(File f) throws Exception {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return out.toByteArray();
        }
    }

    private String sanitize(String input) {
        return input == null ? "" : input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
